package android.ext.cscopes;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.ContactsContract;

/** @hide */
@SystemApi
public class ContactScopesApi {

    public static final String ACTION_NOTIFY_CONTENT_OBSERVERS =
            "android.ext.cscopes.action.NOTIFY_CONTENT_OBSERVERS";

    public static final String SCOPED_CONTACTS_PROVIDER_AUTHORITY = ContactsContract.AUTHORITY + ".scoped";

    // ScopedContactsProvider method IDs
    public static final String METHOD_GET_ID_FROM_URI = "get_id_from_uri";
    public static final String METHOD_GET_VIEW_MODEL = "get_view_model";
    public static final String METHOD_GET_GROUPS = "get_groups";

    // keys for Bundles passed from/to ScopedContactsProvider methods
    public static final String KEY_URI = "uri";
    public static final String KEY_ID = "id";
    public static final String KEY_RESULT = "result";

    @NonNull
    public static Intent createConfigActivityIntent(@NonNull String targetPkg) {
        var i = new Intent();
        String pkg = "com.android.permissioncontroller";
        i.setComponent(ComponentName.createRelative(pkg, ".cscopes.ContactScopesActivity"));
        i.putExtra(Intent.EXTRA_PACKAGE_NAME, targetPkg);
        return i;
    }

    private ContactScopesApi() {}
}
