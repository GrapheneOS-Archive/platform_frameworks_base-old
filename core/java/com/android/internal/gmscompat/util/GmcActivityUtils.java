package com.android.internal.gmscompat.util;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

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
        PlayStoreHooks.activityStarted(activity);
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
}
