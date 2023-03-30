package com.android.internal.util;

import android.annotation.Nullable;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.SigningDetails;

import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;

public class PackageSpec {
    public final String packageName;
    private final long minVersionCode;
    private final byte[][] validCertificatesSha256;

    public PackageSpec(String packageName, long minVersionCode, byte[][] validCertificatesSha256) {
        this.packageName = packageName;
        this.minVersionCode = minVersionCode;
        this.validCertificatesSha256 = validCertificatesSha256;
    }

    public PackageSpec(String packageName, long minVersionCode, String[] validCertificatesSha256) {
        this(packageName, minVersionCode, decodeHexStrings(validCertificatesSha256, 64));
    }

    public boolean validate(PackageManager pm, String packageName, long flags) {
        if (!this.packageName.equals(packageName)) {
            return false;
        }

        return validate(pm, flags);
    }

    public boolean validate(PackageManager pm, long flags) {
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(packageName, PackageInfoFlags.of(GET_SIGNING_CERTIFICATES | flags));
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return validate(pi);
    }

    public boolean validate(PackageInfo pi) {
        return validate(pi.packageName, pi.getLongVersionCode(), pi.signingInfo.getSigningDetails());
    }

    public boolean validate(String packageName, long versionCode, @Nullable SigningDetails signingDetails) {
        if (signingDetails == null) {
            return false;
        }

        if (!this.packageName.equals(packageName)) {
            return false;
        }

        if (versionCode < minVersionCode) {
            return false;
        }

        for (byte[] hash : validCertificatesSha256) {
            if (signingDetails.hasSha256Certificate(hash)) {
                return true;
            }
        }

        return false;
    }

    private static byte[][] decodeHexStrings(String[] arr, int requiredLength) {
        int len = arr.length;
        byte[][] res = new byte[len][];
        for (int i = 0; i < len; ++i) {
            String s = arr[i];
            if (s.length() != requiredLength) {
                throw new IllegalArgumentException(s);
            }
            res[i] = decodeHexString(s);
        }
        return res;
    }

    private static byte[] decodeHexString(String s) {
        // each byte takes 2 characters, so length must be even
        int strLen = s.length();
        if ((strLen & 1) != 0) {
            throw new IllegalArgumentException(s);
        }

        int arrLen = strLen / 2;
        byte[] arr = new byte[arrLen];
        for (int i = 0; i < arrLen; ++i) {
            int off = i << 1;
            int top = Character.digit(s.charAt(off), 16);
            int bot = Character.digit(s.charAt(off + 1), 16);
            arr[i] = (byte) ((top << 4) | bot);
        }
        return arr;
    }
}
