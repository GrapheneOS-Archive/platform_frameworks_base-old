package com.android.internal.gmscompat;

import android.app.ApplicationErrorReport;
import android.app.PendingIntent;

import com.android.internal.gmscompat.GmsCompatConfig;
import com.android.internal.gmscompat.IGca2Gms;
import com.android.internal.gmscompat.dynamite.server.IFileProxyService;

// calls from GMS components to GmsCompatApp
interface IGms2Gca {
    GmsCompatConfig connectGmsCore(String processName, IGca2Gms iGca2Gms, @nullable IFileProxyService dynamiteFileProxyService);
    GmsCompatConfig connect(String packageName, String processName, IGca2Gms iGca2Gms);

    oneway void showPlayStorePendingUserActionNotification();
    oneway void dismissPlayStorePendingUserActionNotification();

    oneway void showPlayStoreMissingObbPermissionNotification();

    oneway void startActivityFromTheBackground(String callerPkg, in PendingIntent intent);

    oneway void showGmsCoreMissingPermissionForNearbyShareNotification();

    oneway void showGmsMissingNearbyDevicesPermissionGeneric(String callerPkg);

    void onUncaughtException(in ApplicationErrorReport aer);
}
