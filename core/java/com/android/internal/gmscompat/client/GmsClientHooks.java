/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.gmscompat.client;

import android.content.pm.parsing.ParsingPackage;
import android.content.pm.parsing.component.ParsedService;
import android.os.Bundle;

import com.android.internal.gmscompat.GmsInfo;

public class GmsClientHooks {
    private static final String TAG = "GmsClientHooks";

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

        ParsedService s = new ParsedService();
        s.setPackageName(pkg.getPackageName());
        s.setName(GmsCompatClientService.class.getName());
        s.setProcessName(pkg.getProcessName());

        s.setDirectBootAware(pkg.isPartiallyDirectBootAware());
        s.setExported(true);

        pkg.addService(s);
    }

    private GmsClientHooks() {}
}
