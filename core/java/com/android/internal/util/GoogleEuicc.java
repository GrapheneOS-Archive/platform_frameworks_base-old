package com.android.internal.util;

import android.app.compat.gms.GmsCompat;
import android.os.UserHandle;

import com.android.internal.gmscompat.GmsInfo;

public class GoogleEuicc {
    // handles firmware updates of embedded secure element that is used for eSIM, NFC, Felica etc
    public static final String EUICC_SUPPORT_PIXEL_PKG_NAME = "com.google.euiccpixel";
    public static final String LPA_PKG_NAME = "com.google.android.euicc";

    public static String[] getLpaDependencies() {
        return new String[] {
            GmsInfo.PACKAGE_GSF, GmsInfo.PACKAGE_GMS_CORE, GmsInfo.PACKAGE_PLAY_STORE,
        };
    }

    public static boolean checkLpaDependencies() {
        for (String pkg : getLpaDependencies()) {
            try {
                if (!GmsCompat.isGmsApp(pkg, UserHandle.USER_SYSTEM)) {
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }
}
