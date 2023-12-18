package com.android.server.ext;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppBindArgs;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArraySet;

import com.android.server.pm.Computer;
import com.android.server.pm.GosPackageStatePmHooks;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.ext.PackageHooks;
import com.android.server.pm.permission.Permission;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.parsing.ParsingPackage;

import static java.util.Objects.requireNonNull;

public class PackageManagerHooks {

    // Called when package enabled setting for a system package is deserialized from storage
    @Nullable
    public static Integer maybeOverrideSystemPackageEnabledSetting(String pkgName, @UserIdInt int userId) {
        switch (pkgName) {
            default:
                return null;
        }
    }

    public static boolean shouldBlockGrantRuntimePermission(
            PackageManagerInternal pm, String permName, String packageName, int userId)
    {
        return false;
    }

    @Nullable
    public static Bundle getExtraAppBindArgs(PackageManagerService pm, String packageName) {
        final int callingUid = Binder.getCallingUid();
        final int appId = UserHandle.getAppId(callingUid);
        final int userId = UserHandle.getUserId(callingUid);

        Computer pmComputer = pm.snapshotComputer();
        PackageStateInternal pkgState = pmComputer.getPackageStateInternal(packageName);
        if (pkgState == null) {
            return null;
        }

        if (pkgState.getAppId() != appId) {
            return null;
        }

        AndroidPackage pkg = pkgState.getPkg();

        if (pkg == null) {
            return null;
        }

        // isSystem() remains true even if isUpdatedSystemApp() is true
        final boolean isUserApp = !pkgState.isSystem();

        GosPackageStatePm unfilteredGosPs = pkgState.getUserStateOrDefault(userId).getGosPackageState();
        // GosPackageState that is filtered for the target app
        GosPackageState gosPs = GosPackageStatePmHooks.get(pmComputer, pkgState, unfilteredGosPs, callingUid, userId);

        ApplicationInfo appInfo =
                requireNonNull(pmComputer.getApplicationInfo(packageName, 0L, userId));

        int[] flagsArr = new int[AppBindArgs.FLAGS_ARRAY_LEN];

        var b = new Bundle();
        b.putParcelable(AppBindArgs.KEY_GOS_PACKAGE_STATE, gosPs);
        b.putIntArray(AppBindArgs.KEY_FLAGS_ARRAY, flagsArr);

        return b;
    }

    // Called when AppsFilter decides whether to restrict package visibility
    public static boolean shouldFilterApplication(
            @Nullable PackageStateInternal callingPkgSetting,
            ArraySet<PackageStateInternal> callingSharedPkgSettings,
            int callingUserId,
            PackageStateInternal targetPkgSetting, int targetUserId
    ) {
        if (callingPkgSetting != null && restrictedVisibilityPackages.contains(callingPkgSetting.getPackageName())) {
            if (!targetPkgSetting.isSystem()) {
                return true;
            }
        }

        if (restrictedVisibilityPackages.contains(targetPkgSetting.getPackageName())) {
            if (callingPkgSetting != null) {
                return !callingPkgSetting.isSystem();
            } else {
                for (int i = callingSharedPkgSettings.size() - 1; i >= 0; i--) {
                    if (!callingSharedPkgSettings.valueAt(i).isSystem()) {
                        return true;
                    }
                }
            }
        }

        if (PackageHooks.shouldBlockAppsFilterVisibility(
                callingPkgSetting, callingSharedPkgSettings, callingUserId,
                targetPkgSetting, targetUserId)
        ) {
            return true;
        }

        return false;
    }

    // Packages in this array are restricted from interacting with and being interacted by non-system apps
    private static final ArraySet<String> restrictedVisibilityPackages = new ArraySet<>(new String[] {
    });
}
