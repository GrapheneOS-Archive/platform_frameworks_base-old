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
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityThread;
import android.app.Application;
import android.app.ApplicationErrorReport;
import android.app.BroadcastOptions;
import android.app.PendingIntent;
import android.app.compat.gms.GmsCompat;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.DeadSystemRuntimeException;
import android.os.PowerExemptionManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Downloads;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.webkit.WebView;

import com.android.internal.gmscompat.client.ClientPriorityManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

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

        Thread.setUncaughtExceptionPreHandler(new UncaughtExceptionPreHandler());
    }

    static class UncaughtExceptionPreHandler implements Thread.UncaughtExceptionHandler {
        final Thread.UncaughtExceptionHandler orig = Thread.getUncaughtExceptionPreHandler();

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            Context ctx = GmsCompat.appContext();

            ApplicationErrorReport aer = new ApplicationErrorReport();
            aer.type = ApplicationErrorReport.TYPE_CRASH;
            aer.crashInfo = new ApplicationErrorReport.ParcelableCrashInfo(e);

            ApplicationInfo ai = ctx.getApplicationInfo();
            aer.packageName = ai.packageName;
            aer.packageVersion = ai.longVersionCode;
            aer.processName = Application.getProcessName();

            // In some cases, GMS kills its process when it receives an uncaught exception, which
            // bypasses the standard crash handling infrastructure.
            // Send the report to GmsCompatApp before GMS receives the uncaughtException() callback.

            if (!isDeadSystemRuntimeException(e)) {
                try {
                    GmsCompatApp.iGms2Gca().onUncaughtException(aer);
                } catch (RemoteException re) {
                    Log.e(TAG, "", re);
                }
            }

            if (orig != null) {
                orig.uncaughtException(t, e);
            }
        }

        // in some cases a DeadSystemRuntimeException is thrown despite the system being actually
        // still alive, likely when the Binder buffer space is full and a binder transaction with
        // system_server fails.
        // See https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/core/jni/android_util_Binder.cpp;l=894
        // (DeadObjectException is rethrown as DeadSystemRuntimeException by
        // android.os.RemoteException#rethrowFromSystemServer())
        private static boolean isDeadSystemRuntimeException(Throwable e) {
            for (;;) {
                if (e == null) {
                    return false;
                }

                if (e instanceof DeadSystemRuntimeException) {
                    return true;
                }

                e = e.getCause();
            }
        }
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
                if (UserHandle.getUserId(rbp.uid) != UserHandle.myUserId()) {
                    // SystemUI from userId 0 sends callbacks to apps from all userIds via
                    // android.window.IOnBackInvokedCallback.
                    // getPackagesForUid() will fail due to missing privileged
                    // INTERACT_ACROSS_USERS permission
                    continue;
                }

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
                if (projection == null) {
                    projection = new String[] { BaseColumns._ID };
                }
                return new MatrixCursor(projection);
            }
        }
        return null;
    }

    // ContentResolver#query(Uri, String[], Bundle, CancellationSignal)
    public static Cursor maybeModifyQueryResult(Uri uri, Cursor origCursor) {
        Consumer<ArrayMap<String, String>> mutator = null;

        if (GmsCompat.isGmsCore()) {
            if ("content://com.google.android.gms.phenotype/com.google.android.gms.fido".equals(uri.toString())) {
                mutator = map -> {
                    String key = "Fido2ApiKnownBrowsers__fingerprints";
                    String origValue = map.get(key);

                    if (origValue == null) {
                        Log.w(TAG, key + " not found");
                        return;
                    }

                    String newValue = origValue + ",C6ADB8B83C6D4C17D292AFDE56FD488A51D316FF8F2C11C5410223BFF8A7DBB3";
                    map.put(key, newValue);
                };
            } else if ("content://com.google.android.gms.phenotype/com.google.android.gms.enpromo".equals(uri.toString())) {
                mutator = map -> {
                    // enpromo is a GmsCore module that shows a notification that prompts the user
                    // to enable Exposure Notifications (en).
                    // It needs location access to determine which location-specific app needs to be
                    // installed for Exposure Notifications to function.
                    // Location permission can't be revoked for privileged GmsCore, it being revoked
                    // leads to a crash (location access being disabled is handled correctly, but
                    // spoofing it would break other functionality)
                    if (!GmsCompat.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        map.put("PromoFeature__enabled", "0");
                    }
                };
            }
        } else if (GmsCompat.isPlayStore()) {
            if ("content://com.google.android.gsf.gservices/prefix".equals(uri.toString())) {
                mutator = map -> {
                    // same as in PackageInstaller#createSession
                    final String prefPrefix = "gmscompat_play_store_unrestrict_pkg_";
                    ContentResolver cr = GmsCompat.appContext().getContentResolver();

                    // Disables auto updates of GMS Core, not of all GMS components.
                    // Updates that don't change version of GMS Core (eg downloading a new APK split
                    // for new device locale) and manual updates are allowed
                    if (Settings.Secure.getInt(cr, prefPrefix + GmsInfo.PACKAGE_GMS_CORE, 0) != 1) {
                        map.put("finsky.AutoUpdateCodegen__gms_auto_update_enabled", "0");
                    }

                    if (Settings.Secure.getInt(cr, prefPrefix + GmsInfo.PACKAGE_PLAY_STORE, 0) != 1) {
                        // prevent auto-updates of Play Store, self-update files are still downloaded
                        map.put("finsky.SelfUpdate__do_not_install", "1");
                        // don't re-download update files after failed self-update
                        map.put("finsky.SelfUpdate__self_update_download_max_valid_time_ms", "" + Long.MAX_VALUE);
                    }

                    // Disable auto-deploying of packages (eg of AR Core ("Google Play Services for AR")).
                    // By default, Play Store attempts to auto-deploy packages only if the device has
                    // at least 1 GiB of free storage space.
                    map.put("finsky.AutoUpdatePolicies__auto_deploy_disk_space_threshold_bytes",
                            "" + Long.MAX_VALUE);
                };
            }
        }

        if (mutator != null) {
            return modifyKvCursor(origCursor, mutator);
        }

        return null;
    }

    private static Cursor modifyKvCursor(Cursor origCursor, Consumer<ArrayMap<String, String>> mutator) {
        final int keyIndex = 0;
        final int valueIndex = 1;
        final int projectionLength = 2;

        String[] projection = origCursor.getColumnNames();

        boolean expectedProjection = projection != null && projection.length == projectionLength
                && "key".equals(projection[keyIndex]) && "value".equals(projection[valueIndex]);

        if (!expectedProjection) {
            Log.e(TAG, "unexpected projection " + Arrays.toString(projection), new Throwable());
            return null;
        }

        ArrayMap<String, String> map = new ArrayMap<>(origCursor.getColumnCount() + 10);

        try (Cursor orig = origCursor) {
            while (orig.moveToNext()) {
                String key = orig.getString(keyIndex);
                String value = orig.getString(valueIndex);

                map.put(key, value);
            }
        }

        mutator.accept(map);

        final int mapSize = map.size();
        MatrixCursor result = new MatrixCursor(projection, mapSize);

        for (int i = 0; i < mapSize; ++i) {
            Object[] row = new Object[projectionLength];
            row[keyIndex] = map.keyAt(i);
            row[valueIndex] = map.valueAt(i);

            result.addRow(row);
        }

        return result;
    }

    // Instrumentation#execStartActivity(Context, IBinder, IBinder, Activity, Intent, int, Bundle)
    public static void onActivityStart(int resultCode, Intent intent, Bundle options) {
        if (resultCode != ActivityManager.START_ABORTED) {
            return;
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
    }

    // Activity#onCreate(Bundle)
    public static void activityOnCreate(Activity activity) {
        if (GmsCompat.isGmsCore()) {
            String className = activity.getClass().getName();
            if ("com.google.android.gms.nearby.sharing.ShareSheetActivity".equals(className)) {
                if (!hasNearbyDevicesPermission()) {
                    try {
                        GmsCompatApp.iGms2Gca().showGmsCoreMissingPermissionForNearbyShareNotification();
                    } catch (RemoteException e) {
                        GmsCompatApp.callFailed(e);
                    }
                }
            }
        }
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

    private static boolean hasNearbyDevicesPermission() {
        // "Nearby devices" permission grants
        // BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE and BLUETOOTH_SCAN, checking one is enough
        return GmsCompat.hasPermission(Manifest.permission.BLUETOOTH_SCAN);
    }

    // NfcAdapter#enable()
    public static void enableNfc() {
        if (ActivityThread.currentActivityThread().hasAtLeastOneResumedActivity()) {
            Intent i = new Intent(Settings.ACTION_NFC_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            GmsCompat.appContext().startActivity(i);
        }
    }

    // ContextImpl#sendBroadcast
    // ContextImpl#sendOrderedBroadcast
    // ContextImpl#sendBroadcastAsUser
    // ContextImpl#sendOrderedBroadcastAsUser
    public static Bundle filterBroadcastOptions(Intent intent, Bundle options) {
        if (options == null) {
            return null;
        }

        String targetPkg = intent.getPackage();

        if (targetPkg == null) {
            ComponentName cn = intent.getComponent();
            if (cn != null) {
                targetPkg = cn.getPackageName();
            }
        }

        if (targetPkg == null) {
            return options;
        }

        return filterBroadcastOptions(options, targetPkg);
    }

    // PendingIntent#send
    public static Bundle filterBroadcastOptions(Bundle options, String targetPkg) {
        BroadcastOptions bo = new BroadcastOptions(options);

        if (bo.getTemporaryAppAllowlistType() == PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE) {
            return options;
        }
        // handle privileged BroadcastOptions#setTemporaryAppAllowlist() that is used for
        // high-priority FCM pushes, location updates via PendingIntent,
        // geofencing and activity detection notifications etc

        long duration = bo.getTemporaryAppAllowlistDuration();

        if (duration <= 0) {
            return options;
        }

        ClientPriorityManager.raiseToForeground(targetPkg, duration,
                bo.getTemporaryAppAllowlistReason(), bo.getTemporaryAppAllowlistReasonCode());

        bo.setTemporaryAppAllowlist(0, PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE,
                PowerExemptionManager.REASON_UNKNOWN, null);
        return bo.toBundle();
    }

    private GmsHooks() {}
}
