package com.android.internal.gmscompat;

import android.os.BinderDef;

import com.android.internal.gmscompat.dynamite.server.IFileProxyService;

// calls from clients of GMS Core to GmsCompatApp
interface IClientOfGmsCore2Gca {
    @nullable BinderDef maybeGetBinderDef(String callerPkg, int processState, String ifaceName);

    IFileProxyService getDynamiteFileProxyService();

    oneway void showMissingAppNotification(String pkgName);
}
