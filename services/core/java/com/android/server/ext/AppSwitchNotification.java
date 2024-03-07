package com.android.server.ext;

import android.annotation.DrawableRes;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.ext.SettingsIntents;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.server.LocalServices;
import com.android.server.pm.pkg.GosPackageStatePm;

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
public class AppSwitchNotification {
    private static final String TAG = "AppSwitchNotif";

    public final Context context;
    public final ApplicationInfo appInfo;
    public final String pkgName;
    public final int userId;
    public final UserHandle userHandle;
    public final String settingsIntentAction; // launched when notif is tapped

    public String notifChannel = SystemNotificationChannels.EXPLOIT_PROTECTION;
    @DrawableRes public int notifSmallIcon = R.drawable.ic_error;

    @StringRes public int titleRes; // has to have a placeholder for app name
    @Nullable public CharSequence titleOverride;

    @Nullable public Intent moreInfoIntent; // optional, shows up as "More info" notif action
    public int gosPsFlagSuppressNotif; // optional, adds "Don't show again" notif action

    @Nullable
    public static AppSwitchNotification maybeCreate(Context ctx, String firstPackageName,
                                                    int packageUid, String settingsIntentAction) {
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

        return new AppSwitchNotification(ctx, appInfo, settingsIntentAction);
    }

    public static AppSwitchNotification create(Context ctx, ApplicationInfo appInfo,
                                               String settingsIntentAction) {
        return new AppSwitchNotification(ctx, appInfo, settingsIntentAction);
    }

    private AppSwitchNotification(Context ctx, ApplicationInfo appInfo, String settingsIntentAction) {
        this.context = ctx;
        this.appInfo = appInfo;
        this.pkgName = appInfo.packageName;
        this.userId = UserHandle.getUserId(appInfo.uid);
        this.userHandle = UserHandle.of(userId);
        this.settingsIntentAction = settingsIntentAction;
    }

    private static final long SAME_PACKAGE_NOTIF_RATE_LIMIT = 30_000L;

    // packageUid -> settingsIntentAction -> prev NotifRecord
    private static final LruCache<Integer, LruCache<String, NotifRecord>> lastShownTracker = new LruCache<>(50) {
        protected LruCache<String, NotifRecord> create(Integer packageUid) {
            return new LruCache<>(20);
        }
    };

    private static int notifIdSource = SystemMessage.NOTE_APP_SWITCH_BASE;

    static class NotifRecord {
        final int notifId;
        final long timestamp;

        NotifRecord(int notifId, long timestamp) {
            this.notifId = notifId;
            this.timestamp = timestamp;
        }
    }

    private void checkInited() {
        checkState(titleRes != 0, "titleRes");
        requireNonNull(settingsIntentAction, "settingsIntentAction");
    }

    public void maybeShow() {
        final long timestamp = SystemClock.uptimeMillis();

        Slog.d(TAG, "maybeShow: pkg: " + pkgName + ", intent " + settingsIntentAction);

        checkInited();

        final var pmi = LocalServices.getService(PackageManagerInternal.class);

        if (gosPsFlagSuppressNotif != 0) {
            GosPackageStatePm gosPs = pmi.getGosPackageState(pkgName, userId);
            if (gosPs != null && gosPs.hasFlags(gosPsFlagSuppressNotif)) {
                Slog.d(TAG, "gosPsFlagSuppressNotif is set");
                return;
            }
        }

        final Context ctx = this.context;

        int notifId;

        synchronized (lastShownTracker) {
            LruCache<String, NotifRecord> map = lastShownTracker.get(Integer.valueOf(appInfo.uid));
            NotifRecord notifRecord = map.get(settingsIntentAction);
            if (notifRecord == null) {
                if (notifIdSource >= SystemMessage.NOTE_APP_SWITCH_MAX) {
                    notifIdSource = SystemMessage.NOTE_APP_SWITCH_BASE;
                }
                notifId = notifIdSource++;
                map.put(settingsIntentAction, new NotifRecord(notifId, timestamp));
            } else {
                if ((timestamp - notifRecord.timestamp) < SAME_PACKAGE_NOTIF_RATE_LIMIT) {
                    Slog.d(TAG, "rate-limited");
                    return;
                }
                // replace previous notification
                notifId = notifRecord.notifId;
                map.put(settingsIntentAction, new NotifRecord(notifId, timestamp));
            }
        }

        var nb = new Notification.Builder(ctx, notifChannel);
        nb.setSmallIcon(notifSmallIcon);

        if (titleOverride != null) {
            nb.setContentTitle(titleOverride);
        } else {
            CharSequence appLabel = appInfo.loadLabel(ctx.getPackageManager());
            if (TextUtils.isEmpty(appLabel)) {
                Slog.d(TAG, "appLabel is empty");
                appLabel = pkgName;
            }
            nb.setContentTitle(ctx.getString(titleRes, appLabel));
        }
        {
            var intent = SettingsIntents.getAppIntent(settingsIntentAction, pkgName);
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
            var args = new Bundle();
            args.putString(Intent.EXTRA_PACKAGE_NAME, pkgName);
            args.putParcelable(Intent.EXTRA_USER, userHandle);
            args.putInt(EXTRA_GOSPS_FLAG_SUPPRESS_NOTIF, gosPsFlagSuppressNotif);
            args.putInt(EXTRA_NOTIF_ID, notifId);

            PendingIntent dontShowAgainPi = IntentReceiver.getPendingIntent(
                    NotifActionReceiver.class, NotifActionReceiver::new, args, ctx);

            addNotifAction(ctx, dontShowAgainPi, R.string.notification_action_dont_show_again, nb);
        }
        ctx.getSystemService(NotificationManager.class)
            .notifyAsUser(null, notifId, nb.build(), userHandle);
    }

    static final String EXTRA_GOSPS_FLAG_SUPPRESS_NOTIF = "gosps_flag_suppress_notif";
    static final String EXTRA_NOTIF_ID = "notif_id";

    static class NotifActionReceiver extends IntentReceiver {
        @Override
        public void onReceive(Context ctx, Bundle args) {
            String packageName = args.getString(Intent.EXTRA_PACKAGE_NAME);
            UserHandle user = args.getParcelable(Intent.EXTRA_USER, UserHandle.class);
            int gosPsFlagSuppressNotif = args.getNumber(EXTRA_GOSPS_FLAG_SUPPRESS_NOTIF);

            var pmi = LocalServices.getService(PackageManagerInternal.class);

            GosPackageStatePm.getEditor(pmi, packageName, user.getIdentifier())
                .addFlags(gosPsFlagSuppressNotif)
                .apply();

            int notifId = args.getNumber(EXTRA_NOTIF_ID);

            ctx.getSystemService(NotificationManager.class).cancelAsUser(null, notifId, user);
        }
    }
}
