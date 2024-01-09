package com.android.server.pm.ext;

import android.Manifest;
import android.ext.PackageId;

import com.android.internal.pm.pkg.component.ParsedUsesPermission;
import com.android.internal.pm.pkg.parsing.PackageParsingHooks;
import com.android.server.pm.pkg.PackageStateInternal;

public class PixelCameraServicesHooks extends PackageHooks {

    static class ParsingHooks extends PackageParsingHooks {
        @Override
        public boolean shouldSkipUsesPermission(ParsedUsesPermission p) {
            switch (p.getName()) {
                // Pixel Camera Services currently doesn't use any of these permissions.
                // [1] states that PCS (Pixel Camera Servicss) is planned "to be able act as a media
                // app and use Exoplayer for playing recorded video files". This functionality is
                // not implemented in the current release as of January 2024.
                //
                // [1] https://android.googlesource.com/device/google/gs-common/+/46d6a8821196ab313a5f28c009ce9b2548ea2569
                case Manifest.permission.BLUETOOTH_CONNECT:
                case Manifest.permission.BLUETOOTH_SCAN:
                case Manifest.permission.CAPTURE_AUDIO_OUTPUT:
                case Manifest.permission.MODIFY_AUDIO_ROUTING:
                // added in 14 QPR3:
                case Manifest.permission.ACCESS_FINE_LOCATION:
                case Manifest.permission.INTERACT_ACROSS_USERS:
                    return true;
            }

            return false;
        }
    }

    @Override
    public boolean shouldBlockPackageVisibility(int userId, PackageStateInternal otherPkg) {
        // Pixel Camera Services is a privileged app, block it from interacting with unprivileged
        // parts of sandboxed Google Play.
        //
        // Pixel Camera Services currently uses GmsCore only for telemetry (perf data reporting),
        // and doesn't use Play Store, at least directly.
        //
        // Also, Play Store would be unable to update Pixel Camera Services if it was allowed to see
        // it due to the fs-verity requirements for system package updates.
        switch (otherPkg.getPackageName()) {
            case PackageId.GSF_NAME:
            case PackageId.GMS_CORE_NAME:
            case PackageId.PLAY_STORE_NAME:
                return true;
        }

        return false;
    }
}
