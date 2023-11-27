package android.ext.settings.app;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateBase;
import android.ext.settings.ExtSettings;

import com.android.internal.os.SELinuxFlags;
import com.android.server.os.nano.AppCompatProtos;

/** @hide */
public class AswDenyNativeDebug extends AppSwitch {
    public static final AswDenyNativeDebug I = new AswDenyNativeDebug();

    private AswDenyNativeDebug() {
        gosPsFlagNonDefault = GosPackageState.FLAG_BLOCK_NATIVE_DEBUGGING_NON_DEFAULT;
        gosPsFlag = GosPackageState.FLAG_BLOCK_NATIVE_DEBUGGING;
        gosPsFlagSuppressNotif = GosPackageState.FLAG_BLOCK_NATIVE_DEBUGGING_SUPPRESS_NOTIF;
        compatChangeToDisableHardening = AppCompatProtos.ALLOW_NATIVE_DEBUGGING;
    }

    @Override
    public Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase ps, StateInfo si) {
        if (appInfo.isSystemApp() && !SELinuxFlags.isSystemAppSepolicyWeakeningAllowed()) {
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
            return true;
        }

        si.defaultValueReason = DVR_DEFAULT_SETTING;
        return !ExtSettings.ALLOW_NATIVE_DEBUG_BY_DEFAULT.get();
    }
}
