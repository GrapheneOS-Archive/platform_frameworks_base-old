package com.android.server.ext;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppBindArgs;
import android.content.Intent;
import android.content.pm.GosPackageState;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.health.connect.HealthConnectManager;
import android.location.HookedLocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.app.ContactScopes;
import com.android.internal.gmscompat.gcarriersettings.GCarrierSettingsApp;
import com.android.internal.util.GoogleEuicc;
import com.android.server.pm.GosPackageStatePmHooks;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.permission.Permission;
import com.android.server.pm.permission.SpecialRuntimePermUtils;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.GosPackageStatePm;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.component.ParsedActivity;
import com.android.server.pm.pkg.component.ParsedMainComponent;
import com.android.server.pm.pkg.component.ParsedUsesPermission;
import com.android.server.pm.pkg.parsing.ParsingPackage;

import java.util.List;

public class PackageManagerHooks {

    // Called when package enabled setting is deserialized from storage
    @Nullable
    public static Integer maybeOverridePackageEnabledSetting(String pkgName, @UserIdInt int userId) {
        switch (pkgName) {
            case GoogleEuicc.EUICC_SUPPORT_PIXEL_PKG_NAME:
                if (userId == UserHandle.USER_SYSTEM) {
                    // EuiccSupportPixel handles firmware updates and should always be enabled.
                    // It was previously unconditionally disabled after reboot.
                    return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                } else {
                    // one of the previous OS versions enabled EuiccSupportPixel in all users
                    return PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                }
            case GoogleEuicc.LPA_PKG_NAME:
                // Google's LPA should be always disabled after reboot
                return PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            default:
                return null;
        }
    }

    // Called when package parsing is completed
    public static void amendParsedPackage(ParsingPackage pkg) {
        String pkgName = pkg.getPackageName();

        switch (pkgName) {
            case GoogleEuicc.EUICC_SUPPORT_PIXEL_PKG_NAME:
                // EuiccSupportPixel uses INTERNET perm only as part of its dev mode
                removeUsesPermissions(pkg, Manifest.permission.INTERNET);
                return;
            case GoogleEuicc.LPA_PKG_NAME:
                // this is the same as android:enabled="false" in <application> AndroidManifest tag,
                // it makes the package disabled by default on first boot, when there's no
                // serialized package state
                pkg.setEnabled(false);
                return;
            default:
                List<ParsedUsesPermission> usesPermissions = pkg.getUsesPermissions();
                if (usesPermissions.isEmpty()) {
                    return;
                }

                // HealthConnectManager#HEALTH_PERMISSION_PREFIX
                String healthPermPrefix = "android.permission.health.";
                String[] healthPermissions = usesPermissions.stream()
                        .map(ParsedUsesPermission::getName)
                        .filter(name -> name.startsWith(healthPermPrefix))
                        .toArray(String[]::new);
                if (healthPermissions.length == 0) {
                    return;
                }

                final List<ParsedActivity> activities = pkg.getActivities();
                if (activities.isEmpty()) {
                    return;
                }

                final boolean shouldRemoveHealthPermission = activities.stream()
                        .filter(ParsedMainComponent::isExported).noneMatch(
                        a -> a.getIntents().stream().anyMatch(i -> {
                            var filter = i.getIntentFilter();
                            return filter.hasCategory(HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS)
                                    && filter.matchAction(Intent.ACTION_VIEW_PERMISSION_USAGE);
                        })
                );

                if (!shouldRemoveHealthPermission) {
                    return;
                }

                Slog.e("PackageManager", "Removing health connect permissions for " + pkgName + "."
                        + " Apps requesting these permissions in Android 14 are required to declare an activity alias"
                        + " with intent filters of action \"android.intent.action.VIEW_PERMISSION_USAGE\""
                        + " and category \"android.intent.category.HEALTH_PERMISSIONS\","
                        + " linking its rationale and privacy policy for requesting health connect permissions."
                        + " Refer to the following documentations for details: \n"
                        + "https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started#show-privacy-policy" + "\n"
                        + "https://developer.android.com/reference/android/health/connect/HealthPermissions");

                removeUsesPermissions(pkg, healthPermissions);
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
        if (ContactScopes.getSpoofablePermissionDflag(permName) != 0) {
            GosPackageStatePm gosPs = pm.getGosPackageState(packageName, userId);
            if (gosPs != null && gosPs.hasFlags(GosPackageState.FLAG_CONTACT_SCOPES_ENABLED)) {
                String msg = "refusing to grant " + permName + " to " + packageName +
                        ": Contact Scopes is enabled";
                Slog.d("PermissionManager", msg);
                return true;
            }
        }

        return false;
    }

    @Nullable
    public static Bundle getExtraAppBindArgs(PackageManagerService pm, String packageName) {
        final int callingUid = Binder.getCallingUid();
        final int appId = UserHandle.getAppId(callingUid);
        final int userId = UserHandle.getUserId(callingUid);

        PackageStateInternal pkgState = pm.snapshotComputer().getPackageStateInternal(packageName);
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

        GosPackageState gosPs = GosPackageStatePmHooks.get(pm, callingUid, packageName, userId);

        int[] flagsArr = new int[AppBindArgs.FLAGS_ARRAY_LEN];
        flagsArr[AppBindArgs.FLAGS_IDX_SPECIAL_RUNTIME_PERMISSIONS] =
                SpecialRuntimePermUtils.getFlags(pm, pkg, pkgState, userId);

        flagsArr[AppBindArgs.FLAGS_IDX_HOOKED_LOCATION_MANAGER] =
                HookedLocationManager.getFlags(gosPs, isUserApp);

        var b = new Bundle();
        b.putParcelable(AppBindArgs.KEY_GOS_PACKAGE_STATE, gosPs);
        b.putIntArray(AppBindArgs.KEY_FLAGS_ARRAY, flagsArr);

        return b;
    }

    public static boolean shouldForciblyGrantPermission(AndroidPackage pkg, Permission perm) {
        if (!Build.IS_DEBUGGABLE) {
            return false;
        }

        String permName = perm.getName();

        switch (pkg.getPackageName()) {
            default:
                return false;
        }
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
        GoogleEuicc.EUICC_SUPPORT_PIXEL_PKG_NAME,
        // prevent it from obtaining carrier config overrides from GmsCore (see CarrierConfig2 README)
        GCarrierSettingsApp.PKG_NAME,
    });
}
