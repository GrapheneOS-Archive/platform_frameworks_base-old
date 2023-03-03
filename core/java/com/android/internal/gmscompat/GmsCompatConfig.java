/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.gmscompat;

import android.app.ActivityThread;
import android.app.compat.gms.GmsCompat;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.gmscompat.flags.GmsFlag;

import java.util.ArrayList;

// Instances of this object should be immutable after publication, make sure to never change it afterwards
public class GmsCompatConfig implements Parcelable {
    public long version;
    public final ArrayMap<String, ArrayMap<String, GmsFlag>> flags = new ArrayMap<>();
    public ArrayMap<String, GmsFlag> gservicesFlags;
    public final ArrayMap<String, ArrayMap<String, StubDef>> stubs = new ArrayMap<>();
    // keys are namespaces, values are regexes of flag names that should be forced to default value
    public final ArrayMap<String, ArrayList<String>> forceDefaultFlagsMap = new ArrayMap<>();
    // keys are package names, values are list of permissions self-checks of which should be spoofed
    public final ArrayMap<String, ArrayList<String>> spoofSelfPermissionChecksMap = new ArrayMap<>();

    // set only in processes for which GmsCompat is enabled, to speed up lookups
    public ArraySet<String> spoofSelfPermissionChecks;

    public long maxGmsCoreVersion;
    public long maxPlayStoreVersion;

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

        p.writeLong(maxGmsCoreVersion);
        p.writeLong(maxPlayStoreVersion);

        writeArrayMapStringStringList(forceDefaultFlagsMap, p);
        writeArrayMapStringStringList(spoofSelfPermissionChecksMap, p);
    }

    static void writeArrayMapStringStringList(ArrayMap<String, ArrayList<String>> map, Parcel p) {
        int cnt = map.size();
        p.writeInt(cnt);
        for (int i = 0; i < cnt; ++i) {
            p.writeString(map.keyAt(i));
            p.writeStringList(map.valueAt(i));
        }
    }

    static void readArrayMapStringStringList(Parcel p, ArrayMap<String, ArrayList<String>> map) {
        int cnt = p.readInt();
        map.ensureCapacity(cnt);
        for (int i = 0; i < cnt; ++i) {
            String namespace = p.readString();
            map.put(namespace, p.createStringArrayList());
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

            r.maxGmsCoreVersion = p.readLong();
            r.maxPlayStoreVersion = p.readLong();

            readArrayMapStringStringList(p, r.forceDefaultFlagsMap);
            readArrayMapStringStringList(p, r.spoofSelfPermissionChecksMap);

            if (GmsCompat.isEnabled()) {
                ArrayList<String> perms = r.spoofSelfPermissionChecksMap.get(ActivityThread.currentPackageName());
                r.spoofSelfPermissionChecks = perms != null ?
                        new ArraySet<>(perms) :
                        new ArraySet<>();
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
