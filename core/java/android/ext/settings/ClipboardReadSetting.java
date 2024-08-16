package android.ext.settings;

/** @hide */
public class ClipboardReadSetting {
    /** Clipboard read allowed as per AppOps. */
    public static final int ALLOWED = 0;
    /** Clipboard read blocked, but notification shown when app tries to read clipboard. */
    public static final int ASK_EVERY_TIME = 1;
    /** Clipboard read blocked, but no notification shown */
    public static final int BLOCKED = 2;

    // TODO: set ASK_EVERY_TIME as default once there are better explicit paste options
    public static final int DEFAULT = ALLOWED;
    public static final int[] VALID_VALUES = new int[]{ASK_EVERY_TIME, ALLOWED, BLOCKED};

    private ClipboardReadSetting() {}
}
