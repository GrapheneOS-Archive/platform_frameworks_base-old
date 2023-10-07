package android.ext.compat;

import com.android.internal.util.PackageSpec;

/** @hide */
public class PkgHardeningConfig {
    public final PackageSpec pkgSpec;
    public final int zygoteFlags;


    public PkgHardeningConfig(PackageSpec pkgSpec, int zygoteFlags) {
        this.pkgSpec = pkgSpec;
        this.zygoteFlags = zygoteFlags;
    }
}
