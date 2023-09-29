package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Context;

import static android.annotation.SystemApi.Client.MODULE_LIBRARIES;

/** @hide */
@SystemApi(client = MODULE_LIBRARIES)
public class WidevineProvisioningSettings {
    public static final int WV_GRAPHENEOS_PROXY = 0;
    public static final int WV_STANDARD_SERVER = 1;

    private static final String WV_GRAPHENEOS_PROXY_HOSTNAME = "widevineprovisioning.grapheneos.org";

    @Nullable
    public static String getServerHostnameOverride(@NonNull Context ctx) {
        if (ExtSettings.WIDEVINE_PROVISIONING_SERVER.get(ctx) == WV_GRAPHENEOS_PROXY) {
            return WV_GRAPHENEOS_PROXY_HOSTNAME;
        }
        return null;
    }

    private WidevineProvisioningSettings() {}
}
