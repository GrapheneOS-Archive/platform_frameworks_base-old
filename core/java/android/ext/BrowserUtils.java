package android.ext;

import android.content.Context;

import com.android.internal.R;
import com.android.internal.util.ArrayUtils;

/** @hide */
public class BrowserUtils {

    public static boolean isSystemBrowser(Context ctx, String pkg) {
        // O(n), array is expected to have 1 or 2 (original-package) entries
        return ArrayUtils.contains(getSystemBrowserPkgs(ctx), pkg);
    }

    private static volatile String[] systemBrowserPkgs;

    public static String[] getSystemBrowserPkgs(Context ctx) {
        String[] arr = systemBrowserPkgs;
        if (arr == null) {
            arr = ctx.getResources().getStringArray(R.array.system_browser_package_names);
            systemBrowserPkgs = arr;
        }
        return arr;
    }
}
