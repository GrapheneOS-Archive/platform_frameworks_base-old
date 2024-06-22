package com.android.server.ext;

import android.annotation.Nullable;
import android.annotation.StringRes;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.ext.SettingsIntents;
import android.ext.settings.app.AppSwitch;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.R;
import com.android.server.LocalServices;

import static com.android.internal.util.Preconditions.checkState;
import static com.android.server.ext.SseUtils.addNotifAction;
import static java.util.Objects.requireNonNull;

/*
 * Notification that links an AppSwitch Settings UI.
 *
 * Example:
 * Title: $APP tried to access a resource
 * Text: Tap to open settings (tapping on notification opens a per-app AppSwitch setting)
 * Actions (optional): "Don't show again", "More info"
 *
 * Rate-limiting is applied for each (app, setting) pair. At most one notification can be shown
 * for each such pair within 30 seconds.
 */
public class AppSwitchNotification extends AppSwitchNotificationBase {
    private static final String TAG = "AppSwitchNotif";

    public final String settingsIntentAction; // launched when notif is tapped

    @StringRes public int titleRes; // has to have a placeholder for app name
    @Nullable public CharSequence titleOverride;

    @Nullable public Intent moreInfoIntent; // optional, shows up as "More info" notif action
    public int gosPsFlagSuppressNotif; // optional, adds "Don't show again" notif action

    @Nullable
    public static AppSwitchNotification maybeCreate(Context ctx, String firstPackageName,
                                                    int packageUid, AppSwitch appSwitch,
                                                    String settingsIntentAction) {
        int userId = UserHandle.getUserId(packageUid);
        var pm = LocalServices.getService(PackageManagerInternal.class);
        ApplicationInfo appInfo = pm.getApplicationInfo(firstPackageName, 0, Process.SYSTEM_UID, userId);
        Slog.d(TAG, "maybeCreate, pkg: " + firstPackageName + ", intentAction: " + settingsIntentAction);
        if (appInfo == null) {
            Slog.d(TAG, "appInfo is null");
            return null;
        }

        if (appInfo.uid != packageUid) {
            Slog.d(TAG, "packageUid mismatch: expected " + packageUid + ", got " + appInfo.uid);
            return null;
        }

        return new AppSwitchNotification(ctx, appInfo, appSwitch, settingsIntentAction);
    }

    public static AppSwitchNotification create(Context ctx, ApplicationInfo appInfo,
                                               AppSwitch appSwitch, String settingsIntentAction) {
        return new AppSwitchNotification(ctx, appInfo, appSwitch, settingsIntentAction);
    }

    private AppSwitchNotification(Context ctx, ApplicationInfo appInfo, AppSwitch appSwitch,
                                  String settingsIntentAction) {
        super(ctx, appInfo, appSwitch);
        this.settingsIntentAction = settingsIntentAction;
    }

    @Override
    protected void checkInited() {
        checkState(titleRes != 0, "titleRes");
        requireNonNull(settingsIntentAction, "settingsIntentAction");
    }

    @Override
    protected void modifyNotification(Notification.Builder nb, int notifId) {
        Slog.d(TAG, "modifyNotification: pkg: " + pkgName + ", intent " + settingsIntentAction);

        final Context ctx = this.context;

        CharSequence title = titleOverride;
        if (title == null) {
            title = ctx.getString(titleRes, getAppLabel(ctx, appInfo));
        }
        nb.setContentTitle(title);
        {
            var intent = SettingsIntents.getAppIntent(ctx, settingsIntentAction, pkgName);
            var pi = PendingIntent.getActivityAsUser(ctx, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE, null, userHandle);
            nb.setContentIntent(pi);
            nb.setContentText(ctx.getText(R.string.notif_text_tap_to_open_settings));
        }
        nb.setAutoCancel(true);

        if (moreInfoIntent != null) {
            var pi = PendingIntent.getActivityAsUser(ctx, 0, moreInfoIntent,
                    PendingIntent.FLAG_IMMUTABLE, null, userHandle);
            addNotifAction(ctx, pi, R.string.notif_action_more_info, nb);
        }

        if (gosPsFlagSuppressNotif != 0) {
            Bundle args = getDefaultNotifArgs(notifId);
            args.putInt(EXTRA_GOSPS_FLAG_SUPPRESS_NOTIF, gosPsFlagSuppressNotif);

            PendingIntent dontShowAgainPi = IntentReceiver.getPendingIntent(
                    SuppressNotifActionReceiver.class, SuppressNotifActionReceiver::new, args, ctx);

            addNotifAction(ctx, dontShowAgainPi, R.string.notification_action_dont_show_again, nb);
        }
    }

    static final String EXTRA_GOSPS_FLAG_SUPPRESS_NOTIF = "gosps_flag_suppress_notif";

    static class SuppressNotifActionReceiver extends NotifActionReceiver {
        @Override
        public void onReceive(Context ctx, String packageName, UserHandle user) {
            int gosPsFlagSuppressNotif = requireNonNull(
                    getArgs().getNumber(EXTRA_GOSPS_FLAG_SUPPRESS_NOTIF));

            setSuppressNotifFlag(packageName, user, gosPsFlagSuppressNotif);
        }
    }
}
