package grapheneos.test.utils;

import com.android.tradefed.device.ITestDevice;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;

import grapheneos.test.consts.Constants;

public class VendorWipeUtils {

    private static final String VENDOR_TIMEOUT_ERROR =
            "vendor service didn't responded to $START_DURESS_WIPE_PROP props changes";
    private final ITestDevice device;

    public VendorWipeUtils(ITestDevice device) {
        this.device = device;
    }

    public boolean waitForVendorWipeResponse(long from) {

        var waitDuration = Duration.ofSeconds(30).toMillis();
        long stopTimeInMilli = System.currentTimeMillis() + waitDuration;

        while (System.currentTimeMillis() < stopTimeInMilli) {
            var status = getVendorWipeStatus(from);
            if (status != null) {
                return status;
            }
        }

        return failTimeout();
    }

    private boolean failTimeout() {
        Assert.fail(VENDOR_TIMEOUT_ERROR);
        throw new IllegalStateException(VENDOR_TIMEOUT_ERROR);
    }

    private Boolean getVendorWipeStatus(long timeSince) {

        var logStreamSource = device.getLogcatSince(timeSince);
        var logStreamReader = new InputStreamReader(logStreamSource.createInputStream());
        var lines = new BufferedReader(logStreamReader).lines().toList();

        for (String line : lines) {

            if (!line.contains(Constants.LOG_TAG)) {
                continue;
            }

            if (line.contains(Constants.VENDOR_WIPE_SUCCESS_REGEXP)) {
                return true;
            } else if (line.contains(Constants.VENDOR_WIPE_FAILED_REGEXP)) {
                return false;
            }

        }

        return null;
    }

}
