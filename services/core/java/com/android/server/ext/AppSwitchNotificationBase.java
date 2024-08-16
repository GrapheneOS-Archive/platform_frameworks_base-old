package com.android.server.ext;

import static java.util.Objects.requireNonNull;

import android.annotation.DrawableRes;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.ext.settings.app.AppSwitch;
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

/**
 * Common code for AppSwitch notification classes
 */
public abstract class AppSwitchNotificationBase {
    private static final String TAG = "AppSwitchNotificationBase";

    public final Context context;
    public final ApplicationInfo appInfo;
    public final String pkgName;
    public final int userId;
    public final UserHandle userHandle;
    public final AppSwitch appSwitch;

    public String notifChannel = SystemNotificationChannels.EXPLOIT_PROTECTION;
    @DrawableRes public int notifSmallIcon = R.drawable.ic_error;

    protected AppSwitchNotificationBase(Context ctx, ApplicationInfo appInfo, AppSwitch appSwitch) {
        this.context = ctx;
        this.appInfo = appInfo;
        this.pkgName = appInfo.packageName;
        this.userId = UserHandle.getUserId(appInfo.uid);
        this.userHandle = UserHandle.of(userId);
        this.appSwitch = appSwitch;
    }

    private static final long SAME_UID_NOTIF_RATE_LIMIT = 30_000L;

    /**
     * Returns the minimum time interval (in milliseconds) between notifications for the same uid,
     * default is 30,000ms (or 30s).
     */
    protected long getSameUidNotifRateLimit() {
        return SAME_UID_NOTIF_RATE_LIMIT;
    }

    // packageUid -> AppSwitch class -> prev NotifRecord
    private static final LruCache<Integer, LruCache<Class<?>, NotifRecord>> lastShownTracker =
            new LruCache<>(50) {
                protected LruCache<Class<?>, NotifRecord> create(Integer packageUid) {
                    return new LruCache<>(20);
                }
            };

    private static int notifIdSource = SystemMessage.NOTE_APP_SWITCH_BASE;

    record NotifRecord(int notifId, long timestamp) {}

    public final void maybeShow() {
        final long timestamp = SystemClock.uptimeMillis();

        Slog.d(TAG, "maybeShow: pkg: " +  pkgName);

        checkInitedInner();

        final var pmi = LocalServices.getService(PackageManagerInternal.class);

        GosPackageStatePm gosPs = pmi.getGosPackageState(pkgName, userId);
        if (appSwitch.isNotificationSuppressed(gosPs)) {
            return;
        }

        final Context ctx = this.context;

        int notifId;

        synchronized (lastShownTracker) {
            Class<? extends AppSwitch> aswClass = appSwitch.getClass();
            LruCache<Class<?>, NotifRecord> map = lastShownTracker.get(appInfo.uid);
            NotifRecord notifRecord = map.get(aswClass);
            if (notifRecord == null) {
                if (notifIdSource >= SystemMessage.NOTE_APP_SWITCH_MAX) {
                    notifIdSource = SystemMessage.NOTE_APP_SWITCH_BASE;
                }
                notifId = notifIdSource;
                map.put(aswClass, new NotifRecord(notifId, timestamp));
            } else {
                if ((timestamp - notifRecord.timestamp) < getSameUidNotifRateLimit()) {
                    Slog.d(TAG, "rate-limited");
                    return;
                }
                // replace previous notification
                notifId = notifRecord.notifId;
                map.put(aswClass, new NotifRecord(notifId, timestamp));
            }
        }

        var nb = new Notification.Builder(ctx, notifChannel);
        nb.setSmallIcon(notifSmallIcon);

        modifyNotification(nb, notifId);

        ctx.getSystemService(NotificationManager.class)
                .notifyAsUser(null, notifId, nb.build(), userHandle);

        onNotificationShown();
    }

    private void checkInitedInner() {
        requireNonNull(appSwitch, "appSwitch");
        checkInited();
    }

    protected void checkInited() {}

    protected abstract void modifyNotification(Notification.Builder nb, int notifId);

    protected void onNotificationShown() {}

    protected final Bundle getDefaultNotifArgs(int notifId) {
        var args = new Bundle();
        args.putString(Intent.EXTRA_PACKAGE_NAME, pkgName);
        args.putParcelable(Intent.EXTRA_USER, userHandle);
        args.putInt(EXTRA_NOTIF_ID, notifId);
        return args;
    }

    static final String EXTRA_NOTIF_ID = "notif_id";

    public abstract static class NotifActionReceiver extends IntentReceiver {
        private Bundle args;

        protected final Bundle getArgs() {
            return args;
        }

        @Override
        public final void onReceive(Context ctx, Bundle args) {
            this.args = args;

            String packageName = args.getString(Intent.EXTRA_PACKAGE_NAME);
            UserHandle user = args.getParcelable(Intent.EXTRA_USER, UserHandle.class);

            onReceive(ctx, packageName, user);

            int notifId = requireNonNull(args.getNumber(EXTRA_NOTIF_ID));

            ctx.getSystemService(NotificationManager.class)
                    .cancelAsUser(null, notifId, user);
        }

        public abstract void onReceive(Context ctx, String packageName, UserHandle user);
    }

    protected static ApplicationInfo getAppInfo(String pkg, int packageUid) {
        int userId = UserHandle.getUserId(packageUid);
        var pm = LocalServices.getService(PackageManagerInternal.class);
        ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0, Process.SYSTEM_UID, userId);
        if (appInfo.uid != packageUid) {
            Slog.d(TAG, "packageUid mismatch: expected " + packageUid + ", got " + appInfo.uid);
            return null;
        }
        return appInfo;
    }

    protected static CharSequence getAppLabel(Context ctx, ApplicationInfo appInfo) {
        CharSequence appLabel = appInfo.loadLabel(ctx.getPackageManager());
        if (TextUtils.isEmpty(appLabel)) {
            Slog.d(TAG, "appLabel is empty");
            appLabel = appInfo.packageName;
        }
        return appLabel;
    }

    protected static void setSuppressNotifFlag(String packageName, UserHandle user,
                                               int gosPsFlagSuppressNotif) {
        var pmi = LocalServices.getService(PackageManagerInternal.class);

        GosPackageStatePm.getEditor(pmi, packageName, user.getIdentifier())
                .addFlags(gosPsFlagSuppressNotif)
                .apply();
    }

    protected static void cancelNotif(Context ctx, int uid, Class<? extends AppSwitch> aswClass) {
        synchronized (lastShownTracker) {
            NotifRecord notifRecord = lastShownTracker.get(uid).get(aswClass);
            if (notifRecord != null) {
                ctx.getSystemService(NotificationManager.class).cancelAsUser(null,
                        notifRecord.notifId, UserHandle.getUserHandleForUid(uid));
            }
        }
    }
}
