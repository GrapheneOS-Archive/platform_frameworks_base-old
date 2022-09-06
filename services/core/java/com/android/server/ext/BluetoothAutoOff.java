package com.android.server.ext;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;
import android.util.Slog;

class BluetoothAutoOff extends DelayedConditionalAction {
    private final BluetoothManager manager;
    @Nullable
    private final BluetoothAdapter adapter;

    BluetoothAutoOff(SystemServerExt sse) {
        super(sse, sse.bgHandler);
        manager = sse.context.getSystemService(BluetoothManager.class);
        adapter = manager.getAdapter();
    }

    @Override
    protected boolean shouldScheduleAlarm() {
        return isAdapterOnAndDisconnected();
    }

    @Override
    protected void alarmTriggered() {
        if (isAdapterOnAndDisconnected()) {
            adapter.disable();
        }
    }

    @Override
    protected void registerStateListener() {
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);

        sse.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context broadcastContext, Intent intent) {
                if (Build.isDebuggable()) {
                    Slog.d("BtAutoOff", "" + intent + ", extras " + intent.getExtras().deepCopy());
                }
                update();
            }
        }, f, handler);
    }

    private boolean isAdapterOnAndDisconnected() {
        if (adapter != null) {
            if (adapter.isLeEnabled()) {
                if (adapter.getConnectionState() == BluetoothAdapter.STATE_DISCONNECTED) {
                    // Bluetooth GATT Profile (Bluetooth LE) connection state is ignored
                    // by getConnectionState()
                    return manager.getConnectedDevices(BluetoothProfile.GATT).size() == 0;
                }
            }

            // isLeEnabled() currently implies isEnabled(), but check again anyway in case
            // this changes in the future
            if (adapter.isEnabled()) {
                return adapter.getConnectionState() == BluetoothAdapter.STATE_DISCONNECTED;
            }
        }

        return false;
    }

    @Override
    protected String getDelayGlobalSettingsKey() {
        return Settings.Global.BLUETOOTH_OFF_TIMEOUT;
    }
}
