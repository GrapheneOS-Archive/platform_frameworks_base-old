package android.ext.settings.app;

import static android.ext.settings.ExtSettings.CLIPBOARD_READ_ACCESS;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateBase;
import android.ext.settings.ClipboardReadSetting;

/** @hide */
public class AswClipboardRead extends AppSwitch {
    public static final AswClipboardRead I = new AswClipboardRead();

    private AswClipboardRead() {
        gosPsFlag = GosPackageState.FLAG_CLIPBOARD_READ;
        gosPsFlagNonDefault = GosPackageState.FLAG_CLIPBOARD_READ_NON_DEFAULT;
        gosPsFlagSuppressNotif = GosPackageState.FLAG_CLIPBOARD_READ_SUPPRESS_NOTIF;
    }

    @Override
    public Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     @Nullable GosPackageStateBase ps, StateInfo si) {
        if (appInfo.isSystemApp()) {
            si.immutabilityReason = IR_IS_SYSTEM_APP;
            return true;
        }

        return null;
    }

    @Override
    protected boolean getDefaultValueInner(Context ctx, int userId, ApplicationInfo appInfo,
                                           @Nullable GosPackageStateBase ps, StateInfo si) {
        si.defaultValueReason = DVR_DEFAULT_SETTING;
        return CLIPBOARD_READ_ACCESS.get(ctx, userId) == ClipboardReadSetting.ALLOWED;
    }

    public boolean isNotificationSuppressed(Context ctx, int userId,
                                            @Nullable GosPackageStateBase ps) {
        if (isUsingDefaultValue(ps)) {
            return CLIPBOARD_READ_ACCESS.get(ctx, userId) == ClipboardReadSetting.BLOCKED;
        }

        return isNotificationSuppressed(ps);
    }
}
