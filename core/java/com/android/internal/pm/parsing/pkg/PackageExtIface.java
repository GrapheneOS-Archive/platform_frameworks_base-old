package com.android.internal.pm.parsing.pkg;

import android.ext.AppInfoExt;

/** @hide */
public interface PackageExtIface {
    AppInfoExt toAppInfoExt(PackageImpl pkg);
}
