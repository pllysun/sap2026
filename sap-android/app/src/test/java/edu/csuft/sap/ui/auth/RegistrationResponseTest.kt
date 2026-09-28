package edu.csuft.sap.ui.auth

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.ApiResult
import edu.csuft.sap.data.repository.RegistrationResult
import edu.csuft.sap.data.repository.parseRegistrationResult
import org.junit.Assert.*
import org.junit.Test

class RegistrationResponseTest {
    @Test fun decodesBothActualWebResponseShapes() {
        assertEquals(Outcome.Success(RegistrationResult.CAPTCHA_REQUIRED), parse("""{"captchaRequired":true}"""))
        assertEquals(Outcome.Success(RegistrationResult.REGISTERED), parse("\"注册成功\""))
    }

    @Test fun malformedOrUnrecognizedResponsesNeverBecomeSuccess() {
        listOf("{}", "null", "[]", "true", """{"captchaRequired":false}""",
            """{"captchaRequired":"true"}""", "\"unexpected\"").forEach {
            assertTrue(it, parse(it) is Outcome.Error)
        }
    }

    private fun parse(data: String): Outcome<RegistrationResult> {
        val type = object : TypeToken<ApiResult<JsonElement>>() {}.type
        val response: ApiResult<JsonElement> = Gson().fromJson("""{"code":200,"message":"success","data":$data}""", type)
        return response.data?.let(::parseRegistrationResult) ?: Outcome.Error("缺少数据")
    }
}
