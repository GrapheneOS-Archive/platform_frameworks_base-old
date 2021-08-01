package com.android.internal.gmscompat.dynamite.server;

/** @hide */
interface IFileProxyService {
    ParcelFileDescriptor openFile(String path);
    long getLastModified(String path);
}
