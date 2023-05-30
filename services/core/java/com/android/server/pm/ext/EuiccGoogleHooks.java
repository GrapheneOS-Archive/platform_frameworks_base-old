package com.android.server.pm.ext;

import android.Manifest;
import android.content.pm.PackageManager;
import android.ext.PackageId;

import com.android.internal.pm.pkg.component.ParsedUsesPermission;
import com.android.internal.pm.pkg.parsing.PackageParsingHooks;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageStateInternal;

class EuiccGoogleHooks extends PackageHooks {

    static class ParsingHooks extends PackageParsingHooks {

        @Override
        public int overrideDefaultPackageEnabledState() {
            return PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        }

        @Override
        public boolean shouldSkipUsesPermission(ParsedUsesPermission p) {
            switch (p.getName()) {
                // Carrier apps aren't shipped on GrapheneOS, these permissions are needed only to
                // install/enable them
                case Manifest.permission.INSTALL_EXISTING_PACKAGES:
                case Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE:
                    return true;
            }

            return false;
        }
    }

    @Override
    public boolean shouldBlockPackageVisibility(int userId, PackageStateInternal otherPkg) {
        // EuiccGoogle is a privileged app, block it from interacting with unprivileged
        // parts of sandboxed Google Play
        switch (otherPkg.getPackageName()) {
            case PackageId.GSF_NAME:
            case PackageId.GMS_CORE_NAME:
            case PackageId.PLAY_STORE_NAME:
                return true;
        }

        // Some third-party carrier apps need to interact with EuiccGoogle for eSIM activation
        return false;
    }
}
