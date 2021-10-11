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

package com.android.internal.gmscompat.dynamite.server;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/** @hide */
public final class FileProxyProvider extends BroadcastReceiver {
    public static final String EXTRA_BUNDLE = "service_bundle";
    public static final String EXTRA_BINDER = "service_binder";
    public static final String EXTRA_PACKAGE = "service_client_package";

    public static final String ACTION_REQUEST = "com.android.internal.gmscompat.REQUEST_DYNAMITE_FILE_PROXY";
    public static final String ACTION_RESPONSE = "com.android.internal.gmscompat.DYNAMITE_FILE_PROXY";

    public static final String THREAD_NAME = "DynamiteFileProxy";

    private FileProxyService service;

    private FileProxyProvider() { }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(ACTION_REQUEST)) {
            return;
        }

        if (service == null) {
            // The service is a singleton, so use the application context to prevent memory leaks.
            service = new FileProxyService(context.getApplicationContext());
        }

        Intent reply = new Intent(ACTION_RESPONSE);

        // Use an explicit intent to avoid sending multiple replies to clients that
        // request the file proxy binder at the same time. Security doesn't matter because
        // this is a public service, and we can't use PendingIntents due to permission checks
        // on the client side.
        String clientPackage = intent.getStringExtra(EXTRA_PACKAGE);
        if (clientPackage == null) {
            return;
        }
        reply.setPackage(clientPackage);

        // New bundle is required because Intent doesn't expose IBinder extras
        Bundle bundle = new Bundle();
        bundle.putBinder(EXTRA_BINDER, service.asBinder());
        reply.putExtra(EXTRA_BUNDLE, bundle);

        Log.d(FileProxyService.TAG, "Sending file proxy binder to " + clientPackage);
        context.sendBroadcast(reply);
    }

    public static void register(Context context) {
        Log.d(FileProxyService.TAG, "Registering file proxy provider from " + Application.getProcessName());

        // Create a dedicated thread to avoid blocking clients for too long
        HandlerThread thread = new HandlerThread(THREAD_NAME);
        thread.start();
        Handler handler = new Handler(thread.getLooper());

        IntentFilter filter = new IntentFilter(ACTION_REQUEST);
        context.registerReceiver(new FileProxyProvider(), filter, null, handler);
    }
}
