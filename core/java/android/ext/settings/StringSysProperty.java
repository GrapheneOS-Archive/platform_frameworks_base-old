package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.UserHandle;

import java.util.function.Function;

/** @hide */
@SystemApi
public class StringSysProperty extends StringSetting {

    public StringSysProperty(@NonNull String key, @NonNull String defaultValue) {
        super(Scope.SYSTEM_PROPERTY, key, defaultValue);
    }

    public StringSysProperty(@NonNull String key, @NonNull Function<Context, String> defaultValue) {
        super(Scope.SYSTEM_PROPERTY, key, defaultValue);
    }

    @NonNull
    public String get() {
        //noinspection DataFlowIssue
        return super.get(null, UserHandle.USER_SYSTEM);
    }

    public boolean put(@NonNull String val) {
        //noinspection DataFlowIssue
        return super.put(null, val);
    }
}
