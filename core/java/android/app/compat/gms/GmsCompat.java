package android.app.compat.gms;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ActivityThread;
import android.app.AppGlobals;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.ext.AppInfoExt;
import android.ext.PackageId;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;

import com.android.internal.gmscompat.GmsHooks;
import com.android.internal.gmscompat.GmsInfo;
import com.android.internal.util.ArrayUtils;

/**
 * This class provides helpers for GMS ("Google Mobile Services") compatibility.
 * <p>
 * It allows the following apps to work as regular, unprivileged user apps:
 * <ul>
 *     <li>GSF ("Google Services Framework")</li>
 *     <li>GmsCore ("Google Play services")</li>
 *     <li>Google Play Store</li>
 *     <li>GSA ("Google Search app", com.google.android.googlequicksearchbox)</li>
 *     <li>Apps that depend on the above</li>
 * </ul>
 * <p>
 *
 * @hide
 */
@SystemApi
public final class GmsCompat {
    private static final String TAG = "GmsCompat/Core";

    private static boolean isGmsCompatEnabled;
    private static int curPackageId;
    private static boolean isGmsCore;

    private static boolean isEligibleForClientCompat;

    // Static only
    private GmsCompat() { }

    public static boolean isEnabled() {
        return isGmsCompatEnabled;
    }

    /** @hide */
    public static boolean isGmsCore() {
        return curPackageId == PackageId.GMS_CORE;
    }

    /** @hide */
    public static boolean isPlayStore() {
        return curPackageId == PackageId.PLAY_STORE;
    }

    /** @hide */
    public static boolean isGCarrierSettings() {
        return curPackageId == PackageId.G_CARRIER_SETTINGS;
    }

    /** @hide */
    public static boolean isAndroidAuto() {
        return curPackageId == PackageId.ANDROID_AUTO;
    }

    /** @hide */
    public static int getCurrentPackageId() {
        return curPackageId;
    }

    private static Context appContext;

    /** @hide */
    public static Context appContext() {
        return appContext;
    }

    /**
     * Call from Instrumentation.newApplication() before Application class in instantiated to
     * make sure init is completed in GMS processes before any of the app's code is executed.
     *
     * @hide
     */
    public static void maybeEnable(Context appCtx) {
        if (!Process.isApplicationUid(Process.myUid())) {
            // note that isApplicationUid() returns false for processes of services that have
            // 'android:isolatedProcess="true"' directive in AndroidManifest, which is fine,
            // because they have no need for GmsCompat
            return;
        }

        appContext = appCtx;
        ApplicationInfo appInfo = appCtx.getApplicationInfo();
        AppInfoExt appInfoExt = appInfo.ext();

        curPackageId = appInfoExt.getPackageId();

        if (isEnabledFor(appInfo)) {
            isGmsCompatEnabled = true;
            GmsHooks.init(appCtx, appInfo.packageName);
        }

        isEligibleForClientCompat = !isGmsCore() &&
                appInfoExt.hasFlag(AppInfoExt.FLAG_HAS_GMSCORE_CLIENT_LIBRARY);
    }

    public static boolean isEnabledFor(@NonNull ApplicationInfo app) {
        if (Build.IS_DEBUGGABLE) {
            if (isTestPackage(app.packageName)) {
                return true;
            }
        }

        return isEnabledFor(app.ext().getPackageId(), app.isPrivilegedApp());
    }

    public static boolean isEnabledFor(int packageId, boolean isPrivileged) {
        if (isPrivileged) {
            // don't enable GmsCompat for privileged GMS
            return false;
        }

        return switch (packageId) {
            case
                PackageId.GSF,
                PackageId.GMS_CORE,
                PackageId.PLAY_STORE,
                PackageId.G_SEARCH_APP,
                PackageId.ANDROID_AUTO,
                PackageId.G_CARRIER_SETTINGS ->
                    true;
            default ->
                    false;
        };
    }

