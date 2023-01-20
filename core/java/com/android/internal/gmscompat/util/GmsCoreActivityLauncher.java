package com.android.internal.gmscompat.util;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.compat.gms.GmsCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.internal.gmscompat.GmsInfo;

import static com.android.internal.gmscompat.GmsHooks.UI_GmsCore_PROCESS;

// Used for launching activity intent from any GmsCore process if GmsCore UI process is running
// and has a visible activity
public class GmsCoreActivityLauncher extends BroadcastReceiver {
    private static final String TAG = GmsCoreActivityLauncher.class.getSimpleName();
    private static final String INTENT_ACTION = GmsCoreActivityLauncher.class.getName();
    private static final String CURRENT_ACTIVITY_REGEX = "CURRENT_ACTIVITY_REGEX";

    public static void maybeRegister(String processName, Context ctx) {
        if (!processName.equals(UI_GmsCore_PROCESS)) {
            return;
        }

        IntentFilter f = new IntentFilter(INTENT_ACTION);
        ctx.registerReceiver(new GmsCoreActivityLauncher(), f, Context.RECEIVER_NOT_EXPORTED);
    }

    // currentActivityRegex parameter allows skipping the activity launch if the most recent
    // visible activity's name doesn't match the regex
    public static void maybeLaunch(Intent activityIntent, @Nullable String currentActivityRegex) {
        Intent i = new Intent(INTENT_ACTION);
        i.putExtra(Intent.EXTRA_INTENT, activityIntent);
        i.putExtra(CURRENT_ACTIVITY_REGEX, currentActivityRegex);
        GmsCompat.appContext().sendBroadcast(i, GmsInfo.SIGNATURE_PROTECTED_PERMISSION);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Activity activity = GmcActivityUtils.getMostRecentVisibleActivity();
        if (activity != null) {
            String regex = intent.getStringExtra(CURRENT_ACTIVITY_REGEX);
            if (regex != null) {
                String className = activity.getClass().getName();
                if (!className.matches(regex)) {
                    Log.d(TAG, "current activity " + className + " doesn't match the regex " + regex);
                    return;
                }
            }
            activity.startActivity(intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class));
        }
    }
}
