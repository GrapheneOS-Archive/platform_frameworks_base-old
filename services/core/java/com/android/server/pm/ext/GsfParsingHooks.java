package com.android.server.pm.ext;

import com.android.internal.pm.pkg.component.ParsedPermission;

class GsfParsingHooks extends GmsCompatPkgParsingHooks {

    @Override
    public boolean shouldSkipPermissionDefinition(ParsedPermission p) {
        // DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION is used to emulate registering a
        // receiver with RECEIVER_NOT_EXPORTED flag on OS versions older than 13:
        // https://cs.android.com/androidx/platform/frameworks/support/+/0177ceca157c815f5e5e46fe5c90e12d9faf4db3
        // https://cs.android.com/androidx/platform/frameworks/support/+/cb9edef10187fe5e0fc55a49db6b84bbecf4ebf2
        // Normally, it is declared as <package name>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION,
        // (ie com.google.android.gsf.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION for GSF)
        // androidx.core.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION declaration seems to
        // be a build system bug.
        // There's also
        // {androidx.fragment,androidx.legacy.coreutils,does.not.matter}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
        // Each of these prefixes is a packageName of a library that GSF seems to be compiled with.

        // All of these DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION permissions are declared
        // with android:protectionLevel="signature", which means that app installation
        // will fail if an app that has the same declaration is already installed
        // (there are some exceptions to this for system apps, but not for regular apps)

        // System package com.shannon.imsservice declares
        // androidx.core.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (likely due to the same
        // bug), which blocks GSF from being installed.
        // Since this permission isn't actually used for anything, removing it is safe.
        return "androidx.core.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION".equals(p.getName());
    }
}
