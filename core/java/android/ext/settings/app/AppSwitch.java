package android.ext.settings.app;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateBase;
import android.ext.compat.AppCompatConfig;
import android.ext.compat.ExtAppCompat;

/** @hide */
public abstract class AppSwitch {
    // optional GosPackageState flag that indicates that non-default value is set, ignored if 0
    int gosPsFlagNonDefault;
    // GosPackageState flag that indicates whether the switch is on or off, ignored if
    // non-default flag is not set
    int gosPsFlag;
    // invert meaning of gosPsFlag, i.e. true is off, false is on
    boolean gosPsFlagInverted;
    // optional GosPackageState flag to suppress switch-related notification (e.g. after
    // "Don't show again" notification action)
    int gosPsFlagSuppressNotif;

    // immutability reasons
    public static final int IR_UNKNOWN = 0;
    public static final int IR_IS_SYSTEM_APP = 1;
    public static final int IR_NO_NATIVE_CODE = 2;
    public static final int IR_NON_64_BIT_NATIVE_CODE = 3;
    public static final int IR_OPTED_IN_VIA_MANIFEST = 4;
    public static final int IR_IS_DEBUGGABLE_APP = 5;
    public static final int IR_EXPLOIT_PROTECTION_COMPAT_MODE = 6;
    public static final int IR_REQUIRED_BY_HARDENED_MALLOC = 7;

    // default value reasons
    public static final int DVR_UNKNOWN = 0;
    public static final int DVR_DEFAULT_SETTING = 1;
    public static final int DVR_PACKAGE_COMPAT_CONFIG_OPT_IN = 2;
    public static final int DVR_PACKAGE_COMPAT_CONFIG_OPT_OUT = 3;

    public static class StateInfo {
        // use it only if StateInfo is not needed, it's not thread-safe to read from this variable
        static final StateInfo PLACEHOLDER = new StateInfo();

        boolean isImmutable;
        int immutabilityReason = IR_UNKNOWN;

        boolean isUsingDefaultValue;
        int defaultValueReason = DVR_UNKNOWN;

        public boolean isImmutable() {
            return isImmutable;
        }

        public int getImmutabilityReason() {
            return immutabilityReason;
        }

        public boolean isUsingDefaultValue() {
            return isUsingDefaultValue;
        }

        public int getDefaultValueReason() {
            return defaultValueReason;
        }
    }

    public final boolean isImmutable(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase ps) {
        return getImmutableValue(ctx, userId, appInfo, ps) != null;
    }

    public final Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase ps) {
        return getImmutableValue(ctx, userId, appInfo, ps, StateInfo.PLACEHOLDER);
    }

    // returns null if value is currently mutable
    public Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase ps, StateInfo si) {
        return null;
    }

    public final boolean getDefaultValue(Context ctx, int userId, ApplicationInfo appInfo,
                                         @Nullable GosPackageStateBase ps) {
        return getDefaultValue(ctx, userId, appInfo, ps, StateInfo.PLACEHOLDER);
    }

    public abstract boolean getDefaultValue(Context ctx, int userId, ApplicationInfo appInfo,
                                            @Nullable GosPackageStateBase ps, StateInfo si);

    public final boolean get(Context ctx, int userId, ApplicationInfo appInfo,
                             @Nullable GosPackageStateBase ps) {
        return get(ctx, userId, appInfo, ps, StateInfo.PLACEHOLDER);
    }

    public final boolean get(Context ctx, int userId, ApplicationInfo appInfo,
                             @Nullable GosPackageStateBase ps, StateInfo si) {
        Boolean immValue = getImmutableValue(ctx, userId, appInfo, ps, si);

        boolean res;
        if (immValue != null) {
            si.isImmutable = true;
            res = immValue.booleanValue();
        } else if (isUsingDefaultValue(ps)) {
            si.isUsingDefaultValue = true;
            res = getDefaultValue(ctx, userId, appInfo, ps, si);
        } else {
            res = ps.hasFlags(gosPsFlag);
            if (gosPsFlagInverted) {
                res = !res;
            }
        }

        return res;
    }

    public final void set(GosPackageState.Editor ed, boolean on) {
        if (gosPsFlagNonDefault != 0) {
            ed.addFlags(gosPsFlagNonDefault);
        }
        
        if (gosPsFlagInverted) {
            ed.setFlagsState(gosPsFlag, !on);
        } else {
            ed.setFlagsState(gosPsFlag, on);
        }
    }

    private boolean isUsingDefaultValue(@Nullable GosPackageStateBase ps) {
        return ps == null || (gosPsFlagNonDefault != 0 && !ps.hasFlags(gosPsFlagNonDefault));
    }

    public final void setUseDefaultValue(GosPackageState.Editor ed) {
        ed.clearFlags(gosPsFlagNonDefault | gosPsFlag);
    }

    public final boolean isNotificationSuppressed(@Nullable GosPackageStateBase ps) {
        int flag = gosPsFlagSuppressNotif;
        if (flag == 0) {
            return false;
        }
        return ps == null || ps.hasFlags(flag);
    }

    public final void addSuppressNotificationFlag(GosPackageState.Editor ed) {
        ed.addFlags(gosPsFlagSuppressNotif);
    }

    @Nullable
    protected static AppCompatConfig getAppCompatConfig(ApplicationInfo appInfo, int userId) {
        return ExtAppCompat.getAppCompatConfig(appInfo, userId);
    }
}
