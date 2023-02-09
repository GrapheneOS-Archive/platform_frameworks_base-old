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

/** @hide */
public class ExtSettings {

    public static final BoolSysProperty EXEC_SPAWNING = new BoolSysProperty(
            "persist.security.exec_spawn", true);

    public static final BoolSetting AUTO_GRANT_OTHER_SENSORS_PERMISSION = new BoolSetting(
            Setting.Scope.PER_USER, "auto_grant_OTHER_SENSORS_perm", true);

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
