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

package com.android.server.pm;

import android.Manifest;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStatePm;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;
import android.util.TypedXmlPullParser;
import android.util.TypedXmlSerializer;

import com.android.internal.annotations.GuardedBy;
import com.android.server.pm.parsing.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.PackageUserStateInternal;
import com.android.server.pm.pkg.component.ParsedUsesPermission;

import java.io.IOException;
import java.util.List;

import static android.content.pm.GosPackageState.*;

class GosPackageStatePmHooks {
    private static final String TAG = "GosPackageStatePmHooks";

    private static final String ATTR_GOS_FLAGS = "GrapheneOS-flags";
    private static final String ATTR_GOS_STORAGE_SCOPES = "GrapheneOS-storage-scopes";

    // com.android.server.pm.Settings#writePackageRestrictionsLPr
    static void serialize(PackageUserStateInternal packageUserState, TypedXmlSerializer serializer) throws IOException {
        GosPackageStatePm ps = packageUserState.getGosPackageState();
        if (ps == null) {
            return;
        }

        final int flags = ps.flags;
        if (flags == 0) {
            return;
        }

        serializer.attributeInt(null, ATTR_GOS_FLAGS, flags);

        if ((flags & FLAG_STORAGE_SCOPES_ENABLED) != 0) {
            byte[] s = ps.storageScopes;
            if (s != null) {
                serializer.attributeBytesHex(null, ATTR_GOS_STORAGE_SCOPES, s);
            }
        }
    }

    // com.android.server.pm.Settings#readPackageRestrictionsLPr
    @Nullable
    static GosPackageStatePm deserialize(TypedXmlPullParser parser) {
        int flags = parser.getAttributeInt(null, ATTR_GOS_FLAGS, 0);
        if (flags == 0) {
            return null;
        }

        byte[] storageScopes = null;
        if ((flags & FLAG_STORAGE_SCOPES_ENABLED) != 0) {
            storageScopes = parser.getAttributeBytesHex(null, ATTR_GOS_STORAGE_SCOPES, null);
        }

        return new GosPackageStatePm(flags, storageScopes);
    }

    // PackageManagerService.IPackageManagerImpl#getGosPackageState
    // PackageManagerService.PackageManagerInternalImpl#getGosPackageState
    @Nullable
    static GosPackageState get(PackageManagerService pm, String packageName, int userId) {
        final int callingUid = Binder.getCallingUid();

        synchronized (pm.mLock) {
            final PackageSetting ps = pm.mSettings.getPackageLPr(packageName);
            if (ps == null) {
                return null;
            }

            if (ps.isPrivileged()) {
                // a precaution, in case there's a serious bug with GosPackageState-related code
                return null;
            }

            boolean selfCheck = false;

            if (callingUid != Process.SYSTEM_UID) {
                boolean allow = false;
                if (userId == UserHandle.getUserId(callingUid)) {
                    int callingAppId = UserHandle.getAppId(callingUid);
                    selfCheck = ps.getAppId() == callingAppId;
                    allow = selfCheck
                            || callingAppId == pm.mediaProviderAppId
                            || callingAppId == pm.permissionControllerAppId
                            || callingAppId == Process.SYSTEM_UID;
                }
                if (!allow) {
                    throw new SecurityException();
                }
            }

            return getInner(pm, ps, userId, selfCheck);
        }
    }

    @GuardedBy("PackageManagerService.mLock")
    @Nullable
    private static GosPackageState getInner(PackageManagerService pm, PackageSetting ps, int userId, boolean selfCheck) {
        GosPackageStatePm gosPs = ps.getGosPackageState(userId);
        if (gosPs == null) {
            gosPs = GosPackageStatePmHooks.maybeGetForSharedUserPackage(pm, ps, userId);
        }

        if (gosPs == null) {
            return null;
        }

        int derivedFlags = GosPackageStatePmHooks.maybeDeriveFlags(pm, gosPs, ps);

        return selfCheck ?
                // return as little information as possible to the target package
                gosPs.externalVersionForTargetPackage(derivedFlags) :
                gosPs.externalVersionForPrivilegedCallers(derivedFlags);
    }

