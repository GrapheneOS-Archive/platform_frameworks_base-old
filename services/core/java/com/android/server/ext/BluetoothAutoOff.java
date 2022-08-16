/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ext;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;
import android.util.Slog;

class BluetoothAutoOff extends DelayedConditionalAction {
    @Nullable
    private final BluetoothAdapter adapter;

    BluetoothAutoOff(SystemServerExt sse) {
        super(sse, sse.bgHandler);
        adapter = sse.context.getSystemService(BluetoothManager.class).getAdapter();
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
            int state = adapter.getLeStateInternal(); // getState() converts BLE states into STATE_OFF

            if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_BLE_ON) {
                // getConnectionState() converts BLE states into STATE_DISCONNECTED
                return adapter.getConnectionStateLeAware() == BluetoothAdapter.STATE_DISCONNECTED;
            }
        }

        return false;
    }

    @Override
    protected String getDelayGlobalSettingsKey() {
        return Settings.Global.BLUETOOTH_OFF_TIMEOUT;
    }
}
