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

import com.android.internal.gmscompat.PlayStoreHooks;

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

    @Override
    public List<SharedLibraryInfo> getSharedLibraries(int flags) {
        return super.getSharedLibraries(flags & ~MATCH_ANY_USER);
    }

    @Override
    public PackageInfo getPackageInfo(VersionedPackage versionedPackage, int flags)
            throws NameNotFoundException {
        return super.getPackageInfo(versionedPackage, flags & ~MATCH_ANY_USER);
    }

    @Override
    public PackageInfo getPackageInfoAsUser(String packageName, int flags, int userId)
            throws NameNotFoundException {
        return super.getPackageInfoAsUser(packageName, flags & ~MATCH_ANY_USER, userId);
    }

    @Override
    public List<PackageInfo> getInstalledPackages(int flags) {
        return super.getInstalledPackages(flags & ~MATCH_ANY_USER);
    }
}
