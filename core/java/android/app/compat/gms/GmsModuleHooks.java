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
import android.annotation.SystemApi;
import android.app.ActivityThread;
import android.os.RemoteException;

import com.android.internal.gmscompat.GmsCompatApp;

/**
 * Hooks that are accessed from APEX modules.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class GmsModuleHooks {

    // BluetoothAdapter#enable()
    // BluetoothAdapter#enableBLE()
    public static boolean canEnableBluetoothAdapter() {
        if (GmsCompat.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return true;
        }

        if (ActivityThread.currentActivityThread().hasAtLeastOneResumedActivity()) {
            String pkgName = GmsCompat.appContext().getPackageName();
            try {
                GmsCompatApp.iGms2Gca().showGmsMissingNearbyDevicesPermissionGeneric(pkgName);
            } catch (RemoteException e) {
                GmsCompatApp.callFailed(e);
            }
        } // else don't bother the user

        return false;
    }

    private GmsModuleHooks() {}
}
