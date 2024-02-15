package grapheneos.test.utils;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.time.Duration;

import grapheneos.test.consts.Constants;

public class UserCredentialStorageUtils {

    private final ITestDevice device;

    public UserCredentialStorageUtils(ITestDevice device) {
        this.device = device;
    }

    public void verifyUserCredentialStorageIsUnavailable()
            throws DeviceNotAvailableException, InterruptedException {
        device.nonBlockingReboot();
        device.waitForDeviceOnline(Constants.BOOT_COMPLETE_TIMEOUT.toMillis());
        device.waitForBootComplete(Constants.BOOT_COMPLETE_TIMEOUT.toMillis());
        Assert.assertTrue(
                "adb root access is required",
                device.enableAdbRoot()
        );

        var screenLockTime = Duration.ofSeconds(15);
        RunUtil.getDefault().sleep(screenLockTime.toMillis());

        Assert.assertFalse(
                "able to unlock the device after reboot",
                new LockScreenUtils(device).tryToUnlockTheDevice(Constants.USER_PIN)
        );

        var verifyPasswordCmd = "cmd lock_settings verify " + Constants.USER_PIN;
        var verifyPasswordError = "Error while executing command: verify";

        Assert.assertTrue(
                "able to verify lock screen creds after wiping vendor nos client (Titan M)",
                device.executeShellCommand(verifyPasswordCmd)
                        .contains(verifyPasswordError)
        );

        Assert.assertFalse(
                "user CE files are available after performing vendor wipe",
                verifyFileContentInUserCredentialStorage()
        );
    }

    public boolean createFileInUserCredentialStorage() throws DeviceNotAvailableException {
        device.executeShellCommand(
                "echo " + Constants.USER_CE_TEST_FILE_CONTENT + " >> "
                        + Constants.USER_CE_TEST_FILE_PATH);
        var response = device.executeShellCommand("cat " + Constants.USER_CE_TEST_FILE_PATH);
        if (response == null || response.isEmpty()) return false;
        return response.contains(Constants.USER_CE_TEST_FILE_CONTENT);
    }

    public boolean verifyFileContentInUserCredentialStorage() throws DeviceNotAvailableException {
        var response = device.executeShellCommand("cat " + Constants.USER_CE_TEST_FILE_PATH);
        if (response == null || response.isEmpty()) return false;
        return response.contains(Constants.USER_CE_TEST_FILE_CONTENT);
    }

}
