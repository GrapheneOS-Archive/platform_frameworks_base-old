package grapheneos.test.utils;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import org.junit.Assert;

public class DeviceUtils {

    //Settings.Global.DEVICE_PROVISIONED
    private static final String DEVICE_PROVISIONED = "device_provisioned";

    //Settings.Secure.USER_SETUP_COMPLETE
    private static final String USER_SETUP_COMPLETE = "user_setup_complete";

    private final ITestDevice device;

    public DeviceUtils(ITestDevice device) {
        this.device = device;
    }

    public void asRoot() throws DeviceNotAvailableException {
        Assert.assertTrue(
                "adb root access is required",
                device.enableAdbRoot()
        );
    }

    public void markSetupCompletedIfNeeded(long bootTimeoutInMilli) throws DeviceNotAvailableException {

        var namespaceGlobal = "global";
        var namespaceSecure = "secure";
        var valueCompleted = "1";

        var deviceProvisioned = device.getSetting(namespaceGlobal, DEVICE_PROVISIONED);
        var userSetupCompleted = device.getSetting(namespaceSecure, USER_SETUP_COMPLETE);

        if (!valueCompleted.equalsIgnoreCase(deviceProvisioned)
                || !valueCompleted.equalsIgnoreCase(userSetupCompleted)
        ) {
            device.setSetting(namespaceGlobal, DEVICE_PROVISIONED, valueCompleted);
            device.setSetting(namespaceSecure, USER_SETUP_COMPLETE, valueCompleted);

            device.reboot();
            device.waitForBootComplete(bootTimeoutInMilli);
        }
    }

}
