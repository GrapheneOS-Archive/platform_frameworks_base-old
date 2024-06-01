package grapheneos.test.duresspassword;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MINUTES;

@RunWith(DeviceJUnit4ClassRunner.class)
public class DuressPasswordTest extends BaseHostJUnit4Test {

    @Test
    public void testDuressPin() throws DeviceNotAvailableException {
        testDuressCredential(true);
    }

    @Test
    public void testDuressPassword() throws DeviceNotAvailableException {
        testDuressCredential(false);
    }

    private void testDuressCredential(boolean isPin) throws DeviceNotAvailableException {
        ITestDevice dev = getDevice();
        assertThat(dev.waitForBootComplete(MINUTES.toMillis(5))).isTrue();

        final int secondaryUserId = dev.createUser("SecondaryUser");

        int[] userIds = { 0, secondaryUserId };

        for (int userId : userIds) {
            String credential = makeUserCredential(userId);
            String cmd = "cmd lock_settings set-" + (isPin? "pin" : "password") + " --user " + userId
                    + " " + credential;
            assertThat(dev.executeShellV2Command(cmd).getExitCode()).isEqualTo(0);

            // check that credential verifies before duress wipe
            CommandResult vcr = verifyCredential(dev, userId, credential);
            List<String> lines = lines(vcr.getStdout());
            assertThat(lines.get(0)).isEqualTo("Lock credential verified successfully");
            // check that Weaver slot value is non-zero
            assertThat(lines.get(1)).matches("WeaverRead\\[slot=., responseStatus=0, valueType=NON_ZERO, valueLength=16, ex=null]");
            assertThat(lines).hasSize(2);
            assertThat(vcr.getExitCode()).isEqualTo(0);
        }

        // check that encryption keys for non-CE storage are available before duress wipe
        assertThat(checkNonCeStorageEncryptionKeys(dev)).asList().containsAtLeast(
                "/data/misc/vold/user_keys/de/0",
                "/data/unencrypted/key",
                "/metadata/vold/metadata_encryption/key"
        );

        final String duressPin = "2222";
        final String duressPassword = "duress_password";

        {
            String cmd = "cmd lock_settings set-duress-credentials --owner-credential " + makeUserCredential(0)
                    + " --duress-pin " + duressPin + " --duress-password " + duressPassword
                    // otherwise device would turn off before test completion
                    + " --sleep-5s-before-poweroff";
            assertThat(dev.executeShellV2Command(cmd).getExitCode()).isEqualTo(0);
        }

        IRunUtil runUtil = RunUtil.getDefault();

        // put device to sleep
        inputKeyEvent(dev, "SLEEP");
        runUtil.sleep(3000);
        // wake it up
        inputKeyEvent(dev, "WAKEUP");
        runUtil.sleep(2000);
        // bring up credential input
        inputKeyEvent(dev, "MENU");
        runUtil.sleep(2000);
        // input and submit duress PIN/password
        assertThat(dev.executeShellV2Command(
                "input text " + (isPin? duressPin : duressPassword)
        ).getExitCode()).isEqualTo(0);
        inputKeyEvent(dev, "ENTER");

        runUtil.sleep(1000);

        // check that encryption keys for non-CE storage are no longer available
        assertThat(checkNonCeStorageEncryptionKeys(dev)).hasLength(0);

        for (int userId : userIds) {
            CommandResult r = verifyCredential(dev, userId, makeUserCredential(userId));
            List<String> stdout = lines(r.getStdout());
            // check that Weaver slot is now zeroed
            assertThat(stdout.get(0)).matches("WeaverRead\\[slot=., responseStatus=0, valueType=ZERO, valueLength=16, ex=null]");
            assertThat(stdout).hasSize(1);
            // credential verification should now fail
            assertThat(r.getExitCode()).isEqualTo(255);
        }
    }

    private static String[] checkNonCeStorageEncryptionKeys(ITestDevice dev)
            throws DeviceNotAvailableException {
        CommandResult r = dev.executeShellV2Command("cmd lock_settings check-non-ce-storage-keys");
        assertThat(r.getExitCode()).isEqualTo(0);

        String stdout = r.getStdout().strip();
        if (stdout.isEmpty()) {
            return new String[0];
        }

        return stdout.split(", ");
    }

    private static CommandResult verifyCredential(ITestDevice dev, int userId, String credential)
            throws DeviceNotAvailableException {
        return dev.executeShellV2Command("cmd lock_settings verify --old " + credential
                + " --user " + userId + " --capture-weaver-ops");
    }

    private static void inputKeyEvent(ITestDevice dev, String ev) throws DeviceNotAvailableException {
        assertThat(dev.executeShellV2Command("input keyevent " + ev).getExitCode()).isEqualTo(0);
    }

    private static String makeUserCredential(int userId) {
        return Integer.toString(userId).repeat(5);
    }

    private static List<String> lines(String s) {
        return Arrays.asList(s.split("\n"));
    }
}
