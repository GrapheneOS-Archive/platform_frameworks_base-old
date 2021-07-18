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

package android.app.compat.gms;

import android.annotation.SystemApi;
import android.app.ActivityThread;
import android.compat.Compatibility;
import android.compat.annotation.ChangeId;
import android.compat.annotation.Disabled;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.compat.CompatibilityChangeInfo;
import com.android.internal.gmscompat.GmsInfo;

/**
 * This class provides helpers for Google Play Services compatibility. It allows the following apps
 * to work as regular, unprivileged user apps:
 *     - Google Play Services (Google Mobile Services, aka "GMS")
 *     - Google Services Framework
 *     - Google Play Store
 *     - All apps depending on Google Play Services
 *
 * All GMS compatibility hooks should call methods on GmsCompat. Hooks that are more complicated
 * than returning a simple constant value should also be implemented in GmsHooks to reduce
 * maintenance overhead.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class GmsCompat {
    private static final String TAG = "GmsCompat/Core";

    /**
     * Whether to enable Google Play Services compatibility for this app.
     *
     * This compatibility change is special because the system enables it automatically for certain
     * apps, but it still needs to be declared with a change ID.
     *
     * We don't have a bug for this in Google's issue tracker, so the change ID is a
     * randomly-generated long.
     */
    @ChangeId
    @Disabled // Overridden as a special case in CompatChange
    private static final long GMS_UNPRIVILEGED_COMPAT = 1531297613045645771L;

    private static final boolean DEBUG_VERBOSE = false;

    // Static only
    private GmsCompat() { }

    private static void logEnabled(boolean enabled) {
        if (!DEBUG_VERBOSE) {
            return;
        }

        String pkg = ActivityThread.currentPackageName();
        if (pkg == null) {
            pkg = (Process.myUid() == Process.SYSTEM_UID) ? "system_server" : "[unknown]";
        }

        Log.d(TAG, "Enabled for " + pkg + " (" + Process.myPid() + "): " + enabled);
    }

    public static boolean isEnabled() {
        boolean enabled = Compatibility.isChangeEnabled(GMS_UNPRIVILEGED_COMPAT);

        // Compatibility changes aren't available in the system process, but this should never be
        // enabled for it.
        if (Process.myUid() == Process.SYSTEM_UID) {
            enabled = false;
        }

        logEnabled(enabled);
        return enabled;
    }

    /**
     * Check whether the given app is unprivileged and part of the Google Play Services family.
     *
     * @hide
     */
    public static boolean isGmsApp(String packageName, Signature[] signatures, boolean isPrivileged) {
        // Privileged GMS doesn't need any compatibility changes
        if (isPrivileged) {
            return false;
        }

        if (!GmsInfo.PACKAGE_GMS.equals(packageName) &&
                !GmsInfo.PACKAGE_GSF.equals(packageName) &&
                !GmsInfo.PACKAGE_PLAY_STORE.equals(packageName)) {
            return false;
        }

        // Validate signature to avoid affecting apps like microG and Gcam Services Provider.
        // This isn't actually necessary from a security perspective because GMS doesn't get any
        // special privileges, but it's a failsafe to avoid unintentional compatibility issues.
        boolean validCert = false;
        for (Signature signature : signatures) {
            if (signature.toCharsString().equals(GmsInfo.SIGNING_CERT)) {
                validCert = true;
            }
        }

        return validCert;
    }

    /** @hide */
    public static boolean isGmsApp(ApplicationInfo app) {
        int userId = UserHandle.getUserId(app.uid);
        IPackageManager pm = ActivityThread.getPackageManager();

        // Fetch PackageInfo to get signing certificates
        PackageInfo pkg;
        long token = Binder.clearCallingIdentity();
        try {
            pkg = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        // Get all applicable certificates, even if GMS switches to multiple signing certificates
        // in the future
        Signature[] signatures = pkg.signingInfo.hasMultipleSigners() ?
                pkg.signingInfo.getApkContentsSigners() :
                pkg.signingInfo.getSigningCertificateHistory();
        return isGmsApp(app.packageName, signatures, app.isPrivilegedApp());
    }

    /** @hide */
    // CompatChange#isEnabled(ApplicationInfo)
    public static boolean isChangeEnabled(CompatibilityChangeInfo change, ApplicationInfo app) {
        return change.getId() == GMS_UNPRIVILEGED_COMPAT && isGmsApp(app);
    }
}
