package com.android.systemui.biometrics;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.fingerprint.IUdfpsHbmListener;
import android.os.RemoteException;
import android.util.Log;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.util.concurrency.Execution;

import javax.inject.Inject;

@SysUISingleton
public class UdfpsDisplayMode implements UdfpsDisplayModeProvider {
    private static final String TAG = "UdfpsDisplayMode";

    final Context context;
    final Execution execution;
    final AuthController authController;

    @Inject
    UdfpsDisplayMode(Context context, Execution execution, AuthController authController) {
        this.context = context;
        this.execution = execution;
        this.authController = authController;
    }

    static class Request {
        int displayId;
    }

    private Request currentRequest;

    @Override
    public void enable(@Nullable Runnable onEnabled) {
        execution.assertIsMainThread();

        Log.v(TAG, "enable");

        if (currentRequest != null) {
            Log.e(TAG, "enable: already requested");
            return;
        }

        IUdfpsHbmListener hbmListener = authController.getUdfpsHbmListener();

        if (hbmListener == null) {
            Log.e(TAG, "enable: hbmListener is null");
            return;
        }

        var req = new Request();
        req.displayId = context.getDisplayId();
        currentRequest = req;

        try {
            hbmListener.onHbmEnabled(req.displayId);
            Log.v(TAG, "enable: requested optimal refresh rate for UDFPS");
        } catch (RemoteException e) {
            Log.e(TAG, "enable", e);
        }

        if (onEnabled != null) {
            onEnabled.run();
        }
    }

    @Override
    public void disable(@Nullable Runnable onDisabled) {
        execution.assertIsMainThread();

        Request req = currentRequest;
        if (req == null) {
            Log.w(TAG, "disable: already disabled");
            return;
        }

        try {
            authController.getUdfpsHbmListener().onHbmDisabled(req.displayId);
            Log.v(TAG, "disable: removed the UDFPS refresh rate request");
        } catch (RemoteException e) {
            Log.e(TAG, "disable", e);
        }

        currentRequest = null;

        if (onDisabled != null) {
            onDisabled.run();
        }
    }
}
