package com.android.server.locksettings;

import android.annotation.UptimeMillisLong;
import android.content.Context;
import android.os.SystemClock;
import android.telephony.euicc.EuiccCardManager;
import android.util.Slog;

import com.android.internal.telephony.euicc.EuiccWipe;
import com.android.server.power.PowerManagerService;
import com.android.server.recoverysystem.RecoverySystemService;

public class DuressWipe {
    static final String TAG = DuressWipe.class.getSimpleName();

    // used only for testing, guarded by owner credential
    public static boolean sleep5sBeforePoweroff;

    static void run(Context context) {
        Slog.d(TAG, "start");

        EuiccWipeThread euiccWipeThread = EuiccWipeThread.start(context);

        Slog.d(TAG, "calling deleteSecrets");
        // deleteSecrets() calls AndroidKeyStoreMaintenance.deleteAllKeys(), which deletes all
        // KeyMint keys, including the storage encryption keys
        RecoverySystemService.deleteSecrets();
        Slog.d(TAG, "deleteSecrets returned");

        euiccWipeThread.await(3000);
        Slog.d(TAG, "finished waiting for euiccWipeThread");

        if (sleep5sBeforePoweroff) {
            SystemClock.sleep(5000);
        }

        PowerManagerService.lowLevelShutdown(null);
    }

    static class EuiccWipeThread extends Thread {
        private final Context context;
        @UptimeMillisLong private long startTime;

        private EuiccWipeThread(Context context) {
            this.context = context;
        }

        static EuiccWipeThread start(Context ctx) {
            var t = new EuiccWipeThread(ctx);
            t.start();
            t.startTime = SystemClock.uptimeMillis();
            return t;
        }

        @Override
        public void run() {
            try {
                EuiccWipe.run(context, EuiccCardManager.RESET_FLAG_IS_FOR_DURESS_WIPE);
            } catch (Throwable e) {
                Slog.e(TAG, "", e);
            }
        }

        void await(long timeoutMs) {
            long remaining = timeoutMs - (SystemClock.uptimeMillis() - startTime);
            if (remaining <= 0) {
                return;
            }
            try {
                join(remaining);
            } catch (InterruptedException e) {
                // should never happen, but don't rethrow it just in case
                Slog.e(TAG, "", e);
            }
        }
    }
}
