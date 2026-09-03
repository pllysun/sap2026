package edu.csuft.sap.data.remote

import edu.csuft.sap.data.remote.dto.ApiResult
import edu.csuft.sap.data.remote.dto.AppVersionDto
import edu.csuft.sap.data.remote.dto.AppAnnouncementDto
import edu.csuft.sap.data.remote.dto.BindRequest
import edu.csuft.sap.data.remote.dto.BindResult
import edu.csuft.sap.data.remote.dto.CaptchaRequest
import edu.csuft.sap.data.remote.dto.CourseDto
import edu.csuft.sap.data.remote.dto.ClassScheduleTermDto
import edu.csuft.sap.data.remote.dto.ClassOptionDto
import edu.csuft.sap.data.remote.dto.EvalAutoRequest
import edu.csuft.sap.data.remote.dto.EvalFormDto
import edu.csuft.sap.data.remote.dto.EvalOverviewDto
import edu.csuft.sap.data.remote.dto.EvalResultDto
import edu.csuft.sap.data.remote.dto.EvalSubmitRequest
import edu.csuft.sap.data.remote.dto.ExamDto
import edu.csuft.sap.data.remote.dto.FeedbackCommentCreateRequest
import edu.csuft.sap.data.remote.dto.FeedbackIssueCreateRequest
import edu.csuft.sap.data.remote.dto.FeedbackIssueDto
import edu.csuft.sap.data.remote.dto.FeedbackPageDto
import edu.csuft.sap.data.remote.dto.GradeDto
import edu.csuft.sap.data.remote.dto.JwAccountDto
import edu.csuft.sap.data.remote.dto.JwAccountRemarkRequest
import edu.csuft.sap.data.remote.dto.JwStatus
import edu.csuft.sap.data.remote.dto.LoginData
import edu.csuft.sap.data.remote.dto.LoginRequest
import edu.csuft.sap.data.remote.dto.MeData
import edu.csuft.sap.data.remote.dto.ScheduleData
import edu.csuft.sap.data.remote.dto.TermDto
import edu.csuft.sap.data.remote.dto.UpdateProfileRequest
import edu.csuft.sap.data.remote.dto.UploadResult
import edu.csuft.sap.data.remote.dto.UserDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

/** 后端 API（对接 sap-backend 的 /api/auth 与 /api/jw） */
interface ApiService {

    /** 健康探针：免登录、极轻，用于「在线/离线」连通性探测。 */
    @GET("api/ping")
    suspend fun ping(): ApiResult<String>

    @POST("api/auth/app/login")
    suspend fun appLogin(@Body body: LoginRequest): ApiResult<LoginData>

    @GET("api/auth/info")
    suspend fun me(): ApiResult<MeData>

    /** 轻量用户信息：不含头像 + identities + updatedAt；用于"按修改时间决定是否重新拉头像"省流量。 */
    @GET("api/auth/info/light")
    suspend fun meLight(): ApiResult<MeData>

    @POST("api/auth/logout")
    suspend fun logout(): ApiResult<Any>

    @GET("api/jw/status")
    suspend fun jwStatus(): ApiResult<JwStatus>

    @GET("api/jw/accounts")
    suspend fun jwAccounts(): ApiResult<List<JwAccountDto>>

    @PUT("api/jw/accounts/remark")
    suspend fun updateJwAccountRemark(@Body body: JwAccountRemarkRequest): ApiResult<Any>

    @POST("api/jw/bind")
    suspend fun jwBind(@Body body: BindRequest): ApiResult<BindResult>

    @POST("api/jw/bind/captcha")
    suspend fun jwBindCaptcha(@Body body: CaptchaRequest): ApiResult<BindResult>

    @POST("api/jw/bind/mfa")
    suspend fun jwBindMfa(@Body body: CaptchaRequest): ApiResult<BindResult>

    @POST("api/jw/bind/mfa/resend")
    suspend fun jwBindMfaResend(@Body body: CaptchaRequest): ApiResult<BindResult>

    @DELETE("api/jw/unbind")
    suspend fun jwUnbind(@Query("account") account: String): ApiResult<Any>

