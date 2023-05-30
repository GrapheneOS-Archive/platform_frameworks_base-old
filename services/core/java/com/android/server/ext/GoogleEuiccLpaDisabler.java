package com.android.server.ext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.PatternMatcher;
import android.util.Slog;

import com.android.internal.util.GoogleEuicc;

/**
 * Automatically disables Google's eUICC (eSIM) LPA package when its dependencies get uninstalled or
 * disabled.
 */
public class GoogleEuiccLpaDisabler extends BroadcastReceiver {
    private static final String TAG = GoogleEuiccLpaDisabler.class.getSimpleName();

    GoogleEuiccLpaDisabler(SystemServerExt sse) {
        var f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);

        f.addDataScheme("package");
        for (String pkg : GoogleEuicc.getLpaDependencies()) {
            f.addDataSchemeSpecificPart(pkg, PatternMatcher.PATTERN_LITERAL);
        }

        sse.registerReceiver(this, f, sse.bgHandler);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // LPA is only enabled in the Owner user, which matches the userId of this Context
        PackageManager pm = context.getPackageManager();
        try {
            if (!GoogleEuicc.checkLpaDependencies()) {
                pm.setApplicationEnabledSetting(GoogleEuicc.LPA_PKG_NAME,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
            }
        } catch (Exception e) {
            // don't crash the system_server
            Slog.e(TAG, "", e);
        }
    }
}
