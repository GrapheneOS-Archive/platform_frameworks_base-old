/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.ext;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.compat.gms.GmsCompat;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.R;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.server.pm.pkg.GosPackageStatePm;

import static com.android.server.ext.SseUtils.notifAction;

public class RelaxAppHardeningNotification {
    private static final int NOTIFICATION_ID = com.android.internal.messages.nano.SystemMessageProto
            .SystemMessage.NOTE_RELAX_APP_HARDENING;

    private static boolean registeredReceiver;

    private static volatile long lastUserRequestedAppKill;

    public static void onUserRequestedAppKill() {
        lastUserRequestedAppKill = SystemClock.uptimeMillis();
    }

    // ActivityManagerService#appDiedLocked()
    // Task#finishTopCrashedActivityLocked()
    public static void maybeShow(ApplicationInfo ai) {
        // called from the critical section, schedule on background thread
        var sse = SystemServerExt.get();
        sse.bgHandler.post(() -> maybeShow(sse, ai));
    }

    static void maybeShow(SystemServerExt sse, ApplicationInfo ai) {
        if (!GosPackageState.eligibleForRelaxHardeningFlag(ai)) {
            return;
        }

        if ((SystemClock.uptimeMillis() - lastUserRequestedAppKill) < 1000L) {
            return;
        }

        final String packageName = ai.packageName;
        final int userId = UserHandle.getUserId(ai.uid);

        if (GmsCompat.isGmsApp(packageName, userId)) {
            return;
        }

        final UserHandle user = UserHandle.of(userId);

        GosPackageStatePm state = GosPackageStatePm.get(sse.packageManager, packageName, userId);

        if (state != null &&
                (state.flags & (GosPackageState.FLAG_ENABLE_COMPAT_VA_39_BIT
                        | GosPackageState.FLAG_DISABLE_HARDENED_MALLOC
                        | GosPackageState.FLAG_DO_NOT_SHOW_RELAX_APP_HARDENING_NOTIFICATION)) != 0)
        {
            return;
        }

        var ctx = sse.context;

        var nb = new Notification.Builder(ctx, SystemNotificationChannels.RELAX_APP_HARDENING);
        nb.setSmallIcon(R.drawable.stat_sys_warning);

        var appLabel = ai.loadLabel(ctx.getPackageManager());
        nb.setContentTitle(ctx.getString(R.string.relax_app_hardening_notification_title, appLabel));

        nb.setContentText(ctx.getString(R.string.relax_app_hardening_notification_message));
        nb.setStyle(new Notification.BigTextStyle());
        {
            var packageUri = Uri.fromParts("package", packageName, null);
            var appInfoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
            appInfoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            var args = new Bundle();
            // :settings constants aren't exposed by Settings as part of its API, but are used in
            // multiple places in the OS
            args.putString(":settings:fragment_args_key", "app_relax_hardening");
            appInfoIntent.putExtra(":settings:show_fragment_args", args);

            var pi = PendingIntent.getActivityAsUser(ctx, 0, appInfoIntent,
                    PendingIntent.FLAG_IMMUTABLE, null, user);
            nb.setContentIntent(pi);
        }
        nb.setAutoCancel(true);
        {
            var i = doNotShowAgainReceiver.getIntentTemplate();
            i.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
            i.putExtra(Intent.EXTRA_USER, user);
            nb.addAction(notifAction(i, R.string.notification_action_dont_show_again));
        }
        ctx.getSystemService(NotificationManager.class)
            .notifyAsUser(null, NOTIFICATION_ID, nb.build(), user);
    }

    private static final PendingActionReceiver doNotShowAgainReceiver = new PendingActionReceiver(intent -> {
        var pkg = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        var user = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);

        var sse = SystemServerExt.get();

        GosPackageStatePm.getEditor(sse.packageManager, pkg, user.getIdentifier())
            .addFlags(GosPackageState.FLAG_DO_NOT_SHOW_RELAX_APP_HARDENING_NOTIFICATION)
            .apply();

        sse.context.getSystemService(NotificationManager.class)
                .cancelAsUser(null, NOTIFICATION_ID, user);
    });
}
