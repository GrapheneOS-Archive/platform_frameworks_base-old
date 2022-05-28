/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.nfc;

import android.annotation.SystemService;
import android.app.AlarmManager;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * High level manager used to obtain an instance of an {@link NfcAdapter}.
 * <p>
 * Use {@link android.content.Context#getSystemService(java.lang.String)}
 * with {@link Context#NFC_SERVICE} to create an {@link NfcManager},
 * then call {@link #getDefaultAdapter} to obtain the {@link NfcAdapter}.
 * <p>
 * Alternately, you can just call the static helper
 * {@link NfcAdapter#getDefaultAdapter(android.content.Context)}.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about using NFC, read the
 * <a href="{@docRoot}guide/topics/nfc/index.html">Near Field Communication</a> developer guide.</p>
 * </div>
 *
 * @see NfcAdapter#getDefaultAdapter(android.content.Context)
 */
@SystemService(Context.NFC_SERVICE)
public final class NfcManager {
    private final NfcAdapter mAdapter;
    private final Context mContext;
    private final AlarmManager mAlarmManager;
    /**
     * @hide
     */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P, trackingBug = 115609023)
    public NfcManager(Context context) {
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mContext = context;
        NfcAdapter adapter;
        context = context.getApplicationContext();
        if (context == null) {
            throw new IllegalArgumentException(
                    "context not associated with any application (using a mock context?)");
        }
        try {
            adapter = NfcAdapter.getNfcAdapter(context);
        } catch (UnsupportedOperationException e) {
            adapter = null;
        }
        mAdapter = adapter;
        reconfigureNfcTimeoutListener();
        IntentFilter filter = new IntentFilter();
        filter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        filter.addAction(NfcAdapter.ACTION_HANDOVER_TRANSFER_STARTED);
        filter.addAction(NfcAdapter.ACTION_HANDOVER_TRANSFER_DONE);
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_TAG_LEFT_FIELD);
        filter.addAction(NfcAdapter.ACTION_TRANSACTION_DETECTED);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                reconfigureNfcTimeoutListener();
            }
        };
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.NFC_OFF_TIMEOUT),
                false,
                new ContentObserver(new Handler(context.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        super.onChange(selfChange);
                        reconfigureNfcTimeoutListener();
                    }
                });
        mContext.registerReceiver(receiver, filter);
    }


    private final AlarmManager.OnAlarmListener  mAlarmListener = new AlarmManager.OnAlarmListener() {
        @Override
        public void onAlarm() {
            if (mAdapter != null) mAdapter.disable();
        }
    };

    private void reconfigureNfcTimeoutListener() {
        long duration = nfcTimeoutDurationInMilli();
        final long timeout = SystemClock.elapsedRealtime() + duration;
        boolean isEnabled = mAdapter != null && mAdapter.isEnabled();

        if (duration == 0 || !isEnabled){
            mAlarmManager.cancel(mAlarmListener);
            return;
        }
        mAlarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                timeout,
                "NFC Idle Timeout",
                mAlarmListener,
                new Handler(mContext.getMainLooper())
        );
    }

    private long nfcTimeoutDurationInMilli() {
        return Settings.Global.getLong(mContext.getContentResolver(),
                Settings.Global.NFC_OFF_TIMEOUT, 0);
    }

    /**
     * Get the default NFC Adapter for this device.
     *
     * @return the default NFC Adapter
     */
    public NfcAdapter getDefaultAdapter() {
        return mAdapter;
    }
}
