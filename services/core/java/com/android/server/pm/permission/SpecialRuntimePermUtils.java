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
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.LruCache;
import android.util.Slog;
import android.util.SparseArray;

import com.android.server.LocalServices;
import com.android.server.ext.SystemServerExt;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.parsing.pkg.AndroidPackage;
import com.android.server.pm.pkg.component.ParsedUsesPermission;

import java.util.List;

import static android.content.pm.SpecialRuntimePermAppUtils.*;

public class SpecialRuntimePermUtils {
    private static final String TAG = "SpecialRuntimePermUtils";

    public static boolean isSpecialRuntimePermission(String permission) {
        switch (permission) {
            case Manifest.permission.INTERNET:
            case Manifest.permission.OTHER_SENSORS:
                return true;
        }
        return false;
    }

    public static boolean shouldAutoGrant(String packageName, int userId, String perm) {
        if (!isSpecialRuntimePermission(perm)) {
            return false;
        }

        if (Manifest.permission.OTHER_SENSORS.equals(perm)) {
            if (ActivityManager.getService() == null) {
                // a failsafe: should never happen
                Slog.d(TAG, "AMS is null");
                if (Build.isDebuggable()) {
                    throw new IllegalStateException();
                }
                return false;
            }

            var um = LocalServices.getService(UserManagerInternal.class);
            // use parent profile settings for work profile
            int userIdForSettings = um.getProfileParentId(userId);

            Context ctx = SystemServerExt.get().context;
            var cr = ctx.getContentResolver();
            var key = Settings.Secure.AUTO_GRANT_OTHER_SENSORS_PERMISSION;
            int def = Settings.Secure.AUTO_GRANT_OTHER_SENSORS_PERMISSION_DEFAULT;

            try {
                return Settings.Secure.getIntForUser(cr, key, def, userIdForSettings) == 1;
            } catch (Exception e) {
                // a failsafe: should never happen
                Slog.d(TAG, "", e);
                if (Build.isDebuggable()) {
                    throw new IllegalStateException(e);
                }
                return false;
            }
        }

        return !isAutoGrantSkipped(packageName, userId, perm);
    }

    public static int getFlags(AndroidPackage pkg) {
        int flags = 0;

        for (ParsedUsesPermission perm : pkg.getUsesPermissions()) {
            String name = perm.getName();
            switch (name) {
                case Manifest.permission.INTERNET:
                    flags |= FLAG_REQUESTS_INTERNET_PERMISSION;
                    continue;
                default:
                    continue;
            }
        }

        if ((flags & FLAG_REQUESTS_INTERNET_PERMISSION) != 0) {
            if (pkg.isSystem()) {
                flags |= FLAG_AWARE_OF_RUNTIME_INTERNET_PERMISSION;
            } else {
                Bundle metadata = pkg.getMetaData();
                if (metadata != null) {
                    String key = Manifest.permission.INTERNET + ".mode";
                    if ("runtime".equals(metadata.getString(key))) {
                        flags |= FLAG_AWARE_OF_RUNTIME_INTERNET_PERMISSION;
                    }
                }
            }
        }

        return flags;
    }

    // Maps userIds to map of package names to permissions that should not be auto granted
    private static SparseArray<LruCache<String, List<String>>> skipAutoGrantsMap = new SparseArray<>();

    public static void skipAutoGrantsForPackage(String packageName, int userId, List<String> perms) {
        var pkg = LocalServices.getService(PackageManagerInternal.class).getPackage(packageName);
        if (pkg != null && pkg.isSystem()) {
            return;
        }

        synchronized (skipAutoGrantsMap) {
            LruCache<String, List<String>> userMap = skipAutoGrantsMap.get(userId);
            if (userMap == null) {
                // 50 entries should be enough, only 1 is needed in vast majority of cases
                userMap = new LruCache<>(50);
                skipAutoGrantsMap.put(userId, userMap);
            }
            userMap.put(packageName, perms);
        }
    }

    private static boolean isAutoGrantSkipped(String packageName, int userId, String perm) {
        List<String> permList;
        synchronized (skipAutoGrantsMap) {
            LruCache<String, List<String>> userMap = skipAutoGrantsMap.get(userId);
            if (userMap == null) {
                return false;
            }
            permList = userMap.get(packageName);
        }
        if (permList == null) {
            return false;
        }
        return permList.contains(perm);
    }

    private SpecialRuntimePermUtils() {}
}
