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

package com.android.internal.gmscompat.dynamite.server;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

public final class FileProxyService extends IFileProxyService.Stub {
    public static final String TAG = "GmsCompat/DynamiteServer";
    private static final String CHIMERA_REL_PATH = "app_chimera/m/";

    private final String chimeraRoot;

    public FileProxyService(Context context) {
        File deDataRoot = context.createDeviceProtectedStorageContext().getDataDir();
        chimeraRoot = deDataRoot.getPath() + "/" + CHIMERA_REL_PATH;
    }

    @Override
    public ParcelFileDescriptor openFile(String rawPath) {
        try {
            String path = sanitizeModulePath(rawPath);
            if (path != null) {
                FileDescriptor fd = Os.open(path, OsConstants.O_RDONLY | OsConstants.O_CLOEXEC, 0);
//                Log.d(TAG, "Opened " + rawPath + " for remote, fd " + fd.getInt$());
                return new ParcelFileDescriptor(fd);
            }
        } catch (IOException | ErrnoException e) {
            Log.d(TAG, "failed security check", e);
        } catch (Throwable t) {
            Log.d(TAG, "unexpected error", t);
        }
        // don't forward exceptions to the untrusted caller to minimize the information leaks
        return null;
    }

    private String sanitizeModulePath(String rawPath) throws IOException, ErrnoException {
        // Normalize path for security checks
        String path = new File(rawPath).getCanonicalPath();

        // Modules can only be in DE Chimera storage
        if (!path.startsWith(chimeraRoot)) {
            Log.d(TAG, "Path " + rawPath + " is not in " + chimeraRoot);
            return null;
        }

        if (!path.endsWith(".apk")) {
            Log.d(TAG, "Path " + rawPath + " is not an APK file");
            return null;
        }
        // Make sure that all path components below chimeraRoot are world-accessible
        {
            // Check full path first to simplify checks of its parents
            int mode = Os.stat(path).st_mode;

            boolean valid = OsConstants.S_ISREG(mode) && (mode & OsConstants.S_IROTH) != 0;
            if (!valid) {
                Log.d(TAG, "Path " + path + " is not a world-readable regular file");
                return null;
            }
        }
        for (int i = chimeraRoot.length(), m = path.length(); i < m; ++i) {
            if (path.charAt(i) != '/') {
                continue;
            }
            String dirPath = path.substring(0, i);
            int mode = Os.stat(dirPath).st_mode;

            boolean valid = OsConstants.S_ISDIR(mode) && (mode & OsConstants.S_IXOTH) != 0;
            if (!valid) {
                Log.d(TAG, "Node " + dirPath + " in path " + path + " is not a world-readable directory");
                return null;
            }
        }
        return path;
    }
}
