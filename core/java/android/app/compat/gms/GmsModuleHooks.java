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
import android.annotation.SystemApi;
import android.os.Build;
import android.os.RemoteException;
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
    public static boolean canEnableBluetoothAdapter() {
        if (GmsCompat.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return true;
        }

        if (GmcActivityUtils.getMostRecentVisibleActivity() != null) {
            String pkgName = GmsCompat.appContext().getPackageName();
            try {
                GmsCompatApp.iGms2Gca().showGmsMissingNearbyDevicesPermissionGeneric(pkgName);
            } catch (RemoteException e) {
                GmsCompatApp.callFailed(e);
            }
        } // else don't bother the user

        return false;
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

    private GmsModuleHooks() {}
}
