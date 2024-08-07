package com.android.settingslib.users;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import static com.android.settingslib.users.AppCopyHelper.SelectableAppInfo;

public final class AppCopyHelperUtils {

    private static final String TAG = "AppCopyHelperUtils";

    static SelectableAppInfoExt instantiateSelectableAppInfoExt(
            PackageManager pm, ApplicationInfo app) {
        try {
            final boolean installed = (app.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
            String installerOfRecordPkgName =
                    pm.getInstallSourceInfo(app.packageName).getInstallingPackageName();

            final int installerOfRecordUid = pm.getInstallerOfRecordUid(
                    app.packageName, UserHandle.getUserId(app.uid));

            return new SelectableAppInfoExt.Builder()
                    .setInstalled(installed)
                    .setInstallerOfRecordUid(installerOfRecordUid)
                    .build();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "", e);
            return new SelectableAppInfoExt.Builder().build();
        }
    }

    public static final class SelectableAppInfoExt {

        public final boolean installed;
        public final int installerOfRecordUid;

        public SelectableAppInfoExt(
                boolean installed, int installerOfRecordUid) {
            this.installed = installed;
            this.installerOfRecordUid = installerOfRecordUid;
        }

        private static final class Builder {
            boolean installed = false;
            int installerOfRecordUid = Process.INVALID_UID;

            Builder setInstalled(boolean installed) {
                this.installed = installed;
                return this;
            }

            Builder setInstallerOfRecordUid(int installerOfRecordUid) {
                this.installerOfRecordUid = installerOfRecordUid;
                return this;
            }

            SelectableAppInfoExt build() {
                return new SelectableAppInfoExt(
                        installed,
                        installerOfRecordUid);
            }

        }
    }

    static int compareExtraContents(SelectableAppInfo lhs, SelectableAppInfo rhs) {
        SelectableAppInfoExt lhsExt = lhs.ext;
        SelectableAppInfoExt rhsExt = rhs.ext;
        final int installedComparison = Boolean.compare(lhsExt.installed, rhsExt.installed);
        if (installedComparison != 0) {
            return installedComparison;
        }

        final int uidComparison =
                Integer.compare(lhsExt.installerOfRecordUid, rhsExt.installerOfRecordUid);
        if (uidComparison != 0) {
            if (lhsExt.installerOfRecordUid == Process.INVALID_UID) {
                return 1;
            }

            if (rhsExt.installerOfRecordUid == Process.INVALID_UID) {
                return -1;
            }

            return uidComparison;
        }

        return 0;
    }
}
