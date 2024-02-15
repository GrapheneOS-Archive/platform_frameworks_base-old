package grapheneos.test;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import grapheneos.test.consts.*;
import grapheneos.test.utils.DeviceUtils;
import grapheneos.test.utils.KeystoreWipeUtils;
import grapheneos.test.utils.LockScreenUtils;
import grapheneos.test.utils.UserCredentialStorageUtils;
import grapheneos.test.utils.VendorWipeUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(DeviceJUnit4ClassRunner.class)
public class DuressWipeTest extends BaseHostJUnit4Test {

    private VendorWipeUtils vendorWipeUtils;
    private LockScreenUtils lockScreenUtils;
    private KeystoreWipeUtils keystoreWipeUtils;
    private ITestDevice device;
    private UserCredentialStorageUtils userCredentialStorageUtils;

    @Before
    public void setUp() throws DeviceNotAvailableException, InterruptedException {
        device = getDevice();

        DeviceUtils deviceUtils = new DeviceUtils(device);
        lockScreenUtils = new LockScreenUtils(device);
        vendorWipeUtils = new VendorWipeUtils(device);
        keystoreWipeUtils = new KeystoreWipeUtils(device);
        userCredentialStorageUtils = new UserCredentialStorageUtils(device);

        deviceUtils.markSetupCompletedIfNeeded(Constants.BOOT_COMPLETE_TIMEOUT.toMillis());
        deviceUtils.asRoot();

        lockScreenUtils.setLockscreenPin(Constants.USER_PIN);
        lockScreenUtils.verifyLockScreenPin(Constants.USER_PIN);

        var createUserCeFile = userCredentialStorageUtils.createFileInUserCredentialStorage();
        Assert.assertTrue(
                "unable to create file in user ce storage",
                createUserCeFile
        );

        lockScreenUtils.putTheDeviceToSleep();
        Thread.sleep(2000);
        lockScreenUtils.wakeUp();

        Assert.assertTrue(
                "device is unlocked after sleep",
                device.getKeyguardState().isKeyguardShowing()
        );
        Assert.assertTrue(
                "failed to unlock the device",
                lockScreenUtils.tryToUnlockTheDevice(Constants.USER_PIN)
        );

    }

    private void makeSureCeFilesAreGone() throws DeviceNotAvailableException, InterruptedException {
        device.setProperty(Constants.VENDOR_DURESS_IN_TEST_MODE, "test");
        device.setProperty(Constants.START_DURESS_WIPE_PROP, Constants.DURESS_START_VENDOR_VALUE);
        Assert.assertTrue(
            "vendor wipe failed",
            vendorWipeUtils.waitForVendorWipeResponse(device.getDeviceDate())
        )
        userCredentialStorageUtils.verifyUserCredentialStorageIsUnavailable();
    }

    @Test
    public void runDuressPinTest() {
        try {
            testDuressPin();
        } catch (InterruptedException | DeviceNotAvailableException e) {
            Assert.fail(e.getLocalizedMessage());
        }
    }

    private void testDuressPin() throws InterruptedException, DeviceNotAvailableException {

        var setDuressCmd = "cmd lock_settings set-duress-creds " +
                "--old " + Constants.USER_PIN + " " +
                "--duressPin " + Constants.DURESS_PIN + " " +
                "--duressPassword " + Constants.DURESS_PASSWORD;

        var setDuressCredsResponse = device.executeShellCommand(setDuressCmd);
        var expectedResponse = "Duress credential set, new Pin : '" + Constants.DURESS_PIN +
                "' Password : '" + Constants.DURESS_PASSWORD + "'";

        Assert.assertTrue(
            "expected duress creds set cli response " + expectedResponse + " , got " + setDuressCredsResponse,
            setDuressCredsResponse.contains(expectedResponse)
        )

        makeSureCeFilesAreGone();

        var from = device.getDeviceDate();

        onSeparateThread(() -> Assert.assertTrue(
                "prop value didn't changed after entering duress password",
                waitForPropPropagation()
        ));

        //make sure vendor part works
        onSeparateThread(() -> Assert.assertTrue(
                "vendor wipe failed",
                vendorWipeUtils.waitForVendorWipeResponse(from)
        ));

        //make sure keystore part works
        onSeparateThread(() -> Assert.assertTrue(
                "keystore wipe failed",
                keystoreWipeUtils.waitForKeyStoreWipeResponse(from)
        ));

        //trigger duress wipe (vendor part in test mode)
        lockScreenUtils.putTheDeviceToSleep();
        device.setProperty(Constants.VENDOR_DURESS_IN_TEST_MODE, "test");
        Assert.assertFalse(
                "able to unlock the device with duress pin ",
                lockScreenUtils.tryToUnlockTheDevice(Constants.DURESS_PIN)
        );

        var deviceBootFailWaitDuration = Duration.ofMinutes(2);
        device.waitForDeviceInRecovery(deviceBootFailWaitDuration.toMillis());

    }

    private void onSeparateThread(Runnable runnable) {
        new Thread(runnable).start();
    }

    private boolean waitForPropPropagation() {
        var waitDuration = Duration.ofSeconds(30).toMillis();
        long stopTimeInMilli = System.currentTimeMillis() + waitDuration;

        while (System.currentTimeMillis() < stopTimeInMilli) {

            try {
                if (device.getProperty(Constants.START_DURESS_WIPE_PROP) != null) {
                    return true;
                }
            } catch (DeviceNotAvailableException ignored) {
                return false;
            }
        }
        return false;
    }

}
