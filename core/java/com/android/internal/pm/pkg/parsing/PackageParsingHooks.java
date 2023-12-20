package com.android.internal.pm.pkg.parsing;

import android.annotation.Nullable;
import android.content.pm.PackageManager;

import com.android.internal.pm.pkg.component.ParsedPermission;
import com.android.internal.pm.pkg.component.ParsedProvider;
import com.android.internal.pm.pkg.component.ParsedService;
import com.android.internal.pm.pkg.component.ParsedServiceImpl;
import com.android.internal.pm.pkg.component.ParsedUsesPermission;
import com.android.internal.pm.pkg.component.ParsedUsesPermissionImpl;

import java.util.ArrayList;
import java.util.List;

public class PackageParsingHooks {
    public static final PackageParsingHooks DEFAULT = new PackageParsingHooks();

    public boolean shouldSkipPermissionDefinition(ParsedPermission p) {
        return false;
    }

    public boolean shouldSkipUsesPermission(ParsedUsesPermission p) {
        return false;
    }

    public boolean shouldSkipProvider(ParsedProvider p) {
        return false;
    }

    @Nullable
    public List<ParsedUsesPermissionImpl> addUsesPermissions() {
        return null;
    }

    protected static List<ParsedUsesPermissionImpl> createUsesPerms(String... perms) {
        int l = perms.length;
        var res = new ArrayList<ParsedUsesPermissionImpl>(l);
        for (int i = 0; i < l; ++i) {
            res.add(new ParsedUsesPermissionImpl(perms[i], 0));
        }
        return res;
    }

    public void amendParsedService(ParsedServiceImpl s) {

    }

    public List<ParsedService> addServices(ParsingPackage pkg) {
        return null;
    }

    // supported return values:
    // PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    // PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    // PackageManager.COMPONENT_ENABLED_STATE_DEFAULT (skip override)
    public int overrideDefaultPackageEnabledState() {
        return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
    }

    public static ParsedServiceImpl createService(ParsingPackage pkg, String className) {
        var s = new ParsedServiceImpl();
        s.setPackageName(pkg.getPackageName());
        s.setName(className);
        s.setProcessName(pkg.getProcessName());
        s.setDirectBootAware(pkg.isPartiallyDirectBootAware());
        s.setExported(true);
        return s;
    }
}
