package android.ext.settings;

/** @hide */
public class DenyNewUsbSetting {

    public static final String DISABLED = "disabled";
    public static final String DYNAMIC = "dynamic";
    public static final String ENABLED = "enabled";
    // also specified in build/make/core/main.mk
    public static final String DEFAULT = DYNAMIC;

    public static final StringSysProperty SYS_PROP = new StringSysProperty(
            "persist.security.deny_new_usb", DEFAULT) {
        @Override
        public boolean validateValue(String val) {
            switch (val) {
                case DISABLED:
                case DYNAMIC:
                case ENABLED:
                    return true;
                default:
                    return false;
            }
        }
    };

    // see system/core/rootdir/init.rc
    public static final String TRANSIENT_PROP = "security.deny_new_usb";
    public static final String TRANSIENT_ENABLE = "1";
    public static final String TRANSIENT_DISABLE = "0";
}
