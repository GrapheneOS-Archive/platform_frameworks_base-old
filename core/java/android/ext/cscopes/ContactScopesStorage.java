package android.ext.cscopes;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.content.pm.GosPackageState;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** @hide */
@SystemApi
public final class ContactScopesStorage {
    private static final String TAG = "ContactScope";

    private final long[][] idsByType;

    @SuppressLint("MinMaxConstant")
    public static final int MAX_COUNT_PER_PACKAGE = 100;

    private ContactScopesStorage() {
        this(new long[ContactScope.TYPE_COUNT][0]);
    }

    private ContactScopesStorage(long[][] idsByType) {
        this.idsByType = idsByType;
    }

    @NonNull
    public long[] getIds(int type) {
        return idsByType[type];
    }

    public boolean add(int type, long id) {
        if (getCount() >= MAX_COUNT_PER_PACKAGE) {
            return false;
        }

        long[] cur = idsByType[type];
        long[] upd = ArrayUtils.appendLong(cur, id);
        if (cur == upd) {
            return false;
        }
        idsByType[type] = upd;
        return true;
    }

    public boolean remove(int type, long id) {
        long[] cur = idsByType[type];
        long[] upd = ArrayUtils.removeLong(cur, id);
        if (cur == upd) {
            return false;
        }
        idsByType[type] = upd;
        return true;
    }

    public int getCount() {
        int res = 0;
        for (long[] arr : idsByType) {
            res += arr.length;
        }
        return res;
    }

    private static final int VERSION = 0;

    @Nullable
    public byte[] serialize() {
        int count = getCount();
        if (count == 0) {
            return null;
        }

        if (count > MAX_COUNT_PER_PACKAGE) {
            throw new IllegalStateException();
        }

        var bos = new ByteArrayOutputStream(20 + (count * 8));

        var s = new DataOutputStream(bos);

        try {
            s.writeByte(VERSION);

            for (int type = ContactScope.TYPE_GROUP; type < ContactScope.TYPE_COUNT; ++type) {
                long[] ids = this.idsByType[type];
                int idsLen = ids.length;
                if (idsLen == 0) {
                    continue;
                }
                s.writeByte(type);
                s.writeInt(idsLen);
                for (int i = 0; i < idsLen; ++i) {
                    s.writeLong(ids[i]);
                }
            }

            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isEmpty(@NonNull GosPackageState gosPackageState) {
        return gosPackageState.contactScopes == null;
    }

    @NonNull
    public static ContactScopesStorage deserialize(@NonNull GosPackageState gosPackageState) {
        byte[] ser = gosPackageState.contactScopes;
        if (ser == null) {
            return new ContactScopesStorage();
        }

        var s = new DataInputStream(new ByteArrayInputStream(ser));

        try {
            final int version = s.readByte();
            if (version != VERSION) {
                Log.e(TAG, "unexpected version " + version);
                return new ContactScopesStorage();
            }

            long[][] idsByType = new long[ContactScope.TYPE_COUNT][0];

            do {
                int type = s.readByte();
                int cnt = s.readInt();

                long[] ids = new long[cnt];

                for (int i = 0; i < cnt; ++i) {
                    ids[i] = s.readLong();
                }

                idsByType[type] = ids;
            } while (s.available() != 0);

            return new ContactScopesStorage(idsByType);
        } catch (IOException e) {
            Log.e(TAG, "", e);
            return new ContactScopesStorage();
        }
    }
}
