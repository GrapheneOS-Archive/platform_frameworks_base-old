/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package android.ext.settings;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** @hide */
public class IntSetting extends Setting {
    private int defaultValue;
    private volatile IntSupplier defaultValueSupplier;

    @Nullable private final int[] validValues;

    private IntSetting(Scope scope, String key, @Nullable int[] validValues) {
        super(scope, key);
        this.validValues = validValues;
    }

    public IntSetting(Scope scope, String key, int defaultValue) {
        this(scope, key, (int[]) null);
        setDefaultValue(defaultValue);
    }

    public IntSetting(Scope scope, String key, int defaultValue, int... validValues) {
        this(scope, key, validValues);
        setDefaultValue(defaultValue);
    }

    public IntSetting(Scope scope, String key, IntSupplier defaultValue) {
        this(scope, key, (int[]) null);
        defaultValueSupplier = defaultValue;
    }

    public IntSetting(Scope scope, String key, IntSupplier defaultValue, int... validValues) {
        this(scope, key, validValues);
        defaultValueSupplier = defaultValue;
    }

    public boolean validateValue(int val) {
        if (validValues == null) {
            return true;
        }
        // don't do sort() + bsearch() of validValues array, it's expected to have a small number of entries
        for (int validValue : validValues) {
            if (val == validValue) {
                return true;
            }
        }
        return false;
    }

    public final int get(Context ctx) {
        String valueStr = getRaw(ctx);

        if (valueStr == null) {
            return getDefaultValue();
        }

        int value;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return getDefaultValue();
        }

        if (!validateValue(value)) {
            return getDefaultValue();
        }

        return value;
    }

    public final boolean put(Context ctx, int val) {
        if (!validateValue(val)) {
            throw new IllegalArgumentException(Integer.toString(val));
        }
        return putRaw(ctx, Integer.toString(val));
    }

    public final Object registerObserver(Context ctx, Consumer<IntSetting> callback, Handler handler) {
        return registerObserverInner(ctx, callback, handler);
    }

    private void setDefaultValue(int val) {
        if (!validateValue(val)) {
            throw new IllegalStateException("invalid default value " + val);
        }
        defaultValue = val;
    }

    private int getDefaultValue() {
        IntSupplier supplier = defaultValueSupplier;
        if (supplier != null) {
            setDefaultValue(supplier.getAsInt());
            defaultValueSupplier = null;
        }
        return defaultValue;
    }
}
