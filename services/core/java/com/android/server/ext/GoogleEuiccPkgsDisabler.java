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

package com.android.server.ext;

import android.app.ActivityThread;
import android.app.compat.gms.GmsCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.gmscompat.GmsInfo;
import com.android.server.LocalServices;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.UserManagerInternal;

import java.util.List;

/**
 * Automatically disables Google's eUICC (eSIM) packages on each boot and when their dependencies
 * get disabled / uninstalled.
 */
class GoogleEuiccPkgsDisabler extends BroadcastReceiver {
    private static final String TAG = "GoogleEuiccPkgsDisabler";

    // only the "Owner" user is allowed to access eUICC packages
    private static final int USER_ID = UserHandle.USER_SYSTEM;

    private final SystemServerExt sse;

    GoogleEuiccPkgsDisabler(SystemServerExt sse) {
        this.sse = sse;

        // disabled unconditionally on each boot, temporarily enabled by a toggle in the Settings app
        disablePackages();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);

        f.addDataScheme("package");
        f.addDataSchemeSpecificPart(GmsInfo.PACKAGE_GSF, PatternMatcher.PATTERN_LITERAL);
        f.addDataSchemeSpecificPart(GmsInfo.PACKAGE_GMS_CORE, PatternMatcher.PATTERN_LITERAL);

        sse.registerReceiver(this, f, sse.bgHandler);
    }

    @Override
    public void onReceive(Context receiverContext, Intent intent) {
        if (shouldDisablePackages()) {
            disablePackages();
        }
    }

    private static boolean shouldDisablePackages() {
        for (String pkg : GmsInfo.DEPENDENCIES_OF_EUICC_PACKAGES) {
            if (!GmsCompat.isGmsApp(pkg, USER_ID)) {
                return true;
            }
        }

        return false;
    }

    private void disablePackages() {
        UserManagerInternal um = LocalServices.getService(UserManagerInternal.class);
        List<UserInfo> users = um.getUsers(false);

        PackageManagerService pm = sse.packageManager;
        IPackageManager pmb = ActivityThread.getPackageManager();

        final String callingPackage = sse.context.getPackageName();

        for (String pkg : GmsInfo.EUICC_PACKAGES) {
            if (pm.snapshotComputer().getApplicationInfo(pkg, 0, USER_ID) == null) {
                // support builds that don't include these packages
                continue;
            }

            // previous OS version enabled one of these packages (com.google.euiccpixel)
            // in all user profiles
            for (UserInfo user : users) {
                try {
                    pmb.setApplicationEnabledSetting(pkg, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            0, user.id, callingPackage);
                } catch (RemoteException e) {
                    // should never happen, we are in the same process
                    Slog.e(TAG, "", e);
                }
            }
        }
    }
}
