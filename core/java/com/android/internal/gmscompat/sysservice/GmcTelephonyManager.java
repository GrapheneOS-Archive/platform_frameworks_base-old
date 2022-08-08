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

package com.android.internal.gmscompat.sysservice;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.os.WorkSource;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotInfo;
import android.util.Log;

import java.util.concurrent.Executor;

public class GmcTelephonyManager extends TelephonyManager {

    private static final String TAG = "GmcTelephonyManager";

    public GmcTelephonyManager(Context ctx) {
        super(ctx);
    }

    public GmcTelephonyManager(Context context, int subId) {
        super(context, subId);
    }

    @Override
    public String getDeviceId() {
        return null;
    }

    @Override
    public String getDeviceId(int slotIndex) {
        return null;
    }

    @Override
    public String getImei(int slotIndex) {
        return null;
    }

    @Override
    public String getMeid(int slotIndex) {
        return null;
    }

    @Override
    public int getNetworkType(int subId) {
        if (GmsCompat.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            return super.getNetworkType(subId);
        }

        return NETWORK_TYPE_UNKNOWN;
    }

    @Override
    public String getSimSerialNumber(int subId) {
        return null;
    }

    @Override
    public UiccSlotInfo[] getUiccSlotsInfo() {
        return null;
    }

    @Override
    public String getSubscriberId(int subId) {
        return null;
    }

    @Override
    public String getLine1Number(int subId) {
        try {
            return super.getLine1Number(subId);
        } catch (SecurityException e) {
            return null;
        }
    }

    @Override
    public void requestCellInfoUpdate(WorkSource workSource, @CallbackExecutor Executor executor, CellInfoCallback callback) {
        // Attribute the work to GMS instead of the client
        requestCellInfoUpdate(executor, callback);
    }

    @Override
    public boolean isIccLockEnabled() {
        return false;
    }

    @Override
    public String getGroupIdLevel1() {
        try {
            return super.getGroupIdLevel1();
        } catch (SecurityException e) {
            Log.d(TAG, "", e);
            return null;
        }
    }

    @Override
    public void registerTelephonyCallback(Executor executor, TelephonyCallback callback) {
        try {
            super.registerTelephonyCallback(executor, callback);
        } catch (SecurityException e) { // requires READ_PHONE_STATE permission
            Log.d(TAG, "", e);
        }
    }

    @Override
    public void unregisterTelephonyCallback(TelephonyCallback callback) {
        try {
            super.unregisterTelephonyCallback(callback);
        } catch (SecurityException e) {
            Log.d(TAG, "", e);
        }
    }

    @Override
    public int getCallState() {
        try {
            return super.getCallState();
        } catch (SecurityException e) { // missing READ_PHONE_STATE permission
            Log.d(TAG, "", e);
            return TelephonyManager.CALL_STATE_IDLE;
        }
    }

    @Override
    public int getCallStateForSubscription() {
        try {
            return super.getCallStateForSubscription();
        } catch (SecurityException e) {
            Log.d(TAG, "", e);
            return TelephonyManager.CALL_STATE_IDLE;
        }
    }

    @Override
    public int getVoiceNetworkType() {
        try {
            return super.getVoiceNetworkType();
        } catch (SecurityException e) { // missing READ_PHONE_STATE permission
            Log.d(TAG, "", e);
            return NETWORK_TYPE_UNKNOWN;
        }
    }
}
