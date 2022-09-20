package com.android.internal.gmscompat.flags;

import android.annotation.Nullable;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Build;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatConfig;
import com.android.internal.gmscompat.GmsHooks;

import java.util.Arrays;

// ProcessStablePhenotypeFlags are cached in snapshot files. These files are updated when snapshot
// tokens change. To update these tokens when GmsCompatConfig changes, pretend that a change was
// made to the database by offsetting ChangeCount by the value of version of GmsCompatConfig.
public class GmsPhenotypeChangeCountsCursor extends CursorWrapper {
    private static final String TAG = "GmsPhenotypeChangeCountsCursor";

    private GmsCompatConfig config;
    private int countCol;

    private GmsPhenotypeChangeCountsCursor(Cursor orig) {
        super(orig);
    }

    @Nullable
    public static GmsPhenotypeChangeCountsCursor maybeCreate(String selection, String[] selectionArgs, Cursor cursor) {
        if (!"packageName = ?".equals(selection)) {
            return null;
        }

        int countCol = cursor.getColumnIndex("count");
        if (countCol < 0) {
            if (Build.isDebuggable()) {
                Log.d(TAG, "unexpected cursor columns: " + Arrays.toString(cursor.getColumnNames()));
            }
            return null;
        }

        GmsCompatConfig config = GmsHooks.config();
        String packageName = selectionArgs[0];

        if (config.flags.get(packageName) == null) {
            return null;
        }

        GmsPhenotypeChangeCountsCursor res = new GmsPhenotypeChangeCountsCursor(cursor);
        res.config = config;
        res.countCol = countCol;
        return res;
    }

    @Override
    public long getLong(int columnIndex) {
        long orig = mCursor.getLong(columnIndex);
        if (columnIndex == countCol) {
            return orig + config.version;
        }
        return orig;
    }
}
