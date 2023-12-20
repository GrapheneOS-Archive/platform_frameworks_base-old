package com.android.server.pm.ext;

import android.annotation.Nullable;
import android.ext.PackageId;

public class PackageHooksRegistry {

    public static PackageParsingHooks getParsingHooks(String pkgName) {
        return switch (pkgName) {
            default -> PackageParsingHooks.DEFAULT;
        };
    }
}
