package edu.csuft.sap.ui.auth

import androidx.lifecycle.ViewModelStore
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.*
import edu.csuft.sap.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordRecoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var gateway: FakeRecovery
    private lateinit var vm: PasswordRecoveryViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        gateway = FakeRecovery()
        vm = PasswordRecoveryViewModel(gateway) { dispatcher.scheduler.currentTime }
        store.put("recovery", vm)
    }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun prepare() {
        vm.edit(RecoveryField.ACCOUNT, "20260001")
        vm.edit(RecoveryField.CAPTCHA, "ABCD")
    }

    @Test fun openingAndRefreshingNeverSendsMail() = runTest(dispatcher) {
        runCurrent()
        assertNotNull(vm.state.value.captcha)
        vm.refreshCaptcha(); runCurrent()
        assertEquals(2, gateway.captchaCalls)
        assertTrue(gateway.sends.isEmpty())
    }
    @Test fun invalidAccountOrMissingCaptchaIsRejectedLocally() = runTest(dispatcher) {
        runCurrent()
        vm.send(); runCurrent()
        assertEquals(setOf(RecoveryField.ACCOUNT, RecoveryField.CAPTCHA), vm.state.value.fieldErrors.keys)
        assertTrue(gateway.sends.isEmpty())
    }
    @Test fun successfulChallengePreventsDoubleSendAndPreservesCooldownAcrossPageReentry() = runTest(dispatcher) {
        runCurrent(); prepare()
        vm.send(); vm.send(); runCurrent()
        assertEquals(1, gateway.sends.size)
        assertEquals(180, vm.state.value.remaining)
        assertNotNull(vm.state.value.requestId)
        assertEquals("", vm.state.value.form.captcha)
        val reopened = PasswordRecoveryViewModel(gateway) { dispatcher.scheduler.currentTime }
        store.put("reopened", reopened); runCurrent()
        assertEquals(180, reopened.state.value.remaining)
        reopened.prefill("20260001"); reopened.edit(RecoveryField.CAPTCHA, "ABCD"); reopened.send(); runCurrent()
        assertEquals(1, gateway.sends.size)
    }
    @Test fun resetRequiresMatchingPasswordAndSixDigitCodeAndClearsSecretsOnSuccess() = runTest(dispatcher) {
        runCurrent(); prepare(); vm.send(); runCurrent()
        vm.edit(RecoveryField.CODE, "12345")
        vm.edit(RecoveryField.PASSWORD, "valid-password")
        vm.edit(RecoveryField.CONFIRMATION, "different")
        vm.reset(); runCurrent()
        assertTrue(gateway.resets.isEmpty())
        assertTrue(vm.state.value.fieldErrors.containsKey(RecoveryField.CODE))
        assertTrue(vm.state.value.fieldErrors.containsKey(RecoveryField.CONFIRMATION))
        vm.edit(RecoveryField.CODE, "123456")
        vm.edit(RecoveryField.CONFIRMATION, "valid-password")
        vm.reset(); vm.reset(); runCurrent()
        assertEquals(1, gateway.resets.size)
        assertTrue(vm.state.value.done)
        assertEquals(RecoveryForm(account = "20260001"), vm.state.value.form)
        assertNull(vm.state.value.requestId)
    }
    @Test fun failedSendRefreshesOneUseImageAndNeverAutomaticallyRetriesEmail() = runTest(dispatcher) {
        runCurrent(); prepare(); gateway.sendError = true
        vm.send(); runCurrent()
        assertEquals(1, gateway.sends.size)
        assertEquals(2, gateway.captchaCalls)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.requestId)
    }
    @Test fun expiredCodeCannotResetAndResendReturnsToExplicitCaptchaStep() = runTest(dispatcher) {
        runCurrent(); prepare(); vm.send(); runCurrent()
        advanceTimeBy(900_001); runCurrent()
        vm.reset(); runCurrent()
        assertTrue(vm.state.value.expired)
        assertTrue(gateway.resets.isEmpty())
        vm.requestAgain(); runCurrent()
        assertNull(vm.state.value.requestId)
        assertEquals("20260001", vm.state.value.form.account)
        assertEquals(1, gateway.sends.size)
        assertNotNull(vm.state.value.captcha)
    }
    @Test fun enforcesServerUtf8PasswordLimit() {
        val long = "密".repeat(25)
        assertTrue(RecoveryForm(code = "123456", password = long, confirmation = long).validateReset().containsKey(RecoveryField.PASSWORD))
        val boundary = "密".repeat(24)
        assertTrue(RecoveryForm(code = "123456", password = boundary, confirmation = boundary).validateReset().isEmpty())
    }
    private class FakeRecovery : PasswordRecoveryGateway {
        private var deadline = 0L
        override val cooldownUntil get() = deadline
        override fun rememberCooldown(deadline: Long) { this.deadline = deadline }
        var captchaCalls = 0
        val sends = mutableListOf<RecoverySendRequest>()
        val resets = mutableListOf<RecoveryResetRequest>()
        var sendError = false
        override suspend fun captcha(): Outcome<RegistrationCaptcha> {
            captchaCalls++
            return Outcome.Success(RegistrationCaptcha("captcha-$captchaCalls", byteArrayOf(1)))
        }
        override suspend fun send(request: RecoverySendRequest): Outcome<RecoverySendData> {
            sends += request
            return if (sendError) Outcome.Error("图形验证码已过期", 400) else
                Outcome.Success(RecoverySendData("00000000-0000-0000-0000-000000000001", 180, 900, "请查收注册 QQ 邮箱"))
        }
        override suspend fun reset(request: RecoveryResetRequest): Outcome<Unit> {
            resets += request
            return Outcome.Success(Unit)
        }
    }
}
