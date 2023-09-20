#include <cstdlib>

#include <android/sharedmem.h>
#undef NDEBUG // needed to enable assertions
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/ashmem.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <unistd.h>

static jint get_errno() {
    int r = errno;
    assert(r > 0);
    return (jint) r;
}

static constexpr size_t DEFAULT_BUF_LEN =  (PAGE_SIZE + 10);

extern "C" jint Java_app_grapheneos_hardeningtest_MultiTests_execmem(JNIEnv *env, jclass cls) {
    size_t len = DEFAULT_BUF_LEN;
    void *addr = mmap(nullptr, len, PROT_EXEC, MAP_PRIVATE | MAP_ANON, -1, 0);
    if (addr != MAP_FAILED) {
        munmap(addr, len);
        return 0;
    }
    return get_errno();
}

extern "C" jint Java_app_grapheneos_hardeningtest_MultiTests_execmod(JNIEnv *env, jclass cls, jint fd) {
    char buf[DEFAULT_BUF_LEN];
    assert(sizeof(buf) == write(fd, buf, sizeof(buf)));

    void *addr = mmap(nullptr, sizeof(buf), PROT_WRITE, MAP_PRIVATE, fd, 0);
    close(fd);
    assert(addr != nullptr);

    // modify the file mapping
    (* ((char*) addr))++;

    jint r = 0;
    if (mprotect(addr, sizeof(buf), PROT_EXEC) != 0) {
        r = get_errno();
    }

    munmap(addr, sizeof(buf));
    return r;
}

extern "C" jint Java_app_grapheneos_hardeningtest_MultiTests_exec_1app_1data_1file(JNIEnv *env, jclass cls, jint fd) {
    const char* argv[] = { "a", nullptr };
    const char* envp[] = { "b", nullptr };

    fexecve(fd, (char *const *) argv, (char *const *) envp);
    close(fd);
    return get_errno();
}

extern "C" jint Java_app_grapheneos_hardeningtest_MultiTests_exec_1appdomain_1tmpfs(JNIEnv *env, jclass cls) {
    int fd = memfd_create("hardeningtest_memfd", 0);
    assert(fd >= 0);

    size_t len = DEFAULT_BUF_LEN;

    void *addr = mmap(nullptr, len, PROT_EXEC, MAP_PRIVATE, fd, 0);
    close(fd);

    if (addr == MAP_FAILED) {
        return get_errno();
    }

    munmap(addr, len);
    return 0;
}

extern "C" jint Java_app_grapheneos_hardeningtest_MultiTests_ptrace(JNIEnv *env, jclass cls, jint pid) {
    pid_t fork_res = fork();

    if (fork_res == 0) {
        // child
        // not necessary and racy
        // ptrace(PTRACE_TRACEME, 0, nullptr, 0);
    } else {
        assert(fork_res > 0);
        pid_t child_pid = fork_res;
        long r = ptrace(PTRACE_ATTACH, child_pid, nullptr, 0);
        if (r == -1) {
            return get_errno();
        }
        ptrace(PTRACE_DETACH, child_pid, nullptr, 0);
    }
    return 0;
}

extern "C" jint Java_app_grapheneos_hardeningtest_MultiTests_execute_1ashmem(JNIEnv *env, jclass cls) {
    // Based on ashmem_create_region() from system/core/libcutils/ashmem-dev.cpp
    // Direct use of that function would return memfd instead of ashmem fd

    int fd = open("/dev/ashmem", O_RDWR);
    if (fd < 0) {
        return get_errno();
    }

    size_t size = DEFAULT_BUF_LEN;

    assert(ioctl(fd, ASHMEM_SET_SIZE, size) == 0);

    void *addr = mmap(nullptr, size, PROT_EXEC, MAP_PRIVATE, fd, 0);
    close(fd);
    if (addr == MAP_FAILED) {
        return get_errno();
    }
    munmap(addr, size);
    return 0;
}

extern "C" jint Java_app_grapheneos_hardeningtest_MultiTests_execute_1ashmem_1libcutils(JNIEnv *env, jclass cls) {
    size_t len = DEFAULT_BUF_LEN;
    int fd = ASharedMemory_create("hardeningtest", len);
    assert(fd >= 0);

    void *addr = mmap(nullptr, len, PROT_EXEC, MAP_PRIVATE, fd, 0);
    close(fd);

    if (addr == MAP_FAILED) {
        return get_errno();
    }

    munmap(addr, len);
    return 0;
}
