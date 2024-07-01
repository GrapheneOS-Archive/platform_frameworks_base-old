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
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.os.BackgroundThread;

import java.util.Objects;

public class UsbPortSecurityHooks {
    private static final String TAG = UsbPortSecurityHooks.class.getSimpleName();
    @Nullable
    private static UsbPortSecurityHooks INSTANCE;

    private final Context context;
    private final Handler handler = BackgroundThread.getHandler();
    private final UsbManager usbManager;

    private UsbPortSecurityHooks(Context ctx) {
        this.context = ctx;
        this.usbManager = Objects.requireNonNull(ctx.getSystemService(UsbManager.class));
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

    private boolean keyguardDismissedAtLeastOnce;
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
              || (keyguardDismissedAtLeastOnce && setting == UsbPortSecurity.MODE_CHARGING_ONLY_WHEN_LOCKED_AFU))
        {
            if (showing) {
                setSecurityStateForAllPorts(android.hardware.usb.ext.PortSecurityState.CHARGING_ONLY);
                usbConnectEventCountBeforeLocked = usbConnectEventCount;
            } else {
                boolean forceReconnect = false;
                if (!keyguardDismissedAtLeastOnce) {
                    for (UsbPort port : usbManager.getPorts()) {
                        UsbPortStatus s = port.getStatus();
                        if (s == null || s.isConnected()) {
                            // at boot-time, "port connected" event might not be delivered if the
                            // event fires before UsbService is initialized, which breaks the
                            // usbConnectEventCountBeforeLocked check below
                            forceReconnect = true;
                            break;
                        }
                    }
                }

                if (!forceReconnect && usbConnectEventCountBeforeLocked == usbConnectEventCount) {
                    setSecurityStateForAllPorts(android.hardware.usb.ext.PortSecurityState.ENABLED);
                } else {
                    // Turn USB ports off and on to trigger reconnection of devices that were connected
                    // in charging-only state. Simply enabling the data path is not enough in some
                    // advanced scenarios, e.g. when port alt mode or port role switching are used.
                    Slog.d(TAG, "toggling USB ports");
                    setSecurityStateForAllPorts(android.hardware.usb.ext.PortSecurityState.DISABLED);
                    final long curShowingChangeCount = keyguardShowingChangeCount;
                    final long delayMs = 1500;
                    handler.postDelayed(() -> {
                        if (keyguardShowingChangeCount == curShowingChangeCount) {
                            setSecurityStateForAllPorts(android.hardware.usb.ext.PortSecurityState.ENABLED);
                        } else {
                            Slog.d(TAG, "showingChangeCount changed, skipping delayed enable");
                        }
                    }, delayMs);
                }
            }
        }

        if (userId == UserHandle.USER_SYSTEM && !showing) {
            keyguardDismissedAtLeastOnce = true;
        }
    }

    private void setSecurityStateForAllPorts(int state) {
        Slog.d(TAG, "setSecurityStateForAllPorts: " + state);

        for (UsbPort port : usbManager.getPorts()) {
            var resultReceiver = new ResultReceiver(null) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (resultCode != android.hardware.usb.ext.IUsbExt.NO_ERROR) {
                        throw new IllegalStateException("setPortSecurityState failed, resultCode: " + resultCode + ", port: " + port);
                    }
                }
            };

            usbManager.setPortSecurityState(port, state, resultReceiver);
        }
    }
}
