package android.content.pm;

import android.Manifest;

/** @hide */
public class SrtPermissions { // "special runtime permissions"
    private static int flags;

    public static int getFlags() {
        return flags;
    }

    public static void setFlags(int value) {
        flags = value;
    }

    public static boolean shouldSpoofSelfCheck(String permName) {
        switch (permName) {
            default:
                return false;
        }
    }
}
