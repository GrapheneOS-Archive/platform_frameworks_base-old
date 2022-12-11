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

package com.android.internal.gmscompat.sysservice;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.ApplicationPackageManager;
import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.VersionedPackage;
import android.os.UserHandle;
import android.util.ArraySet;

import com.android.internal.gmscompat.PlayStoreHooks;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("WrongConstant") // lint doesn't like "flags & ~" expressions
public class GmcPackageManager extends ApplicationPackageManager {

    public GmcPackageManager(Context context, IPackageManager pm) {
        super(context, pm);
    }

    @Override
    public void deletePackage(String packageName, IPackageDeleteObserver observer, int flags) {
        if (GmsCompat.isPlayStore()) {
            PlayStoreHooks.deletePackage(this, packageName, observer, flags);
            return;
        }

        super.deletePackage(packageName, observer, flags);
    }

    @Override
    public void freeStorageAndNotify(String volumeUuid, long idealStorageSize, IPackageDataObserver observer) {
        if (GmsCompat.isPlayStore()) {
            PlayStoreHooks.freeStorageAndNotify(volumeUuid, idealStorageSize, observer);
            return;
        }

        super.freeStorageAndNotify(volumeUuid, idealStorageSize, observer);
    }

    @Override
    public void setApplicationEnabledSetting(String packageName, int newState, int flags) {
        if (GmsCompat.isPlayStore()) {
            PlayStoreHooks.setApplicationEnabledSetting(packageName, newState);
            return;
        }

        super.setApplicationEnabledSetting(packageName, newState, flags);
    }

    @Override
    public boolean hasSystemFeature(String name) {
        switch (name) {
            // checked before accessing privileged UwbManager
            case "android.hardware.uwb":
                return false;
        }

        return super.hasSystemFeature(name);
    }

    // requires privileged OBSERVE_GRANT_REVOKE_PERMISSIONS permission
    @Override
    public void addOnPermissionsChangeListener(OnPermissionsChangedListener listener) {}

    // MATCH_ANY_USER flag requires privileged INTERACT_ACROSS_USERS permission

    private static PackageInfoFlags filterFlags(PackageInfoFlags flags) {
        long v = flags.getValue();

        if ((v & MATCH_ANY_USER) != 0) {
            return PackageInfoFlags.of(v & ~MATCH_ANY_USER);
        }

        return flags;
    }

    @Override
    public @NonNull List<SharedLibraryInfo> getSharedLibraries(PackageInfoFlags flags) {
        return super.getSharedLibraries(filterFlags(flags));
    }

    private static final ArraySet<String> HIDDEN_PACKAGES = new ArraySet<>(new String[] {
            "app.attestation.auditor",
    });

    private static void throwIfHidden(String pkgName) throws NameNotFoundException {
        if (HIDDEN_PACKAGES.contains(pkgName)) {
            throw new NameNotFoundException();
        }
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, PackageInfoFlags flags) throws NameNotFoundException {
        throwIfHidden(packageName);
        return super.getPackageInfo(packageName, filterFlags(flags));
    }

    @Override
    public PackageInfo getPackageInfo(VersionedPackage versionedPackage, PackageInfoFlags flags) throws NameNotFoundException {
        throwIfHidden(versionedPackage.getPackageName());
        return super.getPackageInfo(versionedPackage, filterFlags(flags));
    }

    @Override
    public PackageInfo getPackageInfoAsUser(String packageName, PackageInfoFlags flags, int userId) throws NameNotFoundException {
        throwIfHidden(packageName);
        return super.getPackageInfoAsUser(packageName, filterFlags(flags), userId);
    }

    @Override
    public List<PackageInfo> getInstalledPackagesAsUser(PackageInfoFlags flags, int userId) {
        List<PackageInfo> ret = super.getInstalledPackagesAsUser(filterFlags(flags), userId);
        List<PackageInfo> res = new ArrayList<>(ret.size());

        for (PackageInfo pi : ret) {
            if (HIDDEN_PACKAGES.contains(pi.packageName)) {
                continue;
            }
            res.add(pi);
        }

        return res;
    }

    @Override
    public String[] getPackagesForUid(int uid) {
        int userId = UserHandle.getUserId(uid);
        int myUserId = UserHandle.myUserId();

        if (userId != myUserId) {
            if (userId != 0) {
                throw new IllegalArgumentException("uid from unexpected userId: " + uid);
            }
            // querying uids from other userIds requires a privileged permission
            uid = UserHandle.getUid(myUserId, UserHandle.getAppId(uid));
        }

        return super.getPackagesForUid(uid);
    }
}
