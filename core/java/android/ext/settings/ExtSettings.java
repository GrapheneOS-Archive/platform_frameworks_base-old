package android.ext.settings;

import android.annotation.BoolRes;
import android.annotation.IntegerRes;
import android.annotation.StringRes;
import android.app.AppGlobals;
import android.content.Context;
import android.content.res.Resources;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static android.ext.settings.GnssConstants.PSDS_DISABLED;
import static android.ext.settings.GnssConstants.PSDS_SERVER_GRAPHENEOS;
import static android.ext.settings.GnssConstants.PSDS_SERVER_STANDARD;
import static android.ext.settings.GnssConstants.SUPL_DISABLED;
import static android.ext.settings.GnssConstants.SUPL_SERVER_GRAPHENEOS_PROXY;
import static android.ext.settings.GnssConstants.SUPL_SERVER_STANDARD;

/** @hide */
public class ExtSettings {

    public static final BoolSysProperty EXEC_SPAWNING = new BoolSysProperty(
            "persist.security.exec_spawn", true);

    public static final BoolSetting ALLOW_KEYGUARD_CAMERA = new BoolSetting(
            Setting.Scope.SYSTEM_PROPERTY, "persist.keyguard.camera", true);

    public static final BoolSetting AUTO_GRANT_OTHER_SENSORS_PERMISSION = new BoolSetting(
            Setting.Scope.PER_USER, "auto_grant_OTHER_SENSORS_perm", true);

    public static final IntSetting AUTO_REBOOT_TIMEOUT = new IntSetting(
            Setting.Scope.GLOBAL, "settings_reboot_after_timeout",
            // default value: 18 hours
            (int) TimeUnit.HOURS.toMillis(18));

    public static final BoolSetting SCREENSHOT_TIMESTAMP_EXIF = new BoolSetting(
            Setting.Scope.PER_USER, "screenshot_timestamp_exif", false);

    public static final IntSetting GNSS_SUPL = new IntSetting(
            Setting.Scope.GLOBAL, "force_disable_supl", // historical name
            SUPL_SERVER_GRAPHENEOS_PROXY, // default
            SUPL_SERVER_STANDARD, SUPL_DISABLED, SUPL_SERVER_GRAPHENEOS_PROXY // valid values
    );

    public static final IntSetting GNSS_PSDS_STANDARD = new IntSetting(
            Setting.Scope.GLOBAL, "psds_server", // historical name
            PSDS_SERVER_GRAPHENEOS, // default
            PSDS_SERVER_GRAPHENEOS, PSDS_SERVER_STANDARD, PSDS_DISABLED // valid values
    );

    public static final IntSysProperty GNSS_PSDS_VENDOR = new IntSysProperty(
            // keep in sync with bionic/libc/bionic/gnss_psds_setting.c
            "persist.sys.gnss_psds",
            PSDS_SERVER_GRAPHENEOS, // default
            PSDS_SERVER_GRAPHENEOS, PSDS_SERVER_STANDARD, PSDS_DISABLED
    );

    public static IntSetting getGnssPsdsSetting(Context ctx) {
        String type = ctx.getString(com.android.internal.R.string.config_gnssPsdsType);
        switch (type) {
            case GnssConstants.PSDS_TYPE_QUALCOMM_XTRA:
                return GNSS_PSDS_VENDOR;
            default:
                return GNSS_PSDS_STANDARD;
        }
    }

    public static boolean isStandardGnssPsds(Context ctx) {
        return getGnssPsdsSetting(ctx) == GNSS_PSDS_STANDARD;
    }

    public static final BoolSetting SCRAMBLE_PIN_LAYOUT = new BoolSetting(
            Setting.Scope.PER_USER, "lockscreen_scramble_pin_layout", false);

    public static final BoolSysProperty ALLOW_GOOGLE_APPS_SPECIAL_ACCESS_TO_ACCELERATORS = new BoolSysProperty(
            // also accessed in native code, in frameworks/native/cmds/servicemanager/Access.cpp
            "persist.sys.allow_google_apps_special_access_to_accelerators", true);

    // AppCompatConfig specifies which hardening features are compatible/incompatible with a
    // specific app.
    // This setting controls whether incompatible hardening features would be disabled by default
    // for that app. In both cases, user will still be able to enable/disable them manually.
    //
    // Note that hardening features that are marked as compatible are enabled unconditionally by
    // default, regardless of this setting.
    public static final BoolSetting ALLOW_DISABLING_HARDENING_VIA_APP_COMPAT_CONFIG = new BoolSetting(
            Setting.Scope.GLOBAL, "allow_automatic_pkg_hardening_config", // historical name
            defaultBool(R.bool.setting_default_allow_disabling_hardening_via_app_compat_config));

    private ExtSettings() {}

    // used for making settings defined in this class unreadable by third-party apps
    public static void getKeys(Setting.Scope scope, Set<String> dest) {
        for (Field field : ExtSettings.class.getDeclaredFields()) {
            if (!Setting.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Setting s;
            try {
                s = (Setting) field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }

            if (s.getScope() == scope) {
                if (!dest.add(s.getKey())) {
                    throw new IllegalStateException("duplicate definition of setting " + s.getKey());
                }
            }
        }
    }

    public static BooleanSupplier defaultBool(@BoolRes int res) {
        return () -> getResources().getBoolean(res);
    }

    public static IntSupplier defaultInt(@IntegerRes int res) {
        return () -> getResources().getInteger(res);
    }

    public static Supplier<String> defaultString(@StringRes int res) {
        return () -> getResources().getString(res);
    }

    public static Resources getResources() {
        return AppGlobals.getInitialApplication().getResources();
    }
}
