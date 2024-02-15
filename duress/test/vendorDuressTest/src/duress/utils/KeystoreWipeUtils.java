package grapheneos.test.utils;

import com.android.tradefed.device.ITestDevice;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;

import grapheneos.test.consts.Constants;

public class KeystoreWipeUtils {

    private static final String KEYSTORE_TIMEOUT_ERROR =
            "keystore wipe service didn't responded to "
                    + Constants.START_DURESS_WIPE_PROP + " props changes";

    final ITestDevice device;

    public KeystoreWipeUtils(ITestDevice device) {
        this.device = device;
    }

    public boolean waitForKeyStoreWipeResponse(long startedFrom) {

        var waitDuration = Duration.ofSeconds(30).toMillis();
        long stopTimeInMilli = System.currentTimeMillis() + waitDuration;

        while (System.currentTimeMillis() < stopTimeInMilli) {
            var status = getKeystoreWipeStatus(startedFrom);
            if (status != null) {
                return status;
            }
        }
        return failTimeout();
    }

    public Boolean getKeystoreWipeStatus(long timeSince) {
        var logStreamSource = device.getLogcatSince(timeSince);
        var logStreamReader = new InputStreamReader(logStreamSource.createInputStream());
        var lines = new BufferedReader(logStreamReader).lines().toList();

        for (String line : lines) {

            if (!line.contains(Constants.KEYSTORE_LOG_TAG)) {
                continue;
            }

            if (line.contains(Constants.KEYSTORE_WIPE_SUCCESS_REGEXP)) {
                return true;
            } else if (line.contains(Constants.KEYSTORE_WIPE_FAILED_REGEXP)) {
                return false;
            }
        }

        return null;
    }

    private boolean failTimeout() {
        Assert.fail(KEYSTORE_TIMEOUT_ERROR);
        throw new IllegalStateException(KEYSTORE_TIMEOUT_ERROR);
    }

}
