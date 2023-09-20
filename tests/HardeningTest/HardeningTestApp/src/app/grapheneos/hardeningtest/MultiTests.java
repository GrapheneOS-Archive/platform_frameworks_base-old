package app.grapheneos.hardeningtest;

import android.app.ActivityThread;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

import static app.grapheneos.hardeningtest.MultiTests.Type.MemoryDce;
import static app.grapheneos.hardeningtest.MultiTests.Type.StorageDce;

public class MultiTests {
    static {
        System.loadLibrary("hardening_test");
    }

    enum Type {
        MemoryDce,
        StorageDce,
    }

    @MultiTest(type = MemoryDce)
    public static native int execmem();

    @MultiTest(type = MemoryDce, alwaysDeniedMinSdk = 28, alwaysDeniedMinSdkIsolated = 1, skipAllowedTest = true)
    public static native int execmod(int fd);

    // no @MultiTest, special-cased in test runner
    public static native int ptrace(int pid);

    @MultiTest(type = MemoryDce)
    public static native int exec_appdomain_tmpfs();

    @MultiTest(type = MemoryDce, alwaysDeniedMinSdk = 28, alwaysDeniedMinSdkIsolated = 1)
    public static native int execute_ashmem();

    @MultiTest(type = MemoryDce)
    public static native int execute_ashmem_libcutils();

    // successful allowedTest never returns, skip it
    @MultiTest(type = StorageDce, skipAllowedTest = true)
    public static native int exec_app_data_file(int fd);

    @MultiTest(type = StorageDce, skipAllowedTest = true)
    public static int exec_app_data_file_path() throws IOException {
        var ctx = ActivityThread.currentApplication();
        File file = new File(ctx.getFilesDir(), "self_exe_" + System.nanoTime());

        try {
            Files.copy(Paths.get("/proc/self/exe"), file.toPath());

            FileDescriptor fd = Os.open(file.getPath(), OsConstants.O_RDONLY, 0);
            Os.execv("/proc/self/fd/" + fd.getInt$(), new String[0]);
            return 0;
        } catch (NoSuchFileException | AccessDeniedException e) {
            return OsConstants.EACCES;
        } catch (ErrnoException e) {
            return e.errno;
        } finally {
            file.delete();
        }
    }
}
