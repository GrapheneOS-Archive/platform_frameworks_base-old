/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.gmscompat;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;

import com.android.internal.gmscompat.flags.GmsFlag;

// Instances of this object should be immutable after publication, make sure to never change it afterwards
public class GmsCompatConfig implements Parcelable {
    public long version;
    public final ArrayMap<String, ArrayMap<String, GmsFlag>> flags = new ArrayMap<>();
    public ArrayMap<String, GmsFlag> gservicesFlags;
    public final ArrayMap<String, ArrayMap<String, StubDef>> stubs = new ArrayMap<>();

    public void addFlag(String namespace, GmsFlag flag) {
        ArrayMap<String, GmsFlag> nsFlags = flags.get(namespace);
        if (nsFlags == null) {
            nsFlags = new ArrayMap<>();
            flags.put(namespace, nsFlags);
        }
        nsFlags.put(flag.name, flag);
    }

    public void addGservicesFlag(String key, String value) {
        GmsFlag f = new GmsFlag(key);
        f.initAsSetString(value);
        addGservicesFlag(f);
    }

    public void addGservicesFlag(GmsFlag flag) {
        ArrayMap<String, GmsFlag> map = gservicesFlags;
        if (map == null) {
            map = new ArrayMap<>();
            gservicesFlags = map;
        }
        map.put(flag.name, flag);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel p, int wtpFlags) {
        p.writeLong(version);

        int flagNamespaceCnt = flags.size();
        p.writeInt(flagNamespaceCnt);

        for (int i = 0; i < flagNamespaceCnt; ++i) {
            p.writeString(flags.keyAt(i));
            p.writeTypedArrayMap(flags.valueAt(i), 0);
        }

        p.writeTypedArrayMap(gservicesFlags, 0);

        int classCnt = stubs.size();
        p.writeInt(classCnt);

        for (int i = 0; i < classCnt; ++i) {
            p.writeString(stubs.keyAt(i));
            p.writeTypedArrayMap(stubs.valueAt(i), 0);
        }
    }

    public static final Creator<GmsCompatConfig> CREATOR = new Creator<>() {
        @Override
        public GmsCompatConfig createFromParcel(Parcel p) {
            GmsCompatConfig r = new GmsCompatConfig();
            r.version = p.readLong();

            int flagNamespaceCnt = p.readInt();
            r.flags.ensureCapacity(flagNamespaceCnt);

            for (int i = 0; i < flagNamespaceCnt; ++i) {
                String namespace = p.readString();
                r.flags.put(namespace, readFlagsMap(p));
            }

            r.gservicesFlags = readFlagsMap(p);

            int classCnt = p.readInt();
            r.stubs.ensureCapacity(classCnt);

            for (int i = 0; i < classCnt; ++i) {
                r.stubs.put(p.readString(), p.createTypedArrayMap(StubDef.CREATOR));
            }

            return r;
        }

        private ArrayMap<String, GmsFlag> readFlagsMap(Parcel p) {
            ArrayMap<String, GmsFlag> map = p.createTypedArrayMap(GmsFlag.CREATOR);
            for (int i = 0; i < map.size(); ++i) {
                map.valueAt(i).name = map.keyAt(i);
            }
            return map;
        }

        @Override
        public GmsCompatConfig[] newArray(int size) {
            return new GmsCompatConfig[size];
        }
    };
}
