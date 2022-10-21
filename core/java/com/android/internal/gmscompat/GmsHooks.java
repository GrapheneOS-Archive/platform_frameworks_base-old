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
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityThread;
import android.app.Application;
import android.app.ApplicationErrorReport;
import android.app.BroadcastOptions;
import android.app.PendingIntent;
import android.app.RemoteServiceException;
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
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadSystemRuntimeException;
import android.os.Parcel;
import android.os.PowerExemptionManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Downloads;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.webkit.WebView;

import com.android.internal.gmscompat.client.ClientPriorityManager;
import com.android.internal.gmscompat.flags.GmsFlag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.android.internal.gmscompat.GmsInfo.PACKAGE_GMS_CORE;

public final class GmsHooks {
    private static final String TAG = "GmsCompat/Hooks";

    private static volatile GmsCompatConfig config;

    public static final String PERSISTENT_GmsCore_PROCESS = PACKAGE_GMS_CORE + ".persistent";
    public static boolean inPersistentGmsCoreProcess;

    public static GmsCompatConfig config() {
        // thread-safe: immutable after publication
        return config;
    }

    public static void init(Context ctx, String packageName) {
        String processName = Application.getProcessName();

        if (!packageName.equals(processName)) {
            // Fix RuntimeException: Using WebView from more than one process at once with the same data
            // directory is not supported. https://crbug.com/558377
            WebView.setDataDirectorySuffix("process-shim--" + processName);
        }

        if (GmsCompat.isGmsCore()) {
            inPersistentGmsCoreProcess = processName.equals(PERSISTENT_GmsCore_PROCESS);
        }

        if (GmsCompat.isPlayStore()) {
            PlayStoreHooks.init();
        }

        configUpdateLock = new Object();

        // Locking is needed to prevent a race that would occur if config is updated via
        // BinderGca2Gms#updateConfig in the time window between BinderGms2Gca#connect and setConfig()
        // call below. Older GmsCompatConfig would overwrite the newer one in that case.
        synchronized (configUpdateLock) {
            GmsCompatConfig config = GmsCompatApp.connect(ctx, processName);
            setConfig(config);
        }

        Thread.setUncaughtExceptionPreHandler(new UncaughtExceptionPreHandler());
    }

    static Object configUpdateLock;

