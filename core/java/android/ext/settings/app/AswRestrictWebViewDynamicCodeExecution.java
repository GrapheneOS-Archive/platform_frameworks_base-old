package android.ext.settings.app;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateBase;
import android.ext.settings.ExtSettings;
import android.util.ArraySet;

import com.android.internal.R;

/** @hide */
public class AswRestrictWebViewDynamicCodeExecution extends AppSwitch {
    public static final AswRestrictWebViewDynamicCodeExecution I = new AswRestrictWebViewDynamicCodeExecution();

    private AswRestrictWebViewDynamicCodeExecution() {
        gosPsFlagNonDefault = GosPackageState.FLAG_RESTRICT_WEBVIEW_DYN_CODE_EXEC_NON_DEFAULT;
        gosPsFlag = GosPackageState.FLAG_RESTRICT_WEBVIEW_DYN_CODE_EXEC;
    }

    private static volatile ArraySet<String> allowedSystemPkgs;

    private static boolean shouldAllowByDefaultToSystemPackage(Context ctx, String pkg) {
        var set = allowedSystemPkgs;
        if (set == null) {
            set = new ArraySet<>(ctx.getResources()
                .getStringArray(R.array.system_pkgs_allowed_webview_dyn_code_exec_by_default));
            allowedSystemPkgs = set;
        }
        return set.contains(pkg);
    }

    @Override
    public Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase ps, StateInfo si) {
        if (appInfo.isSystemApp()) {
            // TODO uncomment after WebView respects Environment.isDynamicCodeExecutionRestricted()
            /*
            if (shouldAllowByDefaultToSystemPackage(ctx, packageName)) {
                // allow manual restriction
                return null;
            } else {
                return true;
            }
             */
            return null;
        }

        return null;
    }

    @Override
    public boolean getDefaultValue(Context ctx, int userId, ApplicationInfo appInfo,
                                   @Nullable GosPackageStateBase ps, StateInfo si) {
        if (appInfo.isSystemApp()) {
            // TODO uncomment after WebView respects Environment.isDynamicCodeExecutionRestricted()
            // return !shouldAllowByDefaultToSystemPackage(ctx, packageName);
            return false;
        }
        else {
            return ExtSettings.RESTRICT_WEBVIEW_DYN_CODE_EXEC_BY_DEFAULT.get(ctx, userId);
        }
    }
}
