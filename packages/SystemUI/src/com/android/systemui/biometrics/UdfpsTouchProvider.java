package com.android.systemui.biometrics;

import android.annotation.Nullable;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.systemui.dagger.SysUISingleton;

import com.google.hardware.biometrics.fingerprint.IFingerprintExt;

import javax.inject.Inject;

@SysUISingleton
public class UdfpsTouchProvider implements AlternateUdfpsTouchProvider {
    private static final String TAG = "UdfpsTouchProvider";

    @Inject
    UdfpsTouchProvider() {}

    private volatile IFingerprintExt cachedFingerprintExt;

    @Nullable
    private IFingerprintExt getFingerprintExt() {
        IFingerprintExt cache = cachedFingerprintExt;
        if (cache != null) {
            return cache;
        }

        IBinder binder;
        try {
            var name = "android.hardware.biometrics.fingerprint.IFingerprint/default";
            binder = ServiceManager.waitForDeclaredService(name).getExtension();
        } catch (RemoteException e) {
            Log.d(TAG, "", e);
            return null;
        }

        return cachedFingerprintExt = IFingerprintExt.Stub.asInterface(binder);
    }

    @Override
    public void onPointerDown(long pointerId, int x, int y, float minor, float major) {
        var ext = getFingerprintExt();
        if (ext == null) {
            Log.e(TAG, "onPointerDown: IFingerprintExt is null");
            return;
        }
        try {
            ext.onPointerDown(pointerId, x, y, minor, major);
        } catch (RemoteException e) {
            cachedFingerprintExt = null;
            Log.e(TAG, "onPointerDown", e);
        }
    }

    @Override
    public void onPointerUp(long pointerId) {
        var ext = getFingerprintExt();
        if (ext == null) {
            Log.e(TAG, "onPointerUp: IFingerprintExt is null");
            return;
        }
        try {
            ext.onPointerUp(pointerId);
        } catch (RemoteException e) {
            cachedFingerprintExt = null;
            Log.e(TAG, "onPointerUp", e);
        }
    }

    @Override
    public void onUiReady() {
        var ext = getFingerprintExt();
        if (ext == null) {
            Log.e(TAG, "onUiReady: IFingerprintExt is null");
            return;
        }
        try {
            ext.onUiReady();
        } catch (RemoteException e) {
            cachedFingerprintExt = null;
            Log.e(TAG, "onUiReady", e);
        }
    }
}
