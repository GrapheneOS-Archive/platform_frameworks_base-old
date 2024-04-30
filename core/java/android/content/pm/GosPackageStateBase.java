package android.content.pm;

import android.annotation.Nullable;

import java.util.Arrays;

/**
 * Common code between GosPackageState and GosPackageStatePm.
 *
 * @hide
 */
public abstract class GosPackageStateBase {
    public final int flags;
    // flags that have package-specific meaning
    public final long packageFlags;
    @Nullable
    public final byte[] storageScopes;
    @Nullable
    public final byte[] contactScopes;

    protected GosPackageStateBase(int flags, long packageFlags,
                                  @Nullable byte[] storageScopes, @Nullable byte[] contactScopes) {
        this.flags = flags;
        this.packageFlags = packageFlags;
        this.storageScopes = storageScopes;
        this.contactScopes = contactScopes;
    }

    public final boolean hasFlags(int flags) {
        return (this.flags & flags) == flags;
    }

    public final boolean hasPackageFlags(long packageFlags) {
        return (this.packageFlags & packageFlags) == packageFlags;
    }

    @Override
    public final int hashCode() {
        return 31 * flags + Arrays.hashCode(storageScopes) + Arrays.hashCode(contactScopes) + Long.hashCode(packageFlags);
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof GosPackageStateBase o)) {
            return false;
        }

        if (flags != o.flags) {
            return false;
        }

        if (!Arrays.equals(storageScopes, o.storageScopes)) {
            return false;
        }

        if (!Arrays.equals(contactScopes, o.contactScopes)) {
            return false;
        }

        if (packageFlags != o.packageFlags) {
            return false;
        }

        return true;
    }
}
