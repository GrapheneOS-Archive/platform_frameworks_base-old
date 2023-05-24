package android.ext.settings;

import java.lang.reflect.Field;
import java.util.Set;

/** @hide */
public class ExtSettings {

    public static final BoolSysProperty EXEC_SPAWNING = new BoolSysProperty(
            "persist.security.exec_spawn", true);

    private ExtSettings() {}

    // used for making settings defined in this class unreadable by third-party apps
    public static void getKeys(Setting.Scope scope, Set<String> dest) {
        for (Field field : ExtSettings.class.getDeclaredFields()) {
            if (!Setting.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Setting s;
            try {
                s = (Setting) field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }

            if (s.getScope() == scope) {
                if (!dest.add(s.getKey())) {
                    throw new IllegalStateException("duplicate definition of setting " + s.getKey());
                }
            }
        }
    }
}
