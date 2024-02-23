package com.android.server.policy.keyguard;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.ext.settings.UsbPortSecurity;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.os.BackgroundThread;

public class UsbPortSecurityHooks {
    private static final String TAG = UsbPortSecurityHooks.class.getSimpleName();
    @Nullable
    private static UsbPortSecurityHooks INSTANCE;

    private final Context context;
    private final Handler handler = BackgroundThread.getHandler();

    private UsbPortSecurityHooks(Context ctx) {
        this.context = ctx;
    }

    public static void init(Context ctx) {
        if (!ctx.getResources().getBoolean(R.bool.config_usbPortSecuritySupported)) {
            return;
        }

        var i = new UsbPortSecurityHooks(ctx);
        INSTANCE = i;
        i.registerPortChangeReceiver();
    }

    void registerPortChangeReceiver() {
        var receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Slog.d(TAG, "PortChangeReceiver: " + intent + ", extras " + intent.getExtras().deepCopy());
                UsbPortStatus portStatus = intent.getParcelableExtra(UsbManager.EXTRA_PORT_STATUS,
                        UsbPortStatus.class);
                if (portStatus.isConnected()) {
                    ++usbConnectEventCount;
                    Slog.d(TAG, "usbConnectEventCount: " + usbConnectEventCount);
                }
            }
        };
        var filter = new IntentFilter(UsbManager.ACTION_USB_PORT_CHANGED);
        context.registerReceiver(receiver, filter, null, handler);
    }

    public static void onKeyguardShowingStateChanged(Context ctx, boolean showing, int userId) {
        UsbPortSecurityHooks i = INSTANCE;
        if (i != null) {
            i.handler.post(() -> i.onKeyguardShowingStateChangedInner(ctx, showing, userId));
        }
    }

    private boolean keyguardShownAtLeastOnce;
    private Boolean prevKeyguardShowing; // intentionally using boxed boolean to have a null value
    private long keyguardShowingChangeCount;

    private int usbConnectEventCountBeforeLocked;
    private int usbConnectEventCount;

    void onKeyguardShowingStateChangedInner(Context ctx, boolean showing, int userId) {
        int setting = UsbPortSecurity.MODE_SETTING.get();

        Slog.d(TAG, "onKeyguardShowingStateChanged, showing " + showing + ", userId " + userId
                + ", modeSetting " + setting);

        Boolean showingB = Boolean.valueOf(showing);
        if (prevKeyguardShowing == showingB) {
            Slog.d(TAG, "onKeyguardShowingStateChangedInner: duplicate callback, ignoring");
            return;
        }
        prevKeyguardShowing = showingB;
        ++keyguardShowingChangeCount;

        if (setting == UsbPortSecurity.MODE_CHARGING_ONLY_WHEN_LOCKED
              || (keyguardShownAtLeastOnce && setting == UsbPortSecurity.MODE_CHARGING_ONLY_WHEN_LOCKED_AFU))
        {
            if (showing) {
                setSecurityStateForAllPorts(ctx, android.hardware.usb.ext.PortSecurityState.CHARGING_ONLY);
                usbConnectEventCountBeforeLocked = usbConnectEventCount;
            } else {
                if (usbConnectEventCountBeforeLocked == usbConnectEventCount) {
                    setSecurityStateForAllPorts(ctx, android.hardware.usb.ext.PortSecurityState.ENABLED);
                } else {
                    // Turn USB ports off and on to trigger reconnection of devices that were connected
                    // in charging-only state. Simply enabling the data path is not enough in some
                    // advanced scenarios, e.g. when port alt mode or port role switching are used.
                    Slog.d(TAG, "usbConnectEventCount changed, toggling USB ports");
                    setSecurityStateForAllPorts(ctx, android.hardware.usb.ext.PortSecurityState.DISABLED);
                    final long curShowingChangeCount = keyguardShowingChangeCount;
                    final long delayMs = 1500;
                    handler.postDelayed(() -> {
                        if (keyguardShowingChangeCount == curShowingChangeCount) {
                            setSecurityStateForAllPorts(ctx, android.hardware.usb.ext.PortSecurityState.ENABLED);
                        } else {
                            Slog.d(TAG, "showingChangeCount changed, skipping delayed enable");
                        }
                    }, delayMs);
                }
            }
        }

        if (userId == UserHandle.USER_SYSTEM && showing) {
            keyguardShownAtLeastOnce = true;
        }
    }

    private static void setSecurityStateForAllPorts(Context ctx, int state) {
        Slog.d(TAG, "setSecurityStateForAllPorts: " + state);

        UsbManager um = ctx.getSystemService(UsbManager.class);
        if (um == null) {
            Slog.e(TAG, "UsbManager is null");
            return;
        }

        for (UsbPort p : um.getPorts()) {
            try {
                um.setPortSecurityState(p, state);
            } catch (Exception e) {
                // don't crash the system_server
                Slog.e(TAG, "", e);
            }
        }
    }
}
