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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.Executors;

public final class BinderRedirector {
    private static final String TAG = "BinderRedirector";

    private static final int ID_GoogleLocationManagerService = 0;
    private static final int ID_COUNT = 1;

    private static volatile boolean inited;
    private static IBinder gserviceBroker;
    private static BinderRedirector[] redirectors;

    public final IBinder destination;
    public final int[] codes;

    private BinderRedirector(IBinder destination, int[] codes) {
        this.destination = destination;
        this.codes = codes;
    }

    public static BinderRedirector maybeGet(String interface_) {
        if ("com.google.android.gms.location.internal.IGoogleLocationManagerService".equals(interface_)) {
            return maybeGetById(ID_GoogleLocationManagerService);
        }
        return null;
    }

    private static BinderRedirector maybeGetById(int id) {
        if (!inited) {
            if (!init()) {
                return null;
            }
        }
        BinderRedirector rd = redirectors[id];
        if (rd == null) {
            rd = obtainRedirector(id);
        }
        if (rd != null && rd.destination != null) {
            return rd;
        }
        return null;
    }

    private static boolean init() {
        synchronized (BinderRedirector.class) {
            // previous check was not synchronized
            if (inited) {
                return true;
            }
            Context context = ActivityThread.currentApplication();

            Intent intent = new Intent();
            intent.setClassName(GmsCompatApp.PKG_NAME,
                GmsCompatApp.PKG_NAME + ".GserviceBroker");

            BrokerConnection conn = new BrokerConnection();
            int flags = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT | Context.BIND_ABOVE_CLIENT;
            if (!context.bindService(intent, flags, Executors.newSingleThreadExecutor(), conn)) {
                return false;
            }
            gserviceBroker = conn.waitForService();
            redirectors = new BinderRedirector[ID_COUNT];
            inited = true;
            return true;
        }
    }

    private static BinderRedirector obtainRedirector(int id) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            BinderRedirector rd;
            if (gserviceBroker.transact(IBinder.FIRST_CALL_TRANSACTION + id, data, reply, 0)) {
                IBinder dest = reply.readStrongBinderUnchecked();
                int[] codes = reply.createIntArray();
                rd = new BinderRedirector(dest, codes);
            } else {
                // gservice is disabled or unknown
                rd = new BinderRedirector(null, null);
            }
            redirectors[id] = rd;
            return rd;
        } catch (RemoteException e) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    static class BrokerConnection implements ServiceConnection {
        private IBinder service;

        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                service.linkToDeath(BinderDeathRecipient.INSTANCE, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Binder already died");
                System.exit(1);
            }
            synchronized (this) {
                this.service = service;
                notifyAll();
            }
        }

        IBinder waitForService() {
            synchronized (this) {
                for (;;) {
                    if (service != null) {
                        return service;
                    }
                    try { wait(); } catch (InterruptedException ignored) {}
                }
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            // handled by the BinderDeathRecipient below
        }
    }

    static class BinderDeathRecipient implements IBinder.DeathRecipient {
        static final BinderDeathRecipient INSTANCE = new BinderDeathRecipient();

        private BinderDeathRecipient() {}

        public void binderDied() {
            Log.d(TAG, "binderDied(), calling System.exit(0)");
            System.exit(0);
        }
    }
}
