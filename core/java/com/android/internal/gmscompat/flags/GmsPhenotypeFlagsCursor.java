/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.gmscompat.flags;

import android.annotation.Nullable;
import android.database.Cursor;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatConfig;
import com.android.internal.gmscompat.GmsHooks;
import com.android.internal.gmscompat.util.CursorWrapperExt;

// Intercepts reads of PhenotypeFlags that are defined in GmsCompatConfig
public class GmsPhenotypeFlagsCursor extends CursorWrapperExt {
    private static final String TAG = "GmsPhenotypeCursor";
    private static final boolean DBG = false;

    private GmsCompatConfig config;
    private GmsFlag curFlag;

    // non-null when all rows of this cursor are from the same configPackageName
    private @Nullable ArrayMap<String, GmsFlag> overrides;

    private int configPackageNameCol;
    private int nameCol;
    private int intValCol;
    private int boolValCol;
    private int floatValCol;
    private int stringValCol;
    private int extensionValCol;

    private GmsPhenotypeFlagsCursor(Cursor orig) {
        super(orig);
    }

    @Nullable
    public static GmsPhenotypeFlagsCursor maybeCreate(String selection, String[] selectionArgs, Cursor cursor) {
        int nameCol = cursor.getColumnIndex("name");
        if (nameCol < 0) {
            return null;
        }

        GmsCompatConfig config = GmsHooks.config();

        ArrayMap<String, GmsFlag> packageFlagOverrides = null;
        int configPackageNameCol = -1;

        if (selection != null && selection.startsWith("packageName = ?")) {
            String configPackageName = selectionArgs[0];
            if (DBG) Log.d(TAG, "configPackageName " + configPackageName);
            packageFlagOverrides = config.flags.get(configPackageName);

            if (packageFlagOverrides == null) {
                return null;
            }
        } else {
            configPackageNameCol = cursor.getColumnIndex("packageName");
            if (configPackageNameCol < 0) {
                return null;
            }
        }

        GmsPhenotypeFlagsCursor r = new GmsPhenotypeFlagsCursor(cursor);
        r.config = config;
        r.overrides = packageFlagOverrides;
        r.configPackageNameCol = configPackageNameCol;
        r.nameCol = nameCol;
        // getColumnIndex() will return -1 if column is absent
        r.intValCol = cursor.getColumnIndex("intVal");
        r.boolValCol = cursor.getColumnIndex("boolVal");
        r.floatValCol = cursor.getColumnIndex("floatVal");
        r.stringValCol = cursor.getColumnIndex("stringVal");
        r.extensionValCol = cursor.getColumnIndex("extensionVal");
        return r;
    }

    @Override
    protected void onPositionChanged() {
        String name = mCursor.getString(nameCol);

        GmsFlag flag = null;
        if (overrides != null) {
            flag = overrides.get(name);
        } else {
            String packageName = mCursor.getString(configPackageNameCol);
            ArrayMap<String, GmsFlag> packageOverrides = config.flags.get(packageName);
            if (packageOverrides != null) {
                flag = packageOverrides.get(name);
            }
        }

        if (flag != null && !flag.shouldOverride()) {
            flag = null;
        }

        if (DBG && flag != null) {
            Integer committed = null;
            int commitedIdx = mCursor.getColumnIndex("committed");
            if (commitedIdx >= 0) {
                committed = Integer.valueOf(mCursor.getInt(commitedIdx));
            }
            Log.d(TAG, "cur flag " + flag.name + " committed " + committed);
        }
        curFlag = flag;
    }

    @Override
    public int getInt(int columnIndex) {
        int orig = mCursor.getInt(columnIndex);
        if (curFlag != null && columnIndex == boolValCol) {
            if (DBG) Log.d(TAG, "getInt " + curFlag.name);
            return curFlag.boolVal(orig);
        }
        return orig;
    }

    @Override
    public long getLong(int columnIndex) {
        long orig = mCursor.getLong(columnIndex);
        if (curFlag != null && columnIndex == intValCol) {
            if (DBG) Log.d(TAG, "getLong " + curFlag.name);
            return curFlag.intVal(orig);
        }
        return orig;
    }

    @Override
    public double getDouble(int columnIndex) {
        double orig = mCursor.getDouble(columnIndex);
        if (curFlag != null && columnIndex == floatValCol) {
            if (DBG) Log.d(TAG, "getFloat " + curFlag.name);
            return curFlag.floatVal(orig);
        }
        return orig;
    }

    @Override
    public String getString(int columnIndex) {
        String orig = mCursor.getString(columnIndex);
        if (curFlag != null && columnIndex == stringValCol) {
            if (DBG) Log.d(TAG, "getString " + curFlag.name);
            return curFlag.stringVal(orig);
        }
        return orig;
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        byte[] orig = mCursor.getBlob(columnIndex);
        if (curFlag != null && columnIndex == extensionValCol) {
            if (DBG) Log.d(TAG, "getBlob " + curFlag.name);
            return curFlag.extensionVal(orig);
        }
        return orig;
    }
}
