/*
 * Copyright (C) 2022 GrapheneOS
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

package android.app.compat.gms;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatApp;
import com.android.internal.gmscompat.GmsHooks;
import com.android.internal.gmscompat.StubDef;
import com.android.internal.gmscompat.util.GmcActivityUtils;

/**
 * Hooks that are accessed from APEX modules.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class GmsModuleHooks {
    private static final String TAG = "GmsCompat/MHooks";

    // BluetoothAdapter#enable()
    // BluetoothAdapter#enableBLE()
    @SuppressLint("AutoBoxing")
    @Nullable
    // returns null if hook wasn't applied, otherwise returns boxed return value for the original method
    public static Boolean enableBluetoothAdapter() {
        if (!GmsCompat.isGmsCore()) {
            // others handle this themselves
            return null;
        }

        Activity activity = GmcActivityUtils.getMostRecentVisibleActivity();

        if (activity != null) {
            if (GmsCompat.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                activity.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                try {
                    GmsCompatApp.iGms2Gca().showGmsCoreMissingNearbyDevicesPermissionGeneric();
                } catch (RemoteException e) {
                    GmsCompatApp.callFailed(e);
                }
            }
        } // else don't bother the user

        return Boolean.TRUE;
    }

    // BluetoothAdapter#setScanMode()
    public static void makeBluetoothAdapterDiscoverable() {
        // don't use BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE intent here, this method is often
        // called at a time when the user wouldn't expect to see it
        Log.d(TAG, "makeBluetoothAdapterDiscoverable", new Throwable());
    }

    // com.android.modules.utils.SynchronousResultReceiver.Result#getValue()
    public static boolean interceptSynchronousResultReceiverException(@NonNull RuntimeException origException) {
        if (!(origException instanceof SecurityException)) {
            return false;
        }

        // origException contains service-side stack trace, need to obtain an app-side one
        var stackTrace = new Throwable();
        StubDef stub = StubDef.find(stackTrace.getStackTrace(), GmsHooks.config(), StubDef.FIND_MODE_SynchronousResultReceiver);

        if (stub == null) {
            return false;
        }

        if (stub.type != StubDef.DEFAULT) {
            Log.d(TAG, "interceptSynchronousResultReceiverException: unexpected stub type " + stub.type, stackTrace);
            return false;
        }

        if (Build.isDebuggable()) {
            Log.i(TAG, "intercepted " + origException, stackTrace);
        }

        return true;
    }

    @Nullable
    public static String deviceConfigGetProperty(@NonNull String namespace, @NonNull String name) {
        return GmsCompatApp.getString(GmsCompatApp.deviceConfigNamespace(namespace), name);
    }

    public static boolean deviceConfigSetProperty(@NonNull String namespace, @NonNull String name, @Nullable String value) {
        return GmsCompatApp.putString(GmsCompatApp.deviceConfigNamespace(namespace), name, value);
    }

    public static boolean deviceConfigSetProperties(@NonNull android.provider.DeviceConfig.Properties properties) {
        return GmsCompatApp.setProperties(properties);
    }

    private GmsModuleHooks() {}

    // NfcAdapter#enable()
    public static void enableNfc() {
        Activity activity = GmcActivityUtils.getMostRecentVisibleActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                Intent i = new Intent(Settings.ACTION_NFC_SETTINGS);
                activity.startActivity(i);
            });
        }
    }
}
