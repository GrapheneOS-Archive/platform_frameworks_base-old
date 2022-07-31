package com.android.server.ext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Slog;

class WifiAutoOff extends DelayedConditionalAction {
    private final WifiManager wifiManager;

    WifiAutoOff(SystemServerExt sse) {
        super(sse, sse.bgHandler);
        wifiManager = sse.context.getSystemService(WifiManager.class);
    }

    @Override
    protected boolean shouldScheduleAlarm() {
        return isWifiEnabledAndNotConnected();
    }

    @Override
    protected void alarmTriggered() {
        if (isWifiEnabledAndNotConnected()) {
            wifiManager.setWifiEnabled(false);
        }
    }

    private boolean isWifiEnabledAndNotConnected() {
        if (wifiManager.isWifiEnabled()) {
            WifiInfo i = wifiManager.getConnectionInfo();
            if (i == null) {
                return true;
            }
            return i.getBSSID() == null;
        }

        return false;
    }

    @Override
    protected void registerStateListener() {
        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        // ConnectivityManager APIs seem unfit for listening to Wi-Fi state specifically, they look
        // to be higher level than that, eg VPN over Wi-Fi isn't considered to be a Wi-Fi connection
        // by ConnectivityManager

        sse.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Build.isDebuggable()) {
                    Slog.d("WifiAutoOff", "" + intent + ", extras " + intent.getExtras().deepCopy());
                }
                update();
            }
        }, f, handler);
    }

    @Override
    protected String getDelayGlobalSettingsKey() {
        return Settings.Global.WIFI_OFF_TIMEOUT;
    }
}
