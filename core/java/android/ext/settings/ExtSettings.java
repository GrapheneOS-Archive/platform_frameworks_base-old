package android.ext.settings;

/** @hide */
public class ExtSettings {

    public static final BoolSetting ALLOW_KEYGUARD_CAMERA = new BoolSetting(
            Setting.Scope.SYSTEM_PROPERTY, "persist.keyguard.camera", true);

    public static final BoolSetting AUTO_GRANT_OTHER_SENSORS_PERMISSION = new BoolSetting(
            Setting.Scope.PER_USER, "auto_grant_OTHER_SENSORS_perm", true);

    public static final IntSetting AUTO_REBOOT_TIMEOUT = new IntSetting(
            Setting.Scope.GLOBAL, "settings_reboot_after_timeout", 0);

    public static final BoolSetting SCREENSHOT_TIMESTAMP_EXIF = new BoolSetting(
            Setting.Scope.PER_USER, "screenshot_timestamp_exif", false);

    private ExtSettings() {}
}
