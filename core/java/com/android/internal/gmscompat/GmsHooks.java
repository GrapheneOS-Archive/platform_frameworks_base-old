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
    private static final String FGS_GROUP_ID = "gmscompat_fgs_group";
    private static final String FGS_CHANNEL_ID = "gmscompat_fgs_channel";
    private static final int FGS_NOTIFICATION_ID = 529977835;
    private static boolean fgsChannelCreated = false;

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

    private static void createFgsChannel(Context context) {
        if (fgsChannelCreated) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannelGroup group = new NotificationChannelGroup(FGS_GROUP_ID,
                context.getText(R.string.foreground_service_gmscompat_group));
        notificationManager.createNotificationChannelGroup(group);

        CharSequence name = context.getText(R.string.foreground_service_gmscompat_channel);
        NotificationChannel channel = new NotificationChannel(FGS_CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_LOW);
        channel.setGroup(FGS_GROUP_ID);
        channel.setDescription(context.getString(R.string.foreground_service_gmscompat_channel_desc));
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);

        fgsChannelCreated = true;
    }

    // Post notification on foreground service start
    // ActivityThread#handleCreateService(CreateServiceData)
    public static void attachService(Service service) {
        // Isolated processes (e.g. WebView) don't have access to NotificationManager. They don't
        // need a foreground notification anyway, so bail out early.
        if (!GmsCompat.isEnabled() || Process.isIsolated()) {
            return;
        }

        // Channel
        createFgsChannel(service);

        // Intent: notification channel settings
        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, service.getPackageName());
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, FGS_CHANNEL_ID);
        PendingIntent pi = PendingIntent.getActivity(service, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Notification
        CharSequence appName = service.getApplicationInfo().loadLabel(service.getPackageManager());
        Notification notification = new Notification.Builder(service, FGS_CHANNEL_ID)
                .setSmallIcon(service.getApplicationInfo().icon)
                .setContentTitle(service.getString(R.string.app_running_notification_title, appName))
                .setContentText(service.getText(R.string.foreground_service_gmscompat_notif_desc))
                .setContentIntent(pi)
                .build();

        Log.d(TAG, "Posting notification for service: " + service.getClass().getName());
        service.startForeground(FGS_NOTIFICATION_ID, notification);
    }

    // GMS tries to clean up its own notification channels periodically.
    // Don't let it delete the FGS shim channel because that throws an exception and crashes GMS.
    // NotificationManager#deleteNotificationChannel(String)
    public static boolean skipDeleteNotificationChannel(String channelId) {
        return GmsCompat.isEnabled() && FGS_CHANNEL_ID.equals(channelId);
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
        GmsCompat.initChangeEnableStates();

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

    // Request user action for package install sessions
    // LoadedApk.ReceiverDispatcher.InnerReceiver#performReceive(Intent, int, String, Bundle, boolean, boolean, int)
    public static boolean performReceive(Intent intent) {
        if (!GmsCompat.isEnabled()) {
            return false;
        }

        // Validate - we only want to handle user action requests
        if (!(intent.hasExtra(PackageInstaller.EXTRA_SESSION_ID) &&
                intent.hasExtra(PackageInstaller.EXTRA_STATUS) &&
                intent.hasExtra(Intent.EXTRA_INTENT))) {
            return false;
        }
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) !=
                PackageInstaller.STATUS_PENDING_USER_ACTION) {
            return false;
        }

        Application app = ActivityThread.currentApplication();
        if (app == null) {
            return false;
        }

        // Use the intent
        Log.i(TAG, "Requesting user confirmation for package install session");
        Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        // Make it work with the Application context
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // TODO: post notification if app is in the background
        app.startActivity(confirmIntent);

        // Don't dispatch it, otherwise Play Store abandons the session
        return true;
    }

    // Redirect cross-user interactions to current user
    // ContextImpl#sendOrderedBroadcastAsUser
    // ContextImpl#sendBroadcastAsUser
    public static UserHandle getUserHandle(UserHandle user) {
        return GmsCompat.isEnabled() ? Process.myUserHandle() : user;
    }
}
