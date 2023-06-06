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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.util.Log;

import java.io.FileDescriptor;
import java.util.Arrays;

/**
 * Fuses two binders together.
 * Transaction routing decisions are made by looking at transaction codes.
 * The rest of operations are forwarded to the first ("original") binder.
 */
public final class HybridBinder implements IBinder {
    private static final String TAG = "HybridBinder";
    private static final boolean DEBUG = false;

    private final IBinder original;
    private final BinderRedirector redirector;

    public static HybridBinder maybeCreate(IBinder original) {
        String interface_ = null;
        try {
            interface_ = original.getInterfaceDescriptor();
        } catch (RemoteException ignored) {
        }
        if (DEBUG) {
            Log.d(TAG, "interface " + interface_ + "|");
        }
        if (interface_ == null) {
            return null;
        }
        BinderRedirector rd = BinderRedirector.maybeGet(interface_);
        if (rd == null) {
            return null;
        }
        return new HybridBinder(original, rd);
    }

    private HybridBinder(IBinder original, BinderRedirector redirector) {
        this.original = original;
        this.redirector = redirector;
    }

    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        if (DEBUG) {
            Log.d(TAG, "call " + (code - IBinder.FIRST_CALL_TRANSACTION));
        }
        BinderRedirector rd = redirector;
        if (Arrays.binarySearch(rd.transactionCodes, code) >= 0) {
            return rd.destination.transact(code, data, reply, flags);
        }
        return original.transact(code, data, reply, flags);
    }

    @Nullable
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return null;
    }

    @Nullable
    public String getInterfaceDescriptor() throws RemoteException {
        return original.getInterfaceDescriptor();
    }

    public boolean pingBinder() {
        return original.pingBinder();
    }

    public boolean isBinderAlive() {
        return original.isBinderAlive();
    }

    public void dump(@NonNull FileDescriptor fd, @Nullable String[] args) throws RemoteException {
        original.dump(fd, args);
    }

    public void dumpAsync(@NonNull FileDescriptor fd, @Nullable String[] args) throws RemoteException {
        original.dumpAsync(fd, args);
    }

    public void shellCommand(@Nullable FileDescriptor in, @Nullable FileDescriptor out, @Nullable FileDescriptor err, @NonNull String[] args, @Nullable ShellCallback shellCallback, @NonNull ResultReceiver resultReceiver) throws RemoteException {
        original.shellCommand(in, out, err, args, shellCallback, resultReceiver);
    }

    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) throws RemoteException {
        original.linkToDeath(recipient, flags);
    }

    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        return original.unlinkToDeath(recipient, flags);
    }

    @Nullable
    public IBinder getExtension() throws RemoteException {
        return original.getExtension();
    }
}
