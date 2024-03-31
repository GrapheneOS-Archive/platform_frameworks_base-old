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
import android.content.pm.PackageManager;
import android.ext.settings.ExtSettings;
import android.util.Slog;

import java.util.List;

class BluetoothAutoOff extends DelayedConditionalAction {
    private static final String TAG = BluetoothAutoOff.class.getSimpleName();

    private final BluetoothManager manager;
    @Nullable
    private final BluetoothAdapter adapter;

    private BluetoothAutoOff(SystemServerExt sse) {
        super(sse, ExtSettings.BLUETOOTH_AUTO_OFF, sse.bgHandler);
        manager = sse.context.getSystemService(BluetoothManager.class);
        adapter = manager.getAdapter();
    }

    static void maybeInit(SystemServerExt sse) {
        if (sse.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH, 0)) {
            new BluetoothAutoOff(sse).init();
        }
    }

    @Override
    protected boolean shouldScheduleAlarm() {
        return isAdapterOnAndDisconnected();
    }

    @Override
    protected void alarmTriggered() {
        if (isAdapterOnAndDisconnected()) {
            Slog.d(TAG, "adapter.disable(true)");
            adapter.disable(true);
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
        Slog.d(TAG, "isAdapterOnAndDisconnected");
        if (adapter != null) {
            boolean isLeEnabled = adapter.isLeEnabled();
            Slog.d(TAG, "isLeEnabled: " + isLeEnabled);
            if (isLeEnabled) {
                int connState = adapter.getConnectionState();
                Slog.d(TAG, "leConnState: " + connState);
                if (connState == BluetoothAdapter.STATE_DISCONNECTED) {
                    // Bluetooth GATT Profile (Bluetooth LE) connection state is ignored
                    // by getConnectionState()
                    List<BluetoothDevice> connectedLeDevices;
                    try {
                        connectedLeDevices = manager.getConnectedDevices(BluetoothProfile.GATT);
                    } catch (NullPointerException e) {
                        // getConnectedDevices() throws an undocumented NullPointerException if
                        // GattService gets racily stopped
                        Slog.e(TAG, "", e);
                        return false;
                    }

                    if (connectedLeDevices == null) {
                        Slog.d(TAG, "connectedLeDevices list is null");
                    } else {
                        Slog.d(TAG, "connectedLeDevices list size: " + connectedLeDevices.size());
                    }

                    return connectedLeDevices == null || connectedLeDevices.isEmpty();
                }
            }

            // isLeEnabled() currently implies isEnabled(), but check again anyway in case
            // this changes in the future
            boolean isEnabled = adapter.isEnabled();
            Slog.d(TAG, "isEnabled: " + isEnabled);
            if (isEnabled) {
                int connState = adapter.getConnectionState();
                Slog.d(TAG, "connState: " + connState);
                return connState == BluetoothAdapter.STATE_DISCONNECTED;
            }
        } else {
            Slog.d(TAG, "adapter is null");
        }

        return false;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}
