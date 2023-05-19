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

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.compat.gms.GmsCompat;

import com.android.internal.app.ContactScopes;
import com.android.internal.app.StorageScopesAppHooks;
import com.android.internal.gmscompat.GmsHooks;

import static android.content.pm.GosPackageState.*;

/** @hide */
@SystemApi
public class AppPermissionUtils {

    // If the list of spoofed permissions changes at runtime, make sure to invalidate the permission
    // check cache, it's keyed on the PermissionManager.CACHE_KEY_PACKAGE_INFO system property.
    // Updates of GosPackageState invalidate this cache automatically.
    //
    // android.permission.PermissionManager#checkPermissionUncached
    /** @hide */
    public static boolean shouldSpoofSelfCheck(String permName) {
        if (StorageScopesAppHooks.shouldSpoofSelfPermissionCheck(permName)) {
            return true;
        }

        if (SrtPermissions.shouldSpoofSelfCheck(permName)) {
            return true;
        }

        if (ContactScopes.shouldSpoofSelfPermissionCheck(permName)) {
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
    /** @hide */
    public static boolean shouldSpoofSelfAppOpCheck(int op) {
        if (StorageScopesAppHooks.shouldSpoofSelfAppOpCheck(op)) {
            return true;
        }

        if (ContactScopes.shouldSpoofSelfAppOpCheck(op)) {
            return true;
        }

        return false;
    }

    public static boolean shouldSkipPermissionRequestDialog(@NonNull GosPackageState ps, @NonNull String perm) {
        // Don't check whether the app actually declared this permission:
        // app can request a permission that isn't declared in its AndroidManifest and if that
        // permission is split into multiple permissions (based on app's targetSdk), and at least
        // one of of those split permissions is present in manifest, then permission prompt would be
        // shown anyway.
        return getSpoofablePermissionDflag(ps, perm) != 0;
    }

    // Controls spoofing of Activity#onRequestPermissionsResult() callback
    public static boolean shouldSpoofPermissionRequestResult(@NonNull GosPackageState ps, @NonNull String perm) {
        int dflag = getSpoofablePermissionDflag(ps, perm);
        return dflag != 0 && ps.hasDerivedFlag(dflag);
    }

    private static int getSpoofablePermissionDflag(GosPackageState ps, String perm) {
        if (ps.hasFlag(FLAG_STORAGE_SCOPES_ENABLED)) {
            int permDflag = StorageScopesAppHooks.getSpoofablePermissionDflag(perm);
            if (permDflag != 0) {
                return permDflag;
            }
        }

        if (ps.hasFlag(FLAG_CONTACT_SCOPES_ENABLED)) {
            int permDflag = ContactScopes.getSpoofablePermissionDflag(perm);
            if (permDflag != 0) {
                return permDflag;
            }
        }

        return 0;
    }

    private AppPermissionUtils() {}
}
