package android.ext;

import android.content.Context;
import android.ext.settings.BoolSysProperty;

import com.android.internal.R;

/** @hide */
public class BluetoothUtils {
    private static volatile String bluetoothStackPackageName;

    public static String getBluetoothStackPackageName(Context ctx) {
        String cache = bluetoothStackPackageName;
        if (cache != null) {
            return cache;
        }
        String res = ctx.getString(R.string.config_systemBluetoothStack);
        bluetoothStackPackageName = res;
        return res;
    }
}
