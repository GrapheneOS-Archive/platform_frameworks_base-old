package com.android.server.ext;

import static com.android.server.ext.SseUtils.addNotifAction;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.clipboard.ClipboardManagerInternal;
import android.content.pm.ApplicationInfo;
import android.ext.SettingsIntents;
import android.ext.settings.app.AswClipboardRead;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.R;
import com.android.server.LocalServices;

import java.util.Set;

public class ClipboardReadNotification extends AppSwitchNotificationBase {
    private static final String TAG = "ClipboardAccessNotification";

    public final int deviceId;
    @Nullable public final ApplicationInfo primaryClipAppInfo;

    @Nullable
    public static ClipboardReadNotification maybeCreate(Context ctx, int deviceId,
                                                        String firstPackageName, int packageUid,
                                                        @Nullable String primaryClipPackage,
                                                        int primaryClipUid) {
        ApplicationInfo appInfo = getAppInfo(firstPackageName, packageUid);
        if (appInfo == null) {
            Slog.d(TAG, "appInfo is null");
            return null;
        }

        ApplicationInfo primaryClipAppInfo = null;
        if (primaryClipPackage != null) {
            primaryClipAppInfo = getAppInfo(primaryClipPackage, primaryClipUid);
            if (primaryClipAppInfo == null) {
                Slog.d(TAG, "primaryClipAppInfo is null");
            }
        }

        return new ClipboardReadNotification(ctx, deviceId, appInfo, primaryClipAppInfo);
    }

    public static ClipboardReadNotification create(Context ctx, int deviceId,
                                                   ApplicationInfo appInfo,
                                                   @Nullable ApplicationInfo primaryClipAppInfo) {
        return new ClipboardReadNotification(ctx, deviceId, appInfo, primaryClipAppInfo);
    }

    protected ClipboardReadNotification(Context ctx, int deviceId, ApplicationInfo appInfo,
                                        @Nullable ApplicationInfo primaryClipAppInfo) {
        super(ctx, appInfo, AswClipboardRead.I);
        this.deviceId = deviceId;
        this.primaryClipAppInfo = primaryClipAppInfo;
    }

    /** uids for which notification was shown */
    private static final Set<Integer> notifiedUids = new ArraySet<>();

    public static void cancelAll(Context ctx) {
        synchronized (notifiedUids) {
            for (int uid : notifiedUids) {
                cancelNotif(ctx, uid, AswClipboardRead.class);
            }
            notifiedUids.clear();
        }
    }

    @Override
    protected long getSameUidNotifRateLimit() {
        return 5_000; // ms
    }

    @Override
    protected void modifyNotification(Notification.Builder nb, int notifId) {
        final Context ctx = context;

        CharSequence appLabel = getAppLabel(ctx, appInfo);
        nb.setContentTitle(ctx.getString(R.string.notif_clipboard_read_title, appLabel));

        String contentText;
        if (primaryClipAppInfo == null) {
            contentText = ctx.getString(R.string.notif_text_tap_to_open_settings);
        } else {
            contentText = ctx.getString(R.string.notif_clipboard_read_content_text,
                    getAppLabel(ctx, primaryClipAppInfo));
        }
        nb.setContentText(contentText);

        {
            Intent settingsIntent = SettingsIntents.getAppIntent(SettingsIntents.APP_CLIPBOARD,
                    pkgName);
            PendingIntent pi = PendingIntent.getActivityAsUser(ctx, 0, settingsIntent,
                    PendingIntent.FLAG_IMMUTABLE, null, userHandle);
            nb.setContentIntent(pi);
        }
        nb.setAutoCancel(true);

        {
            Bundle args = getDefaultNotifArgs(notifId);
            PendingIntent cancelPi = IntentReceiver.getPendingIntent(CancelActionReceiver.class,
                    CancelActionReceiver::new, args, ctx);
            addNotifAction(ctx, cancelPi, R.string.cancel, nb);
        }

        if (deviceId == Context.DEVICE_ID_DEFAULT) {
            Bundle args = getDefaultNotifArgs(notifId);
            PendingIntent allowOncePi = IntentReceiver.getPendingIntent(
                    AllowOnceActionReceiver.class, AllowOnceActionReceiver::new, args, ctx);
            addNotifAction(ctx, allowOncePi, R.string.notif_action_allow_once, nb);
        }
    }

    @Override
    protected void onNotificationShown() {
        synchronized (notifiedUids) {
            notifiedUids.add(appInfo.uid);
        }
    }

    static class CancelActionReceiver extends NotifActionReceiver {
        @Override
        public void onReceive(Context ctx, String packageName, UserHandle user) {
            // do nothing, notification is already cancelled by super class
        }
    }

    static class AllowOnceActionReceiver extends NotifActionReceiver {
        @Override
        public void onReceive(Context ctx, String packageName, UserHandle user) {
            LocalServices.getService(ClipboardManagerInternal.class)
                    .setAllowOneTimeAccess(packageName, user.getIdentifier());
        }
    }
}
