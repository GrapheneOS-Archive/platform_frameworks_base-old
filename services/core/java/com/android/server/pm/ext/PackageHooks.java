package com.android.server.pm.ext;

import android.annotation.Nullable;
import android.content.pm.PackageManager;
import android.util.ArraySet;

import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.GosPackageStatePm;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.PackageUserStateInternal;

public class PackageHooks {
    static final PackageHooks DEFAULT = new PackageHooks();

    public static boolean isDefault(PackageHooks hooks) {
        return hooks == DEFAULT;
    }

    protected static final int NO_PERMISSION_OVERRIDE = -8;
    public static final int PERMISSION_OVERRIDE_GRANT = PackageManager.PERMISSION_GRANTED;
    public static final int PERMISSION_OVERRIDE_REVOKE = PackageManager.PERMISSION_DENIED;

    public int overridePermissionState(String permission, int userId) {
        return NO_PERMISSION_OVERRIDE;
    }

    /**
     * @param isSelfToOther direction of visibility: from self to other package or from other
     * package to self
     */
    public boolean shouldBlockPackageVisibility(int userId, PackageStateInternal otherPkg, boolean isSelfToOther) {
        return shouldBlockPackageVisibility(userId, otherPkg);
    }

    public boolean shouldBlockPackageVisibility(int userId, PackageStateInternal otherPkg) {
        return false;
    }

    public static boolean shouldBlockAppsFilterVisibility(
            @Nullable PackageStateInternal callingPkgSetting,
            ArraySet<PackageStateInternal> callingSharedPkgSettings,
            int callingUserId,
            PackageStateInternal targetPkgSetting, int targetUserId) {
        if (callingPkgSetting != null) {
            return shouldBlockPackageVisibilityTwoWay(
                    callingPkgSetting, callingUserId,
                    targetPkgSetting, targetUserId);
        }

        for (int i = callingSharedPkgSettings.size() - 1; i >= 0; --i) {
            boolean res = shouldBlockPackageVisibilityTwoWay(
                    callingSharedPkgSettings.valueAt(i), callingUserId,
                    targetPkgSetting, targetUserId);
            if (res) {
                return true;
            }
        }

        return false;
    }

    private static boolean shouldBlockPackageVisibilityTwoWay(
            PackageStateInternal pkgSetting, int pkgUserId,
            PackageStateInternal otherPkgSetting, int otherPkgUserId) {
        boolean res = shouldBlockPackageVisibilityInner(pkgSetting, pkgUserId, otherPkgSetting, true);
        if (!res) {
            res = shouldBlockPackageVisibilityInner(otherPkgSetting, otherPkgUserId, pkgSetting, false);
        }
        return res;
    }

    private static boolean shouldBlockPackageVisibilityInner(
            PackageStateInternal pkgSetting, int pkgUserId, PackageStateInternal otherPkgSetting,
            boolean isSelfToOther) {
        AndroidPackage pkg = pkgSetting.getPkg();
        if (pkg != null) {
            return PackageExt.get(pkg).hooks()
                    .shouldBlockPackageVisibility(pkgUserId, otherPkgSetting, isSelfToOther);
        }

        return false;
    }

    protected static boolean isUserInstalledPkg(PackageState ps) {
        return !ps.isSystem();
    }

    protected static long getGosPsPackageFlags(PackageUserStateInternal pkgUserState) {
        GosPackageStatePm ps = pkgUserState.getGosPackageState();
        return ps != null ? ps.packageFlags : 0L;
    }
}
