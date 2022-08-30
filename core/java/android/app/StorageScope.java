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

package android.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.content.Intent;
import android.content.pm.GosPackageState;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * @hide
 */
@SystemApi
public final class StorageScope {
    private static final String TAG = "StorageScope";

    @NonNull
    public final String path;
    public final int flags; // note that flags are cast to short during serialization

    public static final int FLAG_ALLOW_WRITES = 1;
    public static final int FLAG_IS_DIR = 1 << 1;

    public StorageScope(@NonNull String path, int flags) {
        this.path = path;
        this.flags = flags;
    }

    @NonNull
    public static Intent createConfigActivityIntent(@NonNull String targetPkg) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setClassName("com.android.permissioncontroller",
                "com.android.permissioncontroller.sscopes.StorageScopesActivity");
        i.putExtra(Intent.EXTRA_PACKAGE_NAME, targetPkg);
        return i;
    }

    public static int maxArrayLength() {
        // Should be less than Byte.MAX_VALUE (it is cast to byte during serialization).
        // Note that the MediaProvider filtering based on StorageScopes is O(n),
        // where n is the number of the StorageScopes
        return 20;
    }

    public boolean isWritable() {
        return (flags & FLAG_ALLOW_WRITES) != 0;
    }

    public boolean isDirectory() {
        return (flags & FLAG_IS_DIR) != 0;
    }

    public boolean isFile() {
        return (flags & FLAG_IS_DIR) == 0;
    }

    private static final int VERSION = 0;

    @Nullable
    public static byte[] serializeArray(@NonNull @SuppressLint("ArrayReturn") StorageScope[] array) {
        if (array.length == 0) {
            return null; // special case to minimize the size of persistent state
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream(1000);
        DataOutputStream s = new DataOutputStream(bos);
        try {
            s.writeByte(VERSION);

            final int cnt = array.length;
            if (cnt > maxArrayLength()) {
                throw new IllegalStateException();
            }
            s.writeByte(cnt);

            for (int i = 0; i < cnt; ++i) {
                StorageScope scope = array[i];
                s.writeUTF(scope.path);
                s.writeShort(scope.flags);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @NonNull @SuppressLint("ArrayReturn")
    public static StorageScope[] deserializeArray(@NonNull GosPackageState gosPackageState) {
        byte[] ser = gosPackageState.storageScopes;

        if (ser == null) {
            return new StorageScope[0];
        }

        DataInputStream s = new DataInputStream(new ByteArrayInputStream(ser));
        try {
            final int version = s.read();
            if (version != StorageScope.VERSION) {
                Log.e(TAG, "unexpected version " + version);
                return new StorageScope[0];
            }

            int cnt = s.read();
            StorageScope[] arr = new StorageScope[cnt];
            for (int i = 0; i < cnt; ++i) {
                String path = s.readUTF();
                short pathFlags = (short) s.readUnsignedShort();

                arr[i] = new StorageScope(path, pathFlags);
            }

            return arr;
        } catch (Exception e) {
            Log.e(TAG, "deserialization failed", e);
            return new StorageScope[0];
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StorageScope)) {
            return false;
        }
        StorageScope o = (StorageScope) obj;
        return path.equals(o.path) && flags == o.flags;
    }

    @Override
    public int hashCode() {
        return 31 * flags + path.hashCode();
    }

    public static final String MEDIA_PROVIDER_METHOD_INVALIDATE_MEDIA_PROVIDER_CACHE = "StorageScopes_invalidateCache";
    public static final String MEDIA_PROVIDER_METHOD_MEDIA_ID_TO_FILE_PATH = "StorageScopes_mediaIdToFilePath";

    public static final String EXTERNAL_STORAGE_PROVIDER_METHOD_CONVERT_DOC_ID_TO_PATH = "StorageScopes_convertDocIdToPath";
}
