package android.ext.settings;

/** @hide */
public interface ConnectivityCheckConstants {
    int GRAPHENEOS_CAPTIVE_PORTAL_HTTP_URL_INTVAL = 0;
    int STANDARD_CAPTIVE_PORTAL_HTTP_URL_INTVAL = 1;
    int DISABLED_CAPTIVE_PORTAL_INTVAL = 2;

    String GRAPHENEOS_CAPTIVE_PORTAL_HTTPS_URL =
            "https://connectivitycheck.grapheneos.network/generate_204";
    String GRAPHENEOS_CAPTIVE_PORTAL_HTTP_URL =
            "http://connectivitycheck.grapheneos.network/generate_204";
    String GRAPHENEOS_CAPTIVE_PORTAL_FALLBACK_URL =
            "http://grapheneos.online/gen_204";
    String GRAPHENEOS_CAPTIVE_PORTAL_OTHER_FALLBACK_URL =
            "http://grapheneos.online/generate_204";

    // imported defaults from AOSP NetworkStack
    String STANDARD_HTTPS_URL =
            "https://www.google.com/generate_204";
    String STANDARD_HTTP_URL =
            "http://connectivitycheck.gstatic.com/generate_204";
    String STANDARD_FALLBACK_URL =
            "http://www.google.com/gen_204";
    String STANDARD_OTHER_FALLBACK_URLS =
            "http://play.googleapis.com/generate_204";
}
