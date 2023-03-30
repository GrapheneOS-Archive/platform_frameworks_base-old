package com.android.internal.util;

import android.content.Context;

public class PixelCameraServicesUtils {
    public static final String PACKAGE_NAME = "com.google.android.apps.camera.services";

    public static final PackageSpec PACKAGE_SPEC = new PackageSpec(
        PACKAGE_NAME, 124000L, new String[] {
            "226bb0439d6baeaa5a397c586e7031d8addfaec73c65be212f4a5dbfbf621b92",
            "340eed953b55f59c65ca8fb6c132974383e9435292639e279aa5e1169ed1ef0d",
    });

    public static boolean validatePackage(Context ctx, String vendorProxyPackage) {
        return PACKAGE_SPEC.validate(ctx.getPackageManager(), vendorProxyPackage, 0L);
    }
}
