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
import android.app.PendingIntent;
import android.app.compat.gms.GmsCompat;
import android.app.usage.StorageStats;
import android.content.BroadcastReceiver;
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

import com.android.internal.gmscompat.util.GmcActivityUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public final class PlayStoreHooks {
    private static final String TAG = "GmsCompat/PlayStore";

    // accessed only from the main thread, no need for synchronization
    static ArrayDeque<Intent> pendingConfirmationIntents;
    static PackageManager packageManager;

    public static void init() {
        pendingConfirmationIntents = new ArrayDeque<>();

        obbDir = Environment.getExternalStorageDirectory().getPath() + "/Android/obb";
        playStoreObbDir = obbDir + '/' + GmsInfo.PACKAGE_PLAY_STORE;
        File.mkdirsFailedHook = PlayStoreHooks::mkdirsFailed;
        packageManager = GmsCompat.appContext().getPackageManager();
    }

    // PackageInstaller#createSession
    public static void adjustSessionParams(PackageInstaller.SessionParams params) {
        String pkg = Objects.requireNonNull(params.appPackageName);

        switch (pkg) {
            case GmsInfo.PACKAGE_GMS_CORE:
            case GmsInfo.PACKAGE_PLAY_STORE:
                String updateRequestReason = "Play Store created PackageInstaller SessionParams for " + pkg;
                GmsCompatConfig config;
                try {
                    config = GmsCompatApp.iGms2Gca().requestConfigUpdate(updateRequestReason);
                } catch (RemoteException e) {
                    throw GmsCompatApp.callFailed(e);
                }
                if (GmsHooks.config().version != config.version) {
                    GmsHooks.setConfig(config);
                }
                break;
        }

        switch (pkg) {
            case GmsInfo.PACKAGE_GMS_CORE:
                params.maxAllowedVersion = GmsHooks.config().maxGmsCoreVersion;
                break;
            case GmsInfo.PACKAGE_PLAY_STORE:
                params.maxAllowedVersion = GmsHooks.config().maxPlayStoreVersion;
                break;
        }
    }

    // PackageInstaller.Session#commit(IntentSender)
    public static IntentSender wrapCommitStatusReceiver(PackageInstaller.Session session, IntentSender statusReceiver) {
        return PackageInstallerStatusForwarder.register((intent, extras) -> sendIntent(intent, statusReceiver))
                .getIntentSender();
    }

    public static void onActivityResumed(Activity activity) {
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

            context.registerReceiver(sf, new IntentFilter(intentAction), Context.RECEIVER_NOT_EXPORTED);
            return sf.pendingIntent;
        }

        public void onReceive(Context receiverContext, Intent intent) {
            Bundle extras = intent.getExtras();
            int status = getIntFromBundle(extras, PackageInstaller.EXTRA_STATUS);

            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);

                String packageName = null;

                if (extras.containsKey(PackageInstaller.EXTRA_SESSION_ID)) {
                    int sessionId = getIntFromBundle(extras, PackageInstaller.EXTRA_SESSION_ID);
                    PackageInstaller pkgInstaller = packageManager.getPackageInstaller();
                    PackageInstaller.SessionInfo si = pkgInstaller.getSessionInfo(sessionId);
                    if (si != null) {
                        packageName = si.getAppPackageName();
                    }
                }

                Activity activity = GmcActivityUtils.getMostRecentVisibleActivity();
                if (activity != null) {
                    activity.startActivity(confirmationIntent);
                } else {
                    pendingConfirmationIntents.addLast(confirmationIntent);
                    try {
                        GmsCompatApp.iGms2Gca().showPlayStorePendingUserActionNotification(packageName);
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

        // Play Store expects call to deletePackage() to always succeed, which almost always happens
        // when it has the privileged DELETE_PACKAGES permission.
        // This is not the case when Play Store has only the unprivileged REQUEST_DELETE_PACKAGES
        // permission, which requires confirmation from the user.
        // There are two difficulties:
        // - user may reject the confirmation prompt, which produces DELETE_FAILED_ABORTED error code,
        // which Play Store ignores
        // - user may dismiss the confirmation prompt without making a choice, which doesn't make
        // any callback at all
        // In both cases, Play Store remains stuck in "Uninstalling..." state for that package.
        // This state is written to persistent storage, it remains stuck even after device reboot.
        //
        // To work-around all these issues, pretend that the package was uninstalled and then installed
        // again, which moves the package state from "Uninstalling..." to "Installed" state, and
        // launch the uninstall request separately.

        PendingIntent pi = PackageInstallerStatusForwarder.register((BiConsumer<Intent, Bundle>) (intent, extras) -> {
            Log.d(TAG, "uninstall status " + extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE));
        });
        pm.getPackageInstaller().uninstall(packageName, pi.getIntentSender());

        GmsCompat.appContext().getMainThreadHandler().postDelayed(() -> {
            try {
                // Play Store ignores this callback as of version 33.6.13, but provide it anyway
                // in case it's fixed
                observer.packageDeleted(packageName, PackageManager.DELETE_FAILED_ABORTED);
            } catch (RemoteException e) {
                throw e.rethrowAsRuntimeException();
            }

            resetPackageState(packageName);
        }, 100L); // delay the callback for to workaround a race condition in Play Store
    }

    // If state transition that is expected to never fail by Play Store does fail, it may get stuck
    // in the old state. This happens, for example, when package uninstall fails.
    // To work-around this, pretend that the package was removed and installed again
    public static void resetPackageState(String packageName) {
        updatePackageState(packageName, Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_ADDED);
    }

    public static void updatePackageState(String packageName, String... broadcasts) {
        Context context = GmsCompat.appContext();

        // default ClassLoader fails to load the needed class
        ClassLoader cl = context.getClassLoader();

        // Depending on Play Store version, target class can be in packagemonitor or in
        // packagemanager package, support both
        String[] classNames = {
            "com.google.android.finsky.packagemonitor.impl.PackageMonitorReceiverImpl$RegisteredReceiver",
            "com.google.android.finsky.packagemanager.impl.PackageMonitorReceiverImpl$RegisteredReceiver",
        };

        for (String className : classNames) {
            try {
                Class cls = Class.forName(className, true, cl);

                for (String action : broadcasts) {
                    // don't reuse BroadcastReceiver, it's expected that a new instance is made each time
                    BroadcastReceiver br = (BroadcastReceiver) cls.newInstance();
                    br.onReceive(context, new Intent(action, packageUri(packageName)));
                }
            } catch (ReflectiveOperationException e) {
                Log.d(TAG, "", e);
                continue;
            }
            break;
        }
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
                    && GmcActivityUtils.getMostRecentVisibleActivity() != null)
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

    private PlayStoreHooks() {}
}
