/*
 * Copyright (C) 2022 GrapheneOS
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

package android.content.pm;

import android.Manifest;

import com.android.internal.app.StorageScopesAppHooks;

import static android.content.pm.GosPackageState.*;

/** @hide */
public class AppPermissionUtils {

    // android.app.ApplicationPackageManager#checkPermission(String permName, String pkgName)
    // android.app.ContextImpl#checkPermission(String permission, int pid, int uid)
    public static boolean shouldSpoofSelfCheck(String permName) {
        if (StorageScopesAppHooks.shouldSpoofSelfPermissionCheck(permName)) {
            return true;
        }

        if (Manifest.permission.INTERNET.equals(permName)
                && SpecialRuntimePermAppUtils.requestsInternetPermission()
                && !SpecialRuntimePermAppUtils.awareOfRuntimeInternetPermission())
        {
            return true;
        }

        return false;
    }

    // android.app.AppOpsManager#checkOpNoThrow
    // android.app.AppOpsManager#noteOpNoThrow
    // android.app.AppOpsManager#noteProxyOpNoThrow
    public static boolean shouldSpoofSelfAppOpCheck(int op) {
        return false;
    }

    public static int getSpoofableStorageRuntimePermissionDflag(String permName) {
        switch (permName) {
            case Manifest.permission.READ_EXTERNAL_STORAGE:
                return DFLAG_HAS_READ_EXTERNAL_STORAGE_DECLARATION;

            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return DFLAG_HAS_WRITE_EXTERNAL_STORAGE_DECLARATION;

            case Manifest.permission.ACCESS_MEDIA_LOCATION:
                return DFLAG_HAS_ACCESS_MEDIA_LOCATION_DECLARATION;

            case Manifest.permission.READ_MEDIA_AUDIO:
                return DFLAG_HAS_READ_MEDIA_AUDIO_DECLARATION;

            case Manifest.permission.READ_MEDIA_IMAGES:
                return DFLAG_HAS_READ_MEDIA_IMAGES_DECLARATION;

            case Manifest.permission.READ_MEDIA_VIDEO:
                return DFLAG_HAS_READ_MEDIA_VIDEO_DECLARATION;

            default:
                return 0;
        }
    }
}
