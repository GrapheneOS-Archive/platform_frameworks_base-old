package com.android.internal.gmscompat.util;

import android.database.Cursor;
import android.database.CursorWrapper;

public abstract class CursorWrapperExt extends CursorWrapper {

    protected CursorWrapperExt(Cursor orig) {
        super(orig);
    }

    protected abstract void onPositionChanged();

    @Override
    public boolean moveToLast() {
        if (super.moveToLast()) {
            onPositionChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean move(int offset) {
        if (super.move(offset)) {
            onPositionChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean moveToPosition(int position) {
        if (super.moveToPosition(position)) {
            onPositionChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean moveToNext() {
        if (super.moveToNext()) {
            onPositionChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean moveToFirst() {
        if (super.moveToFirst()) {
            onPositionChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean moveToPrevious() {
        if (super.moveToPrevious()) {
            onPositionChanged();
            return true;
        }
        return false;
    }
}
