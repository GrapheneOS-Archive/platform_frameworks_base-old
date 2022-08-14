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
import android.app.AppGlobals;
import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.permission.PermissionManager;

/**
 * @hide
 */
@SystemApi
public final class GosPackageState implements Parcelable {
    public final int flags;
    @Nullable
    public final byte[] storageScopes;
    public final int derivedFlags; // derived from persistent state, but not persisted themselves

    String packageName; // needed for instantiation of Editor

    public static final int FLAG_STORAGE_SCOPES_ENABLED = 1;
    // checked only if REQUEST_INSTALL_PACKAGES permission is granted
    public static final int FLAG_ALLOW_ACCESS_TO_OBB_DIRECTORY = 1 << 1;

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

    GosPackageState(int flags, @Nullable byte[] storageScopes, int derivedFlags) {
        this.flags = flags;
        this.storageScopes = storageScopes;
        this.derivedFlags = derivedFlags;
    }

    @Nullable
    public static GosPackageState getForSelf() {
        return get(AppGlobals.getInitialPackage());
    }

    // uses current userId, don't use in places that deal with multiple users (eg system_server)
    @Nullable
    public static GosPackageState get(@NonNull String packageName) {
        Object res = sCache.query(packageName);
        if (res instanceof GosPackageState) {
            return (GosPackageState) res;
        }
        return null;
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

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    public boolean hasFlags(int flags) {
        return (this.flags & flags) == flags;
    }

    public boolean hasDerivedFlag(int flag) {
        return (derivedFlags & flag) != 0;
    }

    public boolean hasDerivedFlags(int flags) {
        return (derivedFlags & flags) == flags;
    }

    public static boolean attachableToPackage(@NonNull String pkg) {
        Context ctx = AppGlobals.getInitialApplication();
        if (ctx == null) {
            return false;
        }

        ApplicationInfo ai;
        try {
            ai = ctx.getPackageManager().getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        // a precaution, in case there's a serious bug with GosPackageState-related code
        return !ai.isPrivilegedApp();
    }

    public boolean shouldSpoofPermissionCheck(@NonNull String perm) {
        if (hasFlag(FLAG_STORAGE_SCOPES_ENABLED)) {
            int dflag = AppPermissionUtils.getSpoofableStorageRuntimePermissionDflag(perm);
            if (dflag != 0 && hasDerivedFlag(dflag)) {
                return true;
            }
        }

        return false;
    }

    // invalidated by PackageManager#invalidatePackageInfoCache() (eg when
    // PackageManagerService#setGosPackageState succeeds)
    private static final PropertyInvalidatedCache<String, Object> sCache =
            new PropertyInvalidatedCache<String, Object>(
                    256, PermissionManager.CACHE_KEY_PACKAGE_INFO,
                    "getGosPackageState") {

                @Override
                public Object recompute(String s) {
                    GosPackageState res = getUncached(s);
                    if (res == null) {
                        // return non-null to cache null results, see javadoc for recompute()
                        return GosPackageState.class;
                    }
                    return res;
                }

                private int cachedUserId = -1;

                @Nullable
                private GosPackageState getUncached(@NonNull String packageName) {
                    int userId = cachedUserId;
                    if (userId < 0) {
                        userId = UserHandle.myUserId();
                        cachedUserId = userId;
                    }

                    try {
                        GosPackageState s = AppGlobals.getPackageManager().getGosPackageState(packageName, userId);
                        if (s != null) {
                            s.packageName = packageName;
                        }
                        return s;
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    }
                }
            };

    @NonNull
    public Editor edit() {
        return new Editor(this);
    }

    @NonNull
    public static Editor edit(@NonNull String packageName) {
        GosPackageState s = GosPackageState.get(packageName);
        if (s != null) {
            return s.edit();
        }

        return new Editor(packageName);
    }

    public static class Editor {
        private final String packageName;
        private int flags;
        private byte[] storageScopes;
        private boolean killUidAfterApply;

        Editor(String packageName) {
            this.packageName = packageName;
        }

        Editor(GosPackageState s) {
            this.packageName = s.packageName;
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
            this.killUidAfterApply = true;
            return this;
        }

        // To simplify chaining of calls if the decision is made at runtime
        @NonNull
        public Editor setKillUidAfterApply(boolean v) {
            this.killUidAfterApply = v;
            return this;
        }

        // Returns null if the package is no longer installed.
        // Note: persistence to storage is asynchronous.
        @Nullable
        public GosPackageState apply() {
            int userId = UserHandle.myUserId();
            try {
                return AppGlobals.getPackageManager().setGosPackageState(packageName, flags,
                        storageScopes, killUidAfterApply, userId);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }
}
