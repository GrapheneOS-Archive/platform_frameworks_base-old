package com.android.internal.gmscompat;

import android.app.PendingIntent;

import com.android.internal.gmscompat.dynamite.server.IFileProxyService;

// calls from main GMS components (GSF, GMS Core, Play Store) to GmsCompatApp
interface IGms2Gca {
    void connectGsf(String processName, IBinder callerBinder);
    void connectGmsCore(String processName, IBinder callerBinder, @nullable IFileProxyService dynamiteFileProxyService);
    void connectPlayStore(String processName, IBinder callerBinder);

    oneway void showPlayStorePendingUserActionNotification();
    oneway void dismissPlayStorePendingUserActionNotification();

    oneway void showPlayStoreMissingObbPermissionNotification();

    oneway void startActivityFromTheBackground(String callerPkg, in PendingIntent intent);

    oneway void showGmsCoreMissingPermissionForNearbyShareNotification();
}
