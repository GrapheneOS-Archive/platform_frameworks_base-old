package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.UserHandle;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** @hide */
@SystemApi
public class BoolSysProperty extends BoolSetting {

    public BoolSysProperty(@NonNull String key, boolean defaultValue) {
        super(Scope.SYSTEM_PROPERTY, key, defaultValue);
    }

    public BoolSysProperty(@NonNull String key, @NonNull Function<Context, Boolean> defaultValue) {
        super(Scope.SYSTEM_PROPERTY, key, defaultValue);
    }

    public boolean get() {
        //noinspection DataFlowIssue
        return super.get(null, UserHandle.USER_SYSTEM);
    }

    public boolean put(boolean val) {
        //noinspection DataFlowIssue
        return super.put(null, val);
    }
}
