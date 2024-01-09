package com.android.server.pm.ext;

import android.ext.PackageId;

import com.android.internal.pm.pkg.parsing.PackageParsingHooks;

public class PackageHooksRegistry {

    public static PackageParsingHooks getParsingHooks(String pkgName) {
        PackageParsingHooks gmsCompatHooks = GmsCompatPkgParsingHooks.maybeGet(pkgName);
        if (gmsCompatHooks != null) {
            return gmsCompatHooks;
        }

        return switch (pkgName) {
            case PackageId.EUICC_SUPPORT_PIXEL_NAME -> new EuiccSupportPixelHooks.ParsingHooks();
            case PackageId.G_EUICC_LPA_NAME -> new EuiccGoogleHooks.ParsingHooks();
            case PackageId.PIXEL_CAMERA_SERVICES_NAME -> new PixelCameraServicesHooks.ParsingHooks();
            default -> PackageParsingHooks.DEFAULT;
        };
    }

    public static PackageHooks getHooks(int packageId) {
        return switch (packageId) {
            case PackageId.EUICC_SUPPORT_PIXEL -> new EuiccSupportPixelHooks();
            case PackageId.G_CARRIER_SETTINGS -> new GCarrierSettingsHooks();
            case PackageId.G_EUICC_LPA -> new EuiccGoogleHooks();
            case PackageId.PIXEL_CAMERA_SERVICES -> new PixelCameraServicesHooks();
            default -> PackageHooks.DEFAULT;
        };
    }
}
