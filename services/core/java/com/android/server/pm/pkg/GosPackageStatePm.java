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

package com.android.server.pm.pkg;

import android.annotation.Nullable;
import android.content.pm.GosPackageState;

import com.android.server.pm.Computer;
import com.android.server.pm.PackageManagerService;

import java.util.Arrays;

import static android.content.pm.GosPackageState.FLAG_ALLOW_ACCESS_TO_OBB_DIRECTORY;
import static android.content.pm.GosPackageState.FLAG_STORAGE_SCOPES_ENABLED;

/**
 * GrapheneOS-specific package state, stored in PackageUserState (per-user, removed during uninstallation).
 *
 * Used directly by the PackageManagerService and by other system_server components,
 * an instance of "external" GosPackageState class is returned to other users.
 *
 * Note that if the package has a sharedUserId, then its GosPackageState is used for all other
 * packages in that sharedUserId. This is done because in some cases (eg when an app accesses
 * MediaProvider via FUSE) there's no way to retrieve the package name, only UID is available.
 * In that case MediaProvider calls getPackageManager().getPackagesForUid(uid)[0] which means that
 * the package name will be wrong if uid belongs to a sharedUserId package that is not at 0-th index
 * in that array.
 *
 * This is a similar approach to the one that the permission system uses: permissions are enforced per-UID,
 * not per package, which means that a sharedUserId package may have permissions that it didn't declare
 * in its AndroidManifest if other packages that use the same sharedUserId are granted those permissions.
 * (see com.android.server.pm.permission.UidPermissionState class and its users)
 *
 * sharedUserIds are deprecated since API 29, with the following note:
 * "Shared user IDs cause non-deterministic behavior within the package manager.
 * As such, its use is strongly discouraged and may be removed in a future version of Android."
 *
 * @hide
 */
public final class GosPackageStatePm {
    public final int flags;
    @Nullable
    public final byte[] storageScopes;

    public GosPackageStatePm(int flags, @Nullable byte[] storageScopes) {
        this.flags = flags;
        this.storageScopes = storageScopes;
    }

    @Nullable
    public static GosPackageStatePm get(PackageManagerService pm, String packageName, int userId) {
        return get(pm.snapshotComputer(), packageName, userId);
    }

    public static GosPackageStatePm get(Computer snapshot, String packageName, int userId) {
        PackageStateInternal psi = snapshot.getPackageStates().get(packageName);
        if (psi == null) {
            return null;
        }

        return get(snapshot, psi, userId);
    }

    public static GosPackageStatePm get(Computer snapshot, PackageStateInternal psi, int userId) {
        GosPackageStatePm res = psi.getUserStateOrDefault(userId).getGosPackageState();
        if (res != null) {
            return res;
        }

        return maybeGetForSharedUserPackage(snapshot, psi, userId);
    }

    @Nullable
    private static GosPackageStatePm maybeGetForSharedUserPackage(Computer snapshot, PackageStateInternal psi, int userId) {
        if (!psi.hasSharedUser()) {
            return null;
        }

        SharedUserApi sharedUser = snapshot.getSharedUser(psi.getSharedUserAppId());

        if (sharedUser == null) {
            return null;
        }

        var packageStates = sharedUser.getPackageStates();

        for (int i = 0, m = packageStates.size(); i < m; ++i) {
            var entry = packageStates.valueAtUnchecked(i);
            if (entry == null) {
                continue;
            }

            GosPackageStatePm s = entry.getUserStateOrDefault(userId).getGosPackageState();
            if (s != null) {
                return s;
            }
        }

        return null;
    }

    public static GosPackageState.Editor getEditor(PackageManagerService pm, String packageName, int userId) {
        var ps = get(pm, packageName, userId);

        if (ps != null) {
            return new GosPackageState.Editor(packageName, userId, ps.flags, ps.storageScopes);
        }

        return new GosPackageState.Editor(packageName, userId);
    }

    public boolean hasFlags(int flags) {
        return (this.flags & flags) == flags;
    }

    @Override
    public int hashCode() {
        return 31 * flags + Arrays.hashCode(storageScopes);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GosPackageStatePm)) {
            return false;
        }

        GosPackageStatePm o = (GosPackageStatePm) obj;
        if (flags != o.flags) {
            return false;
        }

        if (!Arrays.equals(storageScopes, o.storageScopes)) {
            return false;
        }

        return true;
    }

    public GosPackageState externalVersionForPrivilegedCallers(int derivedFlags) {
        return new GosPackageState(flags, storageScopes, derivedFlags);
    }

    private static final int FLAGS_VISIBLE_TO_THE_TARGET_PACKAGE = FLAG_STORAGE_SCOPES_ENABLED
            | FLAG_ALLOW_ACCESS_TO_OBB_DIRECTORY;
    private static final int DFLAGS_VISIBLE_TO_THE_TARGET_PACKAGE = -1;

    public GosPackageState externalVersionForTargetPackage(int derivedFlags) {
        return new GosPackageState(flags & FLAGS_VISIBLE_TO_THE_TARGET_PACKAGE, null,
                derivedFlags & DFLAGS_VISIBLE_TO_THE_TARGET_PACKAGE);
    }
}
