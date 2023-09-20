package app.grapheneos.hardeningtest;

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SELinux;
import android.system.OsConstants;
import android.util.Log;

import java.lang.reflect.Method;

import static app.grapheneos.hardeningtest.Utils.getTargetSdk;
import static junit.framework.Assert.assertEquals;

public class TestService extends Service {

    public void onCreate() {
        super.onCreate();
        Utils.checkSELinuxContextAndFlags(getExpectedSELinuxContextPrefix(), getTargetSdk(this));
    }

    protected String getExpectedSELinuxContextPrefix() {
        int targetSdk = getTargetSdk(this);
        if (targetSdk != Build.VERSION.SDK_INT) {
            assertEquals("targetSdk", 27, targetSdk);
            return "u:r:untrusted_app_27:s0:c";
        }
        return "u:r:untrusted_app:s0:c";
    }

    class BinderImpl extends ITestService.Stub {
        @Override
        @Nullable
        public String testDynamicCodeExecution(String typeStr, boolean isAllowed, ParcelFileDescriptor appDataFileFd, ParcelFileDescriptor execmodFd) {
            boolean isIsolated = Process.isIsolated();
            MultiTests.Type type = MultiTests.Type.valueOf(typeStr);

            var failures = new StringBuilder();

            for (Method m : MultiTests.class.getDeclaredMethods()) {
                var ann = m.getAnnotation(MultiTest.class);
                if (ann == null) {
                    continue;
                }

                if (ann.type() != type) {
                    continue;
                }

                int targetSdk = getApplicationInfo().targetSdkVersion;

                boolean blockedByBasePolicy = targetSdk >=
                    (isIsolated ? ann.alwaysDeniedMinSdkIsolated() : ann.alwaysDeniedMinSdk());

                if (!blockedByBasePolicy && isAllowed && ann.skipAllowedTest()) {
                    continue;
                }

                Object[] args;
                switch (m.getName()) {
                    case "execmod":
                        args = new Object[] { execmodFd.detachFd() };
                        break;
                    case "exec_app_data_file":
                        args = new Object[] { appDataFileFd.detachFd() };
                        break;
                    default:
                        args = new Object[0];
                }
                int ret;
                try {
                    ret = (int) m.invoke(null, args);
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw new IllegalStateException(e);
                }

                Log.d("testDce" + (isAllowed? "_allowed" : "_restricted"), "isolated " + isIsolated
                    +", targetSdk " + targetSdk + " " + m.getName() + ": " + Errno.name(ret));

                int expectedRet = isAllowed?
                    isIsolated ?
                        (blockedByBasePolicy? ann.blockedReturnCodeIsolated() : ann.allowedReturnCodeIsolated()) :
                        (blockedByBasePolicy? ann.blockedReturnCode() : ann.allowedReturnCode()) :
                    isIsolated ?
                        ann.blockedReturnCodeIsolated() : ann.blockedReturnCode();

                if (ret != expectedRet) {
                    failures.append('\n');
                    failures.append(m.getName());
                    failures.append(": expected ");
                    failures.append(Errno.name(expectedRet));
                    failures.append(", got ");
                    failures.append(Errno.name(ret));
                    failures.append(", ");
                    failures.append(getProcInfo());
                }
            }

            if (failures.length() != 0) {
                return failures.toString();
            }

            return null;
        }

        private String getProcInfo() {
            return "SELinux context: " + SELinux.getContext() + ", pkg: " + getPackageName();
        }

        @Override
        @Nullable
        public String testPtrace(boolean isAllowed, int mainProcessPid) {
            int ret = MultiTests.ptrace(mainProcessPid);
            int expectedRet = isAllowed ? 0 : OsConstants.EPERM;

            if (ret != expectedRet) {
                return "expected " + Errno.name(expectedRet) + ", got " + Errno.name(ret) + ", " + getProcInfo();
            }

            return null;
        }
    }

    private final IBinder binder = new BinderImpl();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
