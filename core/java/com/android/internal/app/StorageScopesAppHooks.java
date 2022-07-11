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

package com.android.internal.app;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.GosPackageState;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.provider.Settings;

import static android.content.pm.GosPackageState.*;

public class StorageScopesAppHooks {
    private static final String TAG = "StorageScopesAppHooks";

    private static int isSpoofableRuntimePermission(String permName) {
        switch (permName) {
            case Manifest.permission.READ_EXTERNAL_STORAGE:
                return DFLAG_HAS_READ_EXTERNAL_STORAGE_DECLARATION;

            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return DFLAG_HAS_WRITE_EXTERNAL_STORAGE_DECLARATION;

            case Manifest.permission.ACCESS_MEDIA_LOCATION:
                return DFLAG_HAS_ACCESS_MEDIA_LOCATION_DECLARATION;

            // TODO uncomment after rebase onto AOSP T
            /*
            case Manifest.permission.READ_MEDIA_AUDIO:
                return DFLAG_HAS_READ_MEDIA_AUDIO_DECLARATION;

            case Manifest.permission.READ_MEDIA_IMAGES:
                return DFLAG_HAS_READ_MEDIA_IMAGES_DECLARATION;

            case Manifest.permission.READ_MEDIA_VIDEO:
                return DFLAG_HAS_READ_MEDIA_VIDEO_DECLARATION;
             */
            default:
                return 0;
        }
    }

    private static boolean shouldSpoofPermissionCheck(@Nullable GosPackageState ps, int permDerivedFlag) {
        if (ps == null) {
            return false;
        }
        return ps.hasFlag(FLAG_STORAGE_SCOPES_ENABLED) && ps.hasDerivedFlag(permDerivedFlag);
    }

    // android.app.ApplicationPackageManager#checkPermission(String permName, String pkgName)
    // android.app.ContextImpl#checkPermission(String permission, int pid, int uid)
    public static boolean shouldSpoofSelfPermissionCheck(@NonNull String permName) {
        int permId = isSpoofableRuntimePermission(permName);
        if (permId != 0) {
            return shouldSpoofPermissionCheck(GosPackageState.getForSelf(), permId);
        }
        return false;
    }

    // android.app.Activity#dispatchRequestPermissionsResult(int, Intent)
    // android.app.Activity#dispatchRequestPermissionsResultToFragment(int, Intent, Fragment)
    public static void maybeSpoofSelfPermissionChecks(@NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean psObtained = false;
        GosPackageState ps = null;

        for (int i = 0; i < permissions.length; ++i) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                String perm = permissions[i];
                int permId = isSpoofableRuntimePermission(perm);
                if (permId == 0) {
                    continue;
                }

                if (!psObtained) {
                    ps = GosPackageState.getForSelf();
                    if (ps == null) {
                        return;
                    }
                    psObtained = true;
                }

                if (shouldSpoofPermissionCheck(ps, permId)) {
                    grantResults[i] = PackageManager.PERMISSION_GRANTED;
                }
            }
        }
    }

    public static int isSpoofableAppOp(int op) {
        switch (op) {
            case AppOpsManager.OP_READ_EXTERNAL_STORAGE:
                return DFLAG_HAS_READ_EXTERNAL_STORAGE_DECLARATION;

            case AppOpsManager.OP_WRITE_EXTERNAL_STORAGE:
                return DFLAG_HAS_WRITE_EXTERNAL_STORAGE_DECLARATION;

            case AppOpsManager.OP_READ_MEDIA_AUDIO:
                return DFLAG_HAS_READ_MEDIA_AUDIO_DECLARATION;

            case AppOpsManager.OP_READ_MEDIA_IMAGES:
                return DFLAG_HAS_READ_MEDIA_IMAGES_DECLARATION;

            case AppOpsManager.OP_READ_MEDIA_VIDEO:
                return DFLAG_HAS_READ_MEDIA_VIDEO_DECLARATION;

            case AppOpsManager.OP_MANAGE_EXTERNAL_STORAGE:
                return DFLAG_HAS_MANAGE_EXTERNAL_STORAGE_DECLARATION;

            case AppOpsManager.OP_MANAGE_MEDIA:
                return DFLAG_HAS_MANAGE_MEDIA_DECLARATION;

            case AppOpsManager.OP_ACCESS_MEDIA_LOCATION:
                return DFLAG_HAS_ACCESS_MEDIA_LOCATION_DECLARATION;

            default:
                return 0;
        }
    }

    public static boolean shouldSpoofAppOpCheck(int op, int uid) {
        int opDerivedFlag = isSpoofableAppOp(op);

        if (opDerivedFlag == 0) {
            return false;
        }

        return shouldSpoofAppOpCheckInner(uid, opDerivedFlag);
    }

    private static boolean shouldSpoofAppOpCheckInner(int uid, int opDerivedFlag) {
        if (!isSpoofingAllowed(uid)) {
            return false;
        }

        GosPackageState ps = GosPackageState.getForSelf();
        return ps != null && ps.hasFlag(FLAG_STORAGE_SCOPES_ENABLED) && ps.hasDerivedFlag(opDerivedFlag);
    }

    private static int cachedMyUid;

    private static boolean isSpoofingAllowed(int uid) {
        int myUid = cachedMyUid;
        if (myUid == 0) {
            myUid = Process.myUid();
            cachedMyUid = myUid;
        }

        return uid == myUid && uid != Process.SYSTEM_UID;
    }

    // Instrumentation#execStartActivity(Context, IBinder, IBinder, Activity, Intent, int, Bundle)
    public static void maybeModifyActivityIntent(Context ctx, Intent i) {
        String action = i.getAction();
        if (action == null) {
            return;
        }

        int op;
        switch (action) {
            case Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION:
                op = AppOpsManager.OP_MANAGE_EXTERNAL_STORAGE;
                break;
            case Settings.ACTION_REQUEST_MANAGE_MEDIA:
                op = AppOpsManager.OP_MANAGE_MEDIA;
                break;
            default:
                return;
        }

        Uri uri = i.getData();
        if (uri == null || !"package".equals(uri.getScheme())) {
            return;
        }

        String pkgName = uri.getSchemeSpecificPart();

        if (pkgName == null) {
            return;
        }

        if (!pkgName.equals(AppGlobals.getInitialPackage())) {
            return;
        }

        int uid = Process.myUid();

        if (!isSpoofingAllowed(uid)) {
            return;
        }

        boolean shouldModify = false;

        if (shouldSpoofAppOpCheck(op, uid)) {
            // in case a buggy app launches intent again despite pseudo-having the permission
            shouldModify = true;
        } else {
            if (op == AppOpsManager.OP_MANAGE_EXTERNAL_STORAGE) {
                shouldModify = !Environment.isExternalStorageManager();
            } else if (op == AppOpsManager.OP_MANAGE_MEDIA) {
                shouldModify = !MediaStore.canManageMedia(ctx);
            }
        }

        if (shouldModify) {
            i.setAction(action + "_PROMPT");
        }
    }

    private StorageScopesAppHooks() {}
}
