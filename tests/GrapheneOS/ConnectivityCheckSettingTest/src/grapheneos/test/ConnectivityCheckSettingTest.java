package grapheneos.test;

import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.android.tradefed.device.INativeDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.device.DeviceNotAvailableException;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.result.InputStreamSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

@RunWith(DeviceJUnit4ClassRunner.class)
public class ConnectivityCheckSettingTest extends BaseHostJUnit4Test {
    private static final int GRAPHENEOS_SERVER_SETTING = 0;
    private static final int STANDARD_SERVER_SETTING = 1;
    private static final int DISABLED_SETTING = 2;

    private static final String SYS_PROP = "persist.sys.connectivity_checks";
    private static final String WIFI_CMD = "cmd wifi set-wifi-enabled %s";
    private static final String LOG_TAG = "NetworkMonitor";
    private static final String FILTER_REQ_REGEXP = "PROBE_HTTP|PROBE_HTTPS";
    private static final String FILTER_DISABLED_REGEXP = "Validation disabled";

    enum ConnectivityCheckServer {
        GRAPHENEOS("http://connectivitycheck.grapheneos.network/generate_204", "https://connectivitycheck.grapheneos.network/generate_204"),
        STANDARD("http://connectivitycheck.gstatic.com/generate_204", "https://www.google.com/generate_204");

        public final String httpUrl;
        public final String httpsUrl;

        ConnectivityCheckServer(String httpUrl, String httpsUrl) {
            this.httpUrl = httpUrl;
            this.httpsUrl = httpsUrl;
        }
    }

    private final boolean toggleWifi(String state) throws DeviceNotAvailableException {
        String cmd = String.format(WIFI_CMD, state);
        return CommandStatus.SUCCESS.equals(getDevice().executeShellV2Command(cmd).getStatus());
    }

    @Before
    public void setUp() throws DeviceNotAvailableException {
        boolean isRoot = getDevice().isAdbRoot();
        if (!isRoot) {
            if (!getDevice().enableAdbRoot()) {
                fail("adb root access is required");
            }
        }
    }

    private final ArrayList<String> parseLogcat(String regexp, int setting, long time) throws Exception {
        final Pattern pattern = Pattern.compile(regexp);
        ArrayList<String> result = new ArrayList<String>();

        try (InputStreamSource logSource = getDevice().getLogcatSince(time)) {
            InputStreamReader streamReader = new InputStreamReader(logSource.createInputStream());
            BufferedReader logReader = new BufferedReader(streamReader);

            String line;
            while ((line = logReader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (line.contains(LOG_TAG)) {
                    if (setting != DISABLED_SETTING) {
                        if (matcher.find()) {
                            result.add(line);
                            if (result.toString().contains("PROBE_HTTP") && result.toString().contains("PROBE_HTTPS")) {
                                break;
                            }
                        }
                    } else {
                        if (matcher.find()) {
                            result.add(matcher.group(0));
                            break;
                        }
                    }
                }
            }
            return result;
        }
    }

    private final long triggerCheck(int setting) throws Exception {
        assertTrue(toggleWifi("disabled"));
        boolean isDisabled = false;
        for (int i = 0; i < 100; ++i) {
            RunUtil.getDefault().sleep(100);
            isDisabled = getDevice().executeShellV2Command("cmd wifi status").toString().contains("Wifi is disabled");
            if (isDisabled) {
                break;
            }
        }
        assertTrue(isDisabled);
        assertTrue(getDevice().setProperty(SYS_PROP, Integer.toString(setting)));
        assertTrue(toggleWifi("enabled"));
        boolean isEnabled = false;
        for (int i = 0; i < 100; ++i) {
            RunUtil.getDefault().sleep(100);
            isEnabled = getDevice().executeShellV2Command("cmd wifi status").toString().contains("Wifi is connected to");
            if (isEnabled) {
                break;
            }
        }
        assertTrue(isEnabled);
        return getDevice().getDeviceDate();
    }

    private final boolean checkLogSize(String regexp, int setting, long logStart, int logSize) throws Exception {
        boolean isCorrectSize = false;
        for (int i = 0; i < 20; ++i) {
            RunUtil.getDefault().sleep(500);
            isCorrectSize = (parseLogcat(regexp, setting, logStart).size() == logSize);
            if (isCorrectSize) {
                break;
            }
        }
        return isCorrectSize;
    }

    @Test
    public void testGrapheneOSServer() throws Exception {
        long logStart = triggerCheck(GRAPHENEOS_SERVER_SETTING);
        assertTrue(checkLogSize(FILTER_REQ_REGEXP, GRAPHENEOS_SERVER_SETTING, logStart, 2));
        ArrayList<String> parsedLogcat = parseLogcat(FILTER_REQ_REGEXP, GRAPHENEOS_SERVER_SETTING, logStart);
        assertTrue(parsedLogcat.get(0).contains(ConnectivityCheckServer.GRAPHENEOS.httpUrl));
        assertTrue(parsedLogcat.get(1).contains(ConnectivityCheckServer.GRAPHENEOS.httpsUrl));
    }

    @Test
    public void testStandardServer() throws Exception {
        long logStart = triggerCheck(STANDARD_SERVER_SETTING);
        assertTrue(checkLogSize(FILTER_REQ_REGEXP, STANDARD_SERVER_SETTING, logStart, 2));
        ArrayList<String> parsedLogcat = parseLogcat(FILTER_REQ_REGEXP, STANDARD_SERVER_SETTING, logStart);
        assertTrue(parsedLogcat.get(0).contains(ConnectivityCheckServer.STANDARD.httpUrl));
        assertTrue(parsedLogcat.get(1).contains(ConnectivityCheckServer.STANDARD.httpsUrl));
    }

    @Test
    public void testDisabled() throws Exception {
        long logStart = triggerCheck(DISABLED_SETTING);
        assertTrue(checkLogSize(FILTER_DISABLED_REGEXP, DISABLED_SETTING, logStart, 1));
        ArrayList<String> parsedLogcat = parseLogcat(FILTER_DISABLED_REGEXP, DISABLED_SETTING, logStart);
        assertTrue(parsedLogcat.get(0).equals(FILTER_DISABLED_REGEXP));
    }
}