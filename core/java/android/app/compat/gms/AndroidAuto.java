package android.app.compat.gms;

import android.annotation.SystemApi;

/** @hide */
@SystemApi
public class AndroidAuto {
    public static final long PKG_FLAG_GRANT_PERMS_FOR_WIRED_ANDROID_AUTO = 1L;
    public static final long PKG_FLAG_GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO = 1L << 1;
    public static final long PKG_FLAG_GRANT_AUDIO_ROUTING_PERM = 1L << 2;
    public static final long PKG_FLAG_GRANT_PERMS_FOR_ANDROID_AUTO_PHONE_CALLS = 1L << 3;

    private AndroidAuto() {}
}
