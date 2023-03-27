package com.android.server.pm.ext;

import android.Manifest;

import com.android.internal.pm.pkg.parsing.PackageParsingHooks;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.internal.pm.pkg.component.ParsedUsesPermission;

class EuiccSupportPixelHooks extends PackageHooks {

    static class ParsingHooks extends PackageParsingHooks {
        @Override
        public boolean shouldSkipUsesPermission(ParsedUsesPermission p) {
            // EuiccSupportPixel uses INTERNET perm only as part of its dev mode
            return Manifest.permission.INTERNET.equals(p.getName());
        }
    }

    @Override
    public boolean shouldBlockPackageVisibility(int userId, PackageStateInternal otherPkg) {
        // EuiccSupportPixel is a privileged package, block it from interacting with unprivileged
        // parts of GMS
        return isUserInstalledPkg(otherPkg);
    }
}
