package android.ext.compat;

import com.android.internal.util.PackageSpec;

public record PkgHardeningConfig(
    PackageSpec pkgSpec,
    int zygoteFlags
) {}
