package com.android.internal.os;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageStateBase;
import android.ext.settings.app.AswDenyNativeDebug;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;

import java.io.File;

// Per-app per-user per-process SELinux flags which are passed to the kernel via the selinux_flags
// process attribute.
//
// This process attribute is writable only by zygote and webview_zygote SELinux domains, app_zygote
// domain is intentionally omitted since it can run untrusted app code.
public class SELinuxFlags {
    public static final long DENY_EXECMEM = 1;
    public static final long DENY_EXECMOD = (1 << 1);
    public static final long DENY_EXECUTE_APPDOMAIN_TMPFS = (1 << 2);
    public static final long DENY_EXECUTE_APP_DATA_FILE = (1 << 3);
    public static final long DENY_EXECUTE_NO_TRANS_APP_DATA_FILE = (1 << 4);
    public static final long DENY_EXECUTE_ASHMEM_DEVICE = (1 << 5);
    public static final long DENY_EXECUTE_ASHMEM_LIBCUTILS_DEVICE = (1 << 6);
    public static final long DENY_EXECUTE_PRIVAPP_DATA_FILE = (1 << 7);
    public static final long DENY_PROCESS_PTRACE = (1 << 8);

    public static final long RESTRICT_MEMORY_DYN_CODE_EXEC_FLAGS =
            DENY_EXECMEM
            | DENY_EXECMOD
            | DENY_EXECUTE_APPDOMAIN_TMPFS
            | DENY_EXECUTE_ASHMEM_DEVICE
            | DENY_EXECUTE_ASHMEM_LIBCUTILS_DEVICE;

    public static final long RESTRICT_STORAGE_DYN_CODE_EXEC_FLAGS =
            DENY_EXECUTE_APP_DATA_FILE
            | DENY_EXECUTE_NO_TRANS_APP_DATA_FILE
            | DENY_EXECUTE_PRIVAPP_DATA_FILE;

    public static final long RESTRICT_DYN_CODE_EXEC_FLAGS =
            RESTRICT_MEMORY_DYN_CODE_EXEC_FLAGS | RESTRICT_STORAGE_DYN_CODE_EXEC_FLAGS;

    public static final long MEMORY_DYN_CODE_EXEC_FLAGS_THAT_BREAK_WEB_JIT = DENY_EXECMEM;

    public static final long ALL_RESTRICTIONS =
            RESTRICT_DYN_CODE_EXEC_FLAGS
            | DENY_PROCESS_PTRACE
    ;

    static long getForWebViewProcess(Context ctx, int userId, ApplicationInfo callerAppInfo,
                    @Nullable GosPackageStateBase callerPs) {
        if (Build.IS_EMULATOR) {
            if (shouldSkipOnEmulator()) {
                return 0L;
            }
        }

        long res = ALL_RESTRICTIONS;

        return res;
    }

    static long get(Context ctx, int userId, ApplicationInfo appInfo,
                    @Nullable GosPackageStateBase ps, boolean isIsolatedProcess) {
        if (Build.IS_EMULATOR) {
            if (shouldSkipOnEmulator()) {
                return 0L;
            }
        }

        long res = ALL_RESTRICTIONS;

        if (!AswDenyNativeDebug.I.get(ctx, userId, appInfo, ps)) {
            res &= ~DENY_PROCESS_PTRACE;
        }

        return res;
    }

    private static String getSelfProcAttrPath() {
        return "/proc/self/task/" + Process.myPid() + "/attr/selinux_flags";
    }

    public static boolean isSystemAppSepolicyWeakeningAllowed() {
        if (Build.IS_DEBUGGABLE) {
            return SystemProperties.getBoolean("persist.sys.allow_weakening_system_app_sepolicy", false);
        }
        return false;
    }

    private static volatile Boolean skipOnEmulator;

    // needed to prevent breaking emulator builds that don't have the necessary kernel changes
    private static boolean shouldSkipOnEmulator() {
        if (!Build.IS_EMULATOR) {
            return false;
        }

        Boolean skip = skipOnEmulator;
        if (skip == null) {
            var f = new File(getSelfProcAttrPath());
            skip = !f.exists();
            skipOnEmulator = skip;
        }

        return skip;
    }
}
