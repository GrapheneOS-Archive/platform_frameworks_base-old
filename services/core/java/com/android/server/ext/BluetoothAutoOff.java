package com.android.server.ext;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.ext.settings.ExtSettings;
import android.os.Build;
import android.util.Slog;

import java.util.List;

class BluetoothAutoOff extends DelayedConditionalAction {
    private static final String TAG = BluetoothAutoOff.class.getSimpleName();

    private final BluetoothManager manager;
    @Nullable
    private final BluetoothAdapter adapter;

    BluetoothAutoOff(SystemServerExt sse) {
        super(sse, ExtSettings.BLUETOOTH_AUTO_OFF, sse.bgHandler);
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

        sse.context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context broadcastContext, Intent intent) {
                Slog.d(TAG, "" + intent + ", extras " + intent.getExtras().deepCopy());
                update();
            }
        }, f, null, handler);
    }

    private boolean isAdapterOnAndDisconnected() {
        if (adapter != null) {
            if (adapter.isLeEnabled()) {
                if (adapter.getConnectionState() == BluetoothAdapter.STATE_DISCONNECTED) {
                    // Bluetooth GATT Profile (Bluetooth LE) connection state is ignored
                    // by getConnectionState()
                    List<BluetoothDevice> connectedDevices;
                    try {
                        connectedDevices = manager.getConnectedDevices(BluetoothProfile.GATT);
                    } catch (NullPointerException e) {
                        // getConnectedDevices() throws an undocumented NullPointerException if
                        // GattService gets racily stopped
                        Slog.e(TAG, "", e);
                        return false;
                    }

                    return connectedDevices == null || connectedDevices.size() == 0;
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
}
