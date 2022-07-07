package android.app.compat.browser;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.ActivityThread;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.browsercompat.BrowserInfo;

/**
 * Browser apps compatibility layer allowing to check the calling app's permission upon launching a browser intent.
 * Checking the permission of the app launching an activity without result, along with its identity
 * is normally restricted for select privileged or system apps.
 * The said capability is obtained from making com.android.server.wm.ActivityClientController#canGetLaunchedFrom
 * return true for select packageName and signature found in BrowserInfo,
 * or when the said app is a system or OS bundled browser.
 * Used for enforcing INTERNET permission on Vanadium.
 * @hide
 */
@SystemApi
public final class BrowserCompat {
    private static final String TAG = "BrowserCompat";
    private static final boolean LOG_DBG = Log.isLoggable(TAG, Log.DEBUG);

    /**
     * Check whether the given app is a system browser or browser signed by OS developers.
     * @see android.app.compat.gms.GmsCompat#isGmsApp(@NonNull String, int)
     * @hide
     */
    public static boolean isOsSignedBrowser(@NonNull String packageName, int userId) {
        // Check first if the given package name is a system bundled or installed browser.
        if (isSystemBrowser(packageName, userId)) {
            return true;
        }
        // Then verify if its package name is used by the same OS developers.
        if (!isOsSignedBrowserPackageName(packageName)) {
            return false;
        }
        PackageInfo pkg;
        IPackageManager pm = ActivityThread.getPackageManager();
        long token = Binder.clearCallingIdentity();
        try {
            pkg = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES, userId);
            if (pkg == null) {
                return false;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return isOsSignedBrowser(pkg);
    }

    // @see android.app.compat.gms.GmsCompat#isGmsApp(PackageInfo, boolean)
    private static boolean isOsSignedBrowser(@NonNull PackageInfo pkg) {
        ApplicationInfo app = pkg.applicationInfo;
        if (app == null) {
            return false;
        }
        SigningInfo si = pkg.signingInfo;
        return isOsSignedBrowser(app.packageName,
            si.getApkContentsSigners(), si.getSigningCertificateHistory());
    }

    // @see android.app.compat.gms.GmsCompat#isGmsApp(@NonNull String, Signature[], Signature[], boolean, String)
    private static boolean isOsSignedBrowser(@NonNull String packageName, Signature[] signatures,
                                   Signature[] pastSignatures) {
        // Validate signature to avoid affecting apps using the same package names.
        boolean validCert = validateCerts(signatures);

        if (!validCert && pastSignatures != null) {
            validCert = validateCerts(pastSignatures);
        }

        if (LOG_DBG) {
            Log.d(TAG, "The package being checked has " + (!validCert ? "in" : "")
                    + "eligible signature for browser compatibility layer");
        }
        return validCert;
    }

    private static boolean isOsSignedBrowserPackageName(String pkgName) {
        return BrowserInfo.PACKAGE_VANADIUM_LEGACY.equals(pkgName)
            || BrowserInfo.PACKAGE_VANADIUM.equals(pkgName);
    }

    /**
     * Determine whenever the given package name is a browser bundled in OS, a system browser.
     * Based on #com.android.permissioncontroller.role.model.BrowserRoleBehavior.getQualifyingPackagesAsUserInternal
     * and #com.android.server.wm.ActivityClientController.isLauncherActivity
     * @hide
     */
    private static boolean isSystemBrowser(String pkgName, int userId) {
        IPackageManager pm = ActivityThread.getPackageManager();
        final Intent browserIntent = new Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("http", "", null));
        // Only match system-installed apps that resolves browser intent,
        // regardless of it being boot aware or not.
        // Disable flags are omitted because disabled apps can't call this code.
        final int flags = PackageManager.MATCH_DIRECT_BOOT_AWARE
               | PackageManager.MATCH_DIRECT_BOOT_UNAWARE | PackageManager.MATCH_SYSTEM_ONLY;
        try {
            final ParceledListSlice<ResolveInfo> resolved = pm.queryIntentActivities(
                    browserIntent, null, flags, userId);
            if (resolved == null) {
                return false;
            }
            for (final ResolveInfo ri : resolved.getList()) {
                String systemPkg = ri.activityInfo.packageName;
                if (LOG_DBG) {
                    Log.d(TAG, "Resolved package name includes " + systemPkg);
                }
                if (systemPkg.equals(pkgName) && ri.handleAllWebDataURI) {
                    return true;
                }
            }
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        return false;
    }

    // @see android.app.compat.gms.GmsCompat#validateCerts
    private static boolean validateCerts(Signature[] signatures) {
        for (Signature signature : signatures) {
            String s = signature.toCharsString();

            for (String validSignature : BrowserInfo.VALID_SIGNATURES) {
                if (s.equals(validSignature)) {
                    return true;
                }
            }
        }
        return false;
    }

    private BrowserCompat() { }
}
