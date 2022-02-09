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

package com.android.internal.gmscompat;

import android.app.ActivityThread;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public final class GmsCompatApp {
    private static final String TAG = "GmsCompat/GCA";
    public static final String PKG_NAME = "app.grapheneos.gmscompat";
    private static final String KEY_BINDER = "binder";
    private static final String KEY_BINDER_TRANSACTION_CODES = "binder_txn_codes";
    private static final String KEY_RESULT = "result";

    // needed to establish bidirectional IBinder.linkToDeath()
    @SuppressWarnings("FieldCanBeLocal")
    private static Binder localBinder;
    @SuppressWarnings("FieldCanBeLocal")
    private static IBinder remoteBinder;

    private GmsCompatApp() {}

    // called by GSF, Play services, Play Store during startup
    static IBinder connect(Context ctx) {
        Binder local = new Binder();
        localBinder = local;
        Bundle extras = new Bundle();
        extras.putBinder(KEY_BINDER, local);

        String authority = PKG_NAME + ".BinderProvider";
        Bundle res = call(ctx, authority, ctx.getPackageName(), null, extras);

        IBinder remote = res.getBinder(KEY_BINDER);
        DeathRecipient.register(remote);
        remoteBinder = remote;
        return remote;
    }

    // region | GMS client section

    private static final int METHOD_GET_REDIRECTABLE_INTERFACES = 0;
    private static final int METHOD_GET_REDIRECTOR = 1;

    public static String[] getRedirectableInterfaces() {
        Bundle b = gmsClientProviderCall(METHOD_GET_REDIRECTABLE_INTERFACES, null, null);
        return b.getStringArray(KEY_RESULT);
    }

    public static BinderRedirector getBinderRedirector(int id) {
        Bundle b = gmsClientProviderCall(METHOD_GET_REDIRECTOR, Integer.toString(id), null);
        if (b == null) {
            // redirector is disabled
            return new BinderRedirector(null, null);
        }
        IBinder binder = b.getBinder(KEY_BINDER);
        int[] txnCodes = b.getIntArray(KEY_BINDER_TRANSACTION_CODES);
        return new BinderRedirector(binder, txnCodes);
    }

    private static Bundle gmsClientProviderCall(int method, String arg, Bundle bundleArg) {
        String authority = PKG_NAME + ".GmsClientProvider";
        Context ctx = ActivityThread.currentApplication();
        return call(ctx, authority, Integer.toString(method), arg, bundleArg);
    }
    // endregion

    private static Bundle call(Context ctx, String authority, String method, String arg, Bundle bundleArg) {
        try {
            return ctx.getContentResolver().call(authority, method, arg, bundleArg);
        } catch (Throwable t) {
            // content provider calls are infallible unless something goes very wrong, better fail fast in that case
            Log.e(TAG, "call to " + authority + " failed", t);
            System.exit(1);
            return null;
        }
    }

    static class DeathRecipient implements IBinder.DeathRecipient {
        private static final DeathRecipient INSTANCE = new DeathRecipient();
        private DeathRecipient() {}

        static void register(IBinder b) {
            try {
                b.linkToDeath(INSTANCE, 0);
            } catch (RemoteException e) {
                // binder already died
                INSTANCE.binderDied();
            }
        }

        public void binderDied() {
            Log.e(TAG, PKG_NAME + " died");
            System.exit(1);
        }
    }
}
