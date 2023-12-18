package com.android.server.pm.ext;

import android.ext.AppInfoExt;
import android.ext.PackageId;
import android.os.Parcel;

import com.android.server.os.nano.AppCompatProtos;
import com.android.server.pm.parsing.pkg.PackageImpl;

public class PackageExt {
    public static final PackageExt DEFAULT = new PackageExt(PackageId.UNKNOWN, 0);

    private final int packageId;
    private final int flags;

    private final PackageHooks hooks;

    public PackageExt(int packageId, int flags) {
        this.packageId = packageId;
        this.flags = flags;
        this.hooks = PackageHooksRegistry.getHooks(packageId);
    }

    public int getPackageId() {
        return packageId;
    }

    public PackageHooks hooks() {
        return hooks;
    }

    public AppInfoExt toAppInfoExt(PackageImpl pkg) {
        AppCompatProtos.CompatConfig compatConfig = pkg.getAppCompatConfig();

        if (this == DEFAULT && compatConfig == null) {
            return AppInfoExt.DEFAULT;
        }

        long compatChanges = compatConfig != null ?
                compatConfig.compatChanges | AppInfoExt.HAS_COMPAT_CHANGES : 0L;

        return new AppInfoExt(packageId, flags, compatChanges);
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
