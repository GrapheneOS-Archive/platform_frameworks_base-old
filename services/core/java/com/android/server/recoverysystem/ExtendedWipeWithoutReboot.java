package com.android.server.recoverysystem;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.IVold;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

import java.util.List;

import static java.util.Collections.emptyList;

class ExtendedWipeWithoutReboot {
    static final String TAG = "ExtWipeWithoutReboot";

    static void run() {
        eraseSecureElement();

        IVold vold = null;
        try {
            vold = IVold.Stub.asInterface(ServiceManager.getServiceOrThrow("vold"));
        } catch (Throwable e) {
            Slog.e(TAG, "", e);
        }

        if (vold == null) {
            Slog.e(TAG, "IVold is null");
            return;
        }

        try {
            var um = LocalServices.getService(UserManagerInternal.class);
            for (int userId : um.getUserIds()) {
                destroyUserStorageKeys(vold, userId);
            }
        } catch (Throwable e) {
            Slog.e(TAG, "", e);
        }

        Slog.d(TAG, "calling vold.destroySystemStorageKey()");
        try {
            vold.destroySystemStorageKey();
        } catch (Throwable e) {
            Slog.e(TAG, "", e);
        }

        Slog.d(TAG, "calling vold.destroyMetadataKey(/data)");
        try {
            vold.destroyMetadataKey("/data");
        } catch (Throwable e) {
            Slog.e(TAG, "", e);
        }
    }

    private static void eraseSecureElement() {
        Slog.d(TAG, "eraseSecureElement start");
        String prop = "sys.erase_secure_element";
        try {
            SystemProperties.set(prop, "start");
            for (int i = 0; i < 500; ++i) {
                // wait at most ~2.5 seconds
                SystemClock.sleep(5);
                if ("finished".equals(SystemProperties.get(prop))) {
                    Slog.d(TAG, "eraseSecureElement end");
                    return;
                }
            }
            Slog.e(TAG, "eraseSecureElement timeout");
        } catch (Throwable e) {
            Slog.e(TAG, "", e);
        }
    }

    private static void destroyUserStorageKeys(IVold vold, @UserIdInt int userId) {
        Slog.d(TAG, "calling destroyUserStorageKeys for user " + userId);
        try {
            // calls destroyUserStorageKeys in vold
            vold.destroyUserStorageKeys2(userId,
                    // don't evict loaded keys, it's slow and might cause fatal IO errors
                    false);
        } catch (Throwable e) {
            Slog.w(TAG, "destroyUserStorageKeys2 failed for " + userId, e);
        }
    }
}
