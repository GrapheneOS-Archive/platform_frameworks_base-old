/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.gmscompat;

import android.app.compat.gms.GmsCompat;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.internal.gmscompat.flags.GmsFlag;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
    // keys are serviceIds, values are service permission requirements that need to be bypassed
    public final SparseArray<ArraySet<String>> gmsServiceBrokerPermissionBypasses = new SparseArray<>();

    // keys are package names, values are maps of components names to their component enabled setting
    public final ArrayMap<String, ArrayMap<String, Integer>> forceComponentEnabledSettingsMap = new ArrayMap<>();

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

    public boolean shouldSpoofSelfPermissionCheck(String perm) {
        return spoofSelfPermissionChecks.contains(perm);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel p, int wtpFlags) {
        p.writeLong(version);

        writeStringArrayMapMap(flags, GmsFlag::writeMapEntry, p);
        writeArrayMap(gservicesFlags, GmsFlag::writeMapEntry, p);

        writeStringArrayMapMap(stubs, p);

        p.writeLong(maxGmsCoreVersion);
        p.writeLong(maxPlayStoreVersion);

        writeArrayMapStringStringList(forceDefaultFlagsMap, p);
        writeArrayMapStringStringList(spoofSelfPermissionChecksMap, p);
        {
            var map = gmsServiceBrokerPermissionBypasses;
            int cnt = map.size();
            p.writeInt(cnt);
            for (int i = 0; i < cnt; ++i) {
                p.writeInt(map.keyAt(i));
                p.writeArraySet(map.valueAt(i));
            }
        }
        writeStringArrayMapMap(forceComponentEnabledSettingsMap, Parcel::writeString, Parcel::writeInt, p);
    }

    public static final Creator<GmsCompatConfig> CREATOR = new Creator<>() {
        @Override
        public GmsCompatConfig createFromParcel(Parcel p) {
            GmsCompatConfig r = new GmsCompatConfig();
            r.version = p.readLong();

            readStringArrayMapMap(p, r.flags, GmsFlag::readMapEntry);
            r.gservicesFlags = readArrayMap(p, GmsFlag::readMapEntry);

            readStringArrayMapMap(p, r.stubs, StubDef.CREATOR);

            r.maxGmsCoreVersion = p.readLong();
            r.maxPlayStoreVersion = p.readLong();

            readArrayMapStringStringList(p, r.forceDefaultFlagsMap);
            readArrayMapStringStringList(p, r.spoofSelfPermissionChecksMap);
            {
                int cnt = p.readInt();
                ClassLoader cl = String.class.getClassLoader();
                for (int i = 0; i < cnt; ++i) {
                    r.gmsServiceBrokerPermissionBypasses.put(p.readInt(), (ArraySet<String>) p.readArraySet(cl));
                }
            }

            readStringArrayMapMap(p, r.forceComponentEnabledSettingsMap,
                    Parcel::readString, Parcel::readInt);

            if (GmsCompat.isEnabled()) {
                String pkgName = GmsCompat.appContext().getPackageName();

                ArrayList<String> perms = r.spoofSelfPermissionChecksMap.get(pkgName);
                r.spoofSelfPermissionChecks = perms != null ?
                        new ArraySet<>(perms) :
                        new ArraySet<>();
            }
            return r;
        }

        @Override
        public GmsCompatConfig[] newArray(int size) {
            return new GmsCompatConfig[size];
        }
    };

    static <V extends Parcelable> void writeStringArrayMapMap(
            ArrayMap<String, ArrayMap<String, V>> outerMap, Parcel p) {
        writeStringArrayMapMap(outerMap, Parcel::writeString,
                (parcel, v) -> v.writeToParcel(parcel, 0), p);
    }

    static <V extends Parcelable> void readStringArrayMapMap(Parcel p,
            ArrayMap<String, ArrayMap<String, V>> outerMap, Parcelable.Creator<V> valueCreator) {

        readStringArrayMapMap(p, outerMap, Parcel::readString, valueCreator::createFromParcel);
    }

    static <K, V> void writeStringArrayMapMap(ArrayMap<String, ArrayMap<K, V>> outerMap,
              BiConsumer<Parcel, K> writeK, BiConsumer<Parcel, V> writeV, Parcel p) {
        ArrayMapEntryWriter<K, V> entryWriter = (map, i, parcel) -> {
            writeK.accept(p, map.keyAt(i));
            writeV.accept(p, map.valueAt(i));
        };
        writeStringArrayMapMap(outerMap, entryWriter, p);
    }

    static <K, V> void readStringArrayMapMap(Parcel p, ArrayMap<String, ArrayMap<K, V>> outerMap,
             Function<Parcel, K> readK, Function<Parcel, V> readV) {
        ArrayMapEntryReader<K, V> entryReader = (parcel, map) -> {
            map.append(readK.apply(p), readV.apply(p));
        };
        readStringArrayMapMap(p, outerMap, entryReader);
    }

    interface ArrayMapEntryWriter<K, V> {
        void write(ArrayMap<K, V> map, int idx, Parcel dst);
    }

    interface ArrayMapEntryReader<K, V> {
        void read(Parcel p, ArrayMap<K, V> dst);
    }

    static <K, V> void writeStringArrayMapMap(ArrayMap<String, ArrayMap<K, V>> outerMap,
                                              ArrayMapEntryWriter<K, V> entryWriter, Parcel p) {
        int outerCnt = outerMap.size();
        p.writeInt(outerCnt);
        for (int outerIdx = 0; outerIdx < outerCnt; ++outerIdx) {
            String outerK = outerMap.keyAt(outerIdx);
            p.writeString(outerK);
            writeArrayMap(outerMap.valueAt(outerIdx), entryWriter, p);
        }
    }

    static <K, V> void readStringArrayMapMap(Parcel p, ArrayMap<String, ArrayMap<K, V>> outerMap,
             ArrayMapEntryReader<K, V> entryReader) {
        int outerCnt = p.readInt();
        outerMap.ensureCapacity(outerCnt);
        for (int outerIdx = 0; outerIdx < outerCnt; ++outerIdx) {
            String outerK = p.readString();
            outerMap.put(outerK, readArrayMap(p, entryReader));
        }
    }

    static <K, V> void writeArrayMap(ArrayMap<K, V> map, ArrayMapEntryWriter<K, V> entryWriter,
                                     Parcel p) {
        int cnt = map.size();
        p.writeInt(cnt);
        for (int i = 0; i < cnt; ++i) {
            entryWriter.write(map, i, p);
        }
    }

    static <K, V> ArrayMap<K, V> readArrayMap(Parcel p, ArrayMapEntryReader<K, V> entryReader) {
        int cnt = p.readInt();
        var map = new ArrayMap<K, V>(cnt);
        for (int i = 0; i < cnt; ++i) {
            entryReader.read(p, map);
        }
        return map;
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
            map.put(p.readString(), p.createStringArrayList());
        }
    }
}
