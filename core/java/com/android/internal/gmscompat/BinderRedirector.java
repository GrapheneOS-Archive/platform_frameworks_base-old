/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.gmscompat;

import android.app.ActivityThread;
import android.app.compat.gms.GmsCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import java.util.Arrays;

/**
 * Obtains from GmsCompatApp objects that are needed to create HybridBinder
 * and handles redirection state changes.
 */
public final class BinderRedirector {
    private static final String TAG = "BinderRedirector";

    // written last in the init sequence, "volatile" to publish all the preceding writes
    private static volatile boolean enabled;
    private static String[] redirectableInterfaces;

    private static RedirectionStateListener redirectionStateListener;
    private static BinderRedirector[] cache;

    public final IBinder destination;
    public final int[] transactionCodes;

    public BinderRedirector(IBinder destination, int[] transactionCodes) {
        this.destination = destination;
        this.transactionCodes = transactionCodes;
    }

    public static boolean enabled() {
        return enabled;
    }

    // call from ContextImpl#bindServiceCommon(),
    // after intent is validated, but before request to the ActivityManager
    // (otherwise there would be a race if bindService() is called from the non-main thread)
    public static void maybeInit(Context ctx, Intent intent) {
        if (!GmsInfo.PACKAGE_GMS_CORE.equals(intent.getPackage())) {
            return;
        }
        if (GmsCompat.isEnabled()) {
            return;
        }
        synchronized (BinderRedirector.class) {
            if (enabled) {
                return;
            }
            if (GmsCompat.isClientOfGmsCore(ctx)) {
                redirectableInterfaces = GmsCompatApp.getRedirectableInterfaces();
                enabled = true;
            }
        }
    }

    public static BinderRedirector maybeGet(String interface_) {
        int id = Arrays.binarySearch(redirectableInterfaces, interface_);
        if (id >= 0) {
            BinderRedirector rd = obtain(id);
            if (rd.destination != null) {
                return rd;
            } // else this redirection is disabled
        }
        return null;
    }

    private static BinderRedirector obtain(int id) {
        BinderRedirector[] cache = BinderRedirector.cache;
        if (cache != null) {
            BinderRedirector cached = cache[id];
            if (cached != null) {
                return cached;
            }
        }
        synchronized (BinderRedirector.class) {
            if (redirectionStateListener == null) {
                redirectionStateListener = RedirectionStateListener.register();
                BinderRedirector.cache = new BinderRedirector[redirectableInterfaces.length];
            }
            redirectionStateListener.usedRedirections |= (1L << id);
        }
        BinderRedirector rd = GmsCompatApp.getBinderRedirector(id);
        // all BinderRedirector fields are final, this is thread-safe
        BinderRedirector.cache[id] = rd;

        IBinder dest = rd.destination;
        if (dest != null) {
            GmsCompatApp.DeathRecipient.register(dest);
        }
        return rd;
    }

    static class RedirectionStateListener extends BroadcastReceiver {
        private static final String INTENT_ACTION = GmsCompatApp.PKG_NAME + ".ACTION_REDIRECTION_STATE_CHANGED";
        private static final String PERMISSION = GmsCompatApp.PKG_NAME + ".permission.REDIRECTION_STATE_CHANGED_BROADCAST";
        private static final String KEY_REDIRECTION_ID = "id";

        volatile long usedRedirections;

        static RedirectionStateListener register() {
            Context ctx = ActivityThread.currentApplication();
            RedirectionStateListener l = new RedirectionStateListener();
            ctx.registerReceiver(l, new IntentFilter(INTENT_ACTION), PERMISSION, null);
            return l;
        }

        public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra(KEY_REDIRECTION_ID, 0);
            if ((usedRedirections & (1L << id)) != 0) {
                // it's infeasible to enable / disable redirection without starting over
                Log.d(TAG, "state of redirection (id " + id + ") changed, calling System.exit(0)");
                System.exit(0);
            }
        }
    }
}
