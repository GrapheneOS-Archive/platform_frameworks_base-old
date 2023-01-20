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
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ApplicationPackageManager;
import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageManager;
import android.content.pm.InstallSourceInfo;
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

        maybeInitPseudoDisabledPackages();
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
            if (isPseudoDisabledPackage(packageName)) {
                try {
                    // check whether this package is actually absent
                    super.getApplicationInfoAsUser(packageName, ApplicationInfoFlags.of(0L), getUserId());
                } catch (NameNotFoundException e) {
                    // package state tracking happens in the same process that tries to enable
                    // the package, no need to sync this across all processes, at least for now
                    removePseudoDisabledPackage(packageName);
                    GmsCompat.appContext().getMainThreadHandler().post(() ->
                            PlayStoreHooks.updatePackageState(packageName, Intent.ACTION_PACKAGE_REMOVED));
                    return;
                }
            }
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
    public PackageInfo getPackageInfo(VersionedPackage versionedPackage, PackageInfoFlags flags) throws NameNotFoundException {
        throwIfHidden(versionedPackage.getPackageName());
        flags = filterFlags(flags);
        try {
            return super.getPackageInfo(versionedPackage, flags);
        } catch (NameNotFoundException e) {
            return makePseudoDisabledPackageInfoOrThrow(versionedPackage.getPackageName(), flags);
        }
    }

    @Override
    public PackageInfo getPackageInfoAsUser(String packageName, PackageInfoFlags flags, int userId) throws NameNotFoundException {
        throwIfHidden(packageName);
        flags = filterFlags(flags);
        try {
            return super.getPackageInfoAsUser(packageName, flags, userId);
        } catch (NameNotFoundException e) {
            return makePseudoDisabledPackageInfoOrThrow(packageName, flags);
        }
    }

    @Override
    public ApplicationInfo getApplicationInfoAsUser(String packageName, ApplicationInfoFlags flags, int userId) throws NameNotFoundException {
        try {
            return super.getApplicationInfoAsUser(packageName, flags, userId);
        } catch (NameNotFoundException e) {
            return makePseudoDisabledApplicationInfoOrThrow(packageName, flags);
        }
    }

    @Override
    public List<ApplicationInfo> getInstalledApplicationsAsUser(ApplicationInfoFlags flags, int userId) {
        List<ApplicationInfo> ret = super.getInstalledApplicationsAsUser(flags, userId);
        List<ApplicationInfo> res = new ArrayList<>(ret.size());

        ArraySet<String> pseudoDisabledPackages = clonePseudoDisabledPackages();

        for (ApplicationInfo ai : ret) {
            String pkgName = ai.packageName;
            if (HIDDEN_PACKAGES.contains(pkgName)) {
                continue;
            }
            pseudoDisabledPackages.remove(pkgName);
            res.add(ai);
        }

        for (String pkg : pseudoDisabledPackages) {
            ApplicationInfo ai = maybeMakePseudoDisabledApplicationInfo(pkg, flags);
            if (ai != null) {
                res.add(ai);
            }
        }

        return res;
    }

    @Override
    public List<PackageInfo> getInstalledPackagesAsUser(PackageInfoFlags flags, int userId) {
        flags = filterFlags(flags);
        List<PackageInfo> ret = super.getInstalledPackagesAsUser(flags, userId);
        List<PackageInfo> res = new ArrayList<>(ret.size());

        ArraySet<String> pseudoDisabledPackages = clonePseudoDisabledPackages();

        for (PackageInfo pi : ret) {
            String pkgName = pi.packageName;
            if (HIDDEN_PACKAGES.contains(pkgName)) {
                continue;
            }
            pseudoDisabledPackages.remove(pkgName);
            res.add(pi);
        }

        for (String pkg : pseudoDisabledPackages) {
            PackageInfo pi = maybeMakePseudoDisabledPackageInfo(pkg, flags);
            if (pi != null) {
                res.add(pi);
            }
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

    @Override
    public int getApplicationEnabledSetting(String packageName) {
        try {
            return super.getApplicationEnabledSetting(packageName);
        } catch (Exception e) {
            if (isPseudoDisabledPackage(packageName)) {
                return COMPONENT_ENABLED_STATE_DISABLED_USER;
            }
            throw e;
        }
    }

    @Override
    public String getInstallerPackageName(String packageName) {
        try {
            return super.getInstallerPackageName(packageName);
        } catch (Exception e) {
            if (isPseudoDisabledPackage(packageName)) {
                return getInstallerPackageName(selfPkgName());
            }
            throw e;
        }
    }

    @Override
    public InstallSourceInfo getInstallSourceInfo(String packageName) throws NameNotFoundException {
        try {
            return super.getInstallSourceInfo(packageName);
        } catch (NameNotFoundException e) {
            if (isPseudoDisabledPackage(packageName)) {
                return getInstallSourceInfo(selfPkgName());
            }
            throw e;
        }
    }

    private PackageInfo makePseudoDisabledPackageInfoOrThrow(String pkgName, PackageInfoFlags flags) throws NameNotFoundException {
        if (!isPseudoDisabledPackage(pkgName)) {
            throw new NameNotFoundException();
        }
        PackageInfo pi = maybeMakePseudoDisabledPackageInfo(pkgName, flags);
        if (pi == null) {
            throw new NameNotFoundException();
        }
        return pi;
    }

    private ApplicationInfo makePseudoDisabledApplicationInfoOrThrow(String pkgName, ApplicationInfoFlags flags) throws NameNotFoundException {
        if (!isPseudoDisabledPackage(pkgName)) {
            throw new NameNotFoundException();
        }
        ApplicationInfo ai = maybeMakePseudoDisabledApplicationInfo(pkgName, flags);
        if (ai == null) {
            throw new NameNotFoundException();
        }
        return ai;
    }

    @Nullable
    private PackageInfo maybeMakePseudoDisabledPackageInfo(String pkgName, PackageInfoFlags flags) {
        PackageInfo pi;
        try {
            pi = super.getPackageInfoAsUser(selfPkgName(), flags, getUserId());
        } catch (NameNotFoundException e) {
            return null;
        }
        pi.packageName = pkgName;
        pi.applicationInfo.packageName = pkgName;
        pi.applicationInfo.enabled = false;
        return pi;
    }

    @Nullable
    private ApplicationInfo maybeMakePseudoDisabledApplicationInfo(String pkgName, ApplicationInfoFlags flags) {
        ApplicationInfo ai;
        try {
            ai = super.getApplicationInfoAsUser(selfPkgName(), flags, getUserId());
        } catch (NameNotFoundException e) {
            return null;
        }
        ai.packageName = pkgName;
        ai.enabled = false;
        return ai;
    }

    private static String selfPkgName() {
        return GmsCompat.appContext().getPackageName();
    }

    // Pseudo-disabled PackageInfo/ApplicationInfo is used to prevent Play Store from auto-installing
    // optional packages, such as "Play Services for AR". It's returned only when the package is
    // not installed.
    // When Play Store tries to enable a pseudo-disabled package, it receives a callback that
    // the package was uninstalled. This allows the user to install a pseudo-disabled package
    // by pressing the "Enable" button, which reveals the "Install" button.

    // important to have it static: there are multiple instances of enclosing class in the same process
    private static final ArraySet<String> pseudoDisabledPackages = new ArraySet<>();
    private static boolean pseudoDisabledPackagesInited;

    private static void maybeInitPseudoDisabledPackages() {
        if (GmsCompat.isPlayStore()) {
            synchronized (pseudoDisabledPackages) {
                if (!pseudoDisabledPackagesInited) {
                    // "Play Services for AR"
                    pseudoDisabledPackages.add("com.google.ar.core");
                    pseudoDisabledPackagesInited = true;
                }
            }
        }
    }

    private static boolean isPseudoDisabledPackage(String pkgName) {
        synchronized (pseudoDisabledPackages) {
            return pseudoDisabledPackages.contains(pkgName);
        }
    }

    private static ArraySet<String> clonePseudoDisabledPackages() {
        synchronized (pseudoDisabledPackages) {
            return new ArraySet<>(pseudoDisabledPackages);
        }
    }

    private static boolean removePseudoDisabledPackage(String pkgName) {
        synchronized (pseudoDisabledPackages) {
            return pseudoDisabledPackages.remove(pkgName);
        }
    }
}
