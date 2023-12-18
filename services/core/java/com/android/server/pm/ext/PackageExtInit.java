package com.android.server.pm.ext;

import android.annotation.Nullable;
import android.content.pm.SigningDetails;
import android.content.pm.parsing.result.ParseInput;
import android.content.pm.parsing.result.ParseResult;
import android.ext.PackageId;
import android.ext.AppInfoExt;
import android.os.Bundle;
import android.util.Slog;

import com.android.server.pm.parsing.pkg.PackageImpl;
import com.android.server.pm.pkg.parsing.ParsingPackage;
import com.android.server.pm.pkg.parsing.ParsingPackageUtils;

import libcore.util.HexEncoding;

import static android.ext.PackageId.*;

public class PackageExtInit {
    private static final String TAG = PackageExtInit.class.getSimpleName();

    private final ParseInput input;
    private final ParsingPackage parsingPackage;
    private final PackageImpl pkg;
    private final boolean isSystem;

    @Nullable
    private ParseResult<SigningDetails> signingDetailsParseResult;

    public PackageExtInit(ParseInput input, ParsingPackage parsingPackage, boolean isSystem) {
        this.input = input;
        this.parsingPackage = parsingPackage;
        this.pkg = (PackageImpl) parsingPackage;
        this.isSystem = isSystem;
    }

    @Nullable
    public ParseResult<SigningDetails> getSigningDetailsParseResult() {
        return signingDetailsParseResult;
    }

    public void run() {
        int packageId = getPackageId();

        if (packageId != UNKNOWN) {
            Slog.d(TAG, "set packageId of " +  pkg.getPackageName() + " to " + packageId);
        }

        var ext = new PackageExt(packageId, getExtFlags());

        parsingPackage.setPackageExt(ext);
    }

    private int getExtFlags() {
        int flags = 0;

        Bundle metadata = pkg.getMetaData();
        if (metadata != null) {
        }

        return flags;
    }

    private int getPackageId() {
        return switch (pkg.getPackageName()) {

            default -> PackageId.UNKNOWN;
        };
    }

    private int validateSystemPkg(int packageId) {
        if (isSystem) {
            return packageId;
        }
        Slog.w(TAG, "expected " + pkg.getPackageName() + " to be a part of the system image");
        return PackageId.UNKNOWN;
    }

    private int validate(int packageId, long minVersionCode, String... validCertificatesSha256) {
        if (pkg.getLongVersionCode() < minVersionCode) {
            Slog.d(TAG, "minVersionCode check failed, pkgName " + pkg.getPackageName() + "," +
                    " pkgVersion: " + pkg.getLongVersionCode());
            return PackageId.UNKNOWN;
        }

        SigningDetails signingDetails = pkg.getSigningDetails();

        if (signingDetails == SigningDetails.UNKNOWN) {
            final ParseResult<SigningDetails> result = ParsingPackageUtils.parseSigningDetails(input, parsingPackage);
            signingDetailsParseResult = result;

            if (result.isError()) {
                Slog.e(TAG, "unable to parse SigningDetails for " + parsingPackage.getPackageName()
                        + "; code " + result.getErrorCode() + "; msg " + result.getErrorMessage(),
                        result.getException());
                return PackageId.UNKNOWN;
            }

            signingDetails = result.getResult();
        }

        for (String certSha256String : validCertificatesSha256) {
            byte[] validCertSha256 = HexEncoding.decode(certSha256String);
            if (signingDetails.hasSha256Certificate(validCertSha256)) {
                return packageId;
            }
        }

        Slog.d(TAG, "SigningDetails of " + pkg.getPackageName() + " don't contain any of known certificates");

        return PackageId.UNKNOWN;
    }
}
