/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.app.ActivityThread;
import android.app.Application;
import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.content.res.ApkAssets;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.gmscompat.GmsInfo;
import com.android.internal.gmscompat.dynamite.client.ModuleLoadState;
import com.android.internal.gmscompat.dynamite.client.DynamiteContext;
import com.android.internal.gmscompat.dynamite.server.FileProxyProvider;

import dalvik.system.DelegateLastClassLoader;
import dalvik.system.DexPathList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hooks specific to Dynamite module compatibility.
 *
 * @hide
 */
public final class GmsDynamiteHooks {
    // Created lazily because most apps don't use Dynamite modules
    private static DynamiteContext clientContext = null;

    private GmsDynamiteHooks() { }

    private static DynamiteContext getClientContext() {
        if (clientContext != null) {
            return clientContext;
        }

        Context context = Objects.requireNonNull(ActivityThread.currentApplication());
        clientContext = new DynamiteContext(context);
        return clientContext;
    }

    public static void initClientApp() {
        // Install hooks (requires libcore changes)
        DexPathList.postConstructorBufferHook = GmsDynamiteHooks::getDexPathListBuffers;
        File.lastModifiedHook = GmsDynamiteHooks::getFileLastModified;
        DelegateLastClassLoader.librarySearchPathHook = GmsDynamiteHooks::mapRemoteLibraryPaths;
    }

    public static void initGmsServerApp(Application app) {
        // Main GMS process only, to avoid serving proxy requests multiple times.
        // This is specifically the main process, not persistent, because
        // com.google.android.gms.chimera.container.FileApkIntentOperation$ExternalFileApkService
        // is in the main process and thus the process is guaranteed to start before
        // DelegateLastClassLoader requests the file proxy service.
        if (!Process.isIsolated() && GmsInfo.PACKAGE_GMS.equals(Application.getProcessName())) {
            FileProxyProvider.register(app);
        }
    }

    // For Android assets and resources
    // ApkAssets#loadFromPath(String, int)
    public static ApkAssets loadAssetsFromPath(String path, int flags) throws IOException {
        if (!GmsCompat.isDynamiteClient()) {
            return null;
        }

        ModuleLoadState state = getClientContext().getState();
        if (state == null || !state.modulePath.equals(path)) {
            return null;
        }

        Log.d(DynamiteContext.TAG, "Replacing " + path + " -> fd " + state.moduleFd.getInt$());
        return ApkAssets.loadFromFd(state.moduleFd, path, flags, null);
    }

    // For Java code
    // DexPathList(ClassLoader, String, String, File, boolean)
    private static ByteBuffer[] getDexPathListBuffers(DexPathList pathList) {
        if (!GmsCompat.isDynamiteClient()) {
            return null;
        }

        ModuleLoadState state = getClientContext().getState();
        if (state == null) {
            return null;
        }

        ByteBuffer[] buffers;
        try {
            buffers = state.mapDexBuffers();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Log.d(DynamiteContext.TAG, "Creating class loader with " + buffers.length + " dex buffer(s)");

        // Undo path init and re-initialize with the ByteBuffers
        return buffers;
    }

    // To fix false-positive "Module APK has been modified" check
    // File#lastModified()
    private static Long getFileLastModified(File file) {
        if (!GmsCompat.isDynamiteClient()) {
            return null;
        }

        ModuleLoadState state = getClientContext().getState();
        if (state == null || !state.modulePath.equals(file.getPath())) {
            return null;
        }

        long lastModified;
        try {
            lastModified = getClientContext().getService().getLastModified(file.getPath());
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
        Log.d(DynamiteContext.TAG, "File " + file.getPath() + " lastModified=" + lastModified);

        // This is the final hook in the module loading process, so clear the state.
        getClientContext().setState(null);

        Log.d(DynamiteContext.TAG, "Finished loading module " + state.modulePath);
        return lastModified;
    }

    // To start the module loading process and map native library paths to fd from remote
    public static String mapRemoteLibraryPaths(String librarySearchPath) {
        if (!GmsCompat.isDynamiteClient() || librarySearchPath == null) {
            return librarySearchPath;
        }

        String[] searchPaths = librarySearchPath.split(Pattern.quote(File.pathSeparator));

        List<String> newPaths = Arrays.stream(searchPaths).map(libPath -> {
            if (!libPath.startsWith(getClientContext().gmsDataPrefix)) {
                return libPath;
            }

            Log.d(DynamiteContext.TAG, "Loading module: " + libPath);
            String[] libComponents = libPath.split("!");
            String path = libComponents[0];

            // Ask GMS to open the file and return a PFD. Be careful with ownership.
            ParcelFileDescriptor srcPfd;
            try {
                srcPfd = getClientContext().getService().openFile(path);
            } catch (RemoteException e) {
                throw e.rethrowAsRuntimeException();
            }
            if (srcPfd == null) {
                throw new RuntimeException(new FileNotFoundException(path));
            }

            // For ApkAssets, DexPathList, File#lastModified()
            Log.d(DynamiteContext.TAG, "Received remote fd: " + path + " -> " + srcPfd.getFd());
            ModuleLoadState state = new ModuleLoadState(path, srcPfd.getFileDescriptor());
            getClientContext().setState(state);

            // Native code dups the fd each time it loads a lib
            String fdPath = "/proc/self/fd/" + srcPfd.getFd();
            libComponents[0] = fdPath;

            // Re-combine the path with native library components
            return String.join("!", libComponents);
        }).collect(Collectors.toList());

        return String.join(File.pathSeparator, newPaths);
    }
}
