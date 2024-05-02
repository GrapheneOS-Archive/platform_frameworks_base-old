/*
 * Copyright (C) 2022 GrapheneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.pm;

import android.annotation.Nullable;
import android.content.pm.SigningDetails;
import android.content.pm.parsing.result.ParseResult;
import android.content.pm.parsing.result.ParseTypeImpl;
import android.os.Build;
import android.os.SystemProperties;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.pm.parsing.pkg.ParsedPackage;
import com.android.internal.pm.pkg.parsing.ParsingPackageUtils;
import com.android.internal.security.VerityUtils;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.AndroidPackageSplit;

import static android.content.pm.PackageManager.INSTALL_FAILED_BAD_SIGNATURE;
import static android.content.pm.PackageManager.INSTALL_FAILED_INVALID_APK;
import static android.content.pm.PackageManager.INSTALL_FAILED_UPDATE_INCOMPATIBLE;

// Performs additional checks on system package updates
public class PackageVerityExt {
    private static final String TAG = PackageVerityExt.class.getSimpleName();

    // Parsed packages from immutable partitions. Static shared libraries are handled separately
    // due to a different policy that OS uses for their replacement
    private static final ArrayMap<String, AndroidPackage> packages = new ArrayMap<>();
    private static final ArrayMap<String, AndroidPackage> staticSharedLibraries = new ArrayMap<>();

    // Called when PackageManager scans a package from immutable system image partition during OS boot.
    // All packages from immutable partitions are scanned before any packages from mutable partitions.
    public static void addSystemPackage(AndroidPackage pkg) {
        if (pkg.isStaticSharedLibrary()) {
            String name = pkg.getStaticSharedLibraryName();
            AndroidPackage prev;
            synchronized (staticSharedLibraries) {
                prev = staticSharedLibraries.put(name, pkg);
            }
            if (prev != null) {
                Slog.w(TAG, "duplicate static shared lib " + name
                        + ": prev " + prev.getPath() + " -> new " + pkg.getPath());
            }
        } else {
            String name = pkg.getManifestPackageName();
            AndroidPackage prev;
            synchronized (packages) {
                prev = packages.put(name, pkg);
            }
            if (prev != null) {
                Slog.w(TAG, "duplicate system package " + name + ": prev " + prev.getPath() +
                        " -> new " + pkg.getPath());
            }
        }
    }

    // If pkg is a system package update, returns its matching system image package
    @Nullable public static AndroidPackage getSystemPackage(AndroidPackage pkg) {
        if (pkg.isStaticSharedLibrary()) {
            String name = pkg.getStaticSharedLibraryName();
            synchronized (staticSharedLibraries) {
                return staticSharedLibraries.get(name);
            }
        } else {
            String name = pkg.getManifestPackageName();
            synchronized (packages) {
                return packages.get(name);
            }
        }
    }

    // Called when PackageManager scans a package from mutable partition (ie /data) during OS boot.
    // PackageManagerException thrown from here will prevent this package from replacing its system
    // image version.
    public static void checkSystemPackageUpdate(AndroidPackage maybeSystemPackageUpdate) throws PackageManagerException {
        final AndroidPackage systemPkg = getSystemPackage(maybeSystemPackageUpdate);

        if (systemPkg == null) {
            // not a system package update
            return;
        }

        final AndroidPackage systemPkgUpdate = maybeSystemPackageUpdate;

        Slog.d(TAG, "Performing verification of system package update "
                + systemPkgUpdate.getManifestPackageName());

        boolean checkVersionCode = true;
        if (Build.IS_DEBUGGABLE) {
            // also checked in InstallPackageHelper before package installation
            if (SystemProperties.getBoolean("persist.disable_same_versionCode_sys_pkg_update_check", false)) {
                checkVersionCode = false;
                long version = systemPkgUpdate.getLongVersionCode();
                if (systemPkg.getLongVersionCode() == version) {
                    Slog.w(TAG, "Updated system package is used for " + systemPkg.getPackageName()
                            + " despite it having same version code ("
                            + version + ") as the system image package");
                }
            }
        }

        if (checkVersionCode && systemPkg.getLongVersionCode() >= systemPkgUpdate.getLongVersionCode()) {
            throw new PackageManagerException(INSTALL_FAILED_UPDATE_INCOMPATIBLE,
                    "versionCode of system image package (" + systemPkg.getLongVersionCode()
                            + ") is >= versionCode of system package update ("
                            + systemPkgUpdate.getLongVersionCode() + ")");
        }

        boolean checkFsVerity = true;
        if (Build.IS_DEBUGGABLE) {
            if (SystemProperties.getBoolean("persist.disable_boot_time_fsverity_check", false)) {
                checkFsVerity = false;
            }
        }

        if (checkFsVerity) {
            checkFsVerity(systemPkgUpdate);
        }

        final SigningDetails updatePkgSigningDetails = parseSigningDetails(systemPkgUpdate,
                // verify APK against its signature
                false);

        final SigningDetails systemPkgSigningDetails = parseSigningDetails(systemPkg,
                // skip signature verification, system image APKs are protected by verified boot
                true);

        final boolean valid = updatePkgSigningDetails.checkCapability(systemPkgSigningDetails,
                    SigningDetails.CertCapabilities.INSTALLED_DATA)
                || systemPkgSigningDetails.checkCapability(updatePkgSigningDetails,
                    SigningDetails.CertCapabilities.ROLLBACK);

        if (!valid) {
            String msg = "System package update " + systemPkgUpdate.getManifestPackageName()
                    + " signature doesn't match the signature of system image package";
            throw new PackageManagerException(INSTALL_FAILED_BAD_SIGNATURE, msg);
        }
    }

    public static void checkFsVerity(AndroidPackage pkg) throws PackageManagerException {
        // base APK is considered to be a split too
        for (AndroidPackageSplit split : pkg.getSplits()) {
            String apkPath = split.getPath();
            if (!VerityUtils.hasFsverity(apkPath)) {
                throw new PackageManagerException(INSTALL_FAILED_BAD_SIGNATURE,
                        "APK doesn't have fs-verity: " + apkPath);
            }
        }
    }

    private static SigningDetails parseSigningDetails(AndroidPackage pkg, boolean skipVerify) throws PackageManagerException {
        final ParseTypeImpl input = ParseTypeImpl.forDefaultParsing();
        final ParseResult<SigningDetails> result = ParsingPackageUtils.getSigningDetails(
                input, (ParsedPackage) pkg, skipVerify);

        if (result.isError()) {
            throw new PackageManagerException(
                    result.getErrorCode(), result.getErrorMessage(), result.getException());
        }

        final SigningDetails sd = result.getResult();
        if (sd == null) {
            throw new PackageManagerException(INSTALL_FAILED_INVALID_APK,
                        "Null signing details of package " + pkg.getManifestPackageName());
        }

        return sd;
    }
}
