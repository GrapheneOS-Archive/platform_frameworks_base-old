package com.android.server.ext;

import android.annotation.CurrentTimeMillisLong;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.ext.ErrorReportUi;
import android.os.UserHandle;

import com.android.internal.R;
import com.android.internal.messages.nano.SystemMessageProto;
import com.android.internal.notification.SystemNotificationChannels;

class SystemJournalNotif {

    static void showCrash(Context ctx, String progName, String errorReport,
                          @CurrentTimeMillisLong long crashTimestamp) {
        var i = ErrorReportUi.createBaseIntent(ErrorReportUi.ACTION_CUSTOM_REPORT, errorReport);
        i.putExtra(Intent.EXTRA_TITLE, progName + " crash");
        i.putExtra(ErrorReportUi.EXTRA_SHOW_REPORT_BUTTON, true);

        showGeneric(ctx, crashTimestamp, ctx.getString(R.string.process_crash_notif_title, progName), i);
    }

    static void showGeneric(Context ctx, @CurrentTimeMillisLong long when, String notifTitle, Intent mainIntent) {
        var b = new Notification.Builder(ctx, SystemNotificationChannels.SYSTEM_JOURNAL);
        b.setSmallIcon(R.drawable.ic_error);
        b.setContentTitle(notifTitle);
        b.setContentText(ctx.getText(R.string.notif_text_tap_to_see_details));
        b.setAutoCancel(true);
        b.setWhen(when);
        b.setShowWhen(true);

        UserHandle user = UserHandle.of(ActivityManager.getCurrentUser());

        var pi = PendingIntent.getActivityAsUser(ctx, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT, null, user);

        b.setContentIntent(pi);

        var nm = ctx.getSystemService(NotificationManager.class);

        nm.notifyAsUser(null, createNotifId(), b.build(), user);
    }

    private static int notifIdSrc = SystemMessageProto.SystemMessage.NOTE_SYSTEM_JOURNAL_BASE;

    private static int createNotifId() {
        synchronized (SystemJournalNotif.class) {
            int res = notifIdSrc;
            notifIdSrc = (res == SystemMessageProto.SystemMessage.NOTE_SYSTEM_JOURNAL_MAX) ?
                    SystemMessageProto.SystemMessage.NOTE_SYSTEM_JOURNAL_BASE :
                    res + 1;
            return res;
        }
    }
}
