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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityThread;
import android.app.Application;
import android.app.PendingIntent;
import android.app.compat.gms.GmsCompat;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.Downloads;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * API shims for GMS compatibility. Hooks that are more complicated than a simple
 * constant return value should be delegated to this class for easier maintenance.
 *
 * @hide
 */
public final class GmsHooks {
    private static final String TAG = "GmsCompat/Hooks";

    public static void init(Context ctx, String packageName) {
        String processName = Application.getProcessName();

        if (!packageName.equals(processName)) {
            // Fix RuntimeException: Using WebView from more than one process at once with the same data
            // directory is not supported. https://crbug.com/558377
            WebView.setDataDirectorySuffix("process-shim--" + processName);
        }

        if (GmsCompat.isPlayStore()) {
            PlayStoreHooks.init();
        }

        GmsCompatApp.connect(ctx, processName);
    }

    // ContextImpl#getSystemService(String)
    public static boolean isHiddenSystemService(String name) {
        // return true only for services that are null-checked
        switch (name) {
            case Context.CONTEXTHUB_SERVICE:
            case Context.WIFI_SCANNING_SERVICE:
            case Context.APP_INTEGRITY_SERVICE:
            // used for factory reset protection
            case Context.PERSISTENT_DATA_BLOCK_SERVICE:
            // used for updateable fonts
            case Context.FONT_SERVICE:
                return true;
        }
        return false;
    }

    // ApplicationPackageManager#hasSystemFeature(String, int)
    public static boolean isHiddenSystemFeature(String name) {
        switch (name) {
            // checked before accessing privileged UwbManager
            case "android.hardware.uwb":
                return true;
        }
        return false;
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
        String ssaid = Settings.Secure.getString(GmsCompat.appContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        String serial = ssaid.toUpperCase();
        Log.d(TAG, "Generating serial number from SSAID: " + serial);
        return serial;
    }

    // Only get package info for current user
    // ApplicationPackageManager#getInstalledPackages(int)
    // ApplicationPackageManager#getPackageInfo(VersionedPackage, int)
    // ApplicationPackageManager#getPackageInfoAsUser(String, int, int)
    public static int filterPackageInfoFlags(int flags) {
        if (GmsCompat.isEnabled()) {
            // Remove MATCH_ANY_USER flag to avoid permission denial
            flags &= ~PackageManager.MATCH_ANY_USER;
        }
        return flags;
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
    // GMS Core relies on getRunningAppProcesses() to figure out whether its client is running.
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

    // ContentResolver#query(Uri, String[], Bundle, CancellationSignal)
    public static Cursor interceptQuery(Uri uri, String[] projection) {
        String authority = uri.getAuthority();
        if (ContactsContract.AUTHORITY.equals(authority)
                // com.android.internal.telephony.IccProvider
                || "icc".equals(authority))
        {
            if (!GmsCompat.hasPermission(Manifest.permission.READ_CONTACTS)) {
                return new MatrixCursor(projection);
            }
        }
        return null;
    }

    // ContextImpl#startActivity(Intent, Bundle)
    public static boolean startActivity(Intent intent, Bundle options) {
        if (ActivityThread.currentActivityThread().hasAtLeastOneResumedActivity()) {
            return false;
        }

        // handle background activity starts, which normally require a privileged permission

        Context ctx = GmsCompat.appContext();

        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE, options);
        try {
            GmsCompatApp.iGms2Gca().startActivityFromTheBackground(ctx.getPackageName(), pendingIntent);
        } catch (RemoteException e) {
            GmsCompatApp.callFailed(e);
        }
        return true;
    }

    // ContentResolver#insert(Uri, ContentValues, Bundle)
    public static void filterContentValues(Uri url, ContentValues values) {
        if (values != null && Downloads.Impl.CONTENT_URI.equals(url)) {
            Integer otherUid = values.getAsInteger(Downloads.Impl.COLUMN_OTHER_UID);
            if (otherUid != null) {
                if (otherUid.intValue() != Process.SYSTEM_UID) {
                    throw new IllegalStateException("unexpected COLUMN_OTHER_UID " + otherUid);
                }
                // gated by the privileged ACCESS_DOWNLOAD_MANAGER_ADVANCED permission
                values.remove(Downloads.Impl.COLUMN_OTHER_UID);
            }
        }
    }

    private GmsHooks() {}
}
