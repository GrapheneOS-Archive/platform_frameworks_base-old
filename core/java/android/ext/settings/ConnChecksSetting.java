package android.ext.settings;

import android.annotation.SystemApi;
import android.os.SystemProperties;

/** @hide */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class ConnChecksSetting {

    public static final int VAL_GRAPHENEOS = 0;
    public static final int VAL_STANDARD = 1;
    public static final int VAL_DISABLED = 2;
    public static final int VAL_DEFAULT = VAL_GRAPHENEOS;

    public static int get() {
        return ExtSettings.CONNECTIVITY_CHECKS.get();
    }

    public static boolean put(int val) {
        return ExtSettings.CONNECTIVITY_CHECKS.put(val);
    }

    /**
     * Use only during migration, to decide whether to perform it.
     * @hide
     */
    public static boolean isSet() {
        return !SystemProperties.get(ExtSettings.CONNECTIVITY_CHECKS.getKey()).isEmpty();
    }

    private ConnChecksSetting() {}
}
