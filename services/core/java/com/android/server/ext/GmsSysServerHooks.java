/*
 * Copyright (C) 2022 GrapheneOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ext;

import android.Manifest;
import android.os.Bundle;

import com.android.internal.gmscompat.GmsInfo;
import com.android.internal.gmscompat.client.GmsCompatClientService;
import com.android.server.pm.pkg.component.ParsedPermission;
import com.android.server.pm.pkg.component.ParsedServiceImpl;
import com.android.server.pm.pkg.component.ParsedUsesPermissionImpl;
import com.android.server.pm.pkg.parsing.ParsingPackage;

import java.util.List;

public class GmsSysServerHooks {

    // ParsingPackageUtils#parseBaseApplication
    public static void fixupPermissions(ParsingPackage pkg) {
        String pkgName = pkg.getPackageName();

        if (GmsInfo.PACKAGE_PLAY_STORE.equals(pkgName)) {
            String[] perms = {
                    Manifest.permission.REQUEST_INSTALL_PACKAGES,
                    Manifest.permission.REQUEST_DELETE_PACKAGES,
                    Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION,
            };
            for (String perm : perms) {
                pkg.addUsesPermission(new ParsedUsesPermissionImpl(perm, 0));
            }
        } else if (GmsInfo.PACKAGE_GSF.equals(pkgName)) {
            List<ParsedPermission> perms = pkg.getPermissions();
            for (int i = 0, m = perms.size(); i < m; ++i) {
                var p = perms.get(i);
                if ("androidx.core.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION".equals(p.getName())) {
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
                    perms.remove(i);
                    break;
                }
            }
        }
    }

    // ParsingPackageUtils#parseBaseApplication
    public static void maybeAddServiceDuringParsing(ParsingPackage pkg) {
        Bundle metadata = pkg.getMetaData();
        boolean isGmsClient = metadata != null && metadata.containsKey("com.google.android.gms.version");

        if (!isGmsClient) {
            return;
        }

        if (GmsInfo.PACKAGE_GMS_CORE.equals(pkg.getPackageName())) {
            return;
        }

        ParsedServiceImpl s = new ParsedServiceImpl();
        s.setPackageName(pkg.getPackageName());
        s.setName(GmsCompatClientService.class.getName());
        s.setProcessName(pkg.getProcessName());

        s.setDirectBootAware(pkg.isPartiallyDirectBootAware());
        s.setExported(true);

        pkg.addService(s);
    }
}
