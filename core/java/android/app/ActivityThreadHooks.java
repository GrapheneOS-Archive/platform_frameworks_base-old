package android.app;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.GosPackageState;
import android.content.pm.SrtPermissions;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.app.ContactScopes;
import com.android.internal.app.StorageScopesAppHooks;

import java.util.Objects;

class ActivityThreadHooks {

    private static volatile boolean called;

    // called after the initial app context is constructed
    // ActivityThread.handleBindApplication
    static Bundle onBind(Context appContext) {
        if (called) {
            throw new IllegalStateException("onBind called for the second time");
        }
        called = true;

        if (Process.isIsolated()) {
            return null;
        }

        final String pkgName = appContext.getPackageName();
        final String TAG = "AppBindArgs";

        Bundle args = null;
        try {
            args = ActivityThread.getPackageManager().getExtraAppBindArgs(pkgName);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }

        if (args == null) {
            Log.e(TAG, "bundle is null");
            return null;
        }

        int[] flags = Objects.requireNonNull(args.getIntArray(AppBindArgs.KEY_FLAGS_ARRAY));

        SrtPermissions.setFlags(flags[AppBindArgs.FLAGS_IDX_SPECIAL_RUNTIME_PERMISSIONS]);

        return args;
    }

    // called after ActivityThread instrumentation is inited, which happens before execution of any
    // of app's code
    // ActivityThread.handleBindApplication
    static void onBind2(Context appContext, Bundle appBindArgs) {
        GosPackageState gosPs = appBindArgs.getParcelable(AppBindArgs.KEY_GOS_PACKAGE_STATE,
                GosPackageState.class);
        onGosPackageStateChanged(appContext, gosPs, true);
    }

    // called from both main and worker threads
    static void onGosPackageStateChanged(Context ctx, @Nullable GosPackageState state, boolean fromBind) {
        if (state != null) {
            StorageScopesAppHooks.maybeEnable(state);
            ContactScopes.maybeEnable(ctx, state);
        }
    }
}
