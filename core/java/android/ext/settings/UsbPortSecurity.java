package android.ext.settings;

/** @hide */
public class UsbPortSecurity {
    public static final int MODE_DISABLED = 0;
    public static final int MODE_CHARGING_ONLY = 1;
    // doesn't apply to connections that were made before locking
    public static final int MODE_CHARGING_ONLY_WHEN_LOCKED = 2;
    // doesn't apply to connections that were made before locking or first unlock
    public static final int MODE_CHARGING_ONLY_WHEN_LOCKED_AFU = 3;
    public static final int MODE_ENABLED = 4;

    // keep in sync with USB HAL implementations that check this sysprop during init
    public static final IntSysProperty MODE_SETTING = new IntSysProperty(
            "persist.security.usb_mode", MODE_CHARGING_ONLY_WHEN_LOCKED_AFU);
}
