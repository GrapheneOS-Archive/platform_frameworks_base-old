package app.grapheneos.hardeningtest;

import android.annotation.NonNull;
import android.app.ZygotePreload;
import android.content.pm.ApplicationInfo;
import android.util.Log;

public class ZygotePreloadImpl implements ZygotePreload {
    private static final String TAG = ZygotePreloadImpl.class.getSimpleName();

    @Override
    public void doPreload(@NonNull ApplicationInfo appInfo) {
        // check that app zygote is not allowed to change SELinux flags
        Utils.checkSELinuxContextAndFlags("u:r:app_zygote:s0:c", appInfo.targetSdkVersion);
        Log.d(TAG, "doPreload completed");
    }
}
