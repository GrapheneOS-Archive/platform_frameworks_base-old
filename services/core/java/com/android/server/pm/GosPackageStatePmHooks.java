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
import android.app.ActivityManagerInternal;
import android.app.PropertyInvalidatedCache;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateBase;
import android.ext.KnownSystemPackages;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.os.ShellCommand;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.pm.parsing.pkg.AndroidPackageInternal;
import com.android.internal.pm.pkg.component.ParsedUsesPermission;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.LocalServices;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.GosPackageStatePm;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.PackageUserStateInternal;
import com.android.server.pm.pkg.SharedUserApi;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static android.content.pm.GosPackageState.*;

public class GosPackageStatePmHooks {
    private static final String TAG = "GosPackageStatePmHooks";

    private static final String ATTR_GOS_FLAGS = "GrapheneOS-flags";
    private static final String ATTR_GOS_STORAGE_SCOPES = "GrapheneOS-storage-scopes";
    private static final String ATTR_GOS_CONTACT_SCOPES = "GrapheneOS-contact-scopes";
    private static final String ATTR_GOS_PACKAGE_FLAGS = "GrapheneOS-package-flags";

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

        if ((flags & FLAG_CONTACT_SCOPES_ENABLED) != 0) {
            byte[] s = ps.contactScopes;
            if (s != null) {
                serializer.attributeBytesHex(null, ATTR_GOS_CONTACT_SCOPES, s);
            }
        }

