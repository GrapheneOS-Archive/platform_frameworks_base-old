package com.android.server.pm.ext;

import android.Manifest;
import android.app.ActivityThread;
import android.app.role.RoleManager;
import android.content.ContentResolver;
import android.content.Context;
import android.ext.PackageId;

import com.android.internal.gmscompat.PlayStoreHooks;
import com.android.internal.pm.pkg.component.ParsedUsesPermission;
import com.android.internal.pm.pkg.parsing.PackageParsingHooks;
import com.android.role.RoleManagerLocal;
import com.android.server.LocalManagerRegistry;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageStateInternal;

import java.util.Objects;
import java.util.Set;

class PixelHealthHooks extends PackageHooks {
    static final String PERMISSION_FAR_INFRARED_TEMPERATURE =
            "com.google.sensor.permission.FAR_INFRARED_TEMPERATURE";

    static class ParsingHooks extends PackageParsingHooks {

        @Override
        public boolean shouldSkipUsesPermission(ParsedUsesPermission p) {
            switch (p.getName()) {
                case Manifest.permission.CAMERA:
                case Manifest.permission.VIBRATE:
                case PERMISSION_FAR_INFRARED_TEMPERATURE:
                    return false;
                default:
                    // The only other permission is READ_DEVICE_CONFIG as of versionCode 2224;
                    // intentionally omit unknown permissions that might be added in future versions
                    // since they could require special handling
                    return true;
            }
        }
    }

    @Override
    public int overridePermissionState(String permission, int userId) {
        if (PERMISSION_FAR_INFRARED_TEMPERATURE.equals(permission)) {
            // This permission is defined in the PixelDisplayService (com.android.pixeldisplayservice)
            // system package with protectionLevel "preinstalled|signature", i.e. it's granted
            // automatically to preinstalled packages and to packages that are signed with the same
            // certificate as PixelDisplayService. Note that PixelDisplayService is re-signed with
            // the platform certificate on GrapheneOS.
            //
            // On stock Pixel OS, Pixel Health is a preinstalled app.
            //
            // Allow this permission unconditionally, given that its enforcement is implemented in
            // proprietary Google code and that the Pixel Health app implements a toggle that
            // disables its access to the FIR sensor.
            return PERMISSION_OVERRIDE_GRANT;
        }
        return NO_PERMISSION_OVERRIDE;
    }

    @Override
    public boolean shouldBlockPackageVisibility(int userId, PackageStateInternal otherPkg, boolean isSelfToOther) {
        if (!isUserInstalledPkg(otherPkg)) {
            return false;
        }

        if ("com.fitbit.FitbitMobile".equals(otherPkg.getPackageName())) {
            // Pixel Health app performs Fitbit certificate checks itself
            return false;
        }

        if (isSelfToOther) {
            // hide all other user apps from Pixel Health app
            return true;
        }

        AndroidPackage otherAPkg = otherPkg.getPkg();
        if (otherAPkg == null) {
            return true;
        }

        if (PackageExt.get(otherAPkg).getPackageId() == PackageId.PLAY_STORE) {
            Context ctx = ActivityThread.currentActivityThread().getSystemContext();
            ContentResolver cr = ctx.getContentResolver();
            return !PlayStoreHooks.isInstallAllowed(PackageId.PIXEL_HEALTH_NAME, cr);
        }

        RoleManagerLocal roleManager = Objects.requireNonNull(
                LocalManagerRegistry.getManager(RoleManagerLocal.class)
        );
        Set<String> homeRoleHolders = roleManager.getRolesAndHolders(userId).get(RoleManager.ROLE_HOME);
        // Don't block visiblity from current launcher to Pixel Health app. Association restrictions
        // between them remain enforced.
        boolean isAllowed = homeRoleHolders != null && homeRoleHolders.contains(otherAPkg.getPackageName());
        return !isAllowed;
    }
}
