package android.ext.settings;

/** @hide */
public interface GnssConstants {
    int SUPL_SERVER_STANDARD = 0;
    int SUPL_DISABLED = 1;
    int SUPL_SERVER_GRAPHENEOS_PROXY = 2;

    String PSDS_TYPE_QUALCOMM_XTRA = "qualcomm_xtra";
    String PSDS_TYPE_BROADCOM = "broadcom";

    String PSDS_SERVER_GRAPHENEOS_BROADCOM = "https://broadcom.psds.grapheneos.org";

    // keep in sync with bionic/libc/bionic/gnss_psds_setting.c
    int PSDS_SERVER_GRAPHENEOS = 0;
    int PSDS_SERVER_STANDARD = 1;
    int PSDS_DISABLED = 2;
}
