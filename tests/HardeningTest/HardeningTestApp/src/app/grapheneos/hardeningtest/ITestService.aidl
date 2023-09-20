package app.grapheneos.hardeningtest;

import android.os.ParcelFileDescriptor;

interface ITestService {
    @nullable String testDynamicCodeExecution(String type, boolean isAllowed, in ParcelFileDescriptor appDataFileFd, in ParcelFileDescriptor execmodFd);
    @nullable String testPtrace(boolean isAllowed, int mainProcessPid);
}