    static void setConfig(GmsCompatConfig c) {
        if (GmsCompat.isPlayStore()) {
            PlayStoreHooks.setupGservicesFlags(c);
        }

        // configUpdateLock should never be null at this point, it's initialized before GmsCompatApp
        // gets a handle to BinderGca2Gms that is used for updating GmsCompatConfig
        synchronized (configUpdateLock) {
            config = c;
        }
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

            if (!shouldSkipException(e)) {
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

        private static boolean shouldSkipException(Throwable e) {
            for (;;) {
                if (e == null) {
                    return false;
                }

                boolean skip =
    // in some cases a DeadSystemRuntimeException is thrown despite the system being actually
    // still alive, likely when the Binder buffer space is full and a binder transaction with
    // system_server fails.
    // See https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/core/jni/android_util_Binder.cpp;l=894
    // (DeadObjectException is rethrown as DeadSystemRuntimeException by
    // android.os.RemoteException#rethrowFromSystemServer())
                    e instanceof DeadSystemRuntimeException
    // Seems to be an OS bug, see
    // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/am/BroadcastQueue.java;l=654
                    || e instanceof RemoteServiceException.CannotDeliverBroadcastException
                ;

                if (skip) {
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
            // requires privileged permissions
            case Context.STATS_MANAGER:
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
    // GMS relies on getRunningAppProcesses() to figure out whether its client is running.
    // This workaround is racy, because unprivileged apps can't know whether an arbitrary pid is alive.
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
    public static Cursor maybeModifyQueryResult(Uri uri, @Nullable String[] projection, @Nullable Bundle queryArgs,
                                                Cursor origCursor) {
        String uriString = uri.toString();

        Consumer<ArrayMap<String, String>> mutator = null;

        if (GmsFlag.GSERVICES_URI.equals(uriString)) {
            if (queryArgs == null) {
                return null;
            }
            String[] selectionArgs = queryArgs.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS);
            if (selectionArgs == null) {
                return null;
            }

            ArrayMap<String, GmsFlag> flags = config().gservicesFlags;
            if (flags == null) {
                return null;
            }

            mutator = map -> {
                for (GmsFlag f : flags.values()) {
                    for (String sel : selectionArgs) {
                        if (f.name.startsWith(sel)) {
                            f.applyToGservicesMap(map);
                            break;
                        }
                    }
                }
            };
        } else if (uriString.startsWith(GmsFlag.PHENOTYPE_URI_PREFIX)) {
            List<String> path = uri.getPathSegments();
            if (path.size() != 1) {
                Log.e(TAG, "unknown phenotype uri " + uriString, new Throwable());
                return null;
            }

            String namespace = path.get(0);

            ArrayMap<String, GmsFlag> nsFlags = config().flags.get(namespace);

            if (nsFlags == null) {
                return null;
            }

            mutator = map -> {
                for (GmsFlag f : nsFlags.values()) {
                    f.applyToPhenotypeMap(map);
                }
            };
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

    // SharedPreferencesImpl#getAll
    public static void maybeModifySharedPreferencesValues(String name, HashMap<String, Object> map) {
        // some PhenotypeFlags are stored in SharedPreferences instead of phenotype.db database
        ArrayMap<String, GmsFlag> flags = GmsHooks.config().flags.get(name);
        if (flags == null) {
            return;
        }

        for (GmsFlag f : flags.values()) {
            f.applyToPhenotypeMap(map);
        }
    }

    // Instrumentation#execStartActivity(Context, IBinder, IBinder, Activity, Intent, int, Bundle)
    public static void onActivityStart(int resultCode, Intent intent, int requestCode, Bundle options) {
        if (resultCode != ActivityManager.START_ABORTED) {
            return;
        }

        // handle background activity starts, which normally require a privileged permission

        if (requestCode >= 0) {
            Log.d(TAG, "attempt to call startActivityForResult() from the background " + intent, new Throwable());
            return;
        }

        // needed to prevent invalid reuse of PendingIntents, see PendingIntent doc
        intent.setIdentifier(UUID.randomUUID().toString());

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
        // "Nearby devices" user-facing permission grants multiple underlying permissions,
        // checking one is enough
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

    // Parcel#readException
    public static boolean interceptException(Exception e, Parcel p) {
        if (!(e instanceof SecurityException)) {
            return false;
        }

        if (p.dataAvail() != 0) {
            Log.w(TAG, "malformed Parcel: dataAvail() " + p.dataAvail() + " after exception", e);
            return false;
        }

        StubDef stub = StubDef.find(e, config());

        if (stub == null) {
            return false;
        }

        boolean res = stub.stubOutMethod(p);

        if (Build.isDebuggable()) {
            Log.i(TAG, res ? "intercepted" : "stubOut failed", e);
        }

        return res;
    }

    public static void onSQLiteOpenHelperConstructed(SQLiteOpenHelper h, @Nullable Context context) {
        if (context == null) {
            return;
        }

        if (GmsCompat.isGmsCore()) {
            if (inPersistentGmsCoreProcess) {
                if ("phenotype.db".equals(h.getDatabaseName()) && !context.isDeviceProtectedStorage()) {
                    if (phenotypeDb != null) {
                        Log.w(TAG, "reassigning phenotypeDb", new Throwable());
                    }
                    phenotypeDb = h;
                }
            }
        }
    }

    private static volatile SQLiteOpenHelper phenotypeDb;
    public static SQLiteOpenHelper getPhenotypeDb() { return phenotypeDb; }

    private GmsHooks() {}
}
