/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package android.ext.settings;

import android.content.Context;
import android.os.Handler;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** @hide */
public class StringSetting extends Setting {
    private String defaultValue;
    private volatile Supplier<String> defaultValueSupplier;

    public StringSetting(Scope scope, String key, String defaultValue) {
        super(scope, key);
        setDefaultValue(defaultValue);
    }

    public StringSetting(Scope scope, String key, Supplier<String> defaultValue) {
        super(scope, key);
        this.defaultValueSupplier = defaultValue;
    }

    public boolean validateValue(String val) {
        return true;
    }

    public final String get(Context ctx) {
        String s = getRaw(ctx);
        if (s == null || !validateValue(s)) {
            return getDefaultValue();
        }
        return s;
    }

    public final boolean put(Context ctx, String val) {
        if (!validateValue(val)) {
            throw new IllegalStateException("invalid value " + val);
        }
        return putRaw(ctx, val);
    }

    public final Object registerObserver(Context ctx, Consumer<StringSetting> callback, Handler handler) {
        return registerObserverInner(ctx, callback, handler);
    }

    private void setDefaultValue(String val) {
        if (!validateValue(val)) {
            throw new IllegalStateException("invalid default value " + val);
        }
        defaultValue = val;
    }

    private String getDefaultValue() {
        Supplier<String> supplier = defaultValueSupplier;
        if (supplier != null) {
            setDefaultValue(supplier.get());
            defaultValueSupplier = null;
        }
        return defaultValue;
    }
}
