package com.android.internal.app;

import android.Manifest;
import android.annotation.AnyThread;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.GosPackageState;
import android.database.ContentObserver;
import android.ext.cscopes.ContactScopesApi;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.SimPhonebookContract;

import com.android.internal.app.ContentProviderRedirector.ContentObserverList;

import static android.content.pm.GosPackageState.DFLAG_HAS_GET_ACCOUNTS_DECLARATION;
import static android.content.pm.GosPackageState.DFLAG_HAS_READ_CONTACTS_DECLARATION;
import static android.content.pm.GosPackageState.DFLAG_HAS_WRITE_CONTACTS_DECLARATION;

public class ContactScopes {
    private static volatile boolean isEnabled;
    private static int gosPsDerivedFlags;

    private static ContentObserverList contentObserverList;

    public static boolean isEnabled() {
        return isEnabled;
    }

    @AnyThread
    public static void maybeEnable(Context ctx, GosPackageState ps) {
        synchronized (ContactScopes.class) {
            if (isEnabled) {
                return;
            }

            if (ps.hasFlag(GosPackageState.FLAG_CONTACT_SCOPES_ENABLED)) {
                gosPsDerivedFlags = ps.derivedFlags;
                {
                    var col = new ContentObserverList();
                    String intentAction = ContactScopesApi.ACTION_NOTIFY_CONTENT_OBSERVERS;
                    // this broadcast is sent by PermissionController
                    String permission = Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
                    col.registerNotificationReceiver(ctx, intentAction, permission);
                    contentObserverList = col;
                }
                ContentProviderRedirector.enable();
                isEnabled = true;
            }
        }
    }

    // call only if isEnabled is true
    private static boolean shouldSpoofPermissionCheckInner(int permDflag) {
        if (permDflag == 0) {
            return false;
        }
        return (gosPsDerivedFlags & permDflag) != 0;
    }

    public static boolean shouldSpoofSelfPermissionCheck(String permName) {
        if (!isEnabled) {
            return false;
        }
        return shouldSpoofPermissionCheckInner(getSpoofablePermissionDflag(permName));
    }

    public static boolean shouldSpoofSelfAppOpCheck(int op) {
        if (!isEnabled) {
            return false;
        }
        return shouldSpoofPermissionCheckInner(getSpoofableAppOpPermissionDflag(op));
    }

    public static boolean maybeInterceptRegisterContentObserver(Uri uri, ContentObserver observer) {
        if (!isEnabled) {
            return false;
        }

        if (isContactsUri(uri)) {
            contentObserverList.add(observer, uri);
            return true;
        }

        return false;
    }

    public static boolean maybeInterceptUnregisterContentObserver(ContentObserver observer) {
        if (!isEnabled) {
            return false;
        }

        return contentObserverList.remove(observer);
    }

    public static boolean shouldSkipNotifyChange(Uri uri) {
        if (!isEnabled) {
            return false;
        }

        return isContactsUri(uri);
    }

    public static boolean isContactsUri(Uri uri) {
        return isContactsUriAuthority(uri.getAuthority());
    }

    public static boolean isContactsUriAuthority(String authority) {
        if (authority == null) {
            return false;
        }
        switch (authority) {
            case ContactsContract.AUTHORITY:
            case SimPhonebookContract.AUTHORITY:
            case ICC_PROVIDER_AUTHORITY:
                return true;
        }
        return false;
    }

    // legacy SIM phonebook provider
    public static final String ICC_PROVIDER_AUTHORITY = "icc";

    public static String maybeTranslateAuthority(String auth) {
        if (!isEnabled) {
            return null;
        }

        if (ContactsContract.AUTHORITY.equals(auth)) {
            return ContactScopesApi.SCOPED_CONTACTS_PROVIDER_AUTHORITY;
        }

        if (SimPhonebookContract.AUTHORITY.equals(auth)) {
            return SimPhonebookContract.AUTHORITY + ".stub";
        }

        if (ICC_PROVIDER_AUTHORITY.equals(auth)) {
            return ICC_PROVIDER_AUTHORITY + ".stub";
        }

        return null;
    }

    public static int getSpoofablePermissionDflag(String permName) {
        switch (permName) {
            case Manifest.permission.READ_CONTACTS:
                return DFLAG_HAS_READ_CONTACTS_DECLARATION;
            case Manifest.permission.WRITE_CONTACTS:
                return DFLAG_HAS_WRITE_CONTACTS_DECLARATION;
            case Manifest.permission.GET_ACCOUNTS:
                return DFLAG_HAS_GET_ACCOUNTS_DECLARATION;
            default:
                return 0;
        }
    }

    private static int getSpoofableAppOpPermissionDflag(int op) {
        switch (op) {
            case AppOpsManager.OP_READ_CONTACTS:
                return DFLAG_HAS_READ_CONTACTS_DECLARATION;
            case AppOpsManager.OP_WRITE_CONTACTS:
                return DFLAG_HAS_WRITE_CONTACTS_DECLARATION;
            case AppOpsManager.OP_GET_ACCOUNTS:
                return DFLAG_HAS_WRITE_CONTACTS_DECLARATION;
            default:
                return 0;
        }
    }
}
