/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package android.ext.settings;

import android.content.Context;
import android.os.Handler;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** @hide */
public class BoolSetting extends Setting {
    private boolean defaultValue;
    private volatile BooleanSupplier defaultValueSupplier;

    public BoolSetting(Scope scope, String key, boolean defaultValue) {
        super(scope, key);
        this.defaultValue = defaultValue;
    }

    public BoolSetting(Scope scope, String key, BooleanSupplier defaultValue) {
        super(scope, key);
        defaultValueSupplier = defaultValue;
    }

    public final boolean get(Context ctx) {
        String valueStr = getRaw(ctx);

        if (valueStr == null) {
            return getDefaultValue();
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

        return getDefaultValue();
    }

    public final boolean put(Context ctx, boolean val) {
        return putRaw(ctx, val ? "1" : "0");
    }

    public final Object registerObserver(Context ctx, Consumer<BoolSetting> callback, Handler handler) {
        return registerObserverInner(ctx, callback, handler);
    }

    private boolean getDefaultValue() {
        BooleanSupplier supplier = defaultValueSupplier;
        if (supplier != null) {
            defaultValue = supplier.getAsBoolean();
            defaultValueSupplier = null;
        }
        return defaultValue;
    }
}
