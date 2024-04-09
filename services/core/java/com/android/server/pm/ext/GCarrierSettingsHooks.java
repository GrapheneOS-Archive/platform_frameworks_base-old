package com.android.server.pm.ext;

import android.Manifest;

import com.android.internal.gmscompat.gcarriersettings.TestCarrierConfigService;
import com.android.internal.pm.pkg.component.ParsedProvider;
import com.android.internal.pm.pkg.component.ParsedService;
import com.android.internal.pm.pkg.component.ParsedUsesPermission;
import com.android.internal.pm.pkg.parsing.ParsingPackage;
import com.android.server.pm.pkg.PackageStateInternal;

import java.util.Collections;
import java.util.List;

class GCarrierSettingsHooks extends PackageHooks {

    static class ParsingHooks extends GmsCompatPkgParsingHooks {

        @Override
        public boolean shouldSkipUsesPermission(ParsedUsesPermission p) {
            // make sure GCarrierSettings doesn't download carrier config overrides (see CarrierConfig2 README),
            // access to GmsCore is blocked too
            return Manifest.permission.INTERNET.equals(p.getName());
        }

        @Override
        public List<ParsedService> addServices(ParsingPackage pkg) {
            ParsedService s = createService(pkg, TestCarrierConfigService.class.getName());
            return Collections.singletonList(s);
        }

        @Override
        public boolean shouldSkipProvider(ParsedProvider p) {
            return "com.google.android.carrier.vendorprovider".equals(p.getAuthority());
        }
    }

    @Override
    public boolean shouldBlockPackageVisibility(int userId, PackageStateInternal otherPkg) {
        // prevent obtaining carrier config overrides from GmsCore (see CarrierConfig2 README)
        return isUserInstalledPkg(otherPkg);
    }
}
