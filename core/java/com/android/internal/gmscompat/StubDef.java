/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.gmscompat;

import android.annotation.Nullable;
import android.app.compat.gms.GmsCompat;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Collections;

import libcore.util.SneakyThrow;

public class StubDef implements Parcelable {
    private static final String TAG = "StubDef";

    public int type;
    public long integerVal;
    public double doubleVal;
    public String stringVal;

    public static final int VOID = 0;
    public static final int NULL = 1; // only for types that implement Parcelable
    public static final int NULL_STRING = 2;
    public static final int NULL_ARRAY = 3;
    public static final int EMPTY_BYTE_ARRAY = 4;
    public static final int EMPTY_INT_ARRAY = 5;
    public static final int EMPTY_LONG_ARRAY = 6;
    public static final int EMPTY_STRING = 7;
    public static final int EMPTY_LIST = 8;
    public static final int EMPTY_MAP = 9;

    public static final int BOOLEAN = 10;
    public static final int BYTE = 11;
    public static final int INT = 12;
    public static final int LONG = 13;
    public static final int FLOAT = 14;
    public static final int DOUBLE = 15;
    public static final int STRING = 16;

    public static final int THROW = 17;

    // see com.android.modules.utils.SynchronousResultReceiver.Result#getValue()
    public static final int DEFAULT = 18;

    @Nullable
    public static StubDef find(Throwable e, GmsCompatConfig config) {
        StackTraceElement[] steArr = e.getStackTrace();
        ClassLoader defaultClassLoader = GmsCompat.appContext().getClassLoader();

        // first 2 elements are guaranteed to be inside the Parcel class
        final int firstIndex = 2;

        StackTraceElement targetMethod = null;

        // To find out which API call caused the exception, iterate through the stack trace until
        // the first app's class (app's classes are loaded with PathClassLoader)
        for (int i = firstIndex; i < steArr.length; ++i) {
            StackTraceElement ste = steArr[i];
            String className = ste.getClassName();
            Class class_;
            try {
                class_ = Class.forName(className, false, defaultClassLoader);
            } catch (ClassNotFoundException cnfe) {
                return null;
            }

            ClassLoader classLoader = class_.getClassLoader();
            if (classLoader == null) {
                return null;
            }

            String clName = classLoader.getClass().getName();

            if ("java.lang.BootClassLoader".equals(clName)) {
                continue;
            }

            if (!"dalvik.system.PathClassLoader".equals(clName)) {
                return null;
            }

            if (i == firstIndex) {
                return null;
            }

            targetMethod = steArr[i - 1];
            break;
        }

        if (targetMethod == null) {
            return null;
        }

        return find(targetMethod.getClassName(), targetMethod.getMethodName(), config);
    }

    @Nullable
    private static StubDef find(String className, String methodName, GmsCompatConfig config) {
        ArrayMap<String, StubDef> classStubs = config.stubs.get(className);
        if (classStubs == null) {
            return null;
        }

        return classStubs.get(methodName);
    }

    public boolean stubOutMethod(Parcel p) {
        p.setDataPosition(0);
        p.setDataSize(0);

        final long integer = integerVal;

        switch (type) {
            case VOID:
                break;
            case NULL:
                p.writeTypedObject((Parcelable) null, 0);
                break;
            case NULL_STRING:
                p.writeString(null);
                break;
            case NULL_ARRAY:
                p.writeInt(-1);
                break;
            case EMPTY_BYTE_ARRAY:
                p.writeByteArray(new byte[0]);
                break;
            case EMPTY_INT_ARRAY:
                p.writeIntArray(new int[0]);
                break;
            case EMPTY_LONG_ARRAY:
                p.writeLongArray(new long[0]);
                break;
            case EMPTY_STRING:
                p.writeString("");
                break;
            case EMPTY_LIST:
                p.writeList(Collections.emptyList());
                break;
            case EMPTY_MAP:
                p.writeMap(Collections.emptyMap());
                break;
            case BOOLEAN:
                p.writeBoolean(integer != 0);
                break;
            case BYTE:
                p.writeByte((byte) integer);
                break;
            case INT:
                p.writeInt((int) integer);
                break;
            case LONG:
                p.writeLong(integer);
                break;
            case FLOAT:
                p.writeFloat((float) doubleVal);
                break;
            case DOUBLE:
                p.writeDouble(doubleVal);
                break;
            case STRING:
                p.writeString(stringVal);
                break;
            case THROW: {
                Throwable t;
                try {
                    Class class_ = Class.forName(stringVal);
                    t = (Throwable) class_.newInstance();
                } catch (ReflectiveOperationException e) {
                    Log.e(TAG, "", e);
                    return false;
                }
                SneakyThrow.sneakyThrow(t);
                break;
            }
            default:
                Log.i(TAG, "unknown type " + type);
                // it's fine that Parcel is reset at this point, it won't be read:
                // a pending exception will be thrown when this method returns false
                return false;
        }

        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel p, int flags) {
        p.writeInt(type);
        p.writeLong(integerVal);
        p.writeDouble(doubleVal);
        p.writeString(stringVal);
    }

    public static final Parcelable.Creator<StubDef> CREATOR = new Creator<>() {
        @Override
        public StubDef createFromParcel(Parcel p) {
            StubDef d = new StubDef();
            d.type = p.readInt();
            d.integerVal = p.readLong();
            d.doubleVal = p.readDouble();
            d.stringVal = p.readString();
            return d;
        }

        @Override
        public StubDef[] newArray(int size) {
            return new StubDef[size];
        }
    };
}
