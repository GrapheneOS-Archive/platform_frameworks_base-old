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

package com.android.internal.gmscompat.dynamite.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.gmscompat.GmsInfo;
import com.android.internal.gmscompat.dynamite.server.FileProxyProvider;
import com.android.internal.gmscompat.dynamite.server.IFileProxyService;

import java.io.File;

/** @hide */
public final class DynamiteContext {
    public static final String TAG = "GmsCompat/DynamiteClient";

    // Permission that only GMS holds
    private static final String GMS_PERM = "com.google.android.gms.permission.INTERNAL_BROADCAST";

    // Make sure we don't block the main thread for too long if GMS isn't available
    private static final long GET_SERVICE_TIMEOUT = 250L;

    private final Context context;
    public final String gmsDataPrefix;

    // Manage state for loading one module at a time per thread
    private final ThreadLocal<ModuleLoadState> threadLocalState = new ThreadLocal<>();

    // The remote GMS process can die at any time, so this needs to be managed carefully.
    private IFileProxyService serviceBinder = null;

    public DynamiteContext(Context context) {
        this.context = context;

        // Get data directory path without using package context or current data dir, since not all
        // packages have data directories and package context causes recursion in ApkAssets
        File userDe = Environment.getDataUserDeDirectory(null, context.getUserId());
        gmsDataPrefix = userDe.getPath() + '/' + GmsInfo.PACKAGE_GMS + '/';
    }

    public ModuleLoadState getState() {
        return threadLocalState.get();
    }
    public void setState(ModuleLoadState state) {
        threadLocalState.set(state);
    }

    public IFileProxyService getService() {
        return serviceBinder == null ? getNewBinder() : serviceBinder;
    }

    private IFileProxyService getNewBinder() {
        // Request a fresh service unconditionally
        IFileProxyService binder = requestGmsService();

        // Register before saving to avoid race condition if GMS dies *now*
        try {
            binder.asBinder().linkToDeath(() -> {
                Log.d(DynamiteContext.TAG, "File proxy service has died");
                serviceBinder = null;
            }, 0);

            serviceBinder = binder;
            return binder;
        } catch (RemoteException e) {
            serviceBinder = null;
            return null;
        }
    }

    private IFileProxyService requestGmsService() {
        // Create a dedicated thread to avoid deadlocks, since this might be called on the main thread
        HandlerThread thread = new HandlerThread(FileProxyProvider.THREAD_NAME);
        thread.start();
        Handler handler = new Handler(thread.getLooper());

        // Potential return values
        final IFileProxyService[] service = {null};
        final RuntimeException[] receiverException = {null};
        BroadcastReceiver replyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!intent.getAction().equals(FileProxyProvider.ACTION_RESPONSE)) {
                    return;
                }

                try {
                    Bundle bundle = intent.getBundleExtra(FileProxyProvider.EXTRA_BUNDLE);
                    IBinder binder = bundle.getBinder(FileProxyProvider.EXTRA_BINDER);
                    service[0] = IFileProxyService.Stub.asInterface(binder);
                } catch (RuntimeException e) {
                    receiverException[0] = e;
                } finally {
                    thread.quitSafely();
                }
            }
        };

        // Register receiver first
        IntentFilter filter = new IntentFilter(FileProxyProvider.ACTION_RESPONSE);
        // For security, we require the reply to come from GMS (by permission) so other apps
        // can't inject code into our process by replying with a fake proxy service that returns
        // malicious APKs.
        context.registerReceiver(replyReceiver, filter, GMS_PERM, handler);

        // Now, send the broadcast and wait...
        try {
            Log.d(TAG, "Requesting file proxy service from GMS");

            Intent intent = new Intent(FileProxyProvider.ACTION_REQUEST);
            intent.setPackage(GmsInfo.PACKAGE_GMS);
            intent.putExtra(FileProxyProvider.EXTRA_PACKAGE, context.getPackageName());
            context.sendBroadcast(intent, GMS_PERM);
            thread.join(GET_SERVICE_TIMEOUT);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            context.unregisterReceiver(replyReceiver);
            // Attempt to stop the thread if join() timed out
            thread.quit();
        }

        // Rethrow exception or return value
        if (receiverException[0] != null) {
            throw receiverException[0];
        } else if (service[0] != null) {
            return service[0];
        } else {
            throw new IllegalStateException("Dynamite file proxy request timed out");
        }
    }
}
