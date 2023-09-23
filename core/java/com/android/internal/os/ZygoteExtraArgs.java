package com.android.internal.os;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageStateBase;

// Extra args for:
// - children of main zygote{,64}, including AppZygotes, but excluding WebViewZygote
// - children of WebViewZygote
//
// AppZygote is treated differently from WebViewZygote because the former runs untrusted app code
// (see android.app.ZygotePreload).
public class ZygoteExtraArgs {
    public static final String PREFIX = "--flat-extra-args=";

    public static final ZygoteExtraArgs DEFAULT = new ZygoteExtraArgs();

    public ZygoteExtraArgs() {
    }

    private static final int ARR_LEN = 0;
    private static final String SEPARATOR = "\t";

    public static String createFlat(Context ctx, int userId, ApplicationInfo appInfo,
                                @Nullable GosPackageStateBase ps,
                                boolean isIsolatedProcess) {
        String[] arr = new String[ARR_LEN];
        return PREFIX + String.join(SEPARATOR, arr);
    }

    public static String createFlatForWebviewProcess(Context ctx, int userId,
             ApplicationInfo callerAppInfo, @Nullable GosPackageStateBase callerPs) {
        String[] arr = new String[ARR_LEN];
        return PREFIX + String.join(SEPARATOR, arr);
    }

    static ZygoteExtraArgs parse(String flat) {
        String[] arr = flat.split(SEPARATOR);
        return new ZygoteExtraArgs();
    }

    // keep in sync with ExtraArgs struct in core/jni/com_android_internal_os_Zygote.cpp
    public long[] makeJniLongArray() {
        long[] res = new long[ARR_LEN];
        return res;
    }
}
