package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.util.Log;

import java.io.FileDescriptor;
import java.util.Arrays;

/**
 * Fuses two binders together.
 * Transaction routing decisions are made by looking at transaction codes.
 * The rest of operations are forwarded to the first ("original") binder.
 *
 * @hide
 */
public final class HybridBinder implements IBinder {
    private static final String TAG = "HybridBinder";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.VERBOSE);

    private final IBinder original;
    private final IBinder secondBinder;
    // sorted array of handled transactions codes
    private final int[] secondBinderTxnCodes;

    public HybridBinder(Context ctx, IBinder original, BinderDef secondBinderDef) {
        this.original = original;
        this.secondBinder = secondBinderDef.getInstance(ctx);
        this.secondBinderTxnCodes = secondBinderDef.transactionCodes;
    }

    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        if (DEBUG) {
            Log.d(TAG, "call " + (code - IBinder.FIRST_CALL_TRANSACTION));
        }
        if (Arrays.binarySearch(secondBinderTxnCodes, code) >= 0) {
            return secondBinder.transact(code, data, reply, flags);
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
