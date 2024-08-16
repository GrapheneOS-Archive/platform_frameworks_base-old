package com.android.server.ext;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.PackageManagerInternal;
import android.ext.LogViewerApp;
import android.ext.SettingsIntents;
import android.ext.dcl.DynCodeLoading;
import android.os.Process;
import android.util.Slog;

import com.android.internal.R;
import com.android.server.LocalServices;

import java.util.ArrayList;
import java.util.List;

public class DynCodeLoadingUtils {
    private static final String TAG = DynCodeLoadingUtils.class.getSimpleName();

    public static class DclReport {
        public final String type;
        public final String denialType;
        public ArrayList<String> lines = new ArrayList<>();

        private DclReport(String type, String denialType) {
            this.type = type;
            this.denialType = denialType;
        }

        public static DclReport createForMemoryDcl(String denialType) {
            return new DclReport("memory_DCL", denialType);
        }
    }

    public static void handleAppReportedDcl(Context ctx, int type, String pkgName, int userId, @Nullable String path,
                                            List<String> reportBody, String denialType) {
        Slog.d(TAG, "handleAppReportedDcl, denialType: " + denialType + ", pkg: " + pkgName + ", path: " + path);

        var pm = LocalServices.getService(PackageManagerInternal.class);
        ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, 0, Process.SYSTEM_UID, userId);
        if (appInfo == null) {
            Slog.d(TAG, "appInfo is null");
            return;
        }

        if (type == DynCodeLoading.RESTRICT_MEMORY_DCL) {
            var n = createMemoryDclNotif(ctx, appInfo);
            var report = DclReport.createForMemoryDcl(denialType);
            report.lines.addAll(reportBody);
            n.moreInfoIntent = DynCodeLoadingUtils.getMoreInfoIntent(n, report);
            n.maybeShow();
        } else {
            throw new IllegalArgumentException(Integer.toString(type));
        }
    }

    public static AppSwitchNotification createMemoryDclNotif(
            Context ctx, ApplicationInfo appInfo) {
        var n = AppSwitchNotification.create(ctx, appInfo, SettingsIntents.APP_MEMORY_DYN_CODE_LOADING);
        n.titleRes = R.string.notif_memory_dcl_title;
        n.gosPsFlagSuppressNotif = GosPackageState.FLAG_RESTRICT_MEMORY_DYN_CODE_LOADING_SUPPRESS_NOTIF;
        return n;
    }

    public static Intent getMoreInfoIntent(AppSwitchNotification n, DclReport report) {
        var lines = new ArrayList<String>();
        lines.add("package: " + n.pkgName + ':' + n.appInfo.longVersionCode);
        lines.add("");
        lines.add("DCL denial type: " + report.denialType);
        lines.addAll(report.lines);

        Intent i = LogViewerApp.createBaseErrorReportIntent(String.join("\n", lines));
        i.putExtra(LogViewerApp.EXTRA_ERROR_TYPE, report.type);
        i.putExtra(LogViewerApp.EXTRA_SOURCE_PACKAGE, n.pkgName);
        return i;
    }
}
