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

import android.app.Activity;
import android.app.ActivityThread;
import android.app.PendingIntent;
import android.app.compat.gms.GmsCompat;
import android.app.usage.StorageStats;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.GosPackageState;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public final class PlayStoreHooks {
    private static final String TAG = "GmsCompat/PlayStore";

    // accessed only from the main thread, no need for synchronization
    static ArrayDeque<Intent> pendingConfirmationIntents;
    static PackageManager packageManager;
    static Executor commitExecutor;

    public static void init() {
        pendingConfirmationIntents = new ArrayDeque<>();

        obbDir = Environment.getExternalStorageDirectory().getPath() + "/Android/obb";
        playStoreObbDir = obbDir + '/' + GmsInfo.PACKAGE_PLAY_STORE;
        File.mkdirsFailedHook = PlayStoreHooks::mkdirsFailed;
        packageManager = GmsCompat.appContext().getPackageManager();
        commitExecutor = Executors.newSingleThreadExecutor();
    }

    // PackageInstaller.Session#commit(IntentSender)
    public static IntentSender commitSession(PackageInstaller.Session session, IntentSender statusReceiver) {
        if (!session.isMultiPackage()) {
            return PackageInstallerStatusForwarder.register((intent, extras) -> sendIntent(intent, statusReceiver))
                    .getIntentSender();
        }

        // multiPackage sessions do not work without the privileged INSTALL_PACKAGES permission,
        // commit their children separately instead.

        // Chrome {Stable, Beta, Dev, Canary} (and their "Android System WebView" counterparts)
        // are the only known users of multiPackage sessions, enable this workaround only for them.
        // Installing a Chrome variant or its WebView counterpart installs Trichrome static shared
        // library together in a single multiPackage session. Committing child sessions separately
        // should be fully safe (despite broken atomicity) given that:
        // - PackageManager will not allow installation of Chrome if Trichrome of the right version
        // isn't installed yet
        // - multiple versions of static shared libraries can be installed at the same time, so
        // installing a Trichrome update will not break the current Chrome + Trichrome combination:
        // both previous and new versions of Trichrome will be present after update completes
        // - when Chrome is updated in the subsequent session, older version of Trichrome will
        // become unused and will be deleted by the OS automatically after an OS-defined delay

        // Note that unprivileged package installers can't remove static shared libraries.
        // Privileged Play Store, despite being able to through the DELETE_PACKAGES permission,
        // doesn't do this either, relying on the OS instead.

        int[] sessionIds = session.getChildSessionIds();
        Deque<PackageInstaller.Session> sessions = new ArrayDeque<>(sessionIds.length);
        PackageInstaller installer = packageManager.getPackageInstaller();

        boolean trichromelibraryFound = false;
        for (int id : sessionIds) {
            session.removeChildSessionId(id);

            PackageInstaller.Session childSession;
            String[] names;
            try {
                childSession = installer.openSession(id);
                names = childSession.getNames();
            } catch (IOException e) {
                // child sessions should be already opened by the Play Store
                throw new IllegalStateException(e);
            }
            if (names.length == 1 && names[0].contains("com.google.android.trichromelibrary")) {
                if (trichromelibraryFound) {
                    throw new IllegalStateException("trichromelibrary already found");
                }
                trichromelibraryFound = true;
                sessions.addFirst(childSession);
            } else {
                sessions.addLast(childSession);
            }
        }
        if (!trichromelibraryFound) {
            // this approach breaks atomicity of multiPackage sessions, restrict it to installs
            // of Chrome and "Android System WebView";
            // there are no other known users of multiPackage sessions as of August 2022
            throw new IllegalStateException("trichromelibrary not found");
        }

        session.abandon();
        multiCommitStep(sessions, statusReceiver);

        // commit of the parent session is a no-op
        return null;
    }

    static void multiCommitStep(Deque<PackageInstaller.Session> sessions, IntentSender finalCallback) {
        PackageInstaller.Session session = sessions.removeFirst();
        PendingIntent pi = PackageInstallerStatusForwarder.register(multiCommitListener(sessions, finalCallback));

        // commitInner will block if getSilentUpdateWaitMillis() is > 0
        commitExecutor.execute(() -> session.commitInner(pi.getIntentSender()));
    }

    private static BiConsumer<Intent, Bundle> multiCommitListener(Deque<PackageInstaller.Session> sessions, IntentSender finalCallback) {
        return (intent, extras) -> {
            if (sessions.size() == 0) {
                sendIntent(intent, finalCallback);
            } else {
                if (getIntFromBundle(extras, PackageInstaller.EXTRA_STATUS) == PackageInstaller.STATUS_SUCCESS) {
                    multiCommitStep(sessions, finalCallback);
                } else {
                    for (PackageInstaller.Session s : sessions) {
                        s.abandon();
                    }
                    sendIntent(intent, finalCallback);
                }
            }
        };
    }

    // call at the end of Activity#onResume()
    public static void activityResumed(Activity activity) {
        if (pendingConfirmationIntents.size() != 0) {
            Intent i = pendingConfirmationIntents.removeLast();
            activity.startActivity(i);

            try {
                GmsCompatApp.iGms2Gca().dismissPlayStorePendingUserActionNotification();
            } catch (RemoteException e) {
                GmsCompatApp.callFailed(e);
            }
        }
    }

    static class PackageInstallerStatusForwarder extends BroadcastReceiver {
        private Context context;
        private PendingIntent pendingIntent;
        private BiConsumer<Intent, Bundle> target;

        private static final AtomicLong lastId = new AtomicLong();

        static PendingIntent register(BiConsumer<Intent, Bundle> target) {
            PackageInstallerStatusForwarder sf = new PackageInstallerStatusForwarder();
            Context context = GmsCompat.appContext();
            sf.context = context;
            sf.target = target;

            String intentAction = context.getPackageName()
                + "." + PackageInstallerStatusForwarder.class.getName() + "."
                + lastId.getAndIncrement();

            sf.pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(intentAction),
                    PendingIntent.FLAG_CANCEL_CURRENT |
                        PendingIntent.FLAG_MUTABLE);

            context.registerReceiver(sf, new IntentFilter(intentAction));
            return sf.pendingIntent;
        }

        public void onReceive(Context receiverContext, Intent intent) {
            Bundle extras = intent.getExtras();
            int status = getIntFromBundle(extras, PackageInstaller.EXTRA_STATUS);

            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);

                // there is no public API that I'm aware of (as of API 31)
                // that would allow to *reliably* find this out
                if (ActivityThread.currentActivityThread().hasAtLeastOneResumedActivity()) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmationIntent);
                } else {
                    pendingConfirmationIntents.addLast(confirmationIntent);
                    try {
                        GmsCompatApp.iGms2Gca().showPlayStorePendingUserActionNotification();
                    } catch (RemoteException e) {
                        GmsCompatApp.callFailed(e);
                    }
                }
                // confirmationIntent has a PendingIntent to this instance, don't unregister yet
                return;
            }
            pendingIntent.cancel();
            context.unregisterReceiver(this);

            target.accept(intent, extras);
        }
    }

    // Request user action to uninstall a package
    public static void deletePackage(PackageManager pm, String packageName, IPackageDeleteObserver observer, int flags) {
        if (flags != 0) {
            throw new IllegalStateException("unexpected flags: " + flags);
        }
        PendingIntent pi = PackageInstallerStatusForwarder.register(uninstallListener(packageName, observer));
        pm.getPackageInstaller().uninstall(packageName, pi.getIntentSender());
    }

    private static BiConsumer<Intent, Bundle> uninstallListener(String packageName, IPackageDeleteObserver target) {
        return (intent, extras) -> {
            // EXTRA_STATUS returns PackageInstaller constant,
            // EXTRA_LEGACY_STATUS returns PackageManager constant
            int status = getIntFromBundle(extras, PackageInstaller.EXTRA_LEGACY_STATUS);

            try {
                target.packageDeleted(packageName, status);
            } catch (RemoteException e) {
                throw e.rethrowAsRuntimeException();
            }

            if (status != PackageManager.DELETE_SUCCEEDED) {
                // Play Store doesn't expect uninstallation to fail
                // and ends up in an inconsistent UI state if the following workaround isn't applied

                String[] broadcasts = { Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_ADDED };

                Context context = GmsCompat.appContext();

                // default ClassLoader fails to load the needed class
                ClassLoader cl = context.getClassLoader();
                try {
                    Class cls = Class.forName("com.google.android.finsky.packagemanager.impl.PackageMonitorReceiverImpl$RegisteredReceiver", true, cl);

                    for (String action : broadcasts) {
                        // don't reuse BroadcastReceiver, it's expected that a new instance is made each time
                        BroadcastReceiver br = (BroadcastReceiver) cls.newInstance();
                        br.onReceive(context, new Intent(action, packageUri(packageName)));
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    // Called during self-update sequence because PackageManager requires
    // the restricted CLEAR_APP_CACHE permission
    public static void freeStorageAndNotify(String volumeUuid, long idealStorageSize,
            IPackageDataObserver observer) {
        if (volumeUuid != null) {
            throw new IllegalStateException("unexpected volumeUuid " + volumeUuid);
        }
        StorageManager sm = GmsCompat.appContext().getSystemService(StorageManager.class);
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

    // StorageStatsManager#queryStatsForPackage(UUID, String, UserHandle)
    public static StorageStats queryStatsForPackage(String packageName) throws PackageManager.NameNotFoundException {
        String apkPath = packageManager.getApplicationInfo(packageName, 0).sourceDir;

        StorageStats stats = new StorageStats();
        stats.codeBytes = new File(apkPath).length();
        // leave dataBytes, cacheBytes, externalCacheBytes at 0
        return stats;
    }

    // ApplicationPackageManager#setApplicationEnabledSetting
    public static void setApplicationEnabledSetting(String packageName, int newState) {
        if (newState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    && ActivityThread.currentActivityThread().hasAtLeastOneResumedActivity())
        {
            openAppSettings(packageName);
        }
    }

    private static String obbDir;
    private static String playStoreObbDir;

    // File#mkdirs()
    public static void mkdirsFailed(File file) {
        String path = file.getPath();

        if (path.startsWith(obbDir) && !path.startsWith(playStoreObbDir)) {
            GosPackageState ps = GosPackageState.get(GmsCompat.appContext().getPackageName());
            boolean hasObbAccess = ps != null && ps.hasFlag(GosPackageState.FLAG_ALLOW_ACCESS_TO_OBB_DIRECTORY);

            if (!hasObbAccess) {
                try {
                    GmsCompatApp.iGms2Gca().showPlayStoreMissingObbPermissionNotification();
                } catch (RemoteException e) {
                    GmsCompatApp.callFailed(e);
                }
            }
        }
    }

    static Uri packageUri(String packageName) {
        return Uri.fromParts("package", packageName, null);
    }

    // Unfortunately, there's no other way to ensure that the value is present and is of the right type.
    // Note that Intent.getExtras() makes a copy of the Bundle each time, so reuse its result
    static int getIntFromBundle(Bundle b, String key) {
        return ((Integer) b.get(key)).intValue();
    }

    static void openAppSettings(String packageName) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(packageUri(packageName));
        // FLAG_ACTIVITY_CLEAR_TASK is needed to ensure that the right screen is shown (it's a bug in the Settings app)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        GmsCompat.appContext().startActivity(i);
    }

    static void sendIntent(Intent intent, IntentSender target) {
        try {
            target.sendIntent(GmsCompat.appContext(), 0, intent, null, null);
        } catch (IntentSender.SendIntentException e) {
            Log.d(TAG, "", e);
        }
    }

    static void setupGservicesFlags(GmsCompatConfig config) {
        ContentResolver cr = GmsCompat.appContext().getContentResolver();
        final String prefPrefix = "gmscompat_play_store_unrestrict_pkg_";

        // Disables auto updates of GMS Core, not of all GMS components.
        // Updates that don't change version of GMS Core (eg downloading a new APK split
        // for new device locale) and manual updates are allowed
        if (Settings.Secure.getInt(cr, prefPrefix + GmsInfo.PACKAGE_GMS_CORE, 0) != 1) {
            config.addGservicesFlag("finsky.AutoUpdateCodegen__gms_auto_update_enabled", "0");
        }

        if (Settings.Secure.getInt(cr, prefPrefix + GmsInfo.PACKAGE_PLAY_STORE, 0) != 1) {
            // prevent auto-updates of Play Store, self-update files are still downloaded
            config.addGservicesFlag("finsky.SelfUpdate__do_not_install", "1");
            // don't re-download update files after failed self-update
            config.addGservicesFlag("finsky.SelfUpdate__self_update_download_max_valid_time_ms",
                    "" + Long.MAX_VALUE);
        }
    }

    private PlayStoreHooks() {}
}
