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
    private static final boolean DEBUG_VERBOSE = false;

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

    /**
     * Whether to enable hooks for this app to load Dynamite modules from unprivileged GMS.
     * This is for CLIENT apps, not GMS itself.
     */
    @ChangeId
    @Disabled // Overridden as a special case in CompatChange
    private static final long GMS_UNPRIVILEGED_DYNAMITE_CLIENT = 7528921493777479941L;

    // Some hooks are in (potentially) hot paths, so cache the change enable states.
    private static volatile boolean isGmsCompatEnabled = false;
    private static volatile boolean isDynamiteClientEnabled = false;

    // Static only
    private GmsCompat() { }

    public static boolean isEnabled() {
        return isGmsCompatEnabled;
    }

    /** @hide */
    public static boolean isDynamiteClient() {
        return isDynamiteClientEnabled;
    }

    private static void logEnabled(String changeName, boolean enabled) {
        if (!DEBUG_VERBOSE) {
            return;
        }

        String pkg = ActivityThread.currentPackageName();
        if (pkg == null) {
            pkg = (Process.myUid() == Process.SYSTEM_UID) ? "system_server" : "[unknown]";
        }

        Log.d(TAG, changeName + " enabled for " + pkg + " (" + Process.myPid() + ") = " + enabled);
    }

    private static boolean isChangeEnabled(String changeName, long changeId) {
        boolean enabled = Compatibility.isChangeEnabled(changeId);

        // Compatibility changes aren't available in the system process, but this should never be
        // enabled for it or other core "android" system processes (such as the android:ui process
        // used for chooser and resolver activities).
        if (UserHandle.getAppId(Process.myUid()) == Process.SYSTEM_UID) {
            enabled = false;
        }

        logEnabled(changeName, enabled);
        return enabled;
    }

    /**
     * Must be called to initialize the compatibility change enable states before any hooks run.
     *
     * @hide
     */
    public static void initChangeEnableStates() {
        isGmsCompatEnabled = isChangeEnabled("GMS_UNPRIVILEGED_COMPAT", GMS_UNPRIVILEGED_COMPAT);
        isDynamiteClientEnabled = isChangeEnabled("GMS_UNPRIVILEGED_DYNAMITE_CLIENT", GMS_UNPRIVILEGED_DYNAMITE_CLIENT);
    }

    /**
     * Check whether the given app is unprivileged and part of the Google Play Services family.
     *
     * @hide
     */
    public static boolean isGmsApp(String packageName, Signature[] signatures, boolean isPrivileged,
            String sharedUserId) {
        // Privileged GMS doesn't need any compatibility changes
        if (isPrivileged) {
            return false;
        }

        if (GmsInfo.PACKAGE_GMS.equals(packageName) || GmsInfo.PACKAGE_GSF.equals(packageName)) {
            // Check the shared user ID to avoid affecting microG with a spoofed signature. This is a
            // reliable indicator because apps can't change their shared user ID after shipping with it.
            if (!GmsInfo.SHARED_USER_ID.equals(sharedUserId)) {
                return false;
            }
        } else if (!GmsInfo.PACKAGE_PLAY_STORE.equals(packageName)) {
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
        return isGmsApp(app.packageName, signatures, app.isPrivilegedApp(), pkg.sharedUserId);
    }

    private static boolean isGmsInstalled(ApplicationInfo relatedApp) {
        int userId = UserHandle.getUserId(relatedApp.uid);
        IPackageManager pm = ActivityThread.getPackageManager();

        ApplicationInfo gmsApp;
        try {
            gmsApp = pm.getApplicationInfo(GmsInfo.PACKAGE_GMS, 0, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        // Check signature to avoid breaking microG's implementation of Dynamite
        return gmsApp != null && isGmsApp(gmsApp);
    }

    /** @hide */
    // CompatChange#isEnabled(ApplicationInfo)
    public static boolean isChangeEnabled(CompatibilityChangeInfo change, ApplicationInfo app) {
        if (change.getId() == GMS_UNPRIVILEGED_COMPAT) {
            return isGmsApp(app);
        } else if (change.getId() == GMS_UNPRIVILEGED_DYNAMITE_CLIENT) {
            // Client apps can't be GMS itself, but GMS must be installed in the same user
            return !(GmsInfo.PACKAGE_GMS.equals(app.packageName) && isGmsApp(app)) &&
                    isGmsInstalled(app);
        } else {
            return false;
        }
    }
}
