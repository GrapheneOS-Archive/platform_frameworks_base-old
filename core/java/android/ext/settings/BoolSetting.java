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
public class BoolSetting extends Setting<BoolSetting> {
    private boolean defaultValue;
    private volatile Function<Context, Boolean> defaultValueSupplier;

    public BoolSetting(@NonNull Scope scope, @NonNull String key, boolean defaultValue) {
        super(scope, key);
        this.defaultValue = defaultValue;
    }

    public BoolSetting(@NonNull Scope scope, @NonNull String key, @NonNull Function<Context, Boolean> defaultValue) {
        super(scope, key);
        defaultValueSupplier = defaultValue;
    }

    public final boolean get(@NonNull Context ctx) {
        return get(ctx, ctx.getUserId());
    }

    // use only if this is a per-user setting and the context is not a per-user one
    public final boolean get(@NonNull Context ctx, int userId) {
        String valueStr = getRaw(ctx, userId);

        if (valueStr == null) {
            return getDefaultValue(ctx);
        }

        if (valueStr.equals("true")) {
            return true;
        }

        if (valueStr.equals("false")) {
            return false;
        }

        try {
            int valueInt = Integer.parseInt(valueStr);
            if (valueInt == 1) {
                return true;
            } else if (valueInt == 0) {
                return false;
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return getDefaultValue(ctx);
    }

    public final boolean put(@NonNull Context ctx, boolean val) {
        return putRaw(ctx, val ? "1" : "0");
    }

    private boolean getDefaultValue(Context ctx) {
        Function<Context, Boolean> supplier = defaultValueSupplier;
        if (supplier != null) {
            defaultValue = supplier.apply(ctx).booleanValue();
            defaultValueSupplier = null;
        }
        return defaultValue;
    }
}
