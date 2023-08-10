package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Context;
import android.provider.Settings;

import static android.annotation.SystemApi.Client.MODULE_LIBRARIES;

/** @hide */
@SystemApi(client = MODULE_LIBRARIES)
public class RemoteKeyProvisioningSettings {

    public static final int GRAPHENEOS_PROXY = 0;
    public static final int STANDARD_SERVER = 1;

    private static final String GRAPHENEOS_PROXY_URL = "https://remoteprovisioning.grapheneos.org/v1";

    /** @hide */
    public static final IntSetting SERVER_SETTING = new IntSetting(
            Setting.Scope.GLOBAL, Settings.Global.REMOTE_KEY_PROVISIONING_SERVER,
            GRAPHENEOS_PROXY, // default
            STANDARD_SERVER, GRAPHENEOS_PROXY // valid values
    );

    @Nullable
    public static String getServerUrlOverride(@NonNull Context ctx) {
        if (SERVER_SETTING.get(ctx) == GRAPHENEOS_PROXY) {
            return GRAPHENEOS_PROXY_URL;
        }

        return null;
    }

    private RemoteKeyProvisioningSettings() {}
}