    // new GosPackageState is returned because derivedFlags may change as a result of this call
    // (and to ensure consistency in case more than one process modifies GosPackageState at the same time)
    @Nullable
    // PackageManagerService.IPackageManagerImpl#setGosPackageState
    static GosPackageState set(PackageManagerService pm, String packageName,
                               int flags, @Nullable byte[] storageScopes,
                               boolean killUid,
                               int userId) {
        final int callingUid = Binder.getCallingUid();

        GosPackageState result;
        int appId;

        synchronized (pm.mLock) {
            boolean allow = false;
            if (userId == UserHandle.getUserId(callingUid)) {
                int callingAppId = UserHandle.getAppId(callingUid);
                allow = callingAppId == pm.permissionControllerAppId
                        // appId of the Settings app is the same as SYSTEM_UID
                        || callingAppId == Process.SYSTEM_UID;
            }
            if (!allow) {
                throw new SecurityException();
            }

            PackageSetting ps = pm.mSettings.getPackageLPr(packageName);
            if (ps == null) {
                return null;
            }

            if (ps.isPrivileged()) {
                // a precaution, in case there's a serious bug with GosPackageState-related code
                throw new IllegalStateException( "attaching GosPackageState to a privileged package " + packageName + " is not allowed");
            }

            appId = ps.getAppId();

            GosPackageStatePm gosPs = null;
            if (flags != 0) {
                gosPs = new GosPackageStatePm(flags, storageScopes);
            }

            SharedUserSetting sharedUser = pm.mSettings.getSharedUserSettingLPr(ps);;

            if (sharedUser != null) {
                List<AndroidPackage> sharedPkgs = sharedUser.getPackages();

                if (sharedPkgs == null) {
                    Slog.w(TAG, "SharedUserSetting.getPackages() for " + packageName + " is null");
                    return null;
                }

                // see GosPackageStatePm doc
                for (AndroidPackage sharedPkg : sharedPkgs) {
                    PackageSetting sharedPkgSetting = pm.mSettings.getPackageLPr(sharedPkg.getPackageName());
                    if (sharedPkgSetting != null) {
                        sharedPkgSetting.setGosPackageState(userId, gosPs);
                    }
                }
            } else {
                ps.setGosPackageState(userId, gosPs);
            }

            // will invalidate app-side caches (GosPackageState.sCache)
            pm.scheduleWritePackageRestrictions(userId);

            result = getInner(pm, ps, userId, false);
        }

        if (killUid) {
            final long token = Binder.clearCallingIdentity();
            try {
                // important to call outside the 'synchronized (pm.mLock)' section, may deadlock otherwise
                ActivityManager.getService().killUid(appId, userId, "GosPackageState");
            } catch (RemoteException e) {
                e.rethrowAsRuntimeException();
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        return result;
    }

    @GuardedBy("PackageManagerService.mLock")
    private static int maybeDeriveFlags(PackageManagerService pm, final GosPackageStatePm gosPs, PackageSetting pkgSetting) {
        if ((gosPs.flags & FLAG_STORAGE_SCOPES_ENABLED) == 0) {
            // derived flags are used only by StorageScopes for now
            return 0;
        }

        AndroidPackage pkg = pkgSetting.getPkg();
        if (pkg == null) {
            // see AndroidPackage.pkg javadoc for an explanation
            return 0;
        }

        SharedUserSetting sharedUser = pm.mSettings.getSharedUserSettingLPr(pkgSetting);

        int cachedDerivedFlags = sharedUser != null ?
                sharedUser.gosPackageStateCachedDerivedFlags :
                pkg.getGosPackageStateCachedDerivedFlags();

        if (cachedDerivedFlags != 0) {
            return cachedDerivedFlags;
        }

        int flags;
        if (sharedUser != null) {
            List<AndroidPackage> sharedPkgs = sharedUser.getPackages();
            if (sharedPkgs == null) {
                return 0;
            }

            flags = 0;
            for (AndroidPackage sharedPkg : sharedPkgs) {
                // see GosPackageStatePm doc
                flags = deriveFlags(flags, sharedPkg);
            }
        } else {
            flags = deriveFlags(0, pkg);
        }

        flags |= DFLAGS_SET;

        if (sharedUser != null) {
            sharedUser.gosPackageStateCachedDerivedFlags = flags;
        } else {
            pkg.setGosPackageStateCachedDerivedFlags(flags);
        }

        return flags;
    }

    @GuardedBy("PackageManagerService.mLock")
    private static int deriveFlags(int flags, AndroidPackage pkg) {
        List<ParsedUsesPermission> list = pkg.getUsesPermissions();
        for (int i = 0, m = list.size(); i < m; ++i) {
            ParsedUsesPermission perm = list.get(i);
            String name = perm.getName();
            switch (name) {
                case Manifest.permission.READ_EXTERNAL_STORAGE:
                case Manifest.permission.WRITE_EXTERNAL_STORAGE: {
                    boolean writePerm = name.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    flags |= writePerm ?
                            DFLAG_HAS_WRITE_EXTERNAL_STORAGE_DECLARATION :
                            DFLAG_HAS_READ_EXTERNAL_STORAGE_DECLARATION;

                    int targetSdk = pkg.getTargetSdkVersion();

                    boolean legacy = targetSdk < 29
                                || (targetSdk == 29 && pkg.isRequestLegacyExternalStorage());

                    if (writePerm && legacy) {
                        // when app doesn't have "legacy external storage", WRITE_EXTERNAL_STORAGE
                        // doesn't grant write access
                        flags |= DFLAG_EXPECTS_STORAGE_WRITE_ACCESS;
                    }

                    if ((flags & DFLAG_EXPECTS_ALL_FILES_ACCESS) == 0) {
                        if (legacy) {
                            flags |= (DFLAG_EXPECTS_ALL_FILES_ACCESS
                                    | DFLAG_EXPECTS_LEGACY_EXTERNAL_STORAGE);
                        } else {
                            flags |= DFLAG_EXPECTS_ACCESS_TO_MEDIA_FILES_ONLY;
                        }
                    }
                    continue;
                }

                case Manifest.permission.MANAGE_EXTERNAL_STORAGE:
                    flags &= ~DFLAG_EXPECTS_ACCESS_TO_MEDIA_FILES_ONLY;
                    flags |= DFLAG_EXPECTS_ALL_FILES_ACCESS
                            | DFLAG_EXPECTS_STORAGE_WRITE_ACCESS
                            | DFLAG_HAS_MANAGE_EXTERNAL_STORAGE_DECLARATION;
                    continue;

                case Manifest.permission.MANAGE_MEDIA:
                    flags |= DFLAG_HAS_MANAGE_MEDIA_DECLARATION;
                    continue;

                case Manifest.permission.ACCESS_MEDIA_LOCATION:
                    flags |= DFLAG_HAS_ACCESS_MEDIA_LOCATION_DECLARATION;
                    continue;

                case Manifest.permission.READ_MEDIA_AUDIO:
                    flags |= DFLAG_HAS_READ_MEDIA_AUDIO_DECLARATION;
                    continue;

                case Manifest.permission.READ_MEDIA_IMAGES:
                    flags |= DFLAG_HAS_READ_MEDIA_IMAGES_DECLARATION;
                    continue;

                case Manifest.permission.READ_MEDIA_VIDEO:
                    flags |= DFLAG_HAS_READ_MEDIA_VIDEO_DECLARATION;
                    continue;
                }
        }

        if ((flags & DFLAG_HAS_MANAGE_MEDIA_DECLARATION) != 0) {
            if ((flags & (DFLAG_HAS_READ_EXTERNAL_STORAGE_DECLARATION
                    | DFLAG_HAS_MANAGE_EXTERNAL_STORAGE_DECLARATION)) == 0)
            {
                flags &= ~DFLAG_HAS_MANAGE_MEDIA_DECLARATION;
            }
        }

        if ((flags & DFLAG_HAS_MANAGE_MEDIA_DECLARATION) != 0) {
            flags |= DFLAG_EXPECTS_STORAGE_WRITE_ACCESS;
        }

        if ((flags & DFLAG_HAS_ACCESS_MEDIA_LOCATION_DECLARATION) != 0) {
            if ((flags & (DFLAG_HAS_READ_EXTERNAL_STORAGE_DECLARATION
                    | DFLAG_HAS_MANAGE_EXTERNAL_STORAGE_DECLARATION)) == 0)
            {
                flags &= ~DFLAG_HAS_ACCESS_MEDIA_LOCATION_DECLARATION;
            }
        }

        return flags;
    }

    @Nullable
    @GuardedBy("PackageManagerService.mLock")
    // see GosPackageStatePm doc
    private static GosPackageStatePm maybeGetForSharedUserPackage(PackageManagerService pm, PackageSetting packageSetting, int userId) {
        SharedUserSetting sharedUser = pm.mSettings.getSharedUserSettingLPr(packageSetting);
        if (sharedUser == null) {
            return null;
        }

        var packageStates = sharedUser.getPackageStates();
        if (packageStates == null) {
            return null;
        }

        for (int i = 0, m = packageStates.size(); i < m; ++i) {
            PackageStateInternal state = packageStates.valueAtUnchecked(i);
            GosPackageStatePm ps = state.getUserStateOrDefault(userId).getGosPackageState();
            if (ps != null) {
                return ps;
            }
        }

        return null;
    }
}
