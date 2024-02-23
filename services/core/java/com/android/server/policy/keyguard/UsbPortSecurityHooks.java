package com.android.server.policy.keyguard;

import android.content.Context;
import android.ext.settings.UsbPortSecurity;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.R;

public class UsbPortSecurityHooks {
    private static final String TAG = UsbPortSecurityHooks.class.getSimpleName();

    private static volatile boolean keyguardShownAtLeastOnce;

    public static void onKeyguardShowingStateChanged(Context ctx, boolean showing, int userId) {
        if (!ctx.getResources().getBoolean(R.bool.config_usbPortSecuritySupported)) {
            return;
        }

        int setting = UsbPortSecurity.MODE_SETTING.get();

        Slog.d(TAG, "onKeyguardShowingStateChanged, showing " + showing + ", userId " + userId
                + ", modeSetting " + setting);

        if (setting == UsbPortSecurity.MODE_CHARGING_ONLY_WHEN_LOCKED
              || (keyguardShownAtLeastOnce && setting == UsbPortSecurity.MODE_CHARGING_ONLY_WHEN_LOCKED_AFU))
        {
            int mode = showing ?
                    android.hardware.usb.ext.PortSecurityState.CHARGING_ONLY :
                    android.hardware.usb.ext.PortSecurityState.ENABLED;

            UsbManager um = ctx.getSystemService(UsbManager.class);
            for (UsbPort p : um.getPorts()) {
                try {
                    um.setPortSecurityState(p, mode);
                } catch (Exception e) {
                    // don't crash the system_server
                    Slog.e(TAG, "", e);
                }
            }
        }

        if (userId == UserHandle.USER_SYSTEM && showing) {
            keyguardShownAtLeastOnce = true;
        }
    }
}
