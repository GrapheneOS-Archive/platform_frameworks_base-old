package android.ext.settings;

import android.content.Context;

import com.android.internal.R;

/** @hide */
public class AltTouchscreenMode {
    public static final String STATE_OF_DEFAULT_DISPLAY_PROP = "sys.state_of_default_display";

    public static BoolSysProperty getSetting() {
        return new BoolSysProperty("persist.sys.alt_touch_mode", false);
    }

    public static boolean isAvailable(Context ctx) {
        return ctx.getResources().getBoolean(R.bool.config_has_alternative_touchscreen_mode);
    }
}
