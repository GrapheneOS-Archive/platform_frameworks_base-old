package android.ext.dcl;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageStateBase;
import android.ext.settings.app.AswRestrictMemoryDynCodeLoading;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.Arrays;

import dalvik.system.DexFile;

/** @hide */
@SystemApi
public class DynCodeLoading {
    private static final String TAG = DynCodeLoading.class.getSimpleName();

    /** @hide */
    public static final int RESTRICT_MEMORY_DCL = 1;

    /** @hide */
    public static int getAppBindFlags(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase gosPs) {
        int res = 0;

        if (AswRestrictMemoryDynCodeLoading.I.get(ctx, userId, appInfo, gosPs)) {
            res |= RESTRICT_MEMORY_DCL;
        }

        return res;
    }

    /** @hide */
    public static void handleAppBindFlags(int flags) {
        if ((flags & RESTRICT_MEMORY_DCL) != 0) {
            DexFile.enableDynCodeLoadingChecks(flags);
        }
    }

    private static void showNotif(int type, String path, String denialType, Exception e) {
        String pkgName = AppGlobals.getInitialPackage();

        var reportLines = new ArrayList<String>();
        reportLines.add("process: " + Application.getProcessName());
        reportLines.add("thread: " + Thread.currentThread().getName());
        reportLines.add("");
        String stackTrace = Log.getStackTraceString(e);
        reportLines.addAll(Arrays.asList(stackTrace.split("\n")));

        try {
            ActivityManager.getService().showDynCodeLoadingNotification(type, pkgName, path,
                    reportLines, denialType);
        } catch (RemoteException re) {
            Log.d(TAG, "", re);
        }
    }

    @Keep // called from native ART code
    public static void checkInMemoryDexFileOpen(int flags) {
        var se = new SecurityException();
        showNotif(RESTRICT_MEMORY_DCL, null, "InMemoryDexFile", se);
        throw se;
    }

    private DynCodeLoading() {}
}
