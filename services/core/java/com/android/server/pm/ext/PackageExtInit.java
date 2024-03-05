package com.android.server.pm.ext;

import android.annotation.Nullable;
import android.content.pm.SigningDetails;
import android.content.pm.parsing.result.ParseInput;
import android.content.pm.parsing.result.ParseResult;
import android.ext.AppInfoExt;
import android.ext.PackageId;
import android.os.Bundle;
import android.util.Slog;

import com.android.internal.pm.parsing.pkg.PackageImpl;
import com.android.internal.pm.pkg.parsing.ParsingPackage;
import com.android.internal.pm.pkg.parsing.ParsingPackageUtils;

import libcore.util.HexEncoding;

import static android.ext.PackageId.*;

public class PackageExtInit implements ParsingPackageUtils.PackageExtInitIface {
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
            if (metadata.containsKey("com.google.android.gms.version")) {
                flags |= (1 << AppInfoExt.FLAG_HAS_GMSCORE_CLIENT_LIBRARY);
            }
        }

        return flags;
    }

    private int getPackageId() {
        return switch (pkg.getPackageName()) {
            case GSF_NAME -> validate(GSF, 30L, mainGmsCerts());
            case GMS_CORE_NAME -> validate(GMS_CORE, 21_00_00_000L, mainGmsCerts());
            case PLAY_STORE_NAME -> validate(PLAY_STORE, 0L, mainGmsCerts());
            case G_SEARCH_APP_NAME -> validate(G_SEARCH_APP, 0L, mainGmsCerts());
            case EUICC_SUPPORT_PIXEL_NAME -> validateSystemPkg(EUICC_SUPPORT_PIXEL);
            case G_EUICC_LPA_NAME -> validateSystemPkg(G_EUICC_LPA);
            case G_CARRIER_SETTINGS_NAME -> validate(G_CARRIER_SETTINGS, 37L,
                    "c00409b6524658c2e8eb48975a5952959ea3707dd57bc50fd74d6249262f0e82");
            case G_CAMERA_NAME -> validate(G_CAMERA, 65820000L,
                    "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83",
                    "1975b2f17177bc89a5dff31f9e64a6cae281a53dc1d1d59b1d147fe1c82afa00");
            case PIXEL_CAMERA_SERVICES_NAME -> validate(PIXEL_CAMERA_SERVICES, 124000L,
                    "226bb0439d6baeaa5a397c586e7031d8addfaec73c65be212f4a5dbfbf621b92");
            case ANDROID_AUTO_NAME -> validate(ANDROID_AUTO, 11_0_635014L,
                    "1ca8dcc0bed3cbd872d2cb791200c0292ca9975768a82d676b8b424fb65b5295");

            default -> PackageId.UNKNOWN;
        };
    }

    private static String[] mainGmsCerts() {
        return new String[] {
                // "bd32" SHA256withRSA issued in March 2020
                "7ce83c1b71f3d572fed04c8d40c5cb10ff75e6d87d9df6fbd53f0468c2905053",
                // "38d1" MD5withRSA issued in August 2008
                "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83",
                // "58e1" MD5withRSA issued in April 2008
                "1975b2f17177bc89a5dff31f9e64a6cae281a53dc1d1d59b1d147fe1c82afa00",
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
