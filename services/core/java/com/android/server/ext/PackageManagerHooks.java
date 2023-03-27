package com.android.server.ext;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.UserHandle;
import android.util.ArraySet;

import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.parsing.ParsingPackage;

public class PackageManagerHooks {

    // Called when package enabled setting is deserialized from storage
    @Nullable
    public static Integer maybeOverridePackageEnabledSetting(String pkgName, @UserIdInt int userId) {
        switch (pkgName) {
            default:
                return null;
        }
    }

    // Called when package parsing is completed
    public static void amendParsedPackage(ParsingPackage pkg) {
        String pkgName = pkg.getPackageName();

        switch (pkgName) {
            default:
                return;
        }
    }

    public static void removeUsesPermissions(ParsingPackage pkg, String... perms) {
        var set = new ArraySet<>(perms);
        pkg.getRequestedPermissions().removeAll(set);
        pkg.getUsesPermissions().removeIf(p -> set.contains(p.getName()));
    }

    public static boolean shouldBlockGrantRuntimePermission(
            PackageManagerInternal pm, String permName, String packageName, int userId)
    {
        return false;
    }

    // Called when AppsFilter decides whether to restrict package visibility
    public static boolean shouldFilterAccess(@Nullable PackageStateInternal callingPkgSetting,
                                             ArraySet<PackageStateInternal> callingSharedPkgSettings,
                                             PackageStateInternal targetPkgSetting) {
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
        return false;
    }

    // Packages in this array are restricted from interacting with and being interacted by non-system apps
    private static final ArraySet<String> restrictedVisibilityPackages = new ArraySet<>(new String[] {
    });
}
