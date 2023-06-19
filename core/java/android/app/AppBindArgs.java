package android.app;

/** @hide */
public interface AppBindArgs {
    String KEY_GOS_PACKAGE_STATE = "gosPs";
    String KEY_FLAGS_ARRAY = "flagsArr";

    int FLAGS_IDX_SPECIAL_RUNTIME_PERMISSIONS = 0;
    int FLAGS_IDX_HOOKED_LOCATION_MANAGER = 1;

    int FLAGS_ARRAY_LEN = 10;
}
