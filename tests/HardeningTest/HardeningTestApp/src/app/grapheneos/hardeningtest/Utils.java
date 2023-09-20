package app.grapheneos.hardeningtest;

import android.content.Context;
import android.os.Process;
import android.os.SELinux;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static android.system.OsConstants.EACCES;
import static android.system.OsConstants.EPERM;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_EXCL;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.SEEK_SET;
import static android.system.OsConstants.S_IRUSR;
import static android.system.OsConstants.S_IWUSR;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Utils {

    public static void checkSELinuxContextAndFlags(String expectedPrefix, int targetSdk) {
        String TAG = "SELinux";

        String ctx = SELinux.getContext();
        assertTrue("unexpected SELinux context: " + ctx + " ; expectedPrefix " + expectedPrefix,
            ctx.startsWith(expectedPrefix));

        int myPid = Process.myPid();

        FileDescriptor fd = null;
        try {
            String path = "/proc/" + myPid + "/task/" + myPid + "/attr/selinux_flags";
            fd = Os.open(path, O_RDWR, 0);
            try (var s = new FileInputStream(fd)) {
                var bytes = s.readAllBytes();
                Log.d(TAG, "selinux_flags " + new String(bytes));
            }
            Os.lseek(fd, 0L, SEEK_SET);
            try {
                Os.write(fd, ByteBuffer.wrap("0".getBytes()));
                // app should never be able to change selinux_flags
                fail("write to selinux_flags should have failed");
            } catch (ErrnoException e) {
                int errno = e.errno;
                if (errno != EPERM && errno != EACCES) {
                    fail("selinux_flags should have failed with EPERM or EACCES, got "
                        + Os.strerror(errno));
                }
            }
        } catch (Throwable e) {
            Log.d(TAG, "", e);
            if (e instanceof AssertionError) {
                throw (AssertionError) e;
            }
            throw new IllegalStateException(e);
        } finally {
            libcore.io.IoUtils.closeQuietly(fd);
        }
    }

    public static int getFdForExecAppDataFileTest(Context ctx) throws IOException, ErrnoException {
        File file = new File(ctx.getFilesDir(), "appdata_file_exe");
        synchronized (Utils.class) {
            file.delete();
            Files.copy(Paths.get("/proc/self/exe"), file.toPath());
            return Os.open(file.getPath(), O_RDONLY, 0).getInt$();
        }
    }

    public static int getFdForExecmodTest(Context ctx) throws IOException, ErrnoException {
        File file = new File(ctx.getFilesDir(), "execmod_exe");
        synchronized (Utils.class) {
            file.delete();
            FileDescriptor fd = Os.open(file.getPath(), O_RDWR | O_CREAT | O_EXCL, S_IRUSR | S_IWUSR);
            return fd.getInt$();
        }
    }

    public static int getTargetSdk(Context ctx) {
        return ctx.getApplicationInfo().targetSdkVersion;
    }
}
