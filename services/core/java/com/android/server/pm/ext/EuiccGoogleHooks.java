package com.android.server.pm.ext;

import android.content.pm.PackageManager;

import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.PackageUserStateInternal;

class EuiccGoogleHooks extends PackageHooks {

    static class ParsingHooks extends PackageParsingHooks {

        @Override
        public int overrideDefaultPackageEnabledState() {
            return PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        }
    }
}
