package com.android.server.pm.ext;

import android.Manifest;

import com.android.internal.pm.pkg.component.ParsedUsesPermissionImpl;

import java.util.List;

class PlayStoreHooks {

    static class ParsingHooks extends GmsCompatPkgParsingHooks {

        @Override
        public List<ParsedUsesPermissionImpl> addUsesPermissions() {
            var res = super.addUsesPermissions();
            res.addAll(createUsesPerms(
                    Manifest.permission.REQUEST_INSTALL_PACKAGES,
                    Manifest.permission.REQUEST_DELETE_PACKAGES,
                    Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION
            ));
            return res;
        }
    }
}
