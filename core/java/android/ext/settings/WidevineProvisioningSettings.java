package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Context;
import android.provider.Settings;

import static android.annotation.SystemApi.Client.MODULE_LIBRARIES;

/** @hide */
@SystemApi(client = MODULE_LIBRARIES)
public class WidevineProvisioningSettings {
    /** @hide */
    public static final int WV_GRAPHENEOS_PROXY = 0;
    /** @hide */
    public static final int WV_STANDARD_SERVER = 1;

    private static final String WV_GRAPHENEOS_PROXY_HOSTNAME = "widevineprovisioning.grapheneos.org";

    /** @hide */
    public static final IntSetting SERVER_SETTING = new IntSetting(
            Setting.Scope.GLOBAL, Settings.Global.WIDEVINE_PROVISIONING_SERVER,
            WV_GRAPHENEOS_PROXY, // default
            WV_STANDARD_SERVER, WV_GRAPHENEOS_PROXY // valid values
    );

    @Nullable
    public static String getServerHostnameOverride(@NonNull Context ctx) {
        if (SERVER_SETTING.get(ctx) == WV_GRAPHENEOS_PROXY) {
            return WV_GRAPHENEOS_PROXY_HOSTNAME;
        }
        return null;
    }

    private WidevineProvisioningSettings() {}
}
