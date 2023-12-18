package com.android.server.pm.ext;

import android.Manifest;
import android.content.pm.PackageManagerInternal;
import android.ext.PackageId;

import com.android.internal.pm.pkg.component.ParsedUsesPermissionImpl;
import com.android.server.LocalServices;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.GosPackageStatePm;

import java.util.List;

import static android.app.compat.gms.AndroidAuto.PKG_FLAG_GRANT_AUDIO_ROUTING_PERM;
import static android.app.compat.gms.AndroidAuto.PKG_FLAG_GRANT_PERMS_FOR_ANDROID_AUTO_PHONE_CALLS;
import static android.app.compat.gms.AndroidAuto.PKG_FLAG_GRANT_PERMS_FOR_WIRED_ANDROID_AUTO;
import static android.app.compat.gms.AndroidAuto.PKG_FLAG_GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO;

public class AndroidAutoHooks extends PackageHooks {
    private static final String TAG = AndroidAutoHooks.class.getSimpleName();

    static class ParsingHooks extends GmsCompatPkgParsingHooks {

        @Override
        public List<ParsedUsesPermissionImpl> addUsesPermissions() {
            List<ParsedUsesPermissionImpl> res = super.addUsesPermissions();

            res.addAll(createUsesPerms(
                    Manifest.permission.ASSOCIATE_COMPANION_DEVICES_RESTRICTED,
                    Manifest.permission.BLUETOOTH_PRIVILEGED_ANDROID_AUTO,
                    Manifest.permission.MANAGE_USB_ANDROID_AUTO,
                    Manifest.permission.READ_DEVICE_SERIAL_NUMBER,
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE_ANDROID_AUTO,
                    Manifest.permission.WIFI_PRIVILEGED_ANDROID_AUTO
            ));

            return res;
        }
    }

    public static boolean isAndroidAutoWithGrantedBasePrivPerms(String packageName, int userId) {
        if (!PackageId.ANDROID_AUTO_NAME.equals(packageName)) {
            return false;
        }

        var pm = LocalServices.getService(PackageManagerInternal.class);
        AndroidPackage pkg = pm.getPackage(packageName);
        if (pkg == null || PackageExt.get(pkg).getPackageId() != PackageId.ANDROID_AUTO) {
            return false;
        }

        GosPackageStatePm ps = pm.getGosPackageState(packageName, userId);
        long flags = PKG_FLAG_GRANT_PERMS_FOR_WIRED_ANDROID_AUTO | PKG_FLAG_GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO;
        return ps != null && (ps.packageFlags & flags) != 0;
    }

    @Override
    public int overridePermissionState(String permission, int userId) {
        long flags;

        switch (permission) {
            case
                /** @see android.companion.virtual.VirtualDeviceParams#LOCK_STATE_ALWAYS_UNLOCKED */
                Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY,
                /** @see android.hardware.display.DisplayManager#VIRTUAL_DISPLAY_FLAG_TRUSTED */
                Manifest.permission.ADD_TRUSTED_DISPLAY,
                Manifest.permission.CREATE_VIRTUAL_DEVICE,
                /** @see android.app.UiModeManager#enableCarMode(int, int) */
                Manifest.permission.ENTER_CAR_MODE_PRIORITIZED,
                Manifest.permission.MANAGE_USB_ANDROID_AUTO,
                // allows to enable/disable dark mode
                Manifest.permission.MODIFY_DAY_NIGHT_MODE,
                // allows to asssociate only with DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
                Manifest.permission.REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION,
                // doesn't grant any data access, included here to improve UX
                Manifest.permission.POST_NOTIFICATIONS,
                /** @see android.companion.AssociationInfo#isSelfManaged (check callers)*/
                Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                /** @see android.app.UiModeManager#requestProjection  */
                Manifest.permission.TOGGLE_AUTOMOTIVE_PROJECTION ->
                    flags = PKG_FLAG_GRANT_PERMS_FOR_WIRED_ANDROID_AUTO | PKG_FLAG_GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO;

            case Manifest.permission.MODIFY_AUDIO_ROUTING ->
                    flags = PKG_FLAG_GRANT_AUDIO_ROUTING_PERM;

            case
                // Allows Android Auto to associate with any companion device that has a MAC address
                // Unrestricted version would allow Android Auto to associate any package in any user
                // with any such device. Not clear whether it's feasible to restrict this permission
                // further.
                Manifest.permission.ASSOCIATE_COMPANION_DEVICES_RESTRICTED,
                Manifest.permission.INTERNET,
                // allows to read MAC address of Bluetooth and WiFi adapters
                Manifest.permission.LOCAL_MAC_ADDRESS,
                // unprivileged permission
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.READ_DEVICE_SERIAL_NUMBER,
                // grants access to a small subset of privileged WiFi APIs
                Manifest.permission.WIFI_PRIVILEGED_ANDROID_AUTO ->
                    flags = PKG_FLAG_GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO;

            case
                // unprivileged permission
                Manifest.permission.BLUETOOTH_CONNECT,
                // grants access to a small subset of BLUETOOTH_PRIVILEGED privileges
                Manifest.permission.BLUETOOTH_PRIVILEGED_ANDROID_AUTO,
                // unprivileged permission
                Manifest.permission.BLUETOOTH_SCAN ->
                    flags = PKG_FLAG_GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO | PKG_FLAG_GRANT_PERMS_FOR_ANDROID_AUTO_PHONE_CALLS;

            case
                // unprivileged permission
                Manifest.permission.CALL_PHONE,
                Manifest.permission.CALL_PRIVILEGED,
                Manifest.permission.CONTROL_INCALL_EXPERIENCE,
                // unprivileged permission
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE_ANDROID_AUTO ->
                    flags = PKG_FLAG_GRANT_PERMS_FOR_ANDROID_AUTO_PHONE_CALLS;

            default -> {
                return NO_PERMISSION_OVERRIDE;
            }
        }

        GosPackageStatePm gosPs = LocalServices.getService(PackageManagerInternal.class)
                .getGosPackageState(PackageId.ANDROID_AUTO_NAME, userId);

        long pkgFlags = gosPs != null ? gosPs.packageFlags : 0L;

        return (pkgFlags & flags) != 0 ? PERMISSION_OVERRIDE_GRANT : PERMISSION_OVERRIDE_REVOKE;
    }
}
