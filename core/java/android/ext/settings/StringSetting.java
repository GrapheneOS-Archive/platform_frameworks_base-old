/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Context;

import java.util.function.Function;

/** @hide */
@SystemApi
public class StringSetting extends Setting<StringSetting> {
    private String defaultValue;
    private volatile Function<Context, String> defaultValueSupplier;

    public StringSetting(@NonNull Scope scope, @NonNull String key, @NonNull String defaultValue) {
        super(scope, key);
        setDefaultValue(defaultValue);
    }

    public StringSetting(@NonNull Scope scope, @NonNull String key, @NonNull Function<Context, String> defaultValue) {
        super(scope, key);
        this.defaultValueSupplier = defaultValue;
    }

    public boolean validateValue(@NonNull String val) {
        return true;
    }

    @NonNull
    public final String get(@NonNull Context ctx) {
        return get(ctx, ctx.getUserId());
    }

    @NonNull
    // use only if this is a per-user setting and the context does not specify the userId
    public final String get(@NonNull Context ctx, int userId) {
        String s = getRaw(ctx, userId);
        if (s == null || !validateValue(s)) {
            return getDefaultValue(ctx);
        }
        return s;
    }

    public final boolean put(@NonNull Context ctx, @NonNull String val) {
        if (!validateValue(val)) {
            throw new IllegalStateException("invalid value " + val);
        }
        return putRaw(ctx, val);
    }

    private void setDefaultValue(@NonNull String val) {
        if (!validateValue(val)) {
            throw new IllegalStateException("invalid default value " + val);
        }
        defaultValue = val;
    }

    @NonNull
    private String getDefaultValue(Context ctx) {
        Function<Context, String> supplier = defaultValueSupplier;
        if (supplier != null) {
            setDefaultValue(supplier.apply(ctx));
            defaultValueSupplier = null;
        }
        return defaultValue;
    }
}
