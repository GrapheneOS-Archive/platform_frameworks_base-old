package com.android.server.pm.ext;

import android.ext.PackageId;
import android.os.Parcel;

import com.android.server.pm.parsing.pkg.PackageImpl;

public class PackageExt {
    public static final PackageExt DEFAULT = new PackageExt(PackageId.UNKNOWN, 0);

    private final int packageId;
    private final int flags;

    public PackageExt(int packageId, int flags) {
        this.packageId = packageId;
        this.flags = flags;
    }

    public int getPackageId() {
        return packageId;
    }

    public void writeToParcel(Parcel dest) {
        boolean def = this == DEFAULT;
        dest.writeBoolean(def);
        if (def) {
            return;
        }
        dest.writeInt(this.packageId);
        dest.writeInt(this.flags);
    }

    public static PackageExt createFromParcel(PackageImpl pkg, Parcel p) {
        if (p.readBoolean()) {
            return DEFAULT;
        }
        return new PackageExt(p.readInt(), p.readInt());
    }
}
