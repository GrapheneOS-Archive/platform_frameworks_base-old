package com.android.internal.gmscompat;

import android.app.ApplicationErrorReport;
import android.app.PendingIntent;
import android.content.Intent;

import com.android.internal.gmscompat.GmsCompatConfig;

// calls from GmsCompatApp to GMS components
interface IGca2Gms {
    // intentionally not oneway to simplify code in GmsCompatApp
    void updateConfig(in GmsCompatConfig newConfig);
    void invalidateConfigCaches();

    boolean startActivityIfVisible(in Intent intent);
}