    @GET("api/jw/schedule")
    suspend fun schedule(
        @Query("account") account: String?,
        @Query("term") term: String?,
    ): ApiResult<ScheduleData>

    @GET("api/jw/terms")
    suspend fun terms(@Query("account") account: String?): ApiResult<List<TermDto>>

    @GET("api/jw/grades")
    suspend fun grades(@Query("account") account: String?): ApiResult<List<GradeDto>>

    @GET("api/jw/exams")
    suspend fun exams(
        @Query("account") account: String?,
        @Query("term") term: String?,
    ): ApiResult<List<ExamDto>>

    @GET("api/jw/eval/list")
    suspend fun evalList(
        @Query("account") account: String?,
        @Query("term") term: String?,
    ): ApiResult<EvalOverviewDto>

    @GET("api/jw/eval/form")
    suspend fun evalForm(
        @Query("account") account: String?,
        @Query("taskId") taskId: Long,
        @Query("courseId") courseId: Long,
    ): ApiResult<EvalFormDto>

    @POST("api/jw/eval/submit")
    suspend fun evalSubmit(@Body body: EvalSubmitRequest): ApiResult<EvalResultDto>

    @POST("api/jw/eval/auto")
    suspend fun evalAuto(@Body body: EvalAutoRequest): ApiResult<List<EvalResultDto>>

    /** 班级课表：学期、班级选择器和已合并课表。 */
    @GET("api/class-schedule/terms")
    suspend fun classScheduleTerms(): ApiResult<List<ClassScheduleTermDto>>

    @GET("api/class-schedule/classes")
    suspend fun classScheduleClasses(
        @Query("term") term: String,
        @Query("college") college: String?,
        @Query("major") major: String?,
        @Query("grade") grade: String?,
    ): ApiResult<List<ClassOptionDto>>

    @GET("api/class-schedule/schedule")
    suspend fun classSchedule(
        @Query("term") term: String,
        @Query("college") college: String,
        @Query("major") major: String,
        @Query("grade") grade: String?,
        @Query("className") className: String,
    ): ApiResult<ScheduleData>

    /** 应用内升级：取最新版本元数据（需登录，按当前 App 能力决定是否检查）。 */
    @GET("api/app/latest")
    suspend fun appLatest(): ApiResult<AppVersionDto>

    /** 课表公告：最新公告在前；网络失败时由仓库回退到本地缓存。 */
    @GET("api/app/cloud/announcements")
    suspend fun appAnnouncements(): ApiResult<List<AppAnnouncementDto>>

    @GET("api/app/feedback/issues")
    suspend fun feedbackIssues(
        @Query("current") current: Int,
        @Query("size") size: Int,
        @Query("status") status: String?,
        @Query("category") category: String?,
        @Query("keyword") keyword: String?,
        @Query("mine") mine: Boolean,
    ): ApiResult<FeedbackPageDto>

    @GET("api/app/feedback/issues/{id}")
    suspend fun feedbackIssue(@retrofit2.http.Path("id") id: Long): ApiResult<FeedbackIssueDto>

    @POST("api/app/feedback/issues")
    suspend fun createFeedbackIssue(@Body body: FeedbackIssueCreateRequest): ApiResult<FeedbackIssueDto>

    @Multipart
    @POST("api/app/feedback/images")
    suspend fun uploadFeedbackImages(@Part files: List<MultipartBody.Part>): ApiResult<List<String>>

    @POST("api/app/feedback/issues/{id}/comments")
    suspend fun commentFeedbackIssue(
        @retrofit2.http.Path("id") id: Long,
        @Body body: FeedbackCommentCreateRequest,
    ): ApiResult<FeedbackIssueDto>

    @PUT("api/app/feedback/issues/{id}/close")
    suspend fun closeFeedbackIssue(
        @retrofit2.http.Path("id") id: Long,
    ): ApiResult<FeedbackIssueDto>

    /** 修改本人资料（网名/性别/头像）；复用用户端 /api/auth/profile。 */
    @PUT("api/auth/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiResult<Any>

    /** 上传文件（头像）→ 返回 COS url；复用用户端 /api/file/upload。 */
    @Multipart
    @POST("api/file/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): ApiResult<UploadResult>
}
