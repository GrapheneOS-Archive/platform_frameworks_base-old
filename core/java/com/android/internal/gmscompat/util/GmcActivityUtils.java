package com.android.internal.gmscompat.util;

import android.Manifest;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Application;
import android.app.compat.gms.GmsCompat;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.internal.gmscompat.GmsCompatApp;
import com.android.internal.gmscompat.PlayStoreHooks;

public class GmcActivityUtils implements Application.ActivityLifecycleCallbacks {
    public static final GmcActivityUtils INSTANCE = new GmcActivityUtils();

    @Nullable
    private Activity mostRecentVisibleActivity;

    private GmcActivityUtils() {}

    @Nullable
    public static Activity getMostRecentVisibleActivity() {
        return INSTANCE.mostRecentVisibleActivity;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        mostRecentVisibleActivity = activity;

        String className = activity.getClass().getName();

        if (GmsCompat.isGmsCore()) {
            switch (className) {
                case "com.google.android.gms.nearby.sharing.ShareSheetActivity":
                    handleNearbyShareActivityResume(activity, true);
                    break;
                case "com.google.android.gms.nearby.sharing.InternalReceiveSurfaceActivity":
                    handleNearbyShareActivityResume(activity, false);
                    break;
            }
        }

        if (GmsCompat.isPlayStore()) {
            PlayStoreHooks.onActivityResumed(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (mostRecentVisibleActivity == activity) {
            mostRecentVisibleActivity = null;
        }
    }

    @Override public void onActivityCreated(Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}

    private static long lastBtEnableRequest;
    private static long lastBtDiscoverabilityRequest;

    private static void handleNearbyShareActivityResume(Activity activity, boolean isSend) {
        if (GmsCompat.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            var bm = GmsCompat.appContext().getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = bm.getAdapter();

            final long repeatInterval = 20_000;

            long ts = SystemClock.elapsedRealtime();
            if (isSend) {
                if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                    if (ts - lastBtEnableRequest > repeatInterval) {
                        var intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        activity.startActivity(intent);
                        lastBtEnableRequest = ts;
                    }
                }
            } else {
                if (adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_NONE) {
                    if (ts - lastBtDiscoverabilityRequest > repeatInterval) {
                        var intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                        activity.startActivity(intent);
                        lastBtDiscoverabilityRequest = ts;
                    }
                }
            }
        } else {
            try {
                GmsCompatApp.iGms2Gca().showGmsCoreMissingPermissionForNearbyShareNotification();
            } catch (RemoteException e) {
                GmsCompatApp.callFailed(e);
            }
        }
    }

    // See https://developer.android.com/about/versions/14/behavior-changes-14#background-activity-restrictions
    public static Bundle allowActivityLaunchFromPendingIntent(@Nullable Bundle orig) {
        var ao = orig != null ? ActivityOptions.fromBundle(orig) : ActivityOptions.makeBasic();
        ao.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return ao.toBundle();
    }
}
