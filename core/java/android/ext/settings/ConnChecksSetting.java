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

    /**
     * Note that the underlying system property is read directly in packages/modules/DnsResolver
     *
     * @hide
     * */
    public static final IntSysProperty SYS_PROP = new IntSysProperty(
            "persist.sys.connectivity_checks",
            ConnChecksSetting.VAL_DEFAULT,
            ConnChecksSetting.VAL_GRAPHENEOS, ConnChecksSetting.VAL_STANDARD, ConnChecksSetting.VAL_DISABLED
    );

    public static int get() {
        return SYS_PROP.get();
    }

    public static boolean put(int val) {
        return SYS_PROP.put(val);
    }

    /**
     * Use only during migration, to decide whether to perform it.
     * @hide
     */
    public static boolean isSet() {
        return !SystemProperties.get(SYS_PROP.getKey()).isEmpty();
    }

    private ConnChecksSetting() {}
}