        if ((flags & FLAG_HAS_PACKAGE_FLAGS) != 0) {
            serializer.attributeLong(null, ATTR_GOS_PACKAGE_FLAGS, ps.packageFlags);
        }
    }

    // com.android.server.pm.Settings#readPackageRestrictionsLPr
    @Nullable
    static GosPackageStatePm deserialize(TypedXmlPullParser parser) {
        int flags = parser.getAttributeInt(null, ATTR_GOS_FLAGS, 0);
        if (flags == 0) {
            return null;
        }

        flags = migrateLegacyFlags(flags);

        byte[] storageScopes = null;
        if ((flags & FLAG_STORAGE_SCOPES_ENABLED) != 0) {
            storageScopes = parser.getAttributeBytesHex(null, ATTR_GOS_STORAGE_SCOPES, null);
        }

        byte[] contactScopes = null;
        if ((flags & FLAG_CONTACT_SCOPES_ENABLED) != 0) {
            contactScopes = parser.getAttributeBytesHex(null, ATTR_GOS_CONTACT_SCOPES, null);
        }

        long packageFlags = 0L;
        if ((flags & FLAG_HAS_PACKAGE_FLAGS) != 0) {
            packageFlags = parser.getAttributeLong(null, ATTR_GOS_PACKAGE_FLAGS, 0L);
        }

        return new GosPackageStatePm(flags, packageFlags, storageScopes, contactScopes);
    }

    private static int migrateLegacyFlags(int flags) {
        final int FLAG_DISABLE_HARDENED_MALLOC = 1 << 2;
        if ((flags & FLAG_DISABLE_HARDENED_MALLOC) != 0) {
            flags &= ~(FLAG_USE_HARDENED_MALLOC | FLAG_DISABLE_HARDENED_MALLOC);
            flags |= FLAG_USE_HARDENED_MALLOC_NON_DEFAULT;
        }

        final int FLAG_ENABLE_COMPAT_VA_39_BIT = 1 << 3;
        if ((flags & FLAG_ENABLE_COMPAT_VA_39_BIT) != 0) {
            flags &= ~(FLAG_USE_EXTENDED_VA_SPACE | FLAG_ENABLE_COMPAT_VA_39_BIT);
            flags |= FLAG_USE_EXTENDED_VA_SPACE_NON_DEFAULT;
        }

        return flags;
    }

    @Nullable
    static GosPackageState get(PackageManagerService pm, String packageName, int userId) {
        final int callingUid = Binder.getCallingUid();
        return get(pm, callingUid, packageName, userId);
    }

    @Nullable
    public static GosPackageState get(PackageManagerService pm, int callingUid, String packageName, int userId) {
        Computer pmComputer = pm.snapshotComputer();
        PackageStateInternal packageState = pmComputer.getPackageStates().get(packageName);
        if (packageState == null) {
            // the package was likely racily uninstalled
            return null;
        }

        return get(pmComputer, packageState,
                packageState.getUserStateOrDefault(userId).getGosPackageState(),
                callingUid, userId);
    }

    @Nullable
    public static GosPackageState get(Computer pmComputer,
                                      PackageStateInternal packageState, GosPackageStatePm gosPsPm,
                                      int callingUid, int userId) {
        if (gosPsPm == null) {
            return null;
        }

        final int appId = packageState.getAppId();
        if (!GosPackageState.attachableToPackage(appId)) {
            return null;
        }

        Permission permission = Permission.get(callingUid, appId, userId, false);
        if (permission == null) {
            return null;
        }

        return permission.filterRead(gosPsPm, maybeDeriveFlags(pmComputer, gosPsPm, packageState));
    }

    static boolean set(PackageManagerService pm, String packageName, int userId,
                               GosPackageState update, int editorFlags) {
        final int callingUid = Binder.getCallingUid();

        GosPackageStatePm currentGosPs = GosPackageStatePm.get(pm.snapshotComputer(), packageName, userId);

        final int appId;

        synchronized (pm.mLock) {
            PackageSetting packageSetting = pm.mSettings.getPackageLPr(packageName);
            if (packageSetting == null) {
                return false;
            }

            appId = packageSetting.getAppId();

            if (!GosPackageState.attachableToPackage(appId)) {
                return false;
            }

            Permission permission = Permission.get(callingUid, appId, userId, true);

            if (permission == null) {
                return false;
            }

            GosPackageStatePm updatedGosPs = permission.filterWrite(currentGosPs, update);

            SharedUserSetting sharedUser = pm.mSettings.getSharedUserSettingLPr(packageSetting);

            if (sharedUser != null) {
                List<AndroidPackage> sharedPkgs = sharedUser.getPackages();

                // see GosPackageStatePm doc
                for (AndroidPackage sharedPkg : sharedPkgs) {
                    PackageSetting sharedPkgSetting = pm.mSettings.getPackageLPr(sharedPkg.getPackageName());
                    if (sharedPkgSetting != null) {
                        sharedPkgSetting.setGosPackageState(userId, updatedGosPs);
                    }
                }
            } else {
                packageSetting.setGosPackageState(userId, updatedGosPs);
            }

            // will invalidate app-side caches (GosPackageState.sCache)
            pm.scheduleWritePackageRestrictions(userId);
        }

        if ((editorFlags & EDITOR_FLAG_KILL_UID_AFTER_APPLY) != 0) {
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

        if ((editorFlags & EDITOR_FLAG_NOTIFY_UID_AFTER_APPLY) != 0) {
            int uid = UserHandle.getUid(userId, appId);

            // get GosPackageState as the target app
            GosPackageState ps = get(pm, uid, packageName, userId);

            final long token = Binder.clearCallingIdentity();

            try {
                var am = LocalServices.getService(ActivityManagerInternal.class);
                am.onGosPackageStateChanged(uid, ps);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        return true;
    }

    private static int maybeDeriveFlags(Computer snapshot, final GosPackageStatePm gosPs, PackageStateInternal pkgSetting) {
        if ((gosPs.flags & (FLAG_STORAGE_SCOPES_ENABLED | FLAG_CONTACT_SCOPES_ENABLED)) == 0) {
            return 0;
        }

        var pkg = (AndroidPackageInternal) pkgSetting.getPkg();
        if (pkg == null) {
            // see AndroidPackage.pkg javadoc for an explanation
            return 0;
        }

        int cachedDerivedFlags = pkg.getGosPackageStateCachedDerivedFlags();

        if (cachedDerivedFlags != 0) {
            return cachedDerivedFlags;
        }

        SharedUserApi sharedUser = null;
        if (pkgSetting.hasSharedUser()) {
            sharedUser = snapshot.getSharedUser(pkgSetting.getSharedUserAppId());
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

        pkg.setGosPackageStateCachedDerivedFlags(flags);

        return flags;
    }

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

                case Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED:
                    flags |= DFLAG_HAS_READ_MEDIA_VISUAL_USER_SELECTED_DECLARATION;
                    continue;

                case Manifest.permission.READ_CONTACTS:
                    flags |= DFLAG_HAS_READ_CONTACTS_DECLARATION;
                    continue;

                case Manifest.permission.WRITE_CONTACTS:
                    flags |= DFLAG_HAS_WRITE_CONTACTS_DECLARATION;
                    continue;

                case Manifest.permission.GET_ACCOUNTS:
                    flags |= DFLAG_HAS_GET_ACCOUNTS_DECLARATION;
                    continue;
            }
        }

        if ((flags & DFLAG_HAS_MANAGE_MEDIA_DECLARATION) != 0) {
            if ((flags & (DFLAG_HAS_READ_EXTERNAL_STORAGE_DECLARATION
                    | DFLAG_HAS_READ_MEDIA_AUDIO_DECLARATION
                    | DFLAG_HAS_READ_MEDIA_IMAGES_DECLARATION
                    | DFLAG_HAS_READ_MEDIA_VIDEO_DECLARATION
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
                    | DFLAG_HAS_READ_MEDIA_IMAGES_DECLARATION
                    | DFLAG_HAS_READ_MEDIA_VIDEO_DECLARATION
                    | DFLAG_HAS_MANAGE_EXTERNAL_STORAGE_DECLARATION)) == 0)
            {
                flags &= ~DFLAG_HAS_ACCESS_MEDIA_LOCATION_DECLARATION;
            }
        }

        return flags;
    }

    // IPackageManagerImpl.clearApplicationUserData
    public static void onClearApplicationUserData(PackageManagerService pm, String packageName, int userId) {
        if (packageName.equals(KnownSystemPackages.get(pm.getContext()).contactsProvider)) {
            // discard IDs that refer to entries in the contacts provider database
            clearContactScopesStorage(pm, userId);
        }
    }

    private static void clearContactScopesStorage(PackageManagerService pm, int userId) {
        for (PackageStateInternal ps : pm.snapshotComputer().getPackageStates().values()) {
            PackageUserStateInternal us = ps.getUserStateOrDefault(userId);
            GosPackageStatePm gosPs = us.getGosPackageState();
            if (gosPs != null && gosPs.contactScopes != null) {
                gosPs.edit(ps.getPackageName(), userId)
                        .setContactScopes(null)
                        .apply();
            }
        }
    }

    static class Permission {
        // bitmask of flags that can be read/written
        final int readFlags;
        final int writeFlags;

        private static final int FIELD_STORAGE_SCOPES = 1;
        private static final int FIELD_CONTACT_SCOPES = 1 << 1;
        private static final int FIELD_PACKAGE_FLAGS = 1 << 2;

        // bitmask of fields that can be read/written
        final int readFields;
        final int writeFields;

        final int crossUserOrProfilePermissions;

        private static final int ALLOW_CROSS_PROFILE_READS = 1;
        private static final int ALLOW_CROSS_PROFILE_WRITES = 1 << 1;
        private static final int ALLOW_CROSS_USER_OR_PROFILE_READS = 1 << 2;
        private static final int ALLOW_CROSS_USER_OR_PROFILE_WRITES = 1 << 3;

        private Permission(int readFlags, int writeFlags, int readFields, int writeFields,
                           int crossUserOrProfilePermissions) {
            this.readFlags = readFlags;
            this.writeFlags = writeFlags;
            this.readFields = readFields;
            this.writeFields = writeFields;
            this.crossUserOrProfilePermissions = crossUserOrProfilePermissions;
        }

        private static Permission readOnly(int flags) {
            return readOnly(flags, 0);
        }

        private static Permission readOnly(int flags, int fields) {
            return readOnly(flags, fields, 0);
        }

        private static Permission readOnly(int flags, int fields, int crossUserOrProfilePermissions) {
            return new Permission(flags, 0, fields, 0, crossUserOrProfilePermissions);
        }

        private static Permission readWrite(int flags, int fields) {
            return readWrite(flags, flags, fields, fields);
        }

        private static Permission readWrite(int readFlags, int writeFlags, int readFields, int writeFields) {
            return new Permission(readFlags, writeFlags, readFields, writeFields, 0);
        }

        boolean canWrite() {
            // each field has its own flag, no need to check writeFields
            return writeFlags != 0;
        }

        @Nullable
        static Permission get(int callingUid, int targetAppId, int targetUserId, boolean forWrite) {
            if (callingUid == Process.SYSTEM_UID) {
                return systemUidPermission;
            }

            int callingAppId = UserHandle.getAppId(callingUid);
            Permission permission = grantedPermissions.get(callingAppId);

            if (permission == null) {
                if (targetAppId == callingAppId) {
                    permission = selfAccessPermission;
                } else {
                    Slog.d(TAG, "uid " + callingUid + " doesn't have permission to " +
                            "access GosPackageState of other packages");
                    return null;
                }
            }

            if (forWrite && !permission.canWrite()) {
                return null;
            }

            if (!permission.checkCrossUserOrProfilePermissions(callingUid, targetUserId, forWrite)) {
                return null;
            }

            return permission;
        }

        private boolean checkCrossUserOrProfilePermissions(int callingUid, int targetUserId, boolean forWrite) {
            int callingUserId = UserHandle.getUserId(callingUid);

            if (targetUserId == callingUserId) {
                // caller and target are in the same userId
                return true;
            }

            int perms = crossUserOrProfilePermissions;
            final int crossUserOrProfileFlag = forWrite ?
                    ALLOW_CROSS_USER_OR_PROFILE_WRITES : ALLOW_CROSS_USER_OR_PROFILE_READS;

            if ((perms & crossUserOrProfileFlag) != 0) {
                // caller is allowed to access any user of profile
                return true;
            }

            final int crossProfileFlag = forWrite ?
                    ALLOW_CROSS_PROFILE_WRITES : ALLOW_CROSS_PROFILE_READS;

            if ((perms & crossProfileFlag) != 0) {
                if (userManager.getProfileParentId(targetUserId) == callingUserId) {
                    // caller is allowed to access its child profile
                    return true;
                }
            }

            Slog.d(TAG, "not allowed to access userId " + targetUserId + " from uid " + callingUid);
            return false;
        }

        @Nullable
        GosPackageState filterRead(GosPackageStatePm ps, int derivedFlags) {
            int flags = ps.flags & readFlags;
            if (flags == 0) {
                return null;
            }
            int readFields = this.readFields;
            return new GosPackageState(flags,
                    (readFields & FIELD_PACKAGE_FLAGS) != 0 ? ps.packageFlags : 0L,
                    (readFields & FIELD_STORAGE_SCOPES) != 0 ? ps.storageScopes : null,
                    (readFields & FIELD_CONTACT_SCOPES) != 0 ? ps.contactScopes : null,
                    derivedFlags);
        }

        @Nullable
        GosPackageStatePm filterWrite(@Nullable GosPackageStateBase current, GosPackageState update) {
            if (current == null) {
                current = GosPackageState.createDefault(update.getPackageName(), update.getUserId());
            }
            int curFlags = current.flags;
            int flags = (curFlags & ~writeFlags) | (update.flags & writeFlags);
            if (flags == 0) {
                return null;
            }
            byte[] storageScopes = current.storageScopes;
            byte[] contactScopes = current.contactScopes;
            long packageFlags = current.packageFlags;

            if ((writeFields & FIELD_STORAGE_SCOPES) != 0) {
                storageScopes = update.storageScopes;
            }

            if ((writeFields & FIELD_CONTACT_SCOPES) != 0) {
                contactScopes = update.contactScopes;
            }

            if ((writeFields & FIELD_PACKAGE_FLAGS) != 0)  {
                packageFlags = update.packageFlags;
            }

            return new GosPackageStatePm(flags, packageFlags, storageScopes, contactScopes);
        }

        // Permission that each package has for accessing its own GosPackageState
        private static Permission selfAccessPermission;
        private static Permission systemUidPermission;

        // Maps app's appId to its permission.
        // Written only during PackageManager init, no need to synchronize reads
        private static SparseArray<Permission> grantedPermissions;

        private static UserManagerInternal userManager;

        static void init(PackageManagerService pm) {
            selfAccessPermission = Permission.readOnly(FLAG_STORAGE_SCOPES_ENABLED
                    | FLAG_ALLOW_ACCESS_TO_OBB_DIRECTORY
                    | FLAG_CONTACT_SCOPES_ENABLED
                    ,0);

            grantedPermissions = new SparseArray<>();

            var fullPermission = new Permission(-1, -1, -1, -1,
                Permission.ALLOW_CROSS_USER_OR_PROFILE_READS
                        | Permission.ALLOW_CROSS_USER_OR_PROFILE_WRITES
            );
            grantedPermissions.put(Process.SHELL_UID, fullPermission);
            if (Build.isDebuggable()) {
                // for root adb
                grantedPermissions.put(Process.ROOT_UID, fullPermission);
            }

            Computer computer = pm.snapshotComputer();

            KnownSystemPackages ksp = KnownSystemPackages.get(pm.getContext());

            grantPermission(computer, ksp.mediaProvider,
                    Permission.readOnly(FLAG_STORAGE_SCOPES_ENABLED, FIELD_STORAGE_SCOPES));

            grantPermission(computer, ksp.contactsProvider,
                    Permission.readOnly(FLAG_CONTACT_SCOPES_ENABLED, FIELD_CONTACT_SCOPES));

            grantPermission(computer, ksp.launcher,
                    Permission.readOnly(FLAG_STORAGE_SCOPES_ENABLED
                                    | FLAG_CONTACT_SCOPES_ENABLED
                            , 0,
                            // work profile is handled by the launcher in profile's parent
                            Permission.ALLOW_CROSS_PROFILE_READS));

            grantPermission(computer, ksp.permissionController,
                    Permission.readWrite(
                            FLAG_STORAGE_SCOPES_ENABLED
                                | FLAG_CONTACT_SCOPES_ENABLED
                                | FLAG_HAS_PACKAGE_FLAGS
                                ,FIELD_STORAGE_SCOPES
                                | FIELD_CONTACT_SCOPES
                                | FIELD_PACKAGE_FLAGS
                    ));

            final int settingsReadWriteFlags =
                    FLAG_ALLOW_ACCESS_TO_OBB_DIRECTORY
                    | FLAG_BLOCK_NATIVE_DEBUGGING_NON_DEFAULT
                    | FLAG_BLOCK_NATIVE_DEBUGGING
                    | FLAG_BLOCK_NATIVE_DEBUGGING_SUPPRESS_NOTIF
                    | FLAG_RESTRICT_MEMORY_DYN_CODE_LOADING_NON_DEFAULT
                    | FLAG_RESTRICT_MEMORY_DYN_CODE_LOADING
                    | FLAG_RESTRICT_MEMORY_DYN_CODE_LOADING_SUPPRESS_NOTIF
                    | FLAG_RESTRICT_STORAGE_DYN_CODE_LOADING_NON_DEFAULT
                    | FLAG_RESTRICT_STORAGE_DYN_CODE_LOADING
                    | FLAG_RESTRICT_STORAGE_DYN_CODE_LOADING_SUPPRESS_NOTIF
                    | FLAG_RESTRICT_WEBVIEW_DYN_CODE_LOADING_NON_DEFAULT
                    | FLAG_RESTRICT_WEBVIEW_DYN_CODE_LOADING
                    | FLAG_USE_HARDENED_MALLOC_NON_DEFAULT
                    | FLAG_USE_HARDENED_MALLOC
                    | FLAG_USE_EXTENDED_VA_SPACE_NON_DEFAULT
                    | FLAG_USE_EXTENDED_VA_SPACE
                    | FLAG_FORCE_MEMTAG_NON_DEFAULT
                    | FLAG_FORCE_MEMTAG
                    | FLAG_FORCE_MEMTAG_SUPPRESS_NOTIF
                    | FLAG_ENABLE_EXPLOIT_PROTECTION_COMPAT_MODE
            ;

            final int settingsReadFlags = settingsReadWriteFlags
                    | FLAG_STORAGE_SCOPES_ENABLED
                    | FLAG_CONTACT_SCOPES_ENABLED
                    | FLAG_HAS_PACKAGE_FLAGS
            ;

            final int settingsWriteFlags = settingsReadWriteFlags;

            final int settingsReadFields = FIELD_PACKAGE_FLAGS;

            // note that this applies to all packages that run in the android.uid.system sharedUserId
            // in secondary users, not just the Settings app. Packages that run in this sharedUserId
            // in the primary user get the fullPermission declared above
            grantPermission(computer, ksp.settings,
                    Permission.readWrite(settingsReadFlags, settingsWriteFlags, settingsReadFields, 0));

            final int systemUidWriteFlags = settingsWriteFlags
                    | FLAG_BLOCK_NATIVE_DEBUGGING_SUPPRESS_NOTIF
                    | FLAG_RESTRICT_MEMORY_DYN_CODE_LOADING_SUPPRESS_NOTIF
                    | FLAG_RESTRICT_STORAGE_DYN_CODE_LOADING_SUPPRESS_NOTIF
                    | FLAG_FORCE_MEMTAG_SUPPRESS_NOTIF;

            final int systemUidWriteFields =
                    FIELD_CONTACT_SCOPES // see onClearApplicationUserData() hook
            ;

            // Used for callers that run as SYSTEM_UID, e.g. system_server and packages in the
            // android.uid.system sharedUserId in the primary user (but not in secondary users)
            systemUidPermission = new Permission(-1, systemUidWriteFlags, -1, systemUidWriteFields,
                Permission.ALLOW_CROSS_USER_OR_PROFILE_READS
                        | Permission.ALLOW_CROSS_USER_OR_PROFILE_WRITES
            );

            userManager = Objects.requireNonNull(LocalServices.getService(UserManagerInternal.class));

            if (Build.IS_DEBUGGABLE) {
                var receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        var perm = new Permission(
                            intent.getIntExtra("readFlags", 0),
                            intent.getIntExtra("writeFlags", 0),
                            intent.getIntExtra("readFields", 0),
                            intent.getIntExtra("writeFields", 0),
                            intent.getIntExtra("crossUserOrProfilePermissions", 0)
                        );
                        String pkgName = intent.getStringExtra("pkgName");
                        grantPermission(pm.snapshotComputer(), pkgName, perm);
                        PropertyInvalidatedCache.invalidateCache(PermissionManager.CACHE_KEY_PACKAGE_INFO);
                        Slog.d(TAG, "granted permission " + intent.getExtras());
                    }
                };
                pm.getContext().registerReceiver(receiver, new IntentFilter("GosPackageState.grant_permission"),
                        Context.RECEIVER_EXPORTED);
            }
        }

        private static void grantPermission(Computer computer, String pkgName, Permission filter) {
            PackageStateInternal psi = computer.getPackageStateInternal(pkgName);
            if (psi == null || !psi.isSystem()) {
                Slog.d(TAG, pkgName + " is not a system package");
                if (Build.IS_DEBUGGABLE) {
                    throw new IllegalStateException();
                }
                return;
            }

            int appId = psi.getAppId();

            grantedPermissions.put(appId, filter);
        }
    }

    static void init(PackageManagerService pm) {
        Permission.init(pm);
    }

    public static int runShellCommand(PackageManagerShellCommand cmd) {
        String packageName = cmd.getNextArgRequired();
        int userId = Integer.parseInt(cmd.getNextArgRequired());

        GosPackageState.Editor ed = GosPackageState.edit(packageName, userId);
        boolean updatePermissionState = false;

        for (;;) {
            String arg = cmd.getNextArg();
            if (arg == null) {
                if (!ed.apply()) {
                    return 1;
                }

                if (updatePermissionState) {
                    cmd.mPermissionManager.updatePermissionState(packageName, userId);
                }

                return 0;
            }

            switch (arg) {
                case "add-flags", "clear-flags" ->
                    ed.setFlagsState(Integer.parseInt(cmd.getNextArgRequired(), 16),
                            "add-flags".equals(arg));
                case "add-package-flags", "clear-package-flags" ->
                    ed.setPackageFlagState(Long.parseLong(cmd.getNextArgRequired(), 16),
                            "add-package-flags".equals(arg));
                case "set-storage-scopes" ->
                    ed.setStorageScopes(getByteArrArg(cmd));
                case "set-contact-scopes" ->
                    ed.setContactScopes(getByteArrArg(cmd));
                case "set-kill-uid-after-apply" ->
                    ed.setKillUidAfterApply(Boolean.parseBoolean(cmd.getNextArgRequired()));
                case "set-notify-uid-after-apply" ->
                    ed.setNotifyUidAfterApply(Boolean.parseBoolean(cmd.getNextArgRequired()));
                case "update-permission-state" ->
                    updatePermissionState = true;
                default ->
                    throw new IllegalArgumentException(arg);
            }
        }
    }

    @Nullable
    private static byte[] getByteArrArg(ShellCommand cmd) {
        String s = cmd.getNextArgRequired();
        return "null".equals(s) ? null : libcore.util.HexEncoding.decode(s);
    }
}
