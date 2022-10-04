/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.ext;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseLongArray;

import com.android.internal.R;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.server.LocalServices;

import java.util.UUID;

import static com.android.server.ext.SseUtils.addNotifAction;

public class MissingSpecialRuntimePermissionNotification {
    private static final String TAG = "SrPermissionNotif";

    private static final ArrayMap<String, SparseLongArray> lastShownTracker = new ArrayMap<>();

    public static void maybeShow(Context ctx, String permissionName, int uid, String packageName) {
        final long timestamp = SystemClock.uptimeMillis();

        synchronized (lastShownTracker) {
            SparseLongArray uids = lastShownTracker.get(permissionName);
            if (uids == null) {
                uids = new SparseLongArray();
                lastShownTracker.put(permissionName, uids);
            } else {
                long prevTs = uids.get(uid, 0);

                if (prevTs != 0 && (timestamp - prevTs) < 30_000L) {
                    // don't spam notifications for the same app and the same permission
                    return;
                }

                uids.put(uid, timestamp);
            }
        }

        final UserHandle user = UserHandle.of(UserHandle.getUserId(uid));

        var permManager = ctx.getSystemService(PermissionManager.class);

        if (permManager == null) {
            // might happen during bootup
            Slog.e(TAG, "PermissionManager is null");
            return;
        }

        final int permFlags = permManager.getPermissionFlags(packageName, permissionName, user);

        if ((permFlags & PackageManager.FLAG_PERMISSION_USER_SET) != 0) {
            return;
        }

        var nb = new Notification.Builder(ctx, SystemNotificationChannels.MISSING_PERMISSION);
        nb.setSmallIcon(R.drawable.stat_sys_warning);

        CharSequence appLabel = null;
        {
            ApplicationInfo appInfo = LocalServices.getService(PackageManagerInternal.class)
                    .getApplicationInfo(packageName, 0, Process.SYSTEM_UID, user.getIdentifier());
            if (appInfo != null) {
                appLabel = appInfo.loadLabel(ctx.getPackageManager());
            } else if (uid < Process.FIRST_APPLICATION_UID) {
                appLabel = packageName;
            }
            if (appLabel == null) {
                Slog.d(TAG, "appLabel is null; uid: " + uid + ", pkg: " + packageName
                        + ", perm: " + permissionName);
                return;
            }
        }
        nb.setContentTitle(ctx.getString(R.string.missing_sensors_permission_title, appLabel));

        nb.setContentText(ctx.getString(notifTextForPermission(permissionName)));
        {
            Intent intent = ctx.getPackageManager().buildRequestPermissionsIntent(new String[] { permissionName });
            intent.setAction(PackageManager.ACTION_REQUEST_PERMISSIONS_FOR_OTHER);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setIdentifier(UUID.randomUUID().toString());

            var pi = PendingIntent.getActivityAsUser(ctx, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE, null, user);
            nb.setContentIntent(pi);
        }
        nb.setAutoCancel(true);

        var args = new Bundle();
        args.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        args.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        args.putParcelable(Intent.EXTRA_USER, user);

        PendingIntent dontShowAgainPi = IntentReceiver.getPendingIntent(NotifActionReceiver.class,
                NotifActionReceiver::new, args, ctx);

        addNotifAction(ctx, dontShowAgainPi, R.string.notification_action_dont_show_again, nb);

        var notifManager = ctx.getSystemService(NotificationManager.class);
        if (notifManager == null) {
            // might happen during bootup
            Slog.e(TAG, "NotificationManager is null");
            return;
        }

        notifManager.notifyAsUser(null, notifIdForPermission(permissionName), nb.build(), user);
    }

    static class NotifActionReceiver extends IntentReceiver {
        @Override
        public void onReceive(Context ctx, Bundle args) {
            String packageName = args.getString(Intent.EXTRA_PACKAGE_NAME);
            String permissionName = args.getString(Intent.EXTRA_PERMISSION_NAME);
            var user = args.getParcelable(Intent.EXTRA_USER, UserHandle.class);

            final int flag = PackageManager.FLAG_PERMISSION_USER_SET;

            ctx.getSystemService(PermissionManager.class)
                    .updatePermissionFlags(packageName, permissionName, flag, flag, user);

            ctx.getSystemService(NotificationManager.class)
                    .cancelAsUser(null, notifIdForPermission(permissionName), user);
        }
    }

    private static int notifTextForPermission(String perm) {
        switch (perm) {
            case Manifest.permission.OTHER_SENSORS:
                return R.string.missing_sensors_permission_message;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static int notifIdForPermission(String perm) {
        switch (perm) {
            case Manifest.permission.OTHER_SENSORS:
                return SystemMessage.NOTE_MISSING_PERMISSION_OTHER_SENSORS;
            default:
                throw new IllegalArgumentException();
        }
    }
}
