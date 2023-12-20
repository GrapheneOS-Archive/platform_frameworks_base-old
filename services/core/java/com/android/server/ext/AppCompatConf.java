package com.android.server.ext;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManagerInternal;
import android.content.pm.SigningDetails;
import android.os.PatternMatcher;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;
import com.android.internal.pm.parsing.pkg.PackageImpl;
import com.android.server.LocalServices;
import com.android.server.os.nano.AppCompatProtos;
import com.android.server.os.nano.AppCompatProtos.AppCompatConfig;
import com.android.server.os.nano.AppCompatProtos.CompatConfig;
import com.android.server.pm.pkg.AndroidPackage;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AppCompatConf {
    private static final String TAG = AppCompatConf.class.getSimpleName();

    private static final String CONFIG_HOLDER_PKG_NAME = "app.grapheneos.AppCompatConfig";

    public static class Configs {
        public final long versionCode;
        public final ArrayMap<String, AppCompatConfig> map;

        Configs(long versionCode, ArrayMap<String, AppCompatConfig> map) {
            this.versionCode = versionCode;
            this.map = map;
        }
    }

    private static volatile Configs configs;

    public static Configs getParsedConfigs() {
        return configs;
    }

    @Nullable
    private static AndroidPackage getConfigHolderPackage() {
        var pm = LocalServices.getService(PackageManagerInternal.class);
        AndroidPackage pkg = pm.getPackage(CONFIG_HOLDER_PKG_NAME);
        if (pkg == null) {
            Slog.w(TAG, "missing " + CONFIG_HOLDER_PKG_NAME);
            return null;
        }
        return pkg;
    }

    static void init(Context ctx) {
        AndroidPackage pkg = getConfigHolderPackage();
        if (pkg == null) {
            Slog.w(TAG, "missing " + CONFIG_HOLDER_PKG_NAME);
            // don't register listener
            return;
        }

        update(pkg);

        var filter = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        filter.addDataPath(new PatternMatcher(CONFIG_HOLDER_PKG_NAME, PatternMatcher.PATTERN_LITERAL));
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Slog.d(TAG, "received " + intent);
                AndroidPackage updatedPkg = getConfigHolderPackage();
                if (updatedPkg == null) {
                    Slog.e(TAG, "missing config package after update");
                } else {
                    update(updatedPkg);
                }
            }
        }, filter, null, BackgroundThread.getHandler());
    }

    private static void update(AndroidPackage pkg) {
        String apkPath = pkg.getSplits().get(0).getPath();
        // thread-safe: "configs" field is volatile and map itself is immutable after parsing
        configs = parseFromApk(pkg.getLongVersionCode(), apkPath);
        Slog.d(TAG, "updated from " + apkPath);
    }

    @Nullable
    public static CompatConfig get(Configs configs, PackageImpl pkg) {
        ArrayMap<String, AppCompatConfig> map = configs.map;

        String pkgName = pkg.getPackageName();

        AppCompatConfig acc = map.get(pkgName);

        if (acc == null) {
            return null;
        }

        AppCompatProtos.PackageSpec pkgSpec = acc.packageSpec;

        SigningDetails signingDetails = pkg.getSigningDetails();

        if (signingDetails == SigningDetails.UNKNOWN) {
            Slog.w(TAG, "SigningDetails.UNKNOWN for " + pkgName);
            return null;
        }

        boolean validCert = false;

        for (byte[] cert : pkgSpec.certsSha256) {
            if (signingDetails.hasSha256Certificate(cert)) {
                validCert = true;
                break;
            }
        }

        if (!validCert) {
            Slog.d(TAG, "invalid cert for " + pkgName);
            return null;
        }

        long version = pkg.getLongVersionCode();

        for (CompatConfig c : acc.configs) {
            long min = c.minVersion;
            if (min != 0 && version < min) {
                continue;
            }
            long max = c.maxVersion;
            if (max != 0 && version > max) {
                continue;
            }
            return c;
        }

        Slog.d(TAG, "unknown version " + version + " of " + pkgName);
        return null;
    }

    @Nullable
    private static Configs parseFromApk(long versionCode, String apkPath) {
        try {
            byte[] configBytes;

            try (var f = new ZipFile(apkPath)) {
                ZipEntry e = f.getEntry("app_compat_configs.pb");
                try (var s = f.getInputStream(e)) {
                    configBytes = s.readAllBytes();
                }
            }

            var configsWrapper = AppCompatProtos.AppCompatConfigs.parseFrom(configBytes);

            AppCompatConfig[] configs = configsWrapper.configs;

            var map = new ArrayMap<String, AppCompatConfig>(configs.length);
            for (var e : configs) {
                map.put(e.packageSpec.pkgName, e);
            }

            return new Configs(versionCode, map);
        } catch (Exception e) {
            Slog.e(TAG, "", e);
            return null;
        }
    }

    @Nullable
    public static AppCompatProtos.CompatConfig get(PackageImpl pkg) {
        var configs = com.android.server.ext.AppCompatConf.getParsedConfigs();

        if (configs == null) {
            return null;
        }

        synchronized (pkg) {
            if (configs.versionCode == pkg.cachedCompatConfigVersionCode) {
                return (AppCompatProtos.CompatConfig) pkg.cachedCompatConfig;
            }
        }

        var config = get(configs, pkg);

        synchronized (pkg) {
            pkg.cachedCompatConfigVersionCode = configs.versionCode;
            pkg.cachedCompatConfig = config;
        }

        return config;
    }
}
