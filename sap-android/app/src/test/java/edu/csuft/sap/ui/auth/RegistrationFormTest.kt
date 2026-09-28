package edu.csuft.sap.ui.auth

import org.junit.Assert.*
import org.junit.Test

class RegistrationFormTest {
    private val valid = RegistrationForm("20260001", "secret123", "测试同学", 1, "12345678")

    @Test fun acceptsWebFieldsAndBothGenderValues() {
        assertTrue(valid.validate(false).isEmpty())
        assertTrue(valid.copy(gender = 0).validate(false).isEmpty())
        assertTrue(valid.copy(studentId = "Test_26-1").validate(false).isEmpty())
    }

    @Test fun enforcesRequiredFieldsAndBackendLimits() {
        assertEquals(setOf(RegistrationField.STUDENT_ID, RegistrationField.PASSWORD, RegistrationField.NAME, RegistrationField.QQ),
            RegistrationForm().validate(false).keys)
        assertTrue(valid.copy(studentId = "a".repeat(20), password = "a".repeat(64), name = "名".repeat(50),
            qq = "1".repeat(15)).validate(false).isEmpty())
        assertTrue(valid.copy(password = "a".repeat(6), qq = "12345").validate(false).isEmpty())
        assertEquals(5, valid.copy(studentId = "a".repeat(21), password = "a".repeat(65), name = "名".repeat(51),
            gender = 2, qq = "1".repeat(16)).validate(false).size)
        assertTrue(valid.copy(password = "12345").validate(false).containsKey(RegistrationField.PASSWORD))
        assertTrue(valid.copy(password = "      ").validate(false).containsKey(RegistrationField.PASSWORD))
        assertTrue(valid.copy(studentId = "学号!").validate(false).containsKey(RegistrationField.STUDENT_ID))
    }

    @Test fun rejectsInvalidQqNumbers() {
        listOf("012345", "1234", "123a56", "123 456", "+123456").forEach {
            assertTrue(it, valid.copy(qq = it).validate(false).containsKey(RegistrationField.QQ))
        }
    }

    @Test fun trimsPublicFieldsButPreservesPasswordExactly() {
        val form = valid.copy(studentId = " 20260001 ", name = " 测试同学 ", qq = " 12345678 ",
            password = " secret123 ", captchaCode = " ab23 ")
        val request = form.toRequest("challenge-1")
        assertTrue(form.validate(true).isEmpty())
        assertEquals("20260001", request.studentId)
        assertEquals("测试同学", request.name)
        assertEquals("12345678", request.qq)
        assertEquals(" secret123 ", request.password)
        assertEquals("ab23", request.captchaCode)
        assertEquals("challenge-1", request.captchaId)
    }

    @Test fun requiresCaptchaOnlyAfterServerChallenge() {
        assertTrue(valid.validate(false).isEmpty())
        assertEquals(setOf(RegistrationField.CAPTCHA), valid.validate(true).keys)
        assertTrue(valid.copy(captchaCode = "ab23").validate(true).isEmpty())
        assertNull(valid.copy(captchaCode = "stale").toRequest(null).captchaCode)
    }
}
