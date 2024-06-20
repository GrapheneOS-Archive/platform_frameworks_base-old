package com.android.server.policy.keyguard;

import android.content.Context;
import android.ext.settings.ExtSettings;
import android.os.SystemProperties;
import android.util.Slog;

/** @hide */
class AutoReboot {
    private static final String TAG = AutoReboot.class.getSimpleName();

    // writes to this system property are special-cased in init
    private static final String SYS_PROP = "sys.auto_reboot_ctl";

    // This callback is invoked:
    // - when keyguard becomes active (i.e. when device gets locked, including at boot-time)
    // - when keyguard is dismissed by unlocking the device
    // - when keyguard is dismissed by switching to a user that doesn't have a secure lockscreen,
    // but not when switching to a user that does have a secure lockscreen
    // - showing=false callback is invoked at boot-time when there's no lock screen, i.e. when the
    // device boots straight into the home screen or initial setup wizard
    //
    // Note that "swipe-to-unlock" lockscreen is considered to be a keyguard.
    static void onKeyguardShowingStateChanged(Context ctx, boolean showing, int userId) {
        Slog.d(TAG, "onKeyguardShowingStateChanged, showing: " + showing + ", userId: " + userId);

        if (!showing) {
            SystemProperties.set(SYS_PROP, "on_device_unlocked");
            return;
        }

        final int timeoutMillis = ExtSettings.AUTO_REBOOT_TIMEOUT.get(ctx);
        final int timeoutSeconds = timeoutMillis / 1000;
        if (timeoutSeconds > 0) {
            SystemProperties.set(SYS_PROP, Integer.toString(timeoutSeconds));
        }
        Slog.d(TAG, "timeoutSeconds: " + timeoutSeconds);
    }
}