    /** @hide */
    public static boolean canBeEnabledFor(String pkgName) {
        if (Build.IS_DEBUGGABLE) {
            if (isTestPackage(pkgName)) {
                return true;
            }
        }

        return switch (pkgName) {
            case
                PackageId.GSF_NAME,
                PackageId.GMS_CORE_NAME,
                PackageId.PLAY_STORE_NAME,
                PackageId.G_SEARCH_APP_NAME,
                PackageId.ANDROID_AUTO_NAME,
                PackageId.G_CARRIER_SETTINGS_NAME ->
                    true;
            default ->
                    false;
        };

    }

    /** @hide */
    public static boolean isGmsAppAndUnprivilegedProcess(@NonNull String packageName) {
        if (!isEnabledFor(packageName, UserHandle.USER_CURRENT)) {
            return false;
        }

        Application a = AppGlobals.getInitialApplication();
        if (a == null) {
            return false;
        }

        ApplicationInfo ai = a.getApplicationInfo();
        if (ai == null) {
            return false;
        }

        return !ai.isPrivilegedApp();
    }

    public static boolean isEnabledFor(@NonNull String packageName, int userId) {
        return isEnabledFor(packageName, userId, false);
    }

    /** @hide */
    public static boolean isEnabledFor(@NonNull String packageName, int userId, boolean matchDisabledApp) {
        if (Build.isDebuggable()) {
            if (isTestPackage(packageName, userId, matchDisabledApp)) {
                return true;
            }
        }

        if (!canBeEnabledFor(packageName)) {
            return false;
        }

        if (userId == UserHandle.USER_CURRENT) {
            userId = UserHandle.myUserId();
        }

        Context ctx = AppGlobals.getInitialApplication();
        if (ctx == null) {
            return false;
        }

        PackageManager pm = ctx.getPackageManager();

        ApplicationInfo appInfo;
        long token = Binder.clearCallingIdentity();
        try {
            appInfo = pm.getApplicationInfoAsUser(packageName, 0, userId);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        return isEnabledFor(appInfo, matchDisabledApp);
    }

    /** @hide */
    public static boolean isEnabledFor(@Nullable ApplicationInfo app, boolean matchDisabledApp) {
        if (app == null) {
            return false;
        }
        if (!app.enabled && !matchDisabledApp) {
            return false;
        }
        return isEnabledFor(app);
    }

    private static volatile boolean cachedIsClientOfGmsCore;

    /** @hide */
    public static boolean isClientOfGmsCore() {
        return isClientOfGmsCore(null);
    }

    /** @hide */
    public static boolean isClientOfGmsCore(@Nullable ApplicationInfo gmsCoreAppInfo) {
        if (cachedIsClientOfGmsCore) {
            return true;
        }

        if (!isEligibleForClientCompat) {
            return false;
        }

        boolean res = (gmsCoreAppInfo != null) ?
                isEnabledFor(gmsCoreAppInfo) :
                isEnabledFor(GmsInfo.PACKAGE_GMS_CORE, appContext().getUserId());

        cachedIsClientOfGmsCore = res;
        return res;
    }

    public static boolean hasPermission(@NonNull String perm) {
        Context ctx = appContext();

        if (GmsHooks.config().shouldSpoofSelfPermissionCheck(perm)) {
            // result of checkSelfPermission() below would be spoofed, ask the PackageManager directly
            IPackageManager pm = ActivityThread.getPackageManager();
            try {
                return pm.checkPermission(perm, ctx.getPackageName(), ctx.getUserId())
                        == PackageManager.PERMISSION_GRANTED;
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }

        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isTestPackage(String packageName) {
        String testPkgs = SystemProperties.get("persist.gmscompat_test_pkgs");
        return ArrayUtils.contains(testPkgs.split(","), packageName);
    }

    // call only when Build.isDebuggable() is true
    /** @hide */
    public static boolean isTestPackage(String packageName, int userId, boolean matchDisabledApp) {
        if (!Build.isDebuggable()) {
            return false;
        }
        if (!isTestPackage(packageName)) {
            return false;
        }

        IPackageManager pm = ActivityThread.getPackageManager();
        ApplicationInfo ai;

        long token = Binder.clearCallingIdentity();
        try {
            ai = pm.getApplicationInfo(packageName, 0, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        if (ai == null) {
            return false;
        }
        return ai.enabled || matchDisabledApp;
    }
}
