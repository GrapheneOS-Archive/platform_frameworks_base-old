package grapheneos.test.consts;

import java.time.Duration;

public class Constants {

    public static final String VENDOR_DURESS_IN_TEST_MODE = "vendor.citadeld.duress.mode";
    public static final String START_DURESS_WIPE_PROP = "sys.duress.wipe.start";
    public static final String DURESS_START_VENDOR_VALUE = "vendor";
    public static final String LOG_TAG = "vendorDuressService";

    public static final String VENDOR_WIPE_SUCCESS_REGEXP = "Titan M wipe successful";
    public static final String VENDOR_WIPE_FAILED_REGEXP = "Titan M wipe failed";

    public static final String KEYSTORE_WIPE_SUCCESS_REGEXP = "keystore wiped successfully";
    public static final String KEYSTORE_WIPE_FAILED_REGEXP = "failed to wipe keystore";
    public static final String KEYSTORE_LOG_TAG = "DuressKeystoreWipe";

    public static final String USER_CE_TEST_FILE_CONTENT = "super_secret_content";
    public static final String USER_CE_TEST_FILE_PATH =
            "/data/data/com.android.shell/files/user_ce";

    public static final String USER_PIN = "1234";
    public static final String USER_PASSWORD = "loginPassword";

    public static final String DURESS_PIN = "2345";
    public static final String DURESS_PASSWORD = "duressPassword";

    public static final Duration BOOT_COMPLETE_TIMEOUT = Duration.ofMinutes(2);

}
