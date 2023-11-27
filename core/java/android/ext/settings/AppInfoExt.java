package android.ext.settings;

import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
public class AppInfoExt implements Parcelable {
    public static final AppInfoExt DEFAULT = new AppInfoExt(0, 0L);

    private final int flags;

    public static final long HAS_COMPAT_CHANGES = 1L << 63;
    private final long compatChanges;

    public AppInfoExt(int flags, long compatChanges) {
        this.flags = flags;
        this.compatChanges = compatChanges;
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
    public void writeToParcel(Parcel dest, int parcelFlags) {
        dest.writeInt(flags);
        dest.writeLong(compatChanges);
    }

    public static final Creator<AppInfoExt> CREATOR = new Creator<>() {
        @Override
        public AppInfoExt createFromParcel(Parcel p) {
            return new AppInfoExt(p.readInt(), p.readLong());
        }

        @Override
        public AppInfoExt[] newArray(int size) {
            return new AppInfoExt[size];
        }
    };
}
