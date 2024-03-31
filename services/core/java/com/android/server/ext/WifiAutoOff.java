package com.android.server.ext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.ext.settings.ExtSettings;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.HandlerExecutor;
import android.util.Slog;

import static android.net.wifi.WifiManager.WifiNetworkStateChangedListener;
import static java.util.Objects.requireNonNull;

class WifiAutoOff extends DelayedConditionalAction implements WifiNetworkStateChangedListener {
    private static final String TAG = WifiAutoOff.class.getSimpleName();

    private final WifiManager wifiManager;

    private WifiAutoOff(SystemServerExt sse) {
        super(sse, ExtSettings.WIFI_AUTO_OFF, sse.bgHandler);
        wifiManager = sse.context.getSystemService(WifiManager.class);
    }

    static void maybeInit(SystemServerExt sse) {
        if (sse.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI, 0)) {
            new WifiAutoOff(sse).init();
        }
    }

    @Override
    protected boolean shouldScheduleAlarm() {
        return isEligibleForAutoOff();
    }

    @Override
    protected void alarmTriggered() {
        if (isEligibleForAutoOff()) {
            Slog.d(TAG, "setWifiEnabled(false)");
            // setWifiEnabled() is not deprecated for system apps and components
            // noinspection deprecation
            wifiManager.setWifiEnabled(false);
        }
    }

    private boolean isEligibleForAutoOff() {
        boolean res = currentWifiState == WifiManager.WIFI_STATE_ENABLED
                && !isConnectedOrConnectingNetworkState(currentWifiNetworkState);

        Slog.d(TAG, "isEligibleForAutoOff: " + res
                + "; wifiState: " + wifiStateToString(currentWifiState)
                + ", wifiNetworkState: " + wifiNetworkStateToString(currentWifiNetworkState));

        return res;
    }

    private static boolean isConnectedOrConnectingNetworkState(int state) {
        switch (state) {
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTING:
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_AUTHENTICATING:
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_OBTAINING_IPADDR:
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTED:
                return true;
            default:
                return false;
        }
    }

    private int currentWifiState;
    private int currentWifiNetworkState;

    /** @see WifiNetworkStateChangedListener */
    @Override
    public void onWifiNetworkStateChanged(int cmmRole, int state) {
        Slog.d(TAG, "onWifiNetworkStateChanged: cmmRole " + cmmRole +
                ", state " + wifiNetworkStateToString(state));
        if (cmmRole != WIFI_ROLE_CLIENT_PRIMARY) {
            return;
        }

        currentWifiNetworkState = state;

        if (!isConnectedOrConnectingNetworkState(state)) {
            update();
        }
    }

    @Override
    protected void registerStateListener() {
        var filter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        var receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = requireNonNull(intent.getExtras());
                currentWifiState = requireNonNull(extras.getNumber(WifiManager.EXTRA_WIFI_STATE));
                Slog.d(TAG, "WIFI_STATE_CHANGED broadcast, extras " + extras);

                update();
            }
        };
        sse.context.registerReceiver(receiver, filter, null, handler);
        currentWifiState = wifiManager.getWifiState();

        wifiManager.addWifiNetworkStateChangedListener(new HandlerExecutor(handler), this);
        currentWifiNetworkState = wifiManager.getCurrentNetwork() != null ?
                WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTED :
                WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_DISCONNECTED;
    }

    static String wifiStateToString(int state) {
        return switch (state) {
            case WifiManager.WIFI_STATE_DISABLING -> "DISABLING";
            case WifiManager.WIFI_STATE_DISABLED -> "DISABLED";
            case WifiManager.WIFI_STATE_ENABLING -> "ENABLING";
            case WifiManager.WIFI_STATE_ENABLED -> "ENABLED";
            case WifiManager.WIFI_STATE_UNKNOWN -> "UNKNOWN";
            default -> throw new IllegalArgumentException(Integer.toString(state));
        };
    }

    static String wifiNetworkStateToString(int state) {
        return switch (state) {
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_IDLE -> "IDLE";
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_SCANNING -> "SCANNING";
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTING -> "CONNECTING";
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_AUTHENTICATING -> "AUTHENTICATING";
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_OBTAINING_IPADDR -> "OBTAINING_IPADDR";
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTED -> "CONNECTED";
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_DISCONNECTED -> "DISCONNECTED";
            case WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_FAILED -> "FAILED";
            default -> throw new IllegalArgumentException(Integer.toString(state));
        };
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}
