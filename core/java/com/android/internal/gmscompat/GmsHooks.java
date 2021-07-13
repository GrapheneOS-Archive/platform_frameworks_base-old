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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.compat.gms.GmsCompat;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * API shims for Google Play Services compatibility. Hooks that are more complicated than a simple
 * constant return value should be delegated to this class for easier maintenance.
 *
 * @hide
 */
public final class GmsHooks {
    private static final String TAG = "GmsCompat/Hooks";

    // Foreground service notifications
    private static final String FGS_CHANNEL_ID = "service_shim";
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

    private static void createFgsChannel(Service service) {
        if (fgsChannelCreated) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager)
                service.getSystemService(Context.NOTIFICATION_SERVICE);

        CharSequence name = service.getText(
                com.android.internal.R.string.foreground_service_gms_shim_category);
        NotificationChannel channel = new NotificationChannel(FGS_CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_LOW);
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
        // Notification
        PendingIntent pi = PendingIntent.getActivity(service, 100, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(service, FGS_CHANNEL_ID)
                .setSmallIcon(service.getApplicationInfo().icon)
                .setContentTitle(service.getApplicationInfo().loadLabel(service.getPackageManager()))
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
}
