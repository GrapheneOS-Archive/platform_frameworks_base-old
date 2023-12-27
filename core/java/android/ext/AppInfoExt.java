package android.ext;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
@SystemApi
public final class AppInfoExt implements Parcelable {
    /** @hide */
    public static final AppInfoExt DEFAULT = new AppInfoExt(PackageId.UNKNOWN, 0, 0L);

    private final int packageId;
    private final int flags;

    /** @hide */
    public static final long HAS_COMPAT_CHANGES = 1L << 63;
    private final long compatChanges;

    public static final int FLAG_HAS_GMSCORE_CLIENT_LIBRARY = 0;

    public AppInfoExt(int packageId, int flags, long compatChanges) {
        this.packageId = packageId;
        this.flags = flags;
        this.compatChanges = compatChanges;
    }

    /**
     * One of {@link android.ext.PackageId} int constants.
     */
    public int getPackageId() {
        return packageId;
    }

    public boolean hasFlag(int flag) {
        return (flags & (1 << flag)) != 0;
    }

    public boolean hasCompatConfig() {
        return (compatChanges & HAS_COMPAT_CHANGES) != 0;
    }

    public boolean hasCompatChange(int flag) {
        long mask = (1L << flag) | HAS_COMPAT_CHANGES;
        return (compatChanges & mask) == mask;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int parcelFlags) {
        boolean def = this == DEFAULT;
        dest.writeBoolean(def);
        if (def) {
            return;
        }

        dest.writeInt(packageId);
        dest.writeInt(flags);
        dest.writeLong(compatChanges);
    }

    @NonNull
    public static final Creator<AppInfoExt> CREATOR = new Creator<>() {
        @Override
        public AppInfoExt createFromParcel(@NonNull Parcel p) {
            if (p.readBoolean()) {
                return DEFAULT;
            }
            return new AppInfoExt(p.readInt(), p.readInt(), p.readLong());
        }

        @Override
        public AppInfoExt[] newArray(int size) {
            return new AppInfoExt[size];
        }
    };
}
