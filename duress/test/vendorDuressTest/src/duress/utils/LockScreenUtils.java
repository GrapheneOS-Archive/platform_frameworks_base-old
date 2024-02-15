package grapheneos.test.utils;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import org.junit.Assert;

import java.time.Duration;
import java.util.Objects;

public class LockScreenUtils {

    private static final String KEYCODE_WAKEUP = "KEYCODE_WAKEUP";
    private static final String KEYCODE_SLEEP = "KEYCODE_SLEEP";
    private static final String KEYCODE_MENU = "KEYCODE_MENU";
    private static final String KEYCODE_ENTER = "KEYCODE_ENTER";

    private final ITestDevice device;

    public LockScreenUtils(ITestDevice device) {
        this.device = device;
    }

    public void setLockscreenPin(String pin) throws DeviceNotAvailableException {
        var response = device.executeShellCommand("cmd lock_settings set-pin " + pin);
        var success = "Pin set to '" + pin + "'";
        Assert.assertTrue(
                "unable to set the lock screen pin. expected " + success + ", got " + response,
                response.contains(success)
        );
    }

    public void verifyLockScreenPin(String pin) throws DeviceNotAvailableException {
        var response = device.executeShellCommand("cmd lock_settings verify --old " + pin);
        var success = "Lock credential verified successfully";

        Assert.assertTrue(
                "unable to verify the lock screen pin. Expected " + success + " , got " + response,
                response.contains(success)
        );
    }

    public void putTheDeviceToSleep() throws DeviceNotAvailableException {
        dispatchKeyEvent(KEYCODE_SLEEP);
    }

    public void wakeUp() throws DeviceNotAvailableException {
        dispatchKeyEvent(KEYCODE_SLEEP);
    }


    private void dispatchKeyEvent(String event) throws DeviceNotAvailableException {
        device.executeShellCommand("input keyevent " + event);
    }

    //return true if able to unlock
    public boolean tryToUnlockTheDevice(String pinOrPassword)
            throws InterruptedException, DeviceNotAvailableException {
        var sleepDuration = Duration.ofSeconds(2).toMillis();
        dispatchKeyEvent(KEYCODE_WAKEUP); //wake
        Thread.sleep(sleepDuration);
        dispatchKeyEvent(KEYCODE_MENU); // jump to lockscreen
        Thread.sleep(sleepDuration);
        device.executeShellCommand("input text " + pinOrPassword); // type password
        Thread.sleep(sleepDuration);
        dispatchKeyEvent(KEYCODE_ENTER); // submit the password

        var waitDuration = Duration.ofSeconds(5).toMillis();
        long stopTimeInMilli = System.currentTimeMillis() + waitDuration;

        while (System.currentTimeMillis() < stopTimeInMilli) {
            var isUnlocked = isUnlocked();
            if (isUnlocked != null) {
                return isUnlocked;
            }
        }

        var keyguardState = device.getKeyguardState();
        return keyguardState != null && !keyguardState.isKeyguardShowing();
    }

    private Boolean isUnlocked() {
        try {
            var keyguardState = device.getKeyguardState();
            if (keyguardState != null && !keyguardState.isKeyguardShowing()) {
                return true;
            }
        } catch (DeviceNotAvailableException e) {
            return false;
        }
        return null;
    }

}
