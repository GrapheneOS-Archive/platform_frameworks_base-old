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

package com.android.server.pm.permission;

import android.Manifest;
import android.os.Bundle;

import com.android.internal.annotations.GuardedBy;
import com.android.server.pm.parsing.pkg.AndroidPackage;
import com.android.server.pm.pkg.component.ParsedUsesPermission;

import static android.content.pm.SpecialRuntimePermAppUtils.*;

public class SpecialRuntimePermUtils {

    @GuardedBy("PackageManagerService.mLock")
    public static int getFlags(AndroidPackage pkg) {
        int flags = 0;

        for (ParsedUsesPermission perm : pkg.getUsesPermissions()) {
            String name = perm.getName();
            switch (name) {
                default:
                    continue;
            }
        }

        return flags;
    }

    private SpecialRuntimePermUtils() {}
}
