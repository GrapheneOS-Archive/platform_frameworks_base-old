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

package android.content.pm;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.app.ActivityThread;
import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.permission.PermissionManager;

import java.util.Objects;

import dalvik.system.VMRuntime;

/**
 * @hide
 */
@SystemApi
public final class GosPackageState extends GosPackageStateBase implements Parcelable {
    public final int derivedFlags; // derived from persistent state, but not persisted themselves

    // packageName and userId are stored here for convenience, they don't get serialized
    private String packageName;
    private int userId;

    public static final int FLAG_STORAGE_SCOPES_ENABLED = 1;
    // checked only if REQUEST_INSTALL_PACKAGES permission is granted
    public static final int FLAG_ALLOW_ACCESS_TO_OBB_DIRECTORY = 1 << 1;
    public static final int FLAG_DISABLE_HARDENED_MALLOC = 1 << 2;
    public static final int FLAG_ENABLE_COMPAT_VA_39_BIT = 1 << 3;
    // 1 << 4 was used by a now-removed feature, do not reuse it

    // to distinguish between the case when no dflags are set and the case when dflags weren't calculated yet
    public static final int DFLAGS_SET = 1;

    public static final int DFLAG_EXPECTS_ALL_FILES_ACCESS = 1 << 1;
    public static final int DFLAG_EXPECTS_ACCESS_TO_MEDIA_FILES_ONLY = 1 << 2;
    public static final int DFLAG_EXPECTS_STORAGE_WRITE_ACCESS = 1 << 3;
    public static final int DFLAG_HAS_READ_EXTERNAL_STORAGE_DECLARATION = 1 << 4;
    public static final int DFLAG_HAS_WRITE_EXTERNAL_STORAGE_DECLARATION = 1 << 5;
    public static final int DFLAG_HAS_MANAGE_EXTERNAL_STORAGE_DECLARATION = 1 << 6;
    public static final int DFLAG_HAS_MANAGE_MEDIA_DECLARATION = 1 << 7;
    public static final int DFLAG_HAS_ACCESS_MEDIA_LOCATION_DECLARATION = 1 << 8;
    public static final int DFLAG_HAS_READ_MEDIA_AUDIO_DECLARATION = 1 << 9;
    public static final int DFLAG_HAS_READ_MEDIA_IMAGES_DECLARATION = 1 << 10;
    public static final int DFLAG_HAS_READ_MEDIA_VIDEO_DECLARATION = 1 << 11;
    public static final int DFLAG_EXPECTS_LEGACY_EXTERNAL_STORAGE = 1 << 12;

    /** @hide */
    public GosPackageState(int flags, @Nullable byte[] storageScopes, int derivedFlags) {
        super(flags, storageScopes);
        this.derivedFlags = derivedFlags;
    }

    @Nullable
    public static GosPackageState getForSelf() {
        String packageName = ActivityThread.currentPackageName();
        if (packageName == null) {
            // currentPackageName is null inside system_server
            if (ActivityThread.isSystem()) {
                return null;
            } else {
                throw new IllegalStateException("ActivityThread.currentPackageName() is null");
            }
        }
        return get(packageName);
    }

    // uses current userId, don't use in places that deal with multiple users (eg system_server)
    @Nullable
    public static GosPackageState get(@NonNull String packageName) {
        Object res = sCurrentUserCache.query(packageName);
        if (res instanceof GosPackageState) {
            return (GosPackageState) res;
        }
        return null;
    }

    @Nullable
    public static GosPackageState get(@NonNull String packageName, @UserIdInt int userId) {
        if (userId == myUserId()) {
            return get(packageName);
        }
        Object res = getOtherUsersCache().query(new CacheQuery(packageName, userId));
        if (res instanceof GosPackageState) {
            return (GosPackageState) res;
        }
        return null;
    }

    @NonNull
    public static GosPackageState getOrDefault(@NonNull String packageName) {
        var s = get(packageName);
        if (s == null) {
            s = createDefault(packageName, myUserId());
        }
        return s;
    }

    @NonNull
    public static GosPackageState getOrDefault(@NonNull String packageName, int userId) {
        var s = get(packageName, userId);
        if (s == null) {
            s = createDefault(packageName, userId);
        }
        return s;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(this.flags);
        dest.writeByteArray(storageScopes);
        dest.writeInt(derivedFlags);
    }

