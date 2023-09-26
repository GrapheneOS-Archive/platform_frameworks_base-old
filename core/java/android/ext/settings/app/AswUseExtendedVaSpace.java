package android.ext.settings.app;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateBase;

import com.android.server.os.nano.AppCompatProtos;

import dalvik.system.VMRuntime;

/** @hide */
public class AswUseExtendedVaSpace extends AppSwitch {
    public static final AswUseExtendedVaSpace I = new AswUseExtendedVaSpace();

    private AswUseExtendedVaSpace() {
        gosPsFlag = GosPackageState.FLAG_USE_EXTENDED_VA_SPACE;
        gosPsFlagNonDefault = GosPackageState.FLAG_USE_EXTENDED_VA_SPACE_NON_DEFAULT;
        compatChangeToDisableHardening = AppCompatProtos.DISABLE_EXTENDED_VA_SPACE;
    }

    @Override
    public Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase ps, StateInfo si) {
        if (AswUseHardenedMalloc.I.get(ctx, userId, appInfo, ps)) {
            si.immutabilityReason = IR_REQUIRED_BY_HARDENED_MALLOC;
            return true;
        }

        String primaryAbi = appInfo.primaryCpuAbi;
        if (primaryAbi != null && !VMRuntime.is64BitAbi(primaryAbi)) {
            si.immutabilityReason = IR_NON_64_BIT_NATIVE_CODE;
            return false;
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
        return true;
    }
}
