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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** @hide */
public final class FileProxyService extends IFileProxyService.Stub {
    public static final String TAG = "GmsCompat/DynamiteServer";
    private static final String CHIMERA_REL_PATH = "app_chimera/m/";

    private final File deDataRoot;
    private final String chimeraRoot;

    public FileProxyService(Context context) {
        deDataRoot = context.createDeviceProtectedStorageContext().getDataDir();
        chimeraRoot = deDataRoot.getPath() + "/" + CHIMERA_REL_PATH;
    }

    private String sanitizeModulePath(String rawPath) {
        // Normalize path for security checks
        String path;
        try {
            path = new File(rawPath).getCanonicalPath();
        } catch (IOException e) {
            throw new SecurityException("Invalid path " + rawPath + ": " + e.getMessage());
        }

        // Modules can only be in DE Chimera storage
        if (!path.startsWith(chimeraRoot)) {
            throw new SecurityException("Path " + rawPath + " is not in " + chimeraRoot);
        }

        // Check permissions recursively
        String relPath = path.substring(deDataRoot.getPath().length() + 1); // already checked prefix above
        List<String> relParts = Arrays.asList(relPath.split("/"));
        for (int i = 0; i < relParts.size(); i++) {
            List<String> leadingParts = relParts.subList(0, i + 1);
            String nodePath = deDataRoot + "/" + String.join("/", leadingParts);
            int mode;
            try {
                mode = Os.stat(nodePath).st_mode;
            } catch (ErrnoException e) {
                throw new SecurityException("Failed to stat " + rawPath + ": " + e.getMessage());
            }

            // World-readable or world-executable, depending on type
            boolean isDir = new File(nodePath).isDirectory();
            int permBit = isDir ? OsConstants.S_IXOTH : OsConstants.S_IROTH;
            if ((mode & permBit) == 0) {
                throw new SecurityException("Node " + nodePath + " in path " + rawPath + " is not world-readable");
            }
        }

        return path;
    }

    private ParcelFileDescriptor getFilePfd(String path) {
        Log.d(TAG, "Opening " + path + " for remote");
        try {
            return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(String rawPath) {
        return getFilePfd(sanitizeModulePath(rawPath));
    }

    @Override
    public long getLastModified(String rawPath) {
        return new File(sanitizeModulePath(rawPath)).lastModified();
    }
}
