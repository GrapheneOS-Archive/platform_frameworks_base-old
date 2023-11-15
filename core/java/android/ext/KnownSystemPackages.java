package android.ext;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Context;
import android.content.res.Resources;

import com.android.internal.R;

/** @hide */
@SystemApi
public final class KnownSystemPackages {
    private static volatile KnownSystemPackages INSTANCE;

    @NonNull
    public static KnownSystemPackages get(@NonNull Context ctx) {
        var cache = INSTANCE;
        if (cache != null) {
            return cache;
        }
        return INSTANCE = new KnownSystemPackages(ctx);
    }

    @NonNull public final String contactsProvider;
    @NonNull public final String launcher;
    @NonNull public final String mediaProvider;
    @NonNull public final String permissionController;
    @NonNull public final String settings;
    @NonNull public final String systemUi;

    private KnownSystemPackages(Context ctx) {
        Resources res = ctx.getResources();
        contactsProvider = "com.android.providers.contacts";
        launcher = "com.android.launcher3";
        mediaProvider = "com.android.providers.media.module";
        permissionController = "com.android.permissioncontroller";
        settings = "com.android.settings";
        systemUi = res.getString(R.string.config_systemUi);
    }
}
