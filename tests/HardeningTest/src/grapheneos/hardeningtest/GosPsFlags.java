package grapheneos.hardeningtest;

// GosPackageState class in unreachable from host tests
public class GosPsFlags {
    /** @hide */ public static final int FLAG_BLOCK_NATIVE_DEBUGGING_NON_DEFAULT = 1 << 6;
    /** @hide */ public static final int FLAG_BLOCK_NATIVE_DEBUGGING = 1 << 7;

    /** @hide */ public static final int FLAG_RESTRICT_MEMORY_DYN_CODE_EXEC_NON_DEFAULT = 1 << 9;
    /** @hide */ public static final int FLAG_RESTRICT_MEMORY_DYN_CODE_EXEC = 1 << 10;

    /** @hide */ public static final int FLAG_RESTRICT_STORAGE_DYN_CODE_EXEC_NON_DEFAULT = 1 << 12;
    /** @hide */ public static final int FLAG_RESTRICT_STORAGE_DYN_CODE_EXEC = 1 << 13;

    /** @hide */ public static final int FLAG_RESTRICT_WEBVIEW_DYN_CODE_EXEC_NON_DEFAULT = 1 << 15;
    /** @hide */ public static final int FLAG_RESTRICT_WEBVIEW_DYN_CODE_EXEC = 1 << 16;
}
