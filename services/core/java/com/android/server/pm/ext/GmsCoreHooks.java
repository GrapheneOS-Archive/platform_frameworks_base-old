package com.android.server.pm.ext;

import android.Manifest;
import android.content.pm.ServiceInfo;

import com.android.internal.gmscompat.GmcMediaProjectionService;
import com.android.internal.gmscompat.GmsHooks;
import com.android.internal.pm.pkg.component.ParsedService;
import com.android.internal.pm.pkg.component.ParsedServiceImpl;
import com.android.internal.pm.pkg.component.ParsedUsesPermissionImpl;
import com.android.internal.pm.pkg.parsing.ParsingPackage;

import java.util.Collections;
import java.util.List;

class GmsCoreHooks {

    static class ParsingHooks extends GmsCompatPkgParsingHooks {

        @Override
        public List<ParsedUsesPermissionImpl> addUsesPermissions() {
            var res = super.addUsesPermissions();
            var l = createUsesPerms(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            res.addAll(l);
            return res;
        }

        @Override
        public List<ParsedService> addServices(ParsingPackage pkg) {
            ParsedServiceImpl s = createService(pkg, GmcMediaProjectionService.class.getName());
            s.setProcessName(GmsHooks.PERSISTENT_GmsCore_PROCESS);
            s.setForegroundServiceType(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            s.setExported(false);

            return Collections.singletonList(s);
        }
    }
}
