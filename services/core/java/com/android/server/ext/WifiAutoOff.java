package com.android.server.ext;

import android.ext.settings.ExtSettings;
import android.net.wifi.WifiManager;
import android.os.HandlerExecutor;
import android.util.Slog;

import static android.net.wifi.WifiManager.WifiNetworkStateChangedListener;
import static java.util.Objects.requireNonNull;

class WifiAutoOff extends DelayedConditionalAction implements WifiNetworkStateChangedListener {
    private static final String TAG = WifiAutoOff.class.getSimpleName();

    private final WifiManager wifiManager;

    WifiAutoOff(SystemServerExt sse) {
        super(sse, ExtSettings.WIFI_AUTO_OFF, sse.bgHandler);
        wifiManager = sse.context.getSystemService(WifiManager.class);
    }

    @Override
    protected boolean shouldScheduleAlarm() {
        return isWifiEnabledAndNotConnectedOrConnecting();
    }

    @Override
    protected void alarmTriggered() {
        if (isWifiEnabledAndNotConnectedOrConnecting()) {
            Slog.d(TAG, "setWifiEnabled(false)");
            // setWifiEnabled() is not deprecated for system apps and components
            // noinspection deprecation
            wifiManager.setWifiEnabled(false);
        }
    }

    private boolean isWifiEnabledAndNotConnectedOrConnecting() {
        if (wifiManager.isWifiEnabled()) {
            int state = currentWifiState;
            Slog.d(TAG, "isWifiEnabledAndNotConnected: Wifi enabled, curState: " +
                    networkStateToString(state));
            return !isConnectedOrConnectingState(state);
        }
        Slog.d(TAG, "isWifiEnabledAndNotConnected: Wifi not enabled");
        return false;
    }

    private static boolean isConnectedOrConnectingState(int state) {
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

    /** @see WifiNetworkStateChangedListener */
    @Override
    public void onWifiNetworkStateChanged(int cmmRole, int state) {
        Slog.d(TAG, "onWifiNetworkStateChanged: cmmRole " + cmmRole +
                ", state " + networkStateToString(state));
        if (cmmRole != WIFI_ROLE_CLIENT_PRIMARY) {
            return;
        }

        currentWifiState = state;

        if (!isConnectedOrConnectingState(state)) {
            update();
        }
    }

    @Override
    protected void registerStateListener() {
        WifiManager wifiManager = requireNonNull(sse.context.getSystemService(WifiManager.class));
        wifiManager.addWifiNetworkStateChangedListener(new HandlerExecutor(handler), this);
    }

    static String networkStateToString(int state) {
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
