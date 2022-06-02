/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.gmscompat.dynamite;

import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.content.res.ApkAssets;
import android.content.res.loader.AssetsProvider;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatApp;
import com.android.internal.gmscompat.GmsInfo;
import com.android.internal.gmscompat.dynamite.server.IFileProxyService;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.regex.Pattern;

import dalvik.system.DelegateLastClassLoader;

public final class GmsDynamiteClientHooks {
    static final String TAG = "GmsCompat/DynamiteClient";
    private static final boolean DEBUG = false;

    // written last in the init sequence, "volatile" to publish all the preceding writes
    private static volatile boolean enabled;
    private static String gmsCoreDataPrefix;
    private static ArrayMap<String, ParcelFileDescriptor> pfdCache;
    private static IFileProxyService fileProxyService;

    public static boolean enabled() {
        return enabled;
    }

    // ContentResolver#acquireProvider(Uri)
    public static void maybeInit(String auth) {
        if (!"com.google.android.gms.chimera".equals(auth)) {
            return;
        }
        synchronized (GmsDynamiteClientHooks.class) {
            if (enabled()) {
                return;
            }
            if (!GmsCompat.isClientOfGmsCore()) {
                return;
            }
            // faster than ctx.createPackageContext().createDeviceProtectedStorageContext().getDataDir()
            int userId = GmsCompat.appContext().getUserId();
            String deDataDirectory = Environment.getDataUserDeDirectory(null, userId).getPath();
            gmsCoreDataPrefix = deDataDirectory + '/' + GmsInfo.PACKAGE_GMS_CORE + '/';
            pfdCache = new ArrayMap<>(20);

            try {
                IFileProxyService service = GmsCompatApp.iClientOfGmsCore2Gca().getDynamiteFileProxyService();
                service.asBinder().linkToDeath(() -> {
                    // When GMS Core gets terminated (including package updates and crashes),
                    // processes of Dynamite clients get terminated too (same behavior on stock OS,
                    // likely to avoid hard-to-resolve situation when client starts to load
                    // modules from one GMS Core version and then GMS Core gets updated before the rest of the
                    // modules are loaded).
                    // This ensures that pfdCache never returns stale file descriptors,
                    // because there's only two types of Dynamite modules:
                    // - "core", included with the GMS Core package and always extracted
                    // to the app_chimera/m directory, may have the same name on different GMS Core versions
                    // - on-demand, downloaded on first use, each version has a unique file name

                    Log.d(TAG, "FileProxyService died");
                    // isn't reached in practice, at least on current versions (2022 Q1)
                    System.exit(0);
                }, 0);

                fileProxyService = service;
            } catch (Throwable e) {
                // linkToDeath() failed,
                // most likely because GMS Core crashed very shortly before getDynamiteFileProxyService(),
                // which should be very rare in practice.
                // Waiting for GMS Core to respawn is hard to do correctly, not worth the complexity increase
                Log.e(TAG, "unable to obtain the FileProxyService", e);
                System.exit(1);
            }

            File.lastModifiedHook = GmsDynamiteClientHooks::getFileLastModified;
            DelegateLastClassLoader.modifyClassLoaderPathHook = GmsDynamiteClientHooks::maybeModifyClassLoaderPath;
            enabled = true;
        }
    }

    // ApkAssets#loadFromPath(String, int, AssetsProvider)
    public static ApkAssets loadAssetsFromPath(String path, int flags, AssetsProvider assets) throws IOException {
        if (!path.startsWith(gmsCoreDataPrefix)) {
            return null;
        }
        FileDescriptor fd = modulePathToFd(path);
        // no need to dup the fd, ApkAssets does it itself
        return ApkAssets.loadFromFd(fd, path, flags, assets);
    }

    // To fix false-positive "Module APK has been modified" check
    // File#lastModified()
    public static long getFileLastModified(File file) {
        final String path = file.getPath();

        if (enabled && path.startsWith(gmsCoreDataPrefix)) {
            String fdPath = "/proc/self/fd/" + modulePathToFd(path).getInt$();
            return new File(fdPath).lastModified();
        }
        return 0L;
    }

    // Replaces file paths of Dynamite modules with "/proc/self/fd" file descriptor references
    // DelegateLastClassLoader#maybeModifyClassLoaderPath(String, Boolean)
    public static String maybeModifyClassLoaderPath(String path, Boolean nativeLibsPathB) {
        if (path == null) {
            return null;
        }
        if (!enabled) { // libcore code doesn't have access to this field
            return path;
        }
        boolean nativeLibsPath = nativeLibsPathB.booleanValue();
        String[] pathParts = path.split(Pattern.quote(File.pathSeparator));
        boolean modified = false;

        for (int i = 0; i < pathParts.length; ++i) {
            String pathPart = pathParts[i];
            if (!pathPart.startsWith(gmsCoreDataPrefix)) {
                continue;
            }
            // defined in bionic/linker/linker_utils.cpp kZipFileSeparator
            final String zipFileSeparator = "!/";

            String filePath;
            String nativeLibRelPath;
            if (nativeLibsPath) {
                int idx = pathPart.indexOf(zipFileSeparator);
                filePath = pathPart.substring(0, idx);
                nativeLibRelPath = pathPart.substring(idx + zipFileSeparator.length());
            } else {
                filePath = pathPart;
                nativeLibRelPath = null;
            }
            String fdFilePath = "/gmscompat_fd_" + modulePathToFd(filePath).getInt$();

            pathParts[i] = nativeLibsPath ?
                fdFilePath + zipFileSeparator + nativeLibRelPath :
                fdFilePath;

            modified = true;
        }
        if (!modified) {
            return path;
        }
        return String.join(File.pathSeparator, pathParts);
    }

    // Returned file descriptor should never be closed, because it may be dup()-ed at any time by the native code
    private static FileDescriptor modulePathToFd(String path) {
        if (DEBUG) {
            new Exception("path " + path).printStackTrace();
        }
        try {
            ArrayMap<String, ParcelFileDescriptor> cache = pfdCache;
            // this lock isn't contended, favor simplicity, not making the critical section shorter
            synchronized (cache) {
                ParcelFileDescriptor pfd = cache.get(path);
                if (pfd == null) {
                    pfd = fileProxyService.openFile(path);
                    if (pfd == null) {
                        throw new IllegalStateException("unable to open " + path);
                    }
                    // ParcelFileDescriptor owns the underlying file descriptor
                    cache.put(path, pfd);
                }
                return pfd.getFileDescriptor();
            }
        } catch (RemoteException e) {
            // FileProxyService never forwards exceptions to minimize the information leaks,
            // this is a very rare "binder died" exception
            throw e.rethrowAsRuntimeException();
        }
    }

    private GmsDynamiteClientHooks() {}
}
