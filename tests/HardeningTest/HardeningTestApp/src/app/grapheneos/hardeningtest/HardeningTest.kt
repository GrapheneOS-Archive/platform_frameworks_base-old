package app.grapheneos.hardeningtest

import android.app.Application
import android.content.Intent
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.ServiceTestRule
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.KClass

private val TAG = HardeningTest::class.java.simpleName

@RunWith(AndroidJUnit4::class)
class HardeningTest {
    val ctx = ApplicationProvider.getApplicationContext<Application>()
    val mainProcessPid = Process.myPid()

    @Rule @JvmField
    val serviceRule = ServiceTestRule()
    @Rule @JvmField
    val isolatedServiceRule = ServiceTestRule()

    lateinit var service: ITestService
    lateinit var isolatedService: ITestService

    @Before
    fun bindServices() {
        isolatedService = bindService(isolatedServiceRule, IsolatedTestService::class)
        service = bindService(serviceRule, TestService::class)
    }

    private fun testDynamicCodeExecution(svc: ITestService, isAllowed: Boolean, type: MultiTests.Type) {
        Assert.assertEquals("Environment.isExecmemBlocked()",
            !isAllowed, Environment.isExecmemBlocked())

        svc.testDynamicCodeExecution(
            type.name,
            isAllowed,
            ParcelFileDescriptor.adoptFd(Utils.getFdForExecAppDataFileTest(ctx)),
            ParcelFileDescriptor.adoptFd(Utils.getFdForExecmodTest(ctx)),
        )?.let {
            Assert.fail(it)
        }
    }

    @Test
    fun testMemoryDceAllowed() = testDynamicCodeExecution(service, true, MultiTests.Type.MemoryDce)

    @Test
    fun testMemoryDceAllowedIsolated() = testDynamicCodeExecution(isolatedService, true, MultiTests.Type.MemoryDce)

    @Test
    fun testMemoryDceRestricted() = testDynamicCodeExecution(service, false, MultiTests.Type.MemoryDce)

    @Test
    fun testMemoryDceRestrictedIsolated() = testDynamicCodeExecution(isolatedService, false, MultiTests.Type.MemoryDce)

    @Test
    fun testStorageDceAllowed() = testDynamicCodeExecution(service, true, MultiTests.Type.StorageDce)

    @Test
    fun testStorageDceAllowedIsolated() = testDynamicCodeExecution(isolatedService, true, MultiTests.Type.StorageDce)

    @Test
    fun testStorageDceRestricted() = testDynamicCodeExecution(service, false, MultiTests.Type.StorageDce)

    @Test
    fun testStorageDceRestrictedIsolated() = testDynamicCodeExecution(isolatedService, false, MultiTests.Type.StorageDce)

    private fun testPtrace(svc: ITestService, isAllowed: Boolean) {
        svc.testPtrace(isAllowed, mainProcessPid)?.let {
            Assert.fail(it)
        }
    }

    @Test
    fun testPtraceAllowed() = testPtrace(service, true)

    @Test
    fun testPtraceAllowedIsolated() = testPtrace(isolatedService, true)

    @Test
    fun testPtraceDenied() = testPtrace(service, false)

    @Test
    fun testPtraceDeniedIsolated() = testPtrace(isolatedService, false)

    private fun bindService(rule: ServiceTestRule, cls: KClass<*>): ITestService {
        val binder = rule.bindService(Intent(ctx, cls.java))
        val svc = ITestService.Stub.asInterface(binder)
        return svc
    }
}
