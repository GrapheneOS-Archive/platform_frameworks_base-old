package com.android.server.pm.ext;

import android.annotation.Nullable;
import android.ext.PackageId;

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
