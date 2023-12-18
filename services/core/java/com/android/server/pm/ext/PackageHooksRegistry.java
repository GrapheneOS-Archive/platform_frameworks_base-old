package com.android.server.pm.ext;

import android.annotation.Nullable;
import android.ext.PackageId;

public class PackageHooksRegistry {

    public static PackageParsingHooks getParsingHooks(String pkgName) {
        return switch (pkgName) {
            case PackageId.EUICC_SUPPORT_PIXEL_NAME -> new EuiccSupportPixelHooks.ParsingHooks();
            default -> PackageParsingHooks.DEFAULT;
        };
    }

    public static PackageHooks getHooks(int packageId) {
        return switch (packageId) {
            case PackageId.EUICC_SUPPORT_PIXEL -> new EuiccSupportPixelHooks();
            default -> PackageHooks.DEFAULT;
        };
    }
}
