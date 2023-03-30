package com.android.server.ext;

import com.android.internal.util.PackageSpec;
import com.android.server.pm.parsing.pkg.AndroidPackage;

public class PackageManagerUtils {

    public static boolean validatePackage(AndroidPackage pkg, PackageSpec spec) {
        // PackageSpec can't reference AndroidPackage directly, it's compiled separately
        return spec.validate(pkg.getPackageName(), pkg.getLongVersionCode(), pkg.getSigningDetails());
    }
}
