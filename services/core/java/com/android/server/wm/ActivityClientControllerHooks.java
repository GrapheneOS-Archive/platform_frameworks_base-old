package com.android.server.wm;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityThread;
import android.app.AppGlobals;
import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ext.BrowserUtils;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.util.ArrayUtils;

import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;
import com.android.server.pm.permission.PermissionManagerServiceInternal;

class ActivityClientControllerHooks {

    private static final String TAG = "ActivityClientControllerHooks";
    private static final boolean DEBUG_LOGS = false;

    static boolean canAccessLaunchedFromPackagePermission(@NonNull String permission) {
        if (!isPermissionEligibleForChecking(permission)) {
            Slog.w(TAG, "Permission supplied isn't eligible for permission checks from calling package");
            return false;
        }

        final String[] callerPkgs;
        try {
            callerPkgs = AppGlobals.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        } catch (RemoteException e) {
            Slog.w(TAG, "No packages found for calling app", e);
            return false;
        }

        for (String callerPkgName: callerPkgs) {
            if (isCallerEligibleForChecking(callerPkgName)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isCallerEligibleForChecking(@NonNull String callerPkgName) {
        if (canAccessForDebuggingPurposes(callerPkgName)) {
            if (DEBUG_LOGS) {
                Slog.d(TAG, "Current package " + callerPkgName + " has been allowed "
                        + "to fetch permission state of calling app that opened its activities.");
            }
            return true;
        }

        Context ctx = ActivityThread.currentActivityThread().getSystemContext();
        if (BrowserUtils.isSystemBrowser(ctx, callerPkgName)) {
            return true;
        }

        return false;
    }

    private static boolean isPermissionEligibleForChecking(@NonNull String permission) {
        return switch (permission) {
            case Manifest.permission.INTERNET -> true;
            default -> false;
        };
    }

    private static boolean canAccessForDebuggingPurposes(@NonNull String packageName) {
        if (!Build.IS_DEBUGGABLE) {
            return false;
        }

        String testPkgs = SystemProperties.get("persist.launchedFromPackagePermission_test_pkgs");
        return ArrayUtils.contains(testPkgs.split(","), packageName);
    }

    @PackageManager.PermissionResult
    static int checkLaunchedFromPermission(@Nullable ActivityRecord r, @NonNull String permission,
            int deviceId) {
        if (r == null) {
            Slog.w(TAG, "Treating null previous activity record as permission denied");
            return PackageManager.PERMISSION_DENIED;
        }

        // Fields acquired from ActivityRecord are final, no need to hold the global wm lock.
        final String launchedFromPkgName = r.launchedFromPackage;
        final int launchedFromUid = r.launchedFromUid;
        final int userId = UserHandle.getUserId(launchedFromUid);
        var permService = LocalServices.getService(PermissionManagerServiceInternal.class);

        // Do not take into account of calling app's package visibility towards launchedFromPackage.
        long token = Binder.clearCallingIdentity();
        String persistentDeviceId =
                LocalServices.getService(VirtualDeviceManagerInternal.class)
                        .getPersistentIdForDevice(deviceId);
        persistentDeviceId = persistentDeviceId == null ? VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
                                                        : persistentDeviceId;
        try {
            return permService.checkPermission(
                    launchedFromPkgName, permission, persistentDeviceId, userId);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}