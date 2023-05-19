package android.content.pm;

import android.Manifest;

/** @hide */
public class SrtPermissions { // "special runtime permissions"
    public static final int FLAG_INTERNET_COMPAT_ENABLED = 1;

    private static int flags;

    public static int getFlags() {
        return flags;
    }

    public static void setFlags(int value) {
        flags = value;

        if ((value & FLAG_INTERNET_COMPAT_ENABLED) != 0) {
            SpecialRuntimePermAppUtils.enableInternetCompat();
        }
    }

    public static boolean shouldSpoofSelfCheck(String permName) {
        switch (permName) {
            case Manifest.permission.INTERNET:
                return SpecialRuntimePermAppUtils.isInternetCompatEnabled();
        }

        return false;
    }
}
