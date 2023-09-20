package app.grapheneos.hardeningtest;

import android.system.OsConstants;
import android.util.SparseArray;

import java.lang.reflect.Field;

// values from android.system.OsConstants are not compile-time constants, which prevents using
// them in annotations
public class Errno {
    public static final int EPERM = 1;
    public static final int ENOENT = 2;
    public static final int EACCES = 13;
    public static final int EINVAL = 22;

    static String name(int v) {
        String r = errnoNames.get(v);
        return r != null? r : Integer.toString(v);
    }

    private static final SparseArray<String> errnoNames = new SparseArray<>();

    static {
        for (Field f : OsConstants.class.getFields()) {
            String n = f.getName();
            if (n.charAt(0) == 'E' && n.indexOf('_') < 0) {
                try {
                    errnoNames.put((int) f.get(null), f.getName());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
