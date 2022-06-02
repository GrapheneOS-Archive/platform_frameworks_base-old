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

package com.android.internal.gmscompat.client;

import android.app.compat.gms.GmsCompat;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// See GmsCompatClientService for an explanation
public class ClientPriorityManager implements ServiceConnection, Runnable {
    private static final String TAG = "ClientPriorityManager";
    private static final boolean LOGV = false;

    private static final ScheduledExecutorService unbindExecutor = Executors.newSingleThreadScheduledExecutor();

    private boolean unbound;

    private ClientPriorityManager() {}

    public static void raiseToForeground(String targetPkg, long durationMs) {
        Intent intent = new Intent();
        intent.setClassName(targetPkg, GmsCompatClientService.class.getName());

        ClientPriorityManager csc = new ClientPriorityManager();

        if (GmsCompat.appContext().bindService(intent, csc, Context.BIND_AUTO_CREATE)) {
            unbindExecutor.schedule(csc, durationMs, TimeUnit.MILLISECONDS);

            if (LOGV) {
                Log.d(TAG, "bound to " + targetPkg);
            }
        } else {
            Log.e(TAG, "unable to bind to " + targetPkg, new Exception());
        }
    }

    @Override
    public void run() {
        if (LOGV) {
            Log.d(TAG, "timeout expired, unbinding");
        }
        unbind();
    }

    private void unbind() {
        synchronized (this) {
            if (!unbound) {
                GmsCompat.appContext().unbindService(this);
                unbound = true;
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (LOGV) {
            Log.d(TAG, "onServiceConnected " + name);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.e(TAG, "onServiceDisconnected " + name);
    }

    @Override
    public void onBindingDied(ComponentName name) {
        Log.d(TAG, "onBindingDied " + name);
        unbind();
    }

    @Override
    public void onNullBinding(ComponentName name) {
        Log.e(TAG, "onNullBinding " + name);
        unbind();
    }
}
