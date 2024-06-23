package android.content.clipboard;

/**
 * @hide
 */
public abstract class ClipboardManagerInternal {
    public abstract void setAllowOneTimeAccess(String pkg, int userId);
}
