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
import android.app.compat.gms.GmsCompat;
import android.util.Log;

import com.android.internal.app.StorageScopesAppHooks;
import com.android.internal.gmscompat.GmsHooks;

import static android.content.pm.GosPackageState.*;

/** @hide */
public class AppPermissionUtils {

    // If the list of spoofed permissions changes at runtime, make sure to invalidate the permission
    // check cache, it's keyed on the PermissionManager.CACHE_KEY_PACKAGE_INFO system property.
    // Updates of GosPackageState invalidate this cache automatically.
    //
    // android.permission.PermissionManager#checkPermissionUncached
    public static boolean shouldSpoofSelfCheck(String permName) {
        if (StorageScopesAppHooks.shouldSpoofSelfPermissionCheck(permName)) {
            return true;
        }

        if (Manifest.permission.INTERNET.equals(permName)
                && SpecialRuntimePermAppUtils.requestsInternetPermission()
                && !SpecialRuntimePermAppUtils.awareOfRuntimeInternetPermission())
        {
            SpecialRuntimePermAppUtils.isInternetPermissionCheckSpoofed = true;
            return true;
        }

        if (GmsCompat.isEnabled()) {
            if (GmsHooks.config().shouldSpoofSelfPermissionCheck(permName)) {
                return true;
            }
        }

        return false;
    }

    // android.app.AppOpsManager#checkOpNoThrow
    // android.app.AppOpsManager#noteOpNoThrow
    // android.app.AppOpsManager#noteProxyOpNoThrow
    // android.app.AppOpsManager#unsafeCheckOpRawNoThrow
    public static boolean shouldSpoofSelfAppOpCheck(int op) {
        if (StorageScopesAppHooks.shouldSpoofSelfAppOpCheck(op)) {
            return true;
        }

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
