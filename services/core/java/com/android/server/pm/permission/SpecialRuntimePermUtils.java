package com.android.server.pm.permission;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.os.Build;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.EmptyArray;
import android.util.LruCache;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.pm.pkg.component.ParsedUsesPermission;
import com.android.server.LocalServices;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.PackageStateInternal;

import java.util.List;

public class SpecialRuntimePermUtils {
    private static final String TAG = SpecialRuntimePermUtils.class.getSimpleName();

    private static final ArraySet<String> specialRuntimePermissions = new ArraySet<>(new String[] {
    });

    public static boolean isSpecialRuntimePermission(String permission) {
        return specialRuntimePermissions.contains(permission);
    }

    public static String[] getAll() {
        return specialRuntimePermissions.toArray(EmptyArray.STRING);
    }

    public static boolean shouldAutoGrant(Context ctx, String packageName, int userId, String perm) {
        if (!isSpecialRuntimePermission(perm)) {
            return false;
        }

        return !isAutoGrantSkipped(packageName, userId, perm);
    }

    public static int getFlags(PackageManagerService pm, AndroidPackage pkg, PackageState pkgState, int userId) {
        int flags = 0;

        for (ParsedUsesPermission perm : pkg.getUsesPermissions()) {
            String name = perm.getName();
            switch (name) {
                default:
                    continue;
            }
        }

        return flags;
    }

    // Maps userIds to map of package names to permissions that should not be auto granted
    private static SparseArray<LruCache<String, List<String>>> skipAutoGrantsMap = new SparseArray<>();

    public static void skipAutoGrantsForPackage(String packageName, int userId, List<String> perms) {
        PackageStateInternal psi = LocalServices.getService(PackageManagerInternal.class).getPackageStateInternal(packageName);
        if (psi != null && psi.isSystem()) {
            return;
        }

        synchronized (skipAutoGrantsMap) {
            LruCache<String, List<String>> userMap = skipAutoGrantsMap.get(userId);
            if (userMap == null) {
                // 50 entries should be enough, only 1 is needed in vast majority of cases
                userMap = new LruCache<>(50);
                skipAutoGrantsMap.put(userId, userMap);
            }
            userMap.put(packageName, perms);
        }
    }

    private static boolean isAutoGrantSkipped(String packageName, int userId, String perm) {
        List<String> permList;
        synchronized (skipAutoGrantsMap) {
            LruCache<String, List<String>> userMap = skipAutoGrantsMap.get(userId);
            if (userMap == null) {
                return false;
            }
            permList = userMap.get(packageName);
        }
        if (permList == null) {
            return false;
        }
        return permList.contains(perm);
    }

    private SpecialRuntimePermUtils() {}
}
