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
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityThread;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.compat.gms.GmsCompat;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.webkit.WebView;

import com.android.internal.R;
import com.android.internal.gmscompat.dynamite.GmsDynamiteHooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * API shims for Google Play compatibility. Hooks that are more complicated than a simple
 * constant return value should be delegated to this class for easier maintenance.
 *
 * @hide
 */
public final class GmsHooks {
    private static final String TAG = "GmsCompat/Hooks";

    // Foreground service notifications
    // id was chosen when fgs was the only channel
    static final String COMPAT_GROUP_ID = "gmscompat_fgs_group";
    private static volatile boolean notificationChannelsCreated;

    // Static only
    private GmsHooks() { }

    static Notification.Builder obtainNotificationBuilder(Context context, String channelId) {
        if (!notificationChannelsCreated) {
            createNotificationChannels(context);
            notificationChannelsCreated = true;
        }

        return new Notification.Builder(context, channelId);
    }

    private static void createNotificationChannels(Context context) {
        if (!GmsCompat.isPlayStore()) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannelGroup group = new NotificationChannelGroup(COMPAT_GROUP_ID,
                context.getText(R.string.gmscompat_channel_group));
        manager.createNotificationChannelGroup(group);

        ArrayList<NotificationChannel> channels = new ArrayList<>(7);
        PlayStoreHooks.createNotificationChannel(context, channels);

        for (NotificationChannel channel : channels) {
            channel.setGroup(COMPAT_GROUP_ID);
        }

        manager.createNotificationChannels(channels);
    }

    // GMS tries to clean up its own notification channels periodically.
    // Don't let it delete any of compat channels because that throws an exception and crashes GMS.
    // NotificationManager#deleteNotificationChannel(String)
    public static boolean skipDeleteNotificationChannel(String channelId) {
        if (!GmsCompat.isEnabled()) {
            return false;
        }
        return PlayStoreHooks.PUA_CHANNEL_ID.equals(channelId);
    }

    /**
     * API shims
     */

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

    // Instrumentation#newApplication(ClassLoader, String, Context)
    public static void initApplicationBeforeOnCreate(Application app) {
        GmsCompat.maybeEnable(app);

        if (GmsCompat.isEnabled()) {
            String processName = Application.getProcessName();
            if (!app.getPackageName().equals(processName)) {
                // Fix RuntimeException: Using WebView from more than one process at once with the same data
                // directory is not supported. https://crbug.com/558377
                WebView.setDataDirectorySuffix("process-shim--" + processName);
            }

            if (!Process.isIsolated()) {
                GmsCompatApp.connect(app);
            } else {
                Log.d(TAG, "initApplicationBeforeOnCreate: isolated process " + Application.getProcessName());
            }
        } else if (GmsCompat.isDynamiteClient()) {
            GmsDynamiteHooks.initClientApp();
        }
    }

    static class RecentBinderPid implements Comparable<RecentBinderPid> {
        int pid;
        int uid;
        long lastSeen;
        volatile String[] packageNames; // lazily inited

        static final int MAX_MAP_SIZE = 50;
        static final int MAP_SIZE_TRIM_TO = 40;
        static final SparseArray<RecentBinderPid> map = new SparseArray(MAX_MAP_SIZE + 1);

        public int compareTo(RecentBinderPid b) {
            return Long.compare(b.lastSeen, lastSeen); // newest come first
        }
    }

    // Remember recent Binder peers to include them in the result of ActivityManager.getRunningAppProcesses()
    // Binder#execTransact(int, long, long, int)
    public static void onBinderTransaction(int pid, int uid) {
        SparseArray<RecentBinderPid> map = RecentBinderPid.map;
        synchronized (map) {
            RecentBinderPid rbp = map.get(pid);
            if (rbp != null) {
                if (rbp.uid != uid) { // pid was reused
                    rbp = null;
                }
            }
            if (rbp == null) {
                rbp = new RecentBinderPid();
                rbp.pid = pid;
                rbp.uid = uid;
                map.put(pid, rbp);
            }
            rbp.lastSeen = SystemClock.uptimeMillis();

            int mapSize = map.size();
            if (mapSize <= RecentBinderPid.MAX_MAP_SIZE) {
                return;
            }
            RecentBinderPid[] arr = new RecentBinderPid[mapSize];
            for (int i = 0; i < mapSize; ++i) {
                arr[i] = map.valueAt(i);
            }
            // sorted by lastSeen field in reverse order
            Arrays.sort(arr);
            map.clear();
            for (int i = 0; i < RecentBinderPid.MAP_SIZE_TRIM_TO; ++i) {
                RecentBinderPid e = arr[i];
                map.put(e.pid, e);
            }
        }
    }

    // In some cases (Play Games Services, Play {Asset, Feature} Delivery)
    // GMS relies on getRunningAppProcesses() to figure out whether its client is running.
    // This workaround is racy, because unprivileged apps don't know whether arbitrary pid is alive.
    // ActivityManager#getRunningAppProcesses()
    public static ArrayList<RunningAppProcessInfo> addRecentlyBoundPids(Context context,
                                                                        List<RunningAppProcessInfo> orig) {
        final RecentBinderPid[] binderPids;
        final int binderPidsCount;
        // copy to array to avoid long lock contention with Binder.execTransact(),
        // there are expensive getPackagesForUid() calls below
        {
            SparseArray<RecentBinderPid> map = RecentBinderPid.map;
            synchronized (map) {
                binderPidsCount = map.size();
                binderPids = new RecentBinderPid[binderPidsCount];
                for (int i = 0; i < binderPidsCount; ++i) {
                    binderPids[i] = map.valueAt(i);
                }
            }
        }
        PackageManager pm = context.getPackageManager();
        ArrayList<RunningAppProcessInfo> res = new ArrayList<>(orig.size() + binderPidsCount);
        res.addAll(orig);
        for (int i = 0; i < binderPidsCount; ++i) {
            RecentBinderPid rbp = binderPids[i];
            String[] pkgs = rbp.packageNames;
            if (pkgs == null) {
                pkgs = pm.getPackagesForUid(rbp.uid);
                if (pkgs == null || pkgs.length == 0) {
                    continue;
                }
                // this field is volatile
                rbp.packageNames = pkgs;
            }
            RunningAppProcessInfo pi = new RunningAppProcessInfo();
            // these fields are immutable after publication
            pi.pid = rbp.pid;
            pi.uid = rbp.uid;
            pi.processName = pkgs[0];
            pi.pkgList = pkgs;
            pi.importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            res.add(pi);
        }
        return res;
    }
}
