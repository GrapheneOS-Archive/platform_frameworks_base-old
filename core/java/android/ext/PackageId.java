package android.ext;

import android.annotation.SystemApi;

/** @hide */
@SystemApi
// Int values that are assigned to packages in this interface can be retrieved at runtime from
// ApplicationInfo.ext().getPackageId() or from AndroidPackage.ext().getPackageId() (in system_server).
//
// PackageIds are assigned to parsed APKs only after they are verified, either by a certificate check
// or by a check that the APK is stored on an immutable OS partition.
public interface PackageId {
    int UNKNOWN = 0;

    String GSF_NAME = "com.google.android.gsf";
    int GSF = 1;

    String GMS_CORE_NAME = "com.google.android.gms";
    int GMS_CORE = 2;

    String PLAY_STORE_NAME = "com.android.vending";
    int PLAY_STORE = 3;

    String G_SEARCH_APP_NAME = "com.google.android.googlequicksearchbox";
    int G_SEARCH_APP = 4;

    String EUICC_SUPPORT_PIXEL_NAME = "com.google.euiccpixel";
    int EUICC_SUPPORT_PIXEL = 5;

    String G_EUICC_LPA_NAME = "com.google.android.euicc";
    int G_EUICC_LPA = 6;

    String G_CARRIER_SETTINGS_NAME = "com.google.android.carrier";
    int G_CARRIER_SETTINGS = 7;

    String G_CAMERA_NAME = "com.google.android.GoogleCamera";
    int G_CAMERA = 8;

    String PIXEL_CAMERA_SERVICES_NAME = "com.google.android.apps.camera.services";
    int PIXEL_CAMERA_SERVICES = 9;

    String ANDROID_AUTO_NAME = "com.google.android.projection.gearhead";
    int ANDROID_AUTO = 10;

    // "Google Fi"
    String TYCHO_NAME = "com.google.android.apps.tycho";
    int TYCHO = 11;

    /** @hide */ String G_TEXT_TO_SPEECH_NAME = "com.google.android.tts";
    /** @hide */ int G_TEXT_TO_SPEECH = 12;

    String PIXEL_HEALTH_NAME = "com.google.android.apps.pixel.health";
    int PIXEL_HEALTH = 13;
}
