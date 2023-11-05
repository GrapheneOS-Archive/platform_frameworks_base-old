package android.ext.settings;

import android.annotation.BoolRes;
import android.annotation.IntegerRes;
import android.annotation.StringRes;
import android.content.Context;
import android.provider.Settings;

import com.android.internal.R;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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

    public static final BoolSysProperty ALLOW_GOOGLE_APPS_SPECIAL_ACCESS_TO_ACCELERATORS = new BoolSysProperty(
            // also accessed in native code, in frameworks/native/cmds/servicemanager/Access.cpp
            "persist.sys.allow_google_apps_special_access_to_accelerators", true);

    // The amount of time in milliseconds before a disconnected Wi-Fi adapter is turned off
    public static final IntSetting WIFI_AUTO_OFF = new IntSetting(
            Setting.Scope.GLOBAL, Settings.Global.WIFI_AUTO_OFF, 0 /* off by default */);

    // The amount of time in milliseconds before a disconnected Bluetooth adapter is turned off
    public static final IntSetting BLUETOOTH_AUTO_OFF = new IntSetting(
            Setting.Scope.GLOBAL, Settings.Global.BLUETOOTH_AUTO_OFF, 0 /* off by default */);

    public static final BoolSysProperty ALLOW_NATIVE_DEBUG_BY_DEFAULT = new BoolSysProperty(
            "persist.native_debug", defaultBool(R.bool.setting_default_allow_native_debugging));

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

    public static final BoolSetting RESTRICT_MEMORY_DYN_CODE_LOADING_BY_DEFAULT = new BoolSetting(
            Setting.Scope.GLOBAL, Settings.Global.RESTRICT_MEMORY_DYN_CODE_LOADING_BY_DEFAULT,
            defaultBool(R.bool.setting_default_restrict_memory_dyn_code_loading));

    public static final BoolSetting RESTRICT_STORAGE_DYN_CODE_LOADING_BY_DEFAULT = new BoolSetting(
            Setting.Scope.GLOBAL, Settings.Global.RESTRICT_STORAGE_DYN_CODE_LOADING_BY_DEFAULT,
            defaultBool(R.bool.setting_default_restrict_storage_dyn_code_loading));

    public static final BoolSetting RESTRICT_WEBVIEW_DYN_CODE_LOADING_BY_DEFAULT = new BoolSetting(
            Setting.Scope.GLOBAL, Settings.Global.RESTRICT_WEBVIEW_DYN_CODE_LOADING_BY_DEFAULT,
            defaultBool(R.bool.setting_default_restrict_webview_dyn_code_loading));

    public static final BoolSetting FORCE_APP_MEMTAG_BY_DEFAULT = new BoolSetting(
            Setting.Scope.GLOBAL, Settings.Global.FORCE_APP_MEMTAG_BY_DEFAULT,
            defaultBool(R.bool.setting_default_force_app_memtag));

    public static final BoolSetting SHOW_SYSTEM_PROCESS_CRASH_NOTIFICATIONS = new BoolSetting(
            Setting.Scope.GLOBAL, Settings.Global.SHOW_SYSTEM_PROCESS_CRASH_NOTIFICATIONS, false);

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
