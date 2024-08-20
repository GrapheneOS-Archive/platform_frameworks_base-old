package android.ext.settings.app;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateBase;
import android.ext.settings.ExtSettings;
import android.util.ArraySet;

import com.android.internal.R;
import com.android.internal.os.SELinuxFlags;
import com.android.server.os.nano.AppCompatProtos;

/** @hide */
public class AswRestrictStorageDynCodeLoading extends AppSwitch {
    public static final AswRestrictStorageDynCodeLoading I = new AswRestrictStorageDynCodeLoading();

    private AswRestrictStorageDynCodeLoading() {
        gosPsFlagNonDefault = GosPackageState.FLAG_RESTRICT_STORAGE_DYN_CODE_LOADING_NON_DEFAULT;
        gosPsFlag = GosPackageState.FLAG_RESTRICT_STORAGE_DYN_CODE_LOADING;
        gosPsFlagSuppressNotif = GosPackageState.FLAG_RESTRICT_STORAGE_DYN_CODE_LOADING_SUPPRESS_NOTIF;
        compatChangeToDisableHardening = AppCompatProtos.ALLOW_STORAGE_DYN_CODE_EXEC;
    }

    private static volatile ArraySet<String> allowedSystemPkgs;

    private static boolean shouldAllowByDefaultToSystemPkg(Context ctx, String pkg) {
        var set = allowedSystemPkgs;
        if (set == null) {
            set = new ArraySet<>(ctx.getResources()
                .getStringArray(R.array.system_pkgs_allowed_storage_dyn_code_loading_by_default));
            allowedSystemPkgs = set;
        }
        return set.contains(pkg);
    }

    @Override
    public Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase ps, StateInfo si) {
        if (appInfo.isSystemApp()) {
            if (shouldAllowByDefaultToSystemPkg(ctx, appInfo.packageName)) {
                // allow manual restriction
                return null;
            }
            if (SELinuxFlags.isSystemAppSepolicyWeakeningAllowed()) {
                return null;
            }
            si.immutabilityReason = IR_IS_SYSTEM_APP;
            return true;
        }

        if (ps != null && ps.hasFlags(GosPackageState.FLAG_ENABLE_EXPLOIT_PROTECTION_COMPAT_MODE)) {
            si.immutabilityReason = IR_EXPLOIT_PROTECTION_COMPAT_MODE;
            return false;
        }

        return null;
    }

    @Override
    protected boolean getDefaultValueInner(Context ctx, int userId, ApplicationInfo appInfo,
                                           @Nullable GosPackageStateBase ps, StateInfo si) {
        if (appInfo.isSystemApp()) {
            return !shouldAllowByDefaultToSystemPkg(ctx, appInfo.packageName);
        } else {
            si.defaultValueReason = DVR_DEFAULT_SETTING;
            return ExtSettings.RESTRICT_STORAGE_DYN_CODE_LOADING_BY_DEFAULT.get(ctx, userId);
        }
    }
}
