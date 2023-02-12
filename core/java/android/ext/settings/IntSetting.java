/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Context;

import java.util.function.ToIntFunction;

/** @hide */
@SystemApi
public class IntSetting extends Setting<IntSetting> {
    private int defaultValue;
    private volatile ToIntFunction<Context> defaultValueSupplier;

    @Nullable private final int[] validValues;

    private IntSetting(@NonNull Scope scope, @NonNull String key, @Nullable int[] validValues) {
        super(scope, key);
        this.validValues = validValues;
    }

    public IntSetting(@NonNull Scope scope, @NonNull String key, int defaultValue) {
        this(scope, key, (int[]) null);
        setDefaultValue(defaultValue);
    }

    public IntSetting(@NonNull Scope scope, @NonNull String key, int defaultValue, @NonNull int... validValues) {
        this(scope, key, validValues);
        setDefaultValue(defaultValue);
    }

    public IntSetting(@NonNull Scope scope, @NonNull String key, @NonNull ToIntFunction<Context> defaultValue) {
        this(scope, key, (int[]) null);
        defaultValueSupplier = defaultValue;
    }

    public IntSetting(@NonNull Scope scope, @NonNull String key, @NonNull ToIntFunction<Context> defaultValue,
                      @NonNull int... validValues) {
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

    public final int get(@NonNull Context ctx) {
        return get(ctx, ctx.getUserId());
    }

    // use only if this is a per-user setting and the context is not a per-user one
    public final int get(@NonNull Context ctx, int userId) {
        String valueStr = getRaw(ctx, userId);

        if (valueStr == null) {
            return getDefaultValue(ctx);
        }

        int value;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return getDefaultValue(ctx);
        }

        if (!validateValue(value)) {
            return getDefaultValue(ctx);
        }

        return value;
    }

    public final boolean put(@NonNull Context ctx, int val) {
        if (!validateValue(val)) {
            throw new IllegalArgumentException(Integer.toString(val));
        }
        return putRaw(ctx, Integer.toString(val));
    }

    private void setDefaultValue(int val) {
        if (!validateValue(val)) {
            throw new IllegalStateException("invalid default value " + val);
        }
        defaultValue = val;
    }

    private int getDefaultValue(Context ctx) {
        ToIntFunction<Context> supplier = defaultValueSupplier;
        if (supplier != null) {
            setDefaultValue(supplier.applyAsInt(ctx));
            defaultValueSupplier = null;
        }
        return defaultValue;
    }
}
