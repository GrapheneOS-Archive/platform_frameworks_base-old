package android.ext.settings;

import android.provider.Settings;

/** @hide */
public class GnssSettings {

    public static final int SUPL_SERVER_STANDARD = 0;
    public static final int SUPL_DISABLED = 1;
    public static final int SUPL_SERVER_GRAPHENEOS_PROXY = 2;

    public static final IntSetting SUPL_SETTING = new IntSetting(
            Setting.Scope.GLOBAL, Settings.Global.GNSS_SUPL,
            SUPL_SERVER_GRAPHENEOS_PROXY, // default
            SUPL_SERVER_STANDARD, SUPL_DISABLED, SUPL_SERVER_GRAPHENEOS_PROXY // valid values
    );

}
