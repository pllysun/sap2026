package edu.csuft.sap.data.repository

import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.apiUnit
import edu.csuft.sap.data.remote.dto.BindRequest
import edu.csuft.sap.data.remote.dto.BindResult
import edu.csuft.sap.data.remote.dto.CaptchaRequest
import edu.csuft.sap.data.remote.dto.EvalAutoRequest
import edu.csuft.sap.data.remote.dto.EvalAnswerRequest
import edu.csuft.sap.data.remote.dto.EvalFormDto
import edu.csuft.sap.data.remote.dto.EvalOverviewDto
import edu.csuft.sap.data.remote.dto.EvalResultDto
import edu.csuft.sap.data.remote.dto.EvalSubmitRequest
import edu.csuft.sap.data.remote.dto.ExamDto
import edu.csuft.sap.data.remote.dto.GradeDto
import edu.csuft.sap.data.remote.dto.JwAccountDto
import edu.csuft.sap.data.remote.dto.JwAccountRemarkRequest
import edu.csuft.sap.data.remote.dto.JwStatus
import edu.csuft.sap.data.remote.dto.ScheduleData
import edu.csuft.sap.data.remote.dto.TermDto

/** 教务接口仓库。数据接口按 account(学号) 维度；account 为 null 时后端取默认学号。 */
class JwRepository(private val api: ApiService) {

    suspend fun status(): Outcome<JwStatus> = apiData { api.jwStatus() }

    suspend fun accounts(): Outcome<List<JwAccountDto>> = apiData { api.jwAccounts() }

    suspend fun updateRemark(account: String, remark: String?): Outcome<Unit> =
        apiUnit { api.updateJwAccountRemark(JwAccountRemarkRequest(account.trim(), remark)) }

    suspend fun bind(account: String, password: String): Outcome<BindResult> =
        apiData { api.jwBind(BindRequest(account.trim(), password)) }

    suspend fun bindCaptcha(challengeId: String, code: String): Outcome<BindResult> =
        apiData { api.jwBindCaptcha(CaptchaRequest(challengeId, code.trim())) }

    suspend fun bindMfa(challengeId: String, code: String): Outcome<BindResult> =
        apiData { api.jwBindMfa(CaptchaRequest(challengeId, code.trim())) }

    suspend fun bindMfaResend(challengeId: String): Outcome<BindResult> =
        apiData { api.jwBindMfaResend(CaptchaRequest(challengeId, "")) }

    suspend fun unbind(account: String): Outcome<Unit> = apiUnit { api.jwUnbind(account) }

    suspend fun schedule(account: String?, term: String?): Outcome<ScheduleData> =
        apiData { api.schedule(account, term) }

    suspend fun terms(account: String?): Outcome<List<TermDto>> = apiData { api.terms(account) }

    suspend fun grades(account: String?): Outcome<List<GradeDto>> = apiData { api.grades(account) }

    suspend fun exams(account: String?, term: String?): Outcome<List<ExamDto>> =
        apiData { api.exams(account, term) }

    /** selector 传评教任务 id，可精确读取不同学年/轮次。 */
    suspend fun evalList(account: String?, selector: String?): Outcome<EvalOverviewDto> =
        apiData { api.evalList(account, selector) }

    suspend fun evalForm(account: String?, taskId: Long, courseId: Long): Outcome<EvalFormDto> =
        apiData { api.evalForm(account, taskId, courseId) }

    suspend fun evalSubmit(account: String?, taskId: Long, courseId: Long,
                           answers: List<EvalAnswerRequest>): Outcome<EvalResultDto> =
        apiData { api.evalSubmit(EvalSubmitRequest(account, taskId, courseId, answers)) }

    suspend fun evalAuto(account: String?, taskId: Long?,
                         comment: String?): Outcome<List<EvalResultDto>> =
        apiData { api.evalAuto(EvalAutoRequest(account, taskId, comment)) }
}
