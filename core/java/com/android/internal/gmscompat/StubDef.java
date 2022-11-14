/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.gmscompat;

import android.annotation.Nullable;
import android.app.compat.gms.GmsCompat;
import android.content.pm.ParceledListSlice;
import android.content.pm.StringParceledListSlice;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import libcore.util.SneakyThrow;

public class StubDef implements Parcelable {
    private static final String TAG = "StubDef";

    public int type;
    public long integerVal;
    public double doubleVal;
    public String stringVal;
    private volatile Class parcelListType;

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

    public static final int FIND_MODE_Parcel = 0;
    public static final int FIND_MODE_SynchronousResultReceiver = 1;

    @Nullable
    public static StubDef find(StackTraceElement[] stackTrace, GmsCompatConfig config, int mode) {
        int firstIndex;
        if (mode == FIND_MODE_Parcel) {
            // first four stack trace entries are known:
            // android.os.Parcel.createExceptionOrNull
            // android.os.Parcel.createException
            // android.os.Parcel.readException
            // android.os.Parcel.readException
            firstIndex = 4;
        } else if (mode == FIND_MODE_SynchronousResultReceiver) {
            // first entry is from GmsModuleHooks method
            firstIndex = 1;
        } else {
            return null;
        }

        ClassLoader defaultClassLoader = GmsCompat.appContext().getClassLoader();

        StackTraceElement targetMethod = null;
        Class stubProxyClass = null;
        String stubProxyMethodName = null;

        // To find out which API call caused the exception, iterate through the stack trace until
        // the first app's class (app's classes are loaded with PathClassLoader)
        for (int i = firstIndex; i < stackTrace.length; ++i) {
            StackTraceElement ste = stackTrace[i];
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

            String loaderName = classLoader.getClass().getName();

            if ("java.lang.BootClassLoader".equals(loaderName)) {
                if (stubProxyClass == null && className.endsWith("$Stub$Proxy")) {
                    stubProxyClass = class_;
                    stubProxyMethodName = ste.getMethodName();
                }
                continue;
            }

            if (mode == FIND_MODE_Parcel && stubProxyClass == null) {
                return null;
            }

            if (!"dalvik.system.PathClassLoader".equals(loaderName)) {
                return null;
            }

            if (i == firstIndex) {
                return null;
            }

            targetMethod = stackTrace[i - 1];
            break;
        }

        if (targetMethod == null) {
            return null;
        }

        ArrayMap<String, StubDef> classStubs = config.stubs.get(targetMethod.getClassName());

        if (classStubs == null) {
            return null;
        }

        StubDef stub = classStubs.get(targetMethod.getMethodName());

        if (stub == null) {
            return null;
        }

        if (stub.type == EMPTY_LIST && stub.parcelListType == null) {
            if (stubProxyClass == null) {
                Log.d(TAG, "stub proxy class not found for " + targetMethod);
                return null;
            }

            for (Method m : stubProxyClass.getDeclaredMethods()) {
                if (stubProxyMethodName.equals(m.getName())) {
                    stub.parcelListType = m.getReturnType();
                    break;
                }
            }

            if (stub.parcelListType == null) {
                Log.d(TAG, "stub proxy method not found for " + targetMethod);
                return null;
            }
        }

        return stub;
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
            case EMPTY_LIST: {
                Class t = parcelListType;
                if (t == List.class) {
                    p.writeList(Collections.emptyList());
                } else if (t == ParceledListSlice.class) {
                    p.writeTypedObject(ParceledListSlice.emptyList(), 0);
                } else if (t == StringParceledListSlice.class) {
                    p.writeTypedObject(StringParceledListSlice.emptyList(), 0);
                } else {
                    Log.d(TAG, "unknown parcel list type " + type);
                    return false;
                }
                break;
            }
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

        p.setDataPosition(0);
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
