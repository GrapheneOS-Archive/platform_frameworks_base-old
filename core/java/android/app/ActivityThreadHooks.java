package android.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

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

        return args;
    }

    // called after ActivityThread instrumentation is inited, which happens before execution of any
    // of app's code
    // ActivityThread.handleBindApplication
    static void onBind2(Context appContext, Bundle appBindArgs) {

    }
}
