package android.ext;

import android.content.Context;
import android.content.pm.PackageManager;
import android.ext.settings.ExtSettings;
import android.os.Binder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.widget.ILockSettings;

/** @hide */
public class AutoReboot {
    private static final String TAG = AutoReboot.class.getSimpleName();

    private static final int EV_DEVICE_UNLOCKED = 0;
    private static final int EV_DEVICE_LOCKED = 1;

    // writes to this system property are special-cased in init
    private static final String SYS_PROP = "sys.auto_reboot_ctl";

    // Called by SystemUI after lockscreen is dismissed.
    // Note that "swipe-to-unlock" and "no lockscreen" lockscreen modes produce this callback too.
    // If they were ignored, auto-reboot timer would be unstoppable for user profiles that have these
    // lockscreen modes.
    public static void onDeviceUnlocked(ILockSettings iLockSettings) {
        onEvent(EV_DEVICE_UNLOCKED, iLockSettings);
    }

    // Called by SystemUI right before the device transitions to the lockscreen.
    // "Swipe-to-unlock" and "no lockscreen" lockscreen modes produce this callback too.
    public static void onDeviceLocked(ILockSettings iLockSettings) {
        onEvent(EV_DEVICE_LOCKED, iLockSettings);
    }

    // Event handling is delegated to system_server for the following reasons:
    // - sys.auto_reboot_ctl sysprop is protected by an SELinux policy. SystemUI runs in the
    // platform_app domain, which is used by several other system processes. system_server runs in
    // its own domain.
    // - Auto-reboot timeout value is read from the SettingsProvider, which runs inside system_server.
    // Reading it from system_server is more reliable, since it avoids IPC. IPC can fail in some
    // edge cases.
    private static boolean onEvent(int event, ILockSettings iLockSettings) {
        try {
            return iLockSettings.onAutoRebootEvent(event);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
            return false;
        }
    }

    // called from LockSettingsService.onAutoRebootEvent inside system_server
    public static boolean handleEventInSystemServer(Context ctx, int event) {
        // The only valid caller is SystemUI
        final String systemUiPackage = ctx.getString(com.android.internal.R.string.config_systemUi);
        final int systemUiUid;
        try {
            systemUiUid = ctx.getPackageManager().getPackageUid(systemUiPackage, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(e);
        }
        if (Binder.getCallingUid() != systemUiUid) {
            throw new SecurityException("unknown caller");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            return handleEventInSystemServerInner(ctx, event);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public static boolean handleEventInSystemServerInner(Context ctx, int event) {
        switch (event) {
            case EV_DEVICE_UNLOCKED: {
                SystemProperties.set(SYS_PROP, "on_device_unlocked");
                Log.d(TAG, "onDeviceUnlocked");
                return true;
            }
            case EV_DEVICE_LOCKED: {
                final int timeoutMillis = ExtSettings.AUTO_REBOOT_TIMEOUT.get(ctx);
                final int timeoutSeconds = timeoutMillis / 1000;
                if (timeoutSeconds > 0) {
                    SystemProperties.set(SYS_PROP, Integer.toString(timeoutSeconds));
                }
                Log.d(TAG, "onDeviceLocked, timeoutSeconds: " + timeoutSeconds);
                return timeoutSeconds > 0;
            }
            default: {
                throw new IllegalArgumentException(Integer.toString(event));
            }
        }
    }
}
