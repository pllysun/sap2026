package edu.csuft.sap.ui.auth

import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.RegisterRequest
import edu.csuft.sap.data.remote.dto.RegisterEmailRequest
import edu.csuft.sap.data.remote.dto.RegistrationEmailData
import edu.csuft.sap.data.repository.RegistrationCaptcha
import edu.csuft.sap.data.repository.RegistrationGateway
import edu.csuft.sap.data.repository.RegistrationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeRegistration
    private lateinit var vm: RegistrationViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        gateway = FakeRegistration()
        vm = RegistrationViewModel(gateway) { dispatcher.scheduler.currentTime }
        vm.edit(RegistrationField.STUDENT_ID, " 20260001 ")
        vm.edit(RegistrationField.PASSWORD, "secret123")
        vm.edit(RegistrationField.NAME, "测试同学")
        vm.edit(RegistrationField.GENDER, "0")
        vm.edit(RegistrationField.QQ, "12345678")
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun validationNeverRequestsMailWithInvalidDetails() = runTest(dispatcher) {
        vm.edit(RegistrationField.QQ, "0123")
        vm.sendEmailCode()
        runCurrent()
        assertTrue(gateway.emailRequests.isEmpty())
        assertEquals(setOf(RegistrationField.QQ), vm.state.value.fieldErrors.keys)
        val attempt = vm.state.value.validationAttempt
        vm.edit(RegistrationField.QQ, "123456")
        assertEquals(attempt, vm.state.value.validationAttempt)
        assertTrue(vm.state.value.fieldErrors.isEmpty())
    }

    @Test fun finalRegistrationCannotBypassEmailVerification() = runTest(dispatcher) {
        vm.edit(RegistrationField.EMAIL_CODE, "654321")
        vm.submit()
        runCurrent()
        assertTrue(gateway.requests.isEmpty())
        assertTrue(vm.state.value.fieldErrors.containsKey(RegistrationField.EMAIL_CODE))
        assertNull(vm.state.value.registeredStudentId)
    }

    @Test fun emailChallengeThenCodeRegistersExactlyOnceAndClearsSecrets() = runTest(dispatcher) {
        vm.sendEmailCode()
        vm.sendEmailCode()
        assertTrue(vm.state.value.emailLoading)
        runCurrent()
        assertEquals(1, gateway.emailRequests.size)
        assertNull(gateway.emailRequests.first().captchaId)
        assertTrue(vm.state.value.captchaRequired)
        assertNull(vm.state.value.registeredStudentId)
        assertEquals("secret123", vm.state.value.form.password)
        vm.edit(RegistrationField.CAPTCHA, " Ab23 ")
        gateway.mailReady()
        vm.sendEmailCode()
        vm.sendEmailCode()
        runCurrent()
        assertEquals("captcha-1", gateway.emailRequests.last().captchaId)
        assertEquals("Ab23", gateway.emailRequests.last().captchaCode)
        assertEquals("20260001", gateway.emailRequests.last().studentId)
        assertEquals(180, vm.state.value.cooldownSeconds)
        assertNull(vm.state.value.captcha)
        assertFalse(vm.state.value.captchaRequired)
        assertTrue(gateway.requests.isEmpty())
        vm.edit(RegistrationField.EMAIL_CODE, "654321")
        vm.submit()
        vm.submit()
        vm.edit(RegistrationField.PASSWORD, "must-not-change")
        runCurrent()
        assertEquals(1, gateway.requests.size)
        assertEquals("mail-request-1", gateway.requests.single().emailRequestId)
        assertEquals("654321", gateway.requests.single().emailCode)
        assertEquals("secret123", gateway.requests.single().password)
        assertEquals(0, gateway.requests.single().gender)
        assertNull(gateway.requests.single().captchaId)
        assertEquals("20260001", vm.state.value.registeredStudentId)
        assertEquals(RegistrationForm(), vm.state.value.form)
        assertNull(vm.state.value.emailRequestId)
        assertEquals(0, vm.state.value.cooldownSeconds)
        vm.submit()
        vm.sendEmailCode()
        runCurrent()
        assertEquals(1, gateway.requests.size)
        assertEquals(2, gateway.emailRequests.size)
    }

    @Test fun rejectedGraphicCodeRefreshesAndPreservesDetails() = runTest(dispatcher) {
        vm.sendEmailCode()
        runCurrent()
        vm.edit(RegistrationField.CAPTCHA, "wrong")
        gateway.emailResult = Outcome.Error("图形验证码错误或已过期", 400)
        vm.sendEmailCode()
        runCurrent()
        assertEquals(2, gateway.captchaCalls)
        assertEquals("captcha-2", vm.state.value.captcha?.id)
        assertEquals("", vm.state.value.form.captchaCode)
        assertEquals("secret123", vm.state.value.form.password)
        assertEquals("图形验证码错误或已过期", vm.state.value.error)
        assertNull(vm.state.value.emailRequestId)
    }

    @Test fun failedGraphicLoadCanRetryAndPreventsPrematureMail() = runTest(dispatcher) {
        gateway.captchaFails = true
        vm.sendEmailCode()
        runCurrent()
        assertFalse(vm.state.value.canSendEmail)
        assertNotNull(vm.state.value.captchaError)
        vm.sendEmailCode()
        assertEquals(1, gateway.emailRequests.size)
        gateway.captchaFails = false
        vm.refreshCaptcha()
        vm.refreshCaptcha()
        assertTrue(vm.state.value.captchaLoading)
        runCurrent()
        assertEquals(2, gateway.captchaCalls)
        assertTrue(vm.state.value.canSendEmail)
        vm.sendEmailCode()
        assertTrue(vm.state.value.fieldErrors.containsKey(RegistrationField.CAPTCHA))
        assertEquals(1, gateway.emailRequests.size)
    }

    @Test fun oldImageCallbacksDoNotInvalidateRefreshedChallenge() = runTest(dispatcher) {
        vm.sendEmailCode()
        runCurrent()
        vm.edit(RegistrationField.CAPTCHA, "ab23")
        vm.refreshCaptcha()
        assertNull(vm.state.value.captcha)
        assertEquals("", vm.state.value.form.captchaCode)
        assertFalse(vm.state.value.canSendEmail)
        runCurrent()
        vm.captchaImageFailed("captcha-1")
        assertEquals("captcha-2", vm.state.value.captcha?.id)
        vm.captchaImageFailed("captcha-2")
        assertFalse(vm.state.value.canSendEmail)
        vm.refreshCaptcha()
        runCurrent()
        assertEquals("captcha-3", vm.state.value.captcha?.id)
    }

    @Test fun rejectedEmailCodeRetainsAccountAndClearsOnlyEnteredCode() = runTest(dispatcher) {
        gateway.mailReady()
        vm.sendEmailCode()
        runCurrent()
        vm.edit(RegistrationField.EMAIL_CODE, "654321")
        gateway.nextResult = Outcome.Error("邮箱验证码错误或已过期", 400)
        vm.submit()
        runCurrent()
        assertEquals("邮箱验证码错误或已过期", vm.state.value.error)
        assertEquals("secret123", vm.state.value.form.password)
        assertEquals("", vm.state.value.form.emailCode)
        assertEquals("mail-request-1", vm.state.value.emailRequestId)
        assertNull(vm.state.value.registeredStudentId)
        assertTrue(vm.state.value.canSubmit)
        assertEquals(0, gateway.captchaCalls)
        advanceUntilIdle()
    }

    @Test fun changingQqInvalidatesChallengeWithoutResettingCooldown() = runTest(dispatcher) {
        gateway.mailReady()
        vm.sendEmailCode()
        runCurrent()
        vm.edit(RegistrationField.EMAIL_CODE, "654321")
        vm.edit(RegistrationField.QQ, "88888888")
        assertNull(vm.state.value.emailRequestId)
        assertEquals("", vm.state.value.form.emailCode)
        assertEquals(180, vm.state.value.cooldownSeconds)
        assertTrue(vm.state.value.emailNotice!!.contains("重新获取"))
        vm.submit()
        vm.sendEmailCode()
        runCurrent()
        assertTrue(gateway.requests.isEmpty())
        assertEquals(1, gateway.emailRequests.size)
        advanceTimeBy(180_000)
        runCurrent()
        assertEquals(0, vm.state.value.cooldownSeconds)
        assertTrue(vm.state.value.canSendEmail)
    }

    @Test fun changingAccountInvalidatesCodeButWhitespaceDoesNot() = runTest(dispatcher) {
        gateway.mailReady()
        vm.sendEmailCode()
        runCurrent()
        vm.edit(RegistrationField.STUDENT_ID, "20260001")
        assertNotNull(vm.state.value.emailRequestId)
        vm.edit(RegistrationField.STUDENT_ID, "20260002")
        assertNull(vm.state.value.emailRequestId)
        advanceUntilIdle()
    }

    @Test fun mailNetworkFailureAllowsRetryAndNeverSignalsSuccess() = runTest(dispatcher) {
        gateway.emailResult = Outcome.Error("无法连接服务器", offline = true)
        vm.sendEmailCode()
        vm.edit(RegistrationField.PASSWORD, "changed")
        runCurrent()
        assertEquals("无法连接服务器", vm.state.value.error)
        assertEquals("secret123", vm.state.value.form.password)
        assertNull(vm.state.value.registeredStudentId)
        assertNull(vm.state.value.emailRequestId)
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.canSendEmail)
    }

    private class FakeRegistration : RegistrationGateway {
        val requests = mutableListOf<RegisterRequest>()
        val emailRequests = mutableListOf<RegisterEmailRequest>()
        var nextResult: Outcome<RegistrationResult> = Outcome.Success(RegistrationResult.REGISTERED)
        var emailResult: Outcome<RegistrationEmailData> = Outcome.Success(RegistrationEmailData(captchaRequired = true))
        var captchaCalls = 0
        var captchaFails = false
        fun mailReady() { emailResult = Outcome.Success(RegistrationEmailData(requestId = "mail-request-1", email = "12345678@qq.com")) }
        override suspend fun requestEmailCode(request: RegisterEmailRequest): Outcome<RegistrationEmailData> {
            emailRequests += request
            return emailResult
        }
        override suspend fun register(request: RegisterRequest): Outcome<RegistrationResult> {
            requests += request
            return nextResult
        }
        override suspend fun captcha(): Outcome<RegistrationCaptcha> {
            captchaCalls++
            return if (captchaFails) Outcome.Error("验证码获取失败")
            else Outcome.Success(RegistrationCaptcha("captcha-$captchaCalls", byteArrayOf(1)))
        }
    }
}
