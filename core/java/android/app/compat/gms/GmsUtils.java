package android.app.compat.gms;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Intent;
import android.ext.PackageId;
import android.net.Uri;

/** @hide */
@SystemApi
public class GmsUtils {

    public static @NonNull Intent createAppPlayStoreIntent(@NonNull String pkgName) {
        var i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName));
        i.setPackage(PackageId.PLAY_STORE_NAME);
        return i;
    }

    private GmsUtils() {}
}
