package com.android.server.ext;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.content.Context;
import android.ext.PackageId;
import android.ext.settings.BoolSysProperty;
import android.ext.settings.ExtSettings;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.SparseArray;

import com.android.server.pm.PackageManagerService;
import com.android.server.pm.ext.PackageExt;
import com.android.server.pm.pkg.AndroidPackage;

import java.util.function.BooleanSupplier;

public class SeInfoOverride {
    private final String seInfo;
    private final int packageId;
    private final BooleanSupplier setting;

    private SeInfoOverride(String seInfo, int packageId, BooleanSupplier setting) {
        this.setting = setting;
        this.packageId = packageId;
        this.seInfo = seInfo;
    }

    @Nullable
    public static String maybeGet(AndroidPackage pkg) {
        SeInfoOverride sio = map.get(PackageExt.get(pkg).getPackageId());
        if (sio == null) {
            return null;
        }

        if (!sio.setting.getAsBoolean()) {
            // override is disabled
            return null;
        }

        // PackageIds are assigned to packages only after verification

        return sio.seInfo;
    }

    // called by Settings app when state of the override setting changes
    public static void updateSeInfo(PackageManagerService pm, String packageName) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException();
        }

        try {
            ActivityManager.getService().forceStopPackage(packageName, UserHandle.USER_ALL);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }

        // this will update SELinux contexts of app's data files
        pm.updateSeInfo(packageName);
    }

    // should be immutable after static class initializer completes to ensure thread safety
    private static final SparseArray<SeInfoOverride> map = new SparseArray<>();

    private static void add(String seInfo, int packageId, BooleanSupplier setting) {
        map.put(packageId, new SeInfoOverride(seInfo, packageId, setting));
    }

    private static void add(String seInfo, int packageId, BoolSysProperty sysProp) {
        add(seInfo, packageId, sysProp::get);
    }

    static {
        Context ctx = ActivityThread.currentActivityThread().getSystemContext();
    }
}
