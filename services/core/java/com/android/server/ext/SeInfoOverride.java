package com.android.server.ext;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.content.Context;
import android.ext.settings.BoolSysProperty;
import android.ext.settings.ExtSettings;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;

import com.android.internal.util.GoogleCameraUtils;
import com.android.internal.util.PackageSpec;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.parsing.pkg.AndroidPackage;

import java.util.function.BooleanSupplier;

public class SeInfoOverride {
    private final String seInfo;
    private final PackageSpec packageSpec;
    private final BooleanSupplier setting;

    private SeInfoOverride(String seInfo, PackageSpec packageSpec, BooleanSupplier setting) {
        this.setting = setting;
        this.packageSpec = packageSpec;
        this.seInfo = seInfo;
    }

    @Nullable
    public static String maybeGet(AndroidPackage pkg) {
        SeInfoOverride sio = map.get(pkg.getPackageName());
        if (sio == null) {
            return null;
        }

        if (!sio.setting.getAsBoolean()) {
            // override is disabled
            return null;
        }

        if (PackageManagerUtils.validatePackage(pkg, sio.packageSpec)) {
            return sio.seInfo;
        }

        return null;
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
    private static final ArrayMap<String, SeInfoOverride> map = new ArrayMap<>();

    private static void add(String seInfo, PackageSpec packageSpec, BooleanSupplier setting) {
        map.put(packageSpec.packageName, new SeInfoOverride(seInfo, packageSpec, setting));
    }

    private static void add(String seInfo, PackageSpec packageSpec, BoolSysProperty sysProp) {
        add(seInfo, packageSpec, sysProp::get);
    }

    static {
        Context ctx = ActivityThread.currentActivityThread().getSystemContext();
        if (GoogleCameraUtils.isCustomSeInfoNeededForAccessToAccelerators(ctx)) {
            add("GoogleCamera", GoogleCameraUtils.PACKAGE_SPEC, ExtSettings.ALLOW_GOOGLE_APPS_SPECIAL_ACCESS_TO_ACCELERATORS);
        }
    }
}
