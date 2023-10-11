package com.android.server.ext;

import android.content.pm.PackageManagerInternal;

import com.android.internal.util.PackageSpec;
import com.android.server.LocalServices;
import com.android.server.pm.pkg.AndroidPackage;

public class PackageManagerUtils {

    public static PackageSpec.Validator getPackageSpecValidator() {
        var pm = LocalServices.getService(PackageManagerInternal.class);
        return getPackageSpecValidator(pm);
    }

    public static PackageSpec.Validator getPackageSpecValidator(PackageManagerInternal pm) {
        return (PackageSpec spec) -> {
            AndroidPackage pkg = pm.getPackage(spec.packageName);
            if (pkg != null) {
                return validatePackage(pkg, spec);
            }
            return false;
        };
    }

    public static boolean validatePackage(PackageSpec spec) {
        var pm = LocalServices.getService(PackageManagerInternal.class);
        AndroidPackage pkg = pm.getPackage(spec.packageName);
        if (pkg != null) {
            return validatePackage(pkg, spec);
        }
        return false;
    }

    public static boolean validatePackage(AndroidPackage pkg, PackageSpec spec) {
        // PackageSpec can't reference AndroidPackage directly, it's compiled separately
        return spec.validate(pkg.getPackageName(), pkg.getLongVersionCode(), pkg.getSigningDetails());
    }
}
