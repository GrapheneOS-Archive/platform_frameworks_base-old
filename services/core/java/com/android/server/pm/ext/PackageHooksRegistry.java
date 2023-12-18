package com.android.server.pm.ext;

import android.ext.PackageId;

import com.android.internal.pm.pkg.parsing.PackageParsingHooks;

public class PackageHooksRegistry {

    public static PackageParsingHooks getParsingHooks(String pkgName) {
        return switch (pkgName) {
            default -> PackageParsingHooks.DEFAULT;
        };
    }

    public static PackageHooks getHooks(int packageId) {
        return switch (packageId) {
            default -> PackageHooks.DEFAULT;
        };
    }
}
