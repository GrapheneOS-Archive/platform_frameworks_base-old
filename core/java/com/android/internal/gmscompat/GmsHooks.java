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

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.compat.gms.GmsCompat;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebView;

import com.android.internal.R;
import com.android.internal.gmscompat.dynamite.GmsDynamiteHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * API shims for Google Play Services compatibility. Hooks that are more complicated than a simple
 * constant return value should be delegated to this class for easier maintenance.
 *
 * @hide
 */
public final class GmsHooks {
    private static final String TAG = "GmsCompat/Hooks";

    // Foreground service notifications
    // id was chosen when fgs was the only channel
    static final String COMPAT_GROUP_ID = "gmscompat_fgs_group";
    private static final String FGS_CHANNEL_ID = "gmscompat_fgs_channel";
    private static final int FGS_NOTIFICATION_ID = 529977835;
    private static boolean notificationChannelsCreated;

    // Static only
    private GmsHooks() { }


    /*
     * Foreground service notifications to keep GMS services alive
     */

    // Make all services foreground to keep them alive
    // ContextImpl#startService(Intent)
    public static ComponentName startService(Context context, Intent service) {
        return context.startForegroundService(service);
    }

    static Notification.Builder obtainNotificationBuilder(Context context, String channelId) {
        if (!notificationChannelsCreated) {
            createNotificationChannels(context);
            notificationChannelsCreated = true;
        }
        return new Notification.Builder(context, channelId);
    }

    private static void createNotificationChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannelGroup group = new NotificationChannelGroup(COMPAT_GROUP_ID,
                context.getText(R.string.gmscompat_channel_group));
        manager.createNotificationChannelGroup(group);

        ArrayList<NotificationChannel> channels = new ArrayList<>(7);
        {
            CharSequence name = context.getText(R.string.foreground_service_gmscompat_channel);
            NotificationChannel c = new NotificationChannel(FGS_CHANNEL_ID, name,
                    NotificationManager.IMPORTANCE_LOW);
            c.setDescription(context.getString(R.string.foreground_service_gmscompat_channel_desc));
            c.setShowBadge(false);
            channels.add(c);
        }
        if (GmsCompat.isPlayStore()) {
            PlayStoreHooks.createNotificationChannel(context, channels);
        }

        for (int i = 0; i < channels.size(); ++i) {
            channels.get(i).setGroup(COMPAT_GROUP_ID);
        }

        manager.createNotificationChannels(channels);
    }

    // Post notification on foreground service start
    // ActivityThread#handleCreateService(CreateServiceData)
    public static void attachService(Service service) {
        // Isolated processes (e.g. WebView) don't have access to NotificationManager. They don't
        // need a foreground notification anyway, so bail out early.
        if (!GmsCompat.isEnabled() || Process.isIsolated()) {
            return;
        }

        // Intent: notification channel settings
        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, service.getPackageName());
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, FGS_CHANNEL_ID);
        PendingIntent pi = PendingIntent.getActivity(service, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Notification
        CharSequence appName = service.getApplicationInfo().loadLabel(service.getPackageManager());
        Notification notification = obtainNotificationBuilder(service, FGS_CHANNEL_ID)
                .setSmallIcon(service.getApplicationInfo().icon)
                .setContentTitle(service.getString(R.string.app_running_notification_title, appName))
                .setContentText(service.getText(R.string.foreground_service_gmscompat_notif_desc))
                .setContentIntent(pi)
                .build();

        Log.d(TAG, "Posting notification for service: " + service.getClass().getName());
        service.startForeground(FGS_NOTIFICATION_ID, notification);
    }

    // GMS tries to clean up its own notification channels periodically.
    // Don't let it delete any of compat channels because that throws an exception and crashes GMS.
    // NotificationManager#deleteNotificationChannel(String)
    public static boolean skipDeleteNotificationChannel(String channelId) {
        if (! GmsCompat.isEnabled()) {
            return false;
        }
        return FGS_CHANNEL_ID.equals(channelId) || PlayStoreHooks.PUA_CHANNEL_ID.equals(channelId);
    }

    /**
     * API shims
     */

    // Report a single user on the system
    // UserManager#getSerialNumbersOfUsers(boolean)
    public static long[] getSerialNumbersOfUsers(UserManager userManager) {
        return new long[] { userManager.getSerialNumberForUser(Process.myUserHandle()) };
    }

    // Current user is always active
    // ActivityManager#getCurrentUser()
    public static int getCurrentUser() {
        return Process.myUserHandle().getIdentifier();
    }

    /**
     * Use the per-app SSAID as a random serial number for SafetyNet. This doesn't necessarily make
     * pass, but at least it retusn a valid "failed" response and stops spamming device key
     * requests.
     *
     * This isn't a privacy risk because all unprivileged apps already have access to random SSAIDs.
     */
    // Build#getSerial()
    @SuppressLint("HardwareIds")
    public static String getSerial() {
        Application app = ActivityThread.currentApplication();
        if (app == null) {
            return Build.UNKNOWN;
        }

        String ssaid = Settings.Secure.getString(app.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        String serial = ssaid.toUpperCase();
        Log.d(TAG, "Generating serial number from SSAID: " + serial);
        return serial;
    }

    // Report no shared libraries
    // ApplicationPackageManager#getSharedLibrariesAsUser(int, int)
    public static List<SharedLibraryInfo> getSharedLibrariesAsUser() {
        // TODO: Report standard Pixel libraries?
        return Collections.emptyList();
    }

    // Only get package info for current user
    // ApplicationPackageManager#getPackageInfo(VersionedPackage, int)
    // ApplicationPackageManager#getPackageInfoAsUser(String, int, int)
    public static int getPackageInfoFlags(int flags) {
        if (!GmsCompat.isEnabled()) {
            return flags;
        }

        // Remove MATCH_ANY_USER flag to avoid permission denial
        return flags & ~PackageManager.MATCH_ANY_USER;
    }

    // Fix RuntimeException: Using WebView from more than one process at once with the same data
    // directory is not supported. https://crbug.com/558377
    // Instrumentation#newApplication(ClassLoader, String, Context)
    public static void initApplicationBeforeOnCreate(Application app) {
        GmsCompat.initChangeEnableStates(app);

        if (GmsCompat.isEnabled()) {
            String processName = Application.getProcessName();
            if (!app.getPackageName().equals(processName)) {
                WebView.setDataDirectorySuffix("process-shim--" + processName);
            }

            GmsDynamiteHooks.initGmsServerApp(app);
        } else if (GmsCompat.isDynamiteClient()) {
            GmsDynamiteHooks.initClientApp();
        }
    }

    // Redirect cross-user interactions to current user
    // ContextImpl#sendOrderedBroadcastAsUser
    // ContextImpl#sendBroadcastAsUser
    public static UserHandle getUserHandle(UserHandle user) {
        return GmsCompat.isEnabled() ? Process.myUserHandle() : user;
    }
}
