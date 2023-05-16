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
    @Nullable
    public final byte[] storageScopes;
    @Nullable
    public final byte[] contactScopes;

    protected GosPackageStateBase(int flags, @Nullable byte[] storageScopes, @Nullable byte[] contactScopes) {
        this.flags = flags;
        this.storageScopes = storageScopes;
        this.contactScopes = contactScopes;
    }

    public final boolean hasFlags(int flags) {
        return (this.flags & flags) == flags;
    }

    @Override
    public final int hashCode() {
        return 31 * flags + Arrays.hashCode(storageScopes) + Arrays.hashCode(contactScopes);
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof GosPackageStateBase)) {
            return false;
        }

        GosPackageStateBase o = (GosPackageStateBase) obj;
        if (flags != o.flags) {
            return false;
        }

        if (!Arrays.equals(storageScopes, o.storageScopes)) {
            return false;
        }

        if (!Arrays.equals(contactScopes, o.contactScopes)) {
            return false;
        }

        return true;
    }
}
