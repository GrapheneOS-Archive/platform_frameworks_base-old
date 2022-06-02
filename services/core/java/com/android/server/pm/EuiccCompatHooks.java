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

package com.android.server.pm;

import android.app.compat.gms.GmsCompat;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.gmscompat.GmsInfo;

import java.util.Arrays;
import java.util.List;

class EuiccCompatHooks {
    private static final String TAG = "PackageManager/EuiccCompatHooks";

    // only the "Owner" user is allowed to access eUICC packages
    private static final int USER_ID = UserHandle.USER_SYSTEM;

    // PackageManagerService#systemReady()
    // call at the end of systemReady()
    static void onServiceInitCompleted(PackageManagerService pm) {
        // disabled by default on each boot, temporarily enabled by the toggle in the Settings app
        disableEuiccCompatPackages(pm);
    }

    // PackageManagerService#deletePackageVersionedInternal()
    // call after permission checks succeed, but before PackageManager state is modified
    static void onDeletePackage(PackageManagerService pm, String pkg,
                                       boolean deleteAllUsers, int userId) {
        if (deleteAllUsers || userId == USER_ID) {
            if (neededForEuiccCompat(pm, pkg)) {
                Slog.d(TAG, "dependency is being uninstalled, disabling eUICC compat packages");
                disableEuiccCompatPackages(pm);
            }
        }
    }

    // PackageManagerService#setEnabledSetting()
    // call after permission checks for the package-level change succeed,
    // but before PackageManager state is modified
    static void onSetEnabledSetting(PackageManagerService pm, String pkg, int newState, int userId) {
        if (userId == USER_ID
                && newState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                && newState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                && neededForEuiccCompat(pm, pkg)) {
            Slog.d(TAG, "dependency is being disabled, disabling eUICC compat packages");
            disableEuiccCompatPackages(pm);
        }

        if (userId == USER_ID && newState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            for (String s : GmsInfo.EUICC_PACKAGES) {
                if (pkg.equals(s)) {
                    if (!checkDependencies(pm)) {
                        throw new IllegalStateException(pkg + " depends on " + Arrays.toString(GmsInfo.DEPENDENCIES_OF_EUICC_PACKAGES));
                    }
                }
            }
        }
    }

    private static boolean checkDependencies(PackageManagerService pm) {
        for (String pkg : GmsInfo.DEPENDENCIES_OF_EUICC_PACKAGES) {
            if (!GmsCompat.isGmsApp(pkg, USER_ID)) {
                return false;
            }
        }
        return true;
    }

    private static boolean neededForEuiccCompat(PackageManagerService pm, String pkg) {
        for (String dep : GmsInfo.DEPENDENCIES_OF_EUICC_PACKAGES) {
            if (!pkg.equals(dep)) {
                continue;
            }
            return GmsCompat.isGmsApp(pkg, USER_ID);
        }
        return false;
    }

    private static void disableEuiccCompatPackages(PackageManagerService pm) {
        // in case caller doesn't have a permission to disable these packages for some reason
        long token = Binder.clearCallingIdentity();
        try {
            List<UserInfo> users = pm.mUserManager.getUsers(false);
            for (String pkg : GmsInfo.EUICC_PACKAGES) {
                if (pm.getPackageInfo(pkg, 0, USER_ID) == null) {
                    // support builds that don't include these packages
                    continue;
                }

                // previous OS version enabled one of these packages (com.google.euiccpixel)
                // in all user profiles
                for (UserInfo user : users) {
                    pm.setApplicationEnabledSetting(pkg,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        0, user.id, null);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}
