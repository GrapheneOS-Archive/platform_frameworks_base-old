/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.gmscompat;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.IActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.provider.Downloads;

import com.android.internal.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

public final class PlayStoreHooks {

    private static final String TAG = "GmsCompat/PlayStore";

    // Pending user action notifications
    static final String PUA_CHANNEL_ID = "gmscompat_playstore_pua_channel";
    private static final int PUA_NOTIFICATION_ID = 429825706;

    private PlayStoreHooks() {}

    static void createNotificationChannel(Context context, ArrayList<NotificationChannel> dest) {
        CharSequence name = context.getText(R.string.gmscompat_notif_channel_action_required);
        NotificationChannel c = new NotificationChannel(PUA_CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_LOW);
        c.setShowBadge(false);

        dest.add(c);
    }

    // Play Store doesn't handle PENDING_USER_ACTION status from PackageInstaller
    // PackageInstaller.Session#commit(IntentSender)
    // PackageInstaller#uninstall(VersionedPackage, int, IntentSender)
    public static IntentSender wrapPackageInstallerStatusReceiver(IntentSender statusReceiver) {
        Context context = ActivityThread.currentApplication();
        Objects.requireNonNull(context);
        return PackageInstallerStatusForwarder.wrap(context, statusReceiver).getIntentSender();
    }

    static class PackageInstallerStatusForwarder extends BroadcastReceiver {
        private Context context;
        private IntentSender target;

        private static final AtomicLong lastId = new AtomicLong();

        static PendingIntent wrap(Context context, IntentSender target) {
            PackageInstallerStatusForwarder sf = new PackageInstallerStatusForwarder();
            sf.context = context;
            sf.target = target;

            String intentAction = context.getPackageName()
                + "." + PackageInstallerStatusForwarder.class.getName() + "."
                + lastId.getAndIncrement();
            context.registerReceiver(sf, new IntentFilter(intentAction));

            return PendingIntent.getBroadcast(context, 0, new Intent(intentAction),
                    PendingIntent.FLAG_CANCEL_CURRENT |
                        PendingIntent.FLAG_MUTABLE);
        }

        public void onReceive(Context br_context, Intent intent) {
            String statusKey = PackageInstaller.EXTRA_STATUS;
            if (!intent.hasExtra(statusKey)) {
                throw new IllegalStateException("no EXTRA_STATUS in intent " + intent);
            }
            int status = intent.getIntExtra(statusKey, 0);

            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                boolean foreground = isForeground(Process.myUid(), Process.myPid());

                if (foreground) {
                    context.startActivity(confirmationIntent);
                } else {
                    // allow multiple PUA notifications
                    int id = PUA_NOTIFICATION_ID + (((int) lastId.get()) & 0xffff);

                    PendingIntent pi = PendingIntent.getActivity(context, id, confirmationIntent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

                    Notification notification =
                        GmsHooks.obtainNotificationBuilder(context, PUA_CHANNEL_ID)
                        // TODO better icon
                        .setSmallIcon(context.getApplicationInfo().icon)
                        .setContentTitle(context.getText(R.string.gmscompat_notif_channel_action_required))
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build();

                    NotificationManager nm = context.getSystemService(NotificationManager.class);
                    nm.notify(id, notification);
                }
                // confirmationIntent has a PendingIntent to this instance, don't unregister yet
                return;
            }

            context.unregisterReceiver(this);

            try {
                target.sendIntent(context, 0, intent, null, null);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        }
    }

    static boolean isForeground(int uid, int pid) {
        final List<ActivityManager.RunningAppProcessInfo> procs;
        try {
            // returns only processes that belong to the current app
            procs = ActivityManager.getService().getRunningAppProcesses();
            Objects.requireNonNull(procs);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
        for (int i = 0; i < procs.size(); i++) {
            ActivityManager.RunningAppProcessInfo proc = procs.get(i);
            if (proc.pid == pid && proc.uid == uid
                    && proc.importance == IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    // Request user action to uninstall a package
    // ApplicationPackageManager#deletePackage(String, IPackageDeleteObserver, int)
    public static void deletePackage(Context context, PackageManager pm, String packageName, IPackageDeleteObserver observer, int flags) {
        if (flags != 0) {
            throw new IllegalStateException("unexpected flags: " + flags);
        }
        PendingIntent pi = UninstallStatusForwarder.getPendingIntent(context, packageName, observer);
        // will call PackageInstallerStatusForwarder,
        // no need to handle confirmation in UninstallStatusForwarder
        pm.getPackageInstaller().uninstall(packageName, pi.getIntentSender());
    }

    static class UninstallStatusForwarder extends BroadcastReceiver {
        private Context context;
        private String packageName;
        private IPackageDeleteObserver target;

        private static final AtomicLong lastId = new AtomicLong();

        static PendingIntent getPendingIntent(Context context, String packageName, IPackageDeleteObserver target) {
            UninstallStatusForwarder sf = new UninstallStatusForwarder();
            sf.context = context;
            sf.packageName = packageName;
            sf.target = target;

            String intentAction = context.getPackageName()
                + "." + UninstallStatusForwarder.class.getName() + "."
                + lastId.getAndIncrement();

            context.registerReceiver(sf, new IntentFilter(intentAction));

            return PendingIntent.getBroadcast(context, 0, new Intent(intentAction),
                    PendingIntent.FLAG_CANCEL_CURRENT |
                        PendingIntent.FLAG_ONE_SHOT |
                        PendingIntent.FLAG_MUTABLE);
        }

        public void onReceive(Context br_context, Intent intent) {
            context.unregisterReceiver(this);

            // EXTRA_STATUS returns PackageInstaller constant,
            // EXTRA_LEGACY_STATUS returns PackageManager constant
            String statusKey = PackageInstaller.EXTRA_LEGACY_STATUS;
            if (!intent.hasExtra(statusKey)) {
                throw new IllegalStateException("no EXTRA_LEGACY_STATUS in intent " + intent);
            }

            int status = intent.getIntExtra(statusKey, 0);
            try {
                target.packageDeleted(packageName, status);
            } catch (RemoteException e) {
                throw e.rethrowAsRuntimeException();
            }
        }
    }

    // Called during self-update sequence because PackageManager requires
    // the restricted CLEAR_APP_CACHE permission
    // ApplicationPackageManager#freeStorageAndNotify(String, long, IPackageDataObserver)
    public static void freeStorageAndNotify(Context context, String volumeUuid, long idealStorageSize,
            IPackageDataObserver observer) {
        if (volumeUuid != null) {
            throw new IllegalStateException("unexpected volumeUuid " + volumeUuid);
        }
        StorageManager sm = context.getSystemService(StorageManager.class);
        boolean success = false;
        try {
            sm.allocateBytes(StorageManager.UUID_DEFAULT, idealStorageSize);
            success = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            // same behavior as PackageManagerService#freeStorageAndNotify()
            String packageName = null;
            observer.onRemoveCompleted(packageName, success);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    // Called during self-update sequence
    // ContentResolver#insert(Uri, ContentValues, Bundle)
    public static void filterContentValues(Uri url, ContentValues values) {
        if (values != null && "content://downloads/my_downloads".equals(url.toString())) {
            // gated by the restricted ACCESS_DOWNLOAD_MANAGER_ADVANCED permission
            String otherUid = Downloads.Impl.COLUMN_OTHER_UID;
            if (values.containsKey(otherUid)) {
                int v = values.getAsInteger(otherUid).intValue();
                if (v != 1000) {
                    throw new IllegalStateException("unexpected COLUMN_OTHER_UID " + v);
                }
                values.remove(otherUid);
            }
        }
    }
}
