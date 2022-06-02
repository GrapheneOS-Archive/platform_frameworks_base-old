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
import android.annotation.Nullable;
import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.os.WorkSource;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotInfo;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@SuppressWarnings("AutoBoxing")
public class GmcTelephonyManager extends TelephonyManager {

    private static final String TAG = "GmcTelephonyManager";

    public GmcTelephonyManager(Context ctx) {
        super(ctx);
    }

    public GmcTelephonyManager(Context context, int subId) {
        super(context, subId);
    }

    @Override
    public void requestCellInfoUpdate(WorkSource workSource, @CallbackExecutor Executor executor, CellInfoCallback callback) {
        // Attribute the work to GMS instead of the client
        requestCellInfoUpdate(executor, callback);
    }

    @Override
    public void requestCellInfoUpdate(Executor executor, CellInfoCallback callback) {
        if (!GmsCompat.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return;
        }

        super.requestCellInfoUpdate(executor, callback);
    }

    public static int[] filterTelephonyCallbackEvents(int[] eventsArray) {
        Set<Integer> events = Arrays.stream(eventsArray).boxed().collect(Collectors.toSet());

        var sb = new StringBuilder();

        if (!GmsCompat.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            removeEvents(events, EVENTS_PROT_READ_PHONE_STATE, "READ_PHONE_STATE", sb);
        }

        if (!GmsCompat.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            removeEvents(events, EVENTS_PROT_ACCESS_FINE_LOCATION,
                    "ACCESS_FINE_LOCATION", sb);
        }

        // these events are protected by privileged permissions
        removeEvents(events, EVENTS_PROT_READ_ACTIVE_EMERGENCY_SESSION,
                "READ_ACTIVE_EMERGENCY_SESSION", sb);
        removeEvents(events, EVENTS_PROT_READ_PRIVILEGED_PHONE_STATE,
                "READ_PRIVILEGED_PHONE_STATE", sb);
        removeEvents(events, EVENTS_PROT_READ_PRECISE_PHONE_STATE,
                "READ_PRECISE_PHONE_STATE", sb);

        int[] res = events.stream().mapToInt(Integer::intValue).toArray();

        Log.d(TAG, "registering listener, events: " + Arrays.toString(res) +
                (sb.length() != 0? "\nfiltered events due to missing permission\n" + sb : ""),
                new Throwable());

        return res;
    }

    private static void removeEvents(Set<Integer> events, int[] eventsToRemove, String permName, StringBuilder sb) {
        if (events.size() == 0) {
            return;
        }

        boolean filtered = false;

        for (int event : eventsToRemove) {
            if (!events.remove(event)) {
                continue;
            }
            if (!filtered) {
                sb.append(permName);
                sb.append(": {");
                filtered = true;
            }
            sb.append(event);
            sb.append(", ");
        }

        if (filtered) {
            sb.append("}\n");
        }
    }

    private static final int[] EVENTS_PROT_READ_PHONE_STATE = {
            TelephonyCallback.EVENT_CALL_FORWARDING_INDICATOR_CHANGED,
            TelephonyCallback.EVENT_MESSAGE_WAITING_INDICATOR_CHANGED,
            TelephonyCallback.EVENT_EMERGENCY_NUMBER_LIST_CHANGED,
            TelephonyCallback.EVENT_LEGACY_CALL_STATE_CHANGED,
            TelephonyCallback.EVENT_CALL_STATE_CHANGED,
            TelephonyCallback.EVENT_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGED,
            TelephonyCallback.EVENT_CELL_INFO_CHANGED,
    };

    private static final int[] EVENTS_PROT_ACCESS_FINE_LOCATION = {
            TelephonyCallback.EVENT_CELL_LOCATION_CHANGED,
            TelephonyCallback.EVENT_CELL_INFO_CHANGED,
            TelephonyCallback.EVENT_REGISTRATION_FAILURE,
            TelephonyCallback.EVENT_BARRING_INFO_CHANGED,
    };

    private static final int[] EVENTS_PROT_READ_ACTIVE_EMERGENCY_SESSION = {
            TelephonyCallback.EVENT_OUTGOING_EMERGENCY_CALL,
            TelephonyCallback.EVENT_OUTGOING_EMERGENCY_SMS,
    };

    private static final int[] EVENTS_PROT_READ_PRIVILEGED_PHONE_STATE = {
            TelephonyCallback.EVENT_SRVCC_STATE_CHANGED,
            TelephonyCallback.EVENT_VOICE_ACTIVATION_STATE_CHANGED,
            TelephonyCallback.EVENT_RADIO_POWER_STATE_CHANGED,
            TelephonyCallback.EVENT_ALLOWED_NETWORK_TYPE_LIST_CHANGED,
    };

    private static final int[] EVENTS_PROT_READ_PRECISE_PHONE_STATE = {
            TelephonyCallback.EVENT_PRECISE_DATA_CONNECTION_STATE_CHANGED,
            TelephonyCallback.EVENT_DATA_CONNECTION_REAL_TIME_INFO_CHANGED,
            TelephonyCallback.EVENT_PRECISE_CALL_STATE_CHANGED,
            TelephonyCallback.EVENT_CALL_DISCONNECT_CAUSE_CHANGED,
            TelephonyCallback.EVENT_CALL_ATTRIBUTES_CHANGED,
            TelephonyCallback.EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED,
            TelephonyCallback.EVENT_REGISTRATION_FAILURE,
            TelephonyCallback.EVENT_BARRING_INFO_CHANGED,
            TelephonyCallback.EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED,
            TelephonyCallback.EVENT_DATA_ENABLED_CHANGED,
            TelephonyCallback.EVENT_LINK_CAPACITY_ESTIMATE_CHANGED,
    };
}
