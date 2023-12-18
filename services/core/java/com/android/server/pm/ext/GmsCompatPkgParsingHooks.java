package com.android.server.pm.ext;

import android.Manifest;
import android.annotation.CallSuper;
import android.annotation.Nullable;
import android.app.compat.gms.GmsCompat;
import android.content.pm.ServiceInfo;
import android.ext.PackageId;
import android.os.Bundle;

import com.android.internal.gmscompat.client.GmsCompatClientService;
import com.android.internal.pm.pkg.component.ParsedService;
import com.android.internal.pm.pkg.component.ParsedServiceImpl;
import com.android.internal.pm.pkg.component.ParsedUsesPermissionImpl;
import com.android.internal.pm.pkg.parsing.PackageParsingHooks;
import com.android.internal.pm.pkg.parsing.ParsingPackage;

import java.util.List;

public class GmsCompatPkgParsingHooks extends PackageParsingHooks {

    @Nullable
    public static PackageParsingHooks maybeGet(String pkgName) {
        if (!GmsCompat.canBeEnabledFor(pkgName)) {
            return null;
        }

        return switch (pkgName) {
            case PackageId.GSF_NAME -> new GsfParsingHooks();
            case PackageId.GMS_CORE_NAME -> new GmsCoreHooks.ParsingHooks();
            case PackageId.PLAY_STORE_NAME -> new PlayStoreHooks.ParsingHooks();
            case PackageId.G_CARRIER_SETTINGS_NAME -> new GCarrierSettingsHooks.ParsingHooks();
            case PackageId.ANDROID_AUTO_NAME -> new AndroidAutoHooks.ParsingHooks();
            default -> new GmsCompatPkgParsingHooks();
        };
    }

    public static ParsedService maybeCreateClientService(ParsingPackage pkg) {
        Bundle metadata = pkg.getMetaData();
        boolean isGmsCoreClient = metadata != null && metadata.containsKey("com.google.android.gms.version");

        if (!isGmsCoreClient || PackageId.GMS_CORE_NAME.equals(pkg.getPackageName())) {
            return null;
        }

        return createService(pkg, GmsCompatClientService.class.getName());
    }

    @CallSuper
    @Override
    public List<ParsedUsesPermissionImpl> addUsesPermissions() {
        return createUsesPerms(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE);
    }

    @CallSuper
    @Override
    public void amendParsedService(ParsedServiceImpl s) {
        if (s.getForegroundServiceType() == ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED) {
            // FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED requires special privileges
            s.setForegroundServiceType(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }
    }
}
