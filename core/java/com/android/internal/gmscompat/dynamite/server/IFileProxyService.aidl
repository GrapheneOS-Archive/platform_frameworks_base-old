package com.android.internal.gmscompat.dynamite.server;

interface IFileProxyService {
    ParcelFileDescriptor openFile(String path);
}
