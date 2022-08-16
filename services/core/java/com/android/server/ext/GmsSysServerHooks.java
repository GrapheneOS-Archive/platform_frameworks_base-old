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
import com.android.server.pm.pkg.component.ParsedServiceImpl;
import com.android.server.pm.pkg.component.ParsedUsesPermissionImpl;
import com.android.server.pm.pkg.parsing.ParsingPackage;

public class GmsSysServerHooks {

    // ParsingPackageUtils#parseBaseApplication
    public static void maybeAddUsesPermission(ParsingPackage pkg) {
        if (!GmsInfo.PACKAGE_PLAY_STORE.equals(pkg.getPackageName())) {
            return;
        }

        String[] perms = {
                Manifest.permission.REQUEST_INSTALL_PACKAGES,
                Manifest.permission.REQUEST_DELETE_PACKAGES,
                Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION,
        };
        for (String perm : perms) {
            pkg.addUsesPermission(new ParsedUsesPermissionImpl(perm, 0));
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
