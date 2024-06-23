package com.android.server.clipboard;

import android.content.ClipData;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.ext.settings.app.AswClipboardRead;
import android.os.Process;

import com.android.server.LocalServices;
import com.android.server.ext.ClipboardReadNotification;
import com.android.server.pm.pkg.GosPackageStatePm;

class ClipboardHelper {
    static final ClipData dummyClip = ClipData.newPlainText(null, "");

    static int getPackageUid(String pkgName, int userId) {
        var pmi = LocalServices.getService(PackageManagerInternal.class);
        return pmi.getPackageUid(pkgName, 0, userId);
    }

    static boolean isReadAllowedForPackage(Context ctx, String pkgName, int userId) {
        var pmi = LocalServices.getService(PackageManagerInternal.class);
        ApplicationInfo appInfo = pmi.getApplicationInfo(pkgName, 0, Process.SYSTEM_UID, userId);
        if (appInfo == null) {
            return false;
        }
        GosPackageStatePm ps = pmi.getGosPackageState(pkgName, userId);
        return AswClipboardRead.I.get(ctx, userId, appInfo, ps);
    }

    static boolean isNotificationSuppressed(Context ctx, String pkgName, int userId) {
        var pmi = LocalServices.getService(PackageManagerInternal.class);
        ApplicationInfo appInfo = pmi.getApplicationInfo(pkgName, 0, Process.SYSTEM_UID, userId);
        if (appInfo == null) {
            return true;
        }
        GosPackageStatePm ps = pmi.getGosPackageState(pkgName, userId);
        return AswClipboardRead.I.isNotificationSuppressed(ctx, userId, ps);
    }

    static void maybeNotifyAccessDenied(Context ctx, int deviceId, String pkgName, int pkgUid,
            String primaryClipPackage, int primaryClipUid) {
        var n = ClipboardReadNotification.maybeCreate(ctx, deviceId, pkgName, pkgUid,
                primaryClipPackage, primaryClipUid);
        if (n != null) {
            n.maybeShow();
        }
    }
}
