package com.android.internal.gmscompat;

import android.annotation.Nullable;
import android.app.ActivityThread;
import android.app.compat.gms.GmsCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BinderDef;
import android.os.HybridBinder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

public class GmcBinderDefs {
    static final String TAG = GmcBinderDefs.class.getSimpleName();

    private static final ArrayMap<String, BinderDef> seenInterfaces = new ArrayMap<>();

    @Nullable
    public static IBinder maybeOverrideBinder(IBinder originalBinder, String ifaceName) {
        BinderDef bdef = maybeGetBinderDef(ifaceName);
        if (bdef == null) {
            return null;
        }

        Context ctx = GmsCompat.appContext();

        if (bdef.transactionCodes == null) {
            // this is a complete interface implementation
            return bdef.getInstance(ctx);
        }

        return new HybridBinder(ctx, originalBinder, bdef);
    }

    private static BinderDef maybeGetBinderDef(String iface) {
        synchronized (seenInterfaces) {
            int i = seenInterfaces.indexOfKey(iface);
            if (i >= 0) {
                return seenInterfaces.valueAt(i);
            }
        }

        Context ctx = GmsCompat.appContext();
        String pkgName = ctx.getPackageName();
        int processState = ActivityThread.currentActivityThread().getProcessState();

        BinderDef maybeBinderDef;
        try {
            if (GmsCompat.isEnabled()) {
                maybeBinderDef = GmsCompatApp.iGms2Gca()
                        .maybeGetBinderDef(pkgName, processState, iface);
            } else {
                maybeBinderDef = GmsCompatApp.iClientOfGmsCore2Gca()
                        .maybeGetBinderDef(pkgName, processState, iface);
            }
        } catch (RemoteException e) {
            throw GmsCompatApp.callFailed(e);
        }

        synchronized (seenInterfaces) {
            // in case seenInterfaces changed since last check
            int idx = seenInterfaces.indexOfKey(iface);
            if (idx >= 0) {
                return seenInterfaces.valueAt(idx);
            }

            if (seenInterfaces.size() == 0) {
                BinderDefStateListener.register(ctx);
            }

            seenInterfaces.put(iface, maybeBinderDef);
        }

        return maybeBinderDef;
    }

    public static class BinderDefStateListener extends BroadcastReceiver {
        public static final String INTENT_ACTION = GmsCompatApp.PKG_NAME + ".ACTION_BINDER_DEFS_CHANGED";
        public static final String KEY_CHANGED_IFACE_NAMES = "changed_iface_names";

        static void register(Context ctx) {
            var l = new BinderDefStateListener();
            var filter = new IntentFilter(INTENT_ACTION);
            String permission = GmsCompatApp.SIGNATURE_PROTECTED_PERMISSION;
            ctx.registerReceiver(l, filter, permission, null, Context.RECEIVER_EXPORTED);
        }

        public void onReceive(Context context, Intent intent) {
            String[] changedIfaces = intent.getStringArrayExtra(KEY_CHANGED_IFACE_NAMES);

            boolean exit = false;
            synchronized (seenInterfaces) {
                for (String changedIface : changedIfaces) {
                    if (seenInterfaces.containsKey(changedIface)) {
                        // it's infeasible to apply updated BinderDef without restarting app process
                        Log.d(TAG, "state of BinderDef " + changedIface + " changed, will call System.exit(0)");
                        exit = true;
                    }
                }
            }
            if (exit) {
                System.exit(0);
            }
        }
    }
}
