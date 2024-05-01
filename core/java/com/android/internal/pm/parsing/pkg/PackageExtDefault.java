package com.android.internal.pm.parsing.pkg;

import android.ext.AppInfoExt;

/** @hide */
public class PackageExtDefault implements PackageExtIface {
    public static final PackageExtDefault INSTANCE = new PackageExtDefault();

    @Override
    public AppInfoExt toAppInfoExt(PackageImpl pkg) {
        return AppInfoExt.DEFAULT;
    }
}
