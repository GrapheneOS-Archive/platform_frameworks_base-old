package com.android.internal.util;

import android.content.Context;
import android.content.res.Resources;

import com.android.internal.R;

public class GoogleCameraUtils {
    public static final String PACKAGE_NAME = "com.google.android.GoogleCamera";

    public static final PackageSpec PACKAGE_SPEC = new PackageSpec(
        PACKAGE_NAME, 65820000L, new String[] {
            "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83",
            "1975b2f17177bc89a5dff31f9e64a6cae281a53dc1d1d59b1d147fe1c82afa00",
    });

    public static boolean isCustomSeInfoNeededForAccessToAccelerators(Context ctx) {
        Resources res = ctx.getResources();
        return res.getBoolean(R.bool.config_GoogleCamera_needs_seinfo_for_access_to_accelerators);
    }
}
