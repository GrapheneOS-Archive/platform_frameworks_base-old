package android.ext.compat;

import android.annotation.Nullable;
import android.content.pm.PackageManager;
import android.util.ArrayMap;

import com.android.internal.os.Zygote;
import com.android.internal.util.GoogleCameraUtils;
import com.android.internal.util.PackageSpec;

/** @hide */
public class ExtAppCompat {

    private static final ArrayMap<String, PkgHardeningConfig> PKG_HARDENING_CONFIGS = new ArrayMap<>();

    static {
    };

    @Nullable
    public static PkgHardeningConfig getHardeningConfig(String pkgName, PackageManager pm) {
        PkgHardeningConfig c = PKG_HARDENING_CONFIGS.get(pkgName);

        if (c != null && c.pkgSpec.validate(pm, 0L)) {
            return c;
        }
        return null;
    }

    static void addPkgConfig(PackageSpec spec, int zygoteFlags) {
        addPkgConfig(new PkgHardeningConfig(spec, zygoteFlags));
    }

    static void addPkgConfig(PkgHardeningConfig c) {
        PKG_HARDENING_CONFIGS.put(c.pkgSpec.packageName, c);
    }
}
