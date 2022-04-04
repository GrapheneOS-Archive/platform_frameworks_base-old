package com.android.internal.gmscompat;

import com.android.internal.gmscompat.dynamite.server.IFileProxyService;

parcelable BinderRedirector;

// calls from clients of GMS Core to GmsCompatApp
interface IClientOfGmsCore2Gca {
    String[] getRedirectableInterfaces();
    BinderRedirector getBinderRedirector(int id);

    IFileProxyService getDynamiteFileProxyService();
}