    @NonNull
    public static final Creator<GosPackageState> CREATOR = new Creator<GosPackageState>() {
        @Override
        public GosPackageState createFromParcel(Parcel in) {
            return new GosPackageState(in.readInt(), in.createByteArray(), in.readInt());
        }

        @Override
        public GosPackageState[] newArray(int size) {
            return new GosPackageState[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    public int getUserId() {
        return userId;
    }

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    public boolean hasDerivedFlag(int flag) {
        return (derivedFlags & flag) != 0;
    }

    public boolean hasDerivedFlags(int flags) {
        return (derivedFlags & flags) == flags;
    }

    /** @hide */
    public static boolean attachableToPackage(int appId) {
        // Packages with this appId use the "android.uid.system" sharedUserId, which is expensive
        // to deal with due to the large number of packages that it includes (see GosPackageStatePm
        // doc). These packages have no need for GosPackageState.
        return appId != Process.SYSTEM_UID;
    }

    public static boolean attachableToPackage(@NonNull String pkg) {
        Context ctx = ActivityThread.currentApplication();
        if (ctx == null) {
            return false;
        }

        ApplicationInfo ai;
        try {
            ai = ctx.getPackageManager().getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return attachableToPackage(UserHandle.getAppId(ai.uid));
    }

    // invalidated by PackageManager#invalidatePackageInfoCache() (eg when
    // PackageManagerService#setGosPackageState succeeds)
    private static final PropertyInvalidatedCache<String, Object> sCurrentUserCache =
            new PropertyInvalidatedCache<String, Object>(
                    256, PermissionManager.CACHE_KEY_PACKAGE_INFO,
                    "getGosPackageStateCurrentUser") {

                @Override
                public Object recompute(String packageName) {
                    return getUncached(packageName, myUserId());
                }
            };


    static final class CacheQuery {
        final String packageName;
        final int userId;

        CacheQuery(String packageName, int userId) {
            this.packageName = packageName;
            this.userId = userId;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(packageName.hashCode()) + 31 * userId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CacheQuery) {
                CacheQuery o = (CacheQuery) obj;
                return packageName.equals(o.packageName) && userId == o.userId;
            }
            return false;
        }
    }

    private static volatile PropertyInvalidatedCache<CacheQuery, Object> sOtherUsersCache;

    private static PropertyInvalidatedCache<CacheQuery, Object> getOtherUsersCache() {
        var c = sOtherUsersCache;
        if (c != null) {
            return c;
        }
        return sOtherUsersCache = new PropertyInvalidatedCache<CacheQuery, Object>(
                256, PermissionManager.CACHE_KEY_PACKAGE_INFO,
                "getGosPackageStateOtherUsers") {

            @Override
            public Object recompute(CacheQuery query) {
                return getUncached(query.packageName, query.userId);
            }
        };
    }

    static Object getUncached(String packageName, int userId) {
        try {
            GosPackageState s = ActivityThread.getPackageManager().getGosPackageState(packageName, userId);
            if (s != null) {
                s.packageName = packageName;
                s.userId = userId;
                return s;
            }
            // return non-null to cache null results, see javadoc for PropertyInvalidatedCache#recompute()
            return GosPackageState.class;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @NonNull
    public Editor edit() {
        return new Editor(this, getPackageName(), getUserId());
    }

    @NonNull
    public static Editor edit(@NonNull String packageName) {
        return edit(packageName, myUserId());
    }

    @NonNull
    public static Editor edit(@NonNull String packageName, int userId) {
        GosPackageState s = GosPackageState.get(packageName, userId);
        if (s != null) {
            return s.edit();
        }

        return new Editor(packageName, userId);
    }

    /** @hide */
    public static boolean eligibleForRelaxHardeningFlag(ApplicationInfo ai) {
        String primaryAbi = ai.primaryCpuAbi;
        // non-system app that has native 64-bit code
        return !ai.isSystemApp() && primaryAbi != null && VMRuntime.is64BitAbi(primaryAbi);
    }

    public static final int EDITOR_FLAG_KILL_UID_AFTER_APPLY = 1;

    public static class Editor {
        private final String packageName;
        private final int userId;
        private int flags;
        private byte[] storageScopes;
        private int editorFlags;

        /**
         * Don't call directly, use GosPackageState#edit or GosPackageStatePm#getEditor
         *
         * @hide
         *  */
        public Editor(String packageName, int userId) {
            this(createDefault(packageName, userId), packageName, userId);
        }

        /** @hide */
        public Editor(GosPackageStateBase s, String packageName, int userId) {
            this.packageName = packageName;
            this.userId = userId;
            this.flags = s.flags;
            this.storageScopes = s.storageScopes;
        }

        @NonNull
        public Editor setFlagsState(int flags, boolean state) {
            if (state) {
                addFlags(flags);
            } else {
                clearFlags(flags);
            }
            return this;
        }

        @NonNull
        public Editor addFlags(int flags) {
            this.flags |= flags;
            return this;
        }

        @NonNull
        public Editor clearFlags(int flags) {
            this.flags &= ~flags;
            return this;
        }

        @NonNull
        public Editor setStorageScopes(@Nullable byte[] storageScopes) {
            this.storageScopes = storageScopes;
            return this;
        }

        @NonNull
        public Editor killUidAfterApply() {
            return setKillUidAfterApply(true);
        }

        @NonNull
        public Editor setKillUidAfterApply(boolean v) {
            if (v) {
                this.editorFlags |= EDITOR_FLAG_KILL_UID_AFTER_APPLY;
            } else {
                this.editorFlags &= ~EDITOR_FLAG_KILL_UID_AFTER_APPLY;
            }
            return this;
        }

        // Returns true if the update was successfully applied and is scheduled to be written back
        // to storage. Actual writeback is performed asynchronously.
        public boolean apply() {
            try {
                return ActivityThread.getPackageManager().setGosPackageState(packageName, userId,
                        new GosPackageState(flags, storageScopes, 0),
                        editorFlags);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    private static volatile int myUid;
    private static int myUserId;

    private static int myUserId() {
        if (myUid == 0) {
            int uid = Process.myUid();
            // order is important, volatile write to myUid publishes write to myUserId
            myUserId = UserHandle.getUserId(uid);
            myUid = uid;
        }
        return myUserId;
    }

    private static GosPackageState createDefault(String pkgName, int userId) {
        var ps = new GosPackageState(0, null, 0);
        ps.packageName = pkgName;
        ps.userId = userId;
        return ps;
    }
}
