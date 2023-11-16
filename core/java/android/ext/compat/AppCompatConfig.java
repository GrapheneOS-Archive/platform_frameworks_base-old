package android.ext.compat;

import android.ext.settings.app.AppSwitch;
import android.util.ArraySet;

import com.android.internal.util.PackageSpec;

/** @hide */
public final class AppCompatConfig {
    public final PackageSpec pkgSpec;

    private final ArraySet<Class<? extends AppSwitch>> extraProtections = new ArraySet<>(7);
    private final ArraySet<Class<? extends AppSwitch>> disabledProtections = new ArraySet<>(7);

    public AppCompatConfig(PackageSpec pkgSpec) {
        this.pkgSpec = pkgSpec;
    }

    public void compatibleWith(Class<? extends AppSwitch> asw) {
        extraProtections.add(asw);
    }

    public boolean isCompatibleWith(Class<? extends AppSwitch> asw) {
        return extraProtections.contains(asw);
    }

    public void incompatibleWith(Class<? extends AppSwitch> asw) {
        disabledProtections.add(asw);
    }

    public boolean isIncompatibleWith(Class<? extends AppSwitch> asw) {
        return disabledProtections.contains(asw);
    }
}
