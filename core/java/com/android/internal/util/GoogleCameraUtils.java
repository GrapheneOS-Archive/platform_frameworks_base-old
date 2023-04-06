package com.android.internal.util;

import android.content.Context;
import android.content.res.Resources;
import android.ext.PackageId;

import com.android.internal.R;

public class GoogleCameraUtils {
    public static final String PACKAGE_NAME = PackageId.G_CAMERA_NAME;

    public static boolean isCustomSeInfoNeededForAccessToAccelerators(Context ctx) {
        Resources res = ctx.getResources();
        return res.getBoolean(R.bool.config_GoogleCamera_needs_seinfo_for_access_to_accelerators);
    }
}
