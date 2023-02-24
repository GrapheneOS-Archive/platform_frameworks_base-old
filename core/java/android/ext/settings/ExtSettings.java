package android.ext.settings;

import android.annotation.BoolRes;
import android.annotation.IntegerRes;
import android.annotation.StringRes;
import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;

import com.android.internal.R;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Note that android.provider.Settings setting names should be defined in the corresponding classes,
 * since the readability of settings is determined by using Java reflection on members of that class.
 *
 * @see android.provider.Settings#getPublicSettingsForClass
 * @hide
 */
public class ExtSettings {

    public static final BoolSysProperty EXEC_SPAWNING = new BoolSysProperty(
            "persist.security.exec_spawn", true);

    public static final BoolSetting ALLOW_KEYGUARD_CAMERA = new BoolSetting(
            Setting.Scope.SYSTEM_PROPERTY, "persist.keyguard.camera", true);

    public static final BoolSetting AUTO_GRANT_OTHER_SENSORS_PERMISSION = new BoolSetting(
            Setting.Scope.PER_USER, Settings.Secure.AUTO_GRANT_OTHER_SENSORS_PERMISSION, true);

    public static final IntSetting AUTO_REBOOT_TIMEOUT = new IntSetting(
            Setting.Scope.GLOBAL, Settings.Global.AUTO_REBOOT_TIMEOUT,
            // default value: 18 hours
            (int) TimeUnit.HOURS.toMillis(18));

    public static final BoolSetting SCREENSHOT_TIMESTAMP_EXIF = new BoolSetting(
            Setting.Scope.PER_USER, Settings.Secure.SCREENSHOT_TIMESTAMP_EXIF, false);

    public static final BoolSetting SCRAMBLE_LOCKSCREEN_PIN_LAYOUT = new BoolSetting(
            Setting.Scope.PER_USER, Settings.Secure.SCRAMBLE_PIN_LAYOUT, false);

    public static final BoolSetting SCRAMBLE_SIM_PIN_LAYOUT = new BoolSetting(
            Setting.Scope.PER_USER, Settings.Secure.SCRAMBLE_SIM_PIN_LAYOUT,
            // inherit lockscreen PIN setting by default
            SCRAMBLE_LOCKSCREEN_PIN_LAYOUT::get);

    // AppCompatConfig specifies which hardening features are compatible/incompatible with a
    // specific app.
    // This setting controls whether incompatible hardening features would be disabled by default
    // for that app. In both cases, user will still be able to enable/disable them manually.
    //
    // Note that hardening features that are marked as compatible are enabled unconditionally by
    // default, regardless of this setting.
    public static final BoolSetting ALLOW_DISABLING_HARDENING_VIA_APP_COMPAT_CONFIG = new BoolSetting(
            Setting.Scope.GLOBAL, Settings.Global.ALLOW_DISABLING_HARDENING_VIA_APP_COMPAT_CONFIG,
            defaultBool(R.bool.setting_default_allow_disabling_hardening_via_app_compat_config));

    private ExtSettings() {}

    public static Function<Context, Boolean> defaultBool(@BoolRes int res) {
        return ctx -> ctx.getResources().getBoolean(res);
    }

    public static ToIntFunction<Context> defaultInt(@IntegerRes int res) {
        return ctx -> ctx.getResources().getInteger(res);
    }

    public static Function<Context, String> defaultString(@StringRes int res) {
        return ctx -> ctx.getString(res);
    }
}
