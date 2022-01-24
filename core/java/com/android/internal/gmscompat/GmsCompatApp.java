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

    // needed to establish bidirectional IBinder.linkToDeath()
    @SuppressWarnings("FieldCanBeLocal")
    private static Binder localBinder;
    @SuppressWarnings("FieldCanBeLocal")
    private static IBinder remoteBinder;

    private GmsCompatApp() {}

    static IBinder connect(Context ctx) {
        Binder local = new Binder();
        localBinder = local;
        Bundle extras = new Bundle();
        extras.putBinder(KEY_BINDER, local);

        Bundle res = null;
        String provider = PKG_NAME + ".BinderProvider";
        try {
            res = ctx.getContentResolver().call(provider, ctx.getPackageName(), null, extras);
        } catch (Throwable t) {
            Log.e(TAG, "call to " + provider + " failed", t);
            System.exit(1);
        }
        IBinder b = res.getBinder(KEY_BINDER);
        try {
            b.linkToDeath(new DeathRecipient(), 0);
        } catch (RemoteException e) {
            Log.e(TAG, PKG_NAME + " already died", e);
            System.exit(1);
        }
        remoteBinder = b;
        return b;
    }

    static class DeathRecipient implements IBinder.DeathRecipient {
        public void binderDied() {
            Log.e(TAG, PKG_NAME + " died");
            System.exit(1);
        }
    }
}
