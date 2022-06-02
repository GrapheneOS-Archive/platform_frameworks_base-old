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

import android.app.compat.gms.GmsCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Obtains from GmsCompatApp objects that are needed to create HybridBinder
 * and handles redirection state changes.
 */
public final class BinderRedirector implements Parcelable {
    private static final String TAG = "BinderRedirector";

    // written last in the init sequence, "volatile" to publish all the preceding writes
    private static volatile boolean enabled;
    private static String[] redirectableInterfaces;
    private static String[] notableInterfaces;

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
    public static void maybeInit(Intent intent) {
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
            if (GmsCompat.isClientOfGmsCore()) {
                try {
                    ArrayList<String> notableIfaces = new ArrayList<>(10);
                    redirectableInterfaces = GmsCompatApp.iClientOfGmsCore2Gca().getRedirectableInterfaces(notableIfaces);
                    notableIfaces.toArray(notableInterfaces = new String[notableIfaces.size()]);
                } catch (RemoteException e) {
                    GmsCompatApp.callFailed(e);
                }
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

        } else if (Arrays.binarySearch(notableInterfaces, interface_) >= 0) {
            try {
                GmsCompatApp.iClientOfGmsCore2Gca().onNotableInterfaceAcquired(interface_);
            } catch (RemoteException e) {
                GmsCompatApp.callFailed(e);
            }
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
        BinderRedirector rd;
        try {
            rd = GmsCompatApp.iClientOfGmsCore2Gca().getBinderRedirector(id);
        } catch (RemoteException e) {
            throw GmsCompatApp.callFailed(e);
        }
        // all BinderRedirector fields are final, this is thread-safe
        BinderRedirector.cache[id] = rd;
        return rd;
    }

    public static class RedirectionStateListener extends BroadcastReceiver {
        public static final String INTENT_ACTION = GmsCompatApp.PKG_NAME + ".ACTION_REDIRECTION_STATE_CHANGED";
        public static final String PERMISSION = GmsCompatApp.PKG_NAME + ".permission.REDIRECTION_STATE_CHANGED_BROADCAST";
        public static final String KEY_REDIRECTION_ID = "id";

        volatile long usedRedirections;

        static RedirectionStateListener register() {
            RedirectionStateListener l = new RedirectionStateListener();
            GmsCompat.appContext().registerReceiver(l, new IntentFilter(INTENT_ACTION), PERMISSION, null);
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

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        IBinder binder = destination;
        dest.writeBoolean(binder != null);
        if (binder != null) {
            dest.writeStrongBinder(destination);
            dest.writeIntArray(transactionCodes);
        }
    }

    public static final Parcelable.Creator<BinderRedirector> CREATOR
            = new Parcelable.Creator<BinderRedirector>() {
        @Override
        public BinderRedirector createFromParcel(Parcel source) {
            if (!source.readBoolean()) {
                return new BinderRedirector(null, null);
            }
            IBinder destination = source.readStrongBinder();
            int[] transactionCodes = source.createIntArray();
            return new BinderRedirector(destination, transactionCodes);
        }

        @Override
        public BinderRedirector[] newArray(int size) {
            return new BinderRedirector[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
