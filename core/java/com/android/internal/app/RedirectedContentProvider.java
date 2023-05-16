package com.android.internal.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.util.Arrays;

/**
 * A base class for stubbed out or scoped content providers.
 * Stubbed out providers are fully empty and immutable.
 * Scoped providers export a subset of data from a different content provider and are expected to
 * be immutable.
 */
public class RedirectedContentProvider extends ContentProvider {
    protected String TAG;
    protected boolean DEBUG;

    protected String authorityOverride;

    @Override
    public boolean onCreate() {
        DEBUG = Log.isLoggable(TAG, Log.DEBUG);
        if (DEBUG) Log.d(TAG, "onCreate");
        return true;
    }

    @Override
    public final Cursor query(Uri uri, @Nullable String[] projection, @Nullable String selection,
                              @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (DEBUG) Log.d(TAG, "query from " + getCallingPackage() + "; uri " + uri
                + ", projection " + Arrays.toString(projection) + ", selection " + selection
                + ", selectionArgs " + Arrays.toString(selectionArgs)
                + ", sortOrder " + sortOrder);
        return queryInner(uri, projection, selection, selectionArgs, sortOrder);
    }

    public Cursor queryInner(Uri uri, @Nullable String[] projection, @Nullable String selection,
                             @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Override
    public final String getType(Uri uri) {
        if (DEBUG) Log.d(TAG, "getType from " + getCallingPackage() + "; uri " + uri);

        // getType() call doesn't require any permission, can always forward it to the original provider
        return requireContext().getContentResolver().getType(uri);
    }

    @Override
    public final Uri insert(Uri uri, ContentValues values) {
        if (DEBUG) Log.d(TAG, "insert from " + getCallingPackage() + "; uri " + uri + ", values " + values);
        return null;
    }

    @Override
    public final int delete(Uri uri, String selection, String[] selectionArgs) {
        if (DEBUG) Log.d(TAG, "delete from " + getCallingPackage() + "; uri " + uri
                + ", selection " + selection + ", selectionArgs " + Arrays.toString(selectionArgs));
        return 0;
    }

    @Override
    public final int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (DEBUG) Log.d(TAG, "update from " + getCallingPackage() + "; uri " + uri + ", values " + values
                + ", selection " + selection + ", selectionArgs " + Arrays.toString(selectionArgs));
        return 0;
    }

    @Nullable
    @Override
    public final ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) {
        if (DEBUG) Log.d(TAG, "openFile from " + getCallingPackage() + "; uri " + uri + ", mode " + mode);
        return null;
    }

    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (DEBUG) Log.d(TAG, "call from " + getCallingPackage() + "; method " + method + ", arg " + arg
                + ", extras " + (extras != null ? extras.deepCopy() : extras));
        return new Bundle();
    }

    @Override
    public Uri validateIncomingUri(Uri uri) throws SecurityException {
        if (DEBUG) Log.d(TAG, "validateIncomingUri: " + uri);
        uri = uri.buildUpon().authority(authorityOverride).build();

        uri = super.validateIncomingUri(uri);

        if (DEBUG) Log.d(TAG, "override uri to: " + uri);

        return uri;
    }

    @Override
    protected final void validateIncomingAuthority(String authority) throws SecurityException {
        if (DEBUG) Log.d(TAG, "validateIncomingAuthority: " + authority);
    }
}
