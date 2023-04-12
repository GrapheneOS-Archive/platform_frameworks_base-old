package android.ext.settings;

/** @hide */
public interface PsdsServerConstants {
    int PSDS_SERVER_GRAPHENEOS_PROXY = 0;
    int PSDS_SERVER_STANDARD = 1;
    int PSDS_DISABLED = 2; // unused for now

    String GRAPHENEOS_LONGTERM_PSDS_SERVER_1 = "https://broadcom.psds.grapheneos.org/lto2.dat";
    String GRAPHENEOS_LONGTERM_PSDS_SERVER_2 = null;
    String GRAPHENEOS_LONGTERM_PSDS_SERVER_3 = null;
    String GRAPHENEOS_NORMAL_PSDS_SERVER = "https://broadcom.psds.grapheneos.org/rto.dat";
    String GRAPHENEOS_REALTIME_PSDS_SERVER = "https://broadcom.psds.grapheneos.org/rtistatus.dat";
}
