package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.UserHandle;

import java.util.function.ToIntFunction;

/** @hide */
@SystemApi
public class IntSysProperty extends IntSetting {

    public IntSysProperty(@NonNull String key, int defaultValue) {
        super(Scope.SYSTEM_PROPERTY, key, defaultValue);
    }

    public IntSysProperty(@NonNull String key, int defaultValue, @NonNull int... validValues) {
        super(Scope.SYSTEM_PROPERTY, key, defaultValue, validValues);
    }

    public IntSysProperty(@NonNull String key, @NonNull ToIntFunction<Context> defaultValue) {
        super(Scope.SYSTEM_PROPERTY, key, defaultValue);
    }

    public IntSysProperty(@NonNull String key, @NonNull ToIntFunction<Context> defaultValue, @NonNull int... validValues) {
        super(Scope.SYSTEM_PROPERTY, key, defaultValue, validValues);
    }

    public int get() {
        //noinspection DataFlowIssue
        return super.get(null, UserHandle.USER_SYSTEM);
    }

    public boolean put(int val) {
        //noinspection DataFlowIssue
        return super.put(null, val);
    }
}
