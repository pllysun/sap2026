package edu.csuft.sap.data.remote.dto

/** 后端统一返回：{ code, message, data } */
data class ApiResult<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null,
)

data class LoginData(
    val token: String? = null,
    val user: UserDto? = null,
    val roles: List<Int> = emptyList(),
    val appAccessLevel: Int = 0,
)

/**
 * /api/auth/info（含头像）与 /api/auth/info/light（不含头像）共用结构。
 * - identities：平台身份（届+职务），如 2025/宣传部部长、2026/会长；空=游客。
 * - updatedAt：用户信息修改时间(毫秒)，App 据此判断是否需要重新拉头像（省流量）。
 */
data class MeData(
    val user: UserDto? = null,
    val roles: List<Int> = emptyList(),
    val appAccessLevel: Int = 0,
    val identities: List<IdentityDto> = emptyList(),
    val updatedAt: Long? = null,
)

data class AppAnnouncementDto(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val published: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** 平台身份：届(grade) + 职务(positionName)，如 grade=2025 positionName=宣传部部长。 */
data class IdentityDto(
    val grade: String? = null,
    val positionName: String? = null,
)

data class UserDto(
    val id: Long? = null,
    val studentId: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val grade: String? = null,
    val gender: Int? = null, // 1=男 2=女 0/其它=未知
)

/** 修改个人信息请求体（仅非核心字段：网名/性别/头像）。 */
data class UpdateProfileRequest(
    val nickname: String? = null,
    val gender: Int? = null,
    val avatar: String? = null,
)

/** 文件上传返回：{ url, name }。 */
data class UploadResult(
    val url: String? = null,
    val name: String? = null,
)

/** 教务绑定状态（默认学号 + 绑定数量） */
data class JwStatus(
    val bound: Boolean = false,
    val account: String? = null,
    val count: Int = 0,
)

/** 已绑定的单个教务学号 */
data class JwAccountDto(
    val account: String = "",
    val remark: String? = null,
    val lastSyncAt: String? = null,
)

/** 教务账号备注保存请求；remark=null 表示清除。 */
data class JwAccountRemarkRequest(
    val account: String,
    val remark: String? = null,
)

data class ScheduleData(
    val term: String? = null,
    val terms: List<TermDto> = emptyList(),
    val weekdays: List<String> = emptyList(),
    val courses: List<CourseDto> = emptyList(),
    val remarks: List<RemarkDto> = emptyList(),
    val semesterStartDate: String? = null, // 开学日期(第1周周一,ISO)，来自教务教学周历；可能为 null
)

/** 课表备注（无固定时间格的实验/实习/集中实践课） */
data class RemarkDto(
    val name: String? = null,
    val teacher: String? = null,
    val weeks: String? = null,
    val clazz: String? = null,
    val raw: String? = null,
)

data class TermDto(
    val value: String = "",
    val label: String = "",
    val current: Boolean = false,
)

data class CourseDto(
    val day: Int = 0,
    val dayName: String? = null,
    val section: String? = null,
    val sectionIndex: Int = 0,
    val name: String? = null,
    val type: String? = null,
    val teacher: String? = null,
    val weeks: String? = null,
    val room: String? = null,
)

data class GradeDto(
    val term: String? = null,
    val courseNo: String? = null,
    val courseName: String? = null,
    val score: String? = null,
    val credit: String? = null,
    val hours: String? = null,
    val gradePoint: String? = null,
    val flag: String? = null,
    val assessMethod: String? = null,
    val examNature: String? = null,
    val courseAttr: String? = null,
    val courseNature: String? = null,
)

data class ExamDto(
    val session: String? = null,
    val courseNo: String? = null,
    val courseName: String? = null,
    val time: String? = null,
    val room: String? = null,
    val seat: String? = null,
    val admissionTicket: String? = null,
)

/** 一条学生评教任务 */
data class EvalTaskDto(
    val term: String? = null,
    val taskId: Long? = null,
    val courseId: Long? = null,
    val courseCode: String? = null,
    val courseName: String? = null,
    val classNo: String? = null,
    val teacherNo: String? = null,
    val teacher: String? = null,
    val college: String? = null,
    val typeName: String? = null,
    val score: String? = null,
    val evaluated: Boolean = false,
    val submitted: Boolean = false,
    val status: Int = 0,
    val statusText: String? = null,
    val editUrl: String? = null,
    val jx0404id: String? = null,
)

data class EvalRoundDto(
    val id: Long? = null,
    val name: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val status: String? = null,
)

/** 评教总览：新教学质量保障系统的一轮任务及其课程。 */
data class EvalOverviewDto(
    val term: String? = null,
    val terms: List<String> = emptyList(),
    val taskId: Long? = null,
    val taskName: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val status: String? = null,
    val restrictHighest: Boolean = false,
    val restrictLowest: Boolean = false,
    val rounds: List<EvalRoundDto> = emptyList(),
    val tasks: List<EvalTaskDto> = emptyList(),
)

data class EvalOptionDto(
    val id: Long? = null,
    val title: String? = null,
    val score: Double = 0.0,
)

data class EvalQuestionDto(
    val indexId: Long? = null,
    val order: Int = 0,
    val section: String? = null,
    val type: String? = null,
    val title: String? = null,
    val remark: String? = null,
    val required: Boolean = false,
    val scored: Boolean = false,
    val maxScore: Double = 0.0,
    val scoringType: Int = 0,
    val options: List<EvalOptionDto> = emptyList(),
)

data class EvalFormDto(
    val taskId: Long? = null,
    val taskName: String? = null,
    val courseId: Long? = null,
    val courseCode: String? = null,
    val courseName: String? = null,
    val teacherNo: String? = null,
    val teacher: String? = null,
    val typeName: String? = null,
    val restrictHighest: Boolean = false,
    val restrictLowest: Boolean = false,
    val maxTotal: Double = 0.0,
    val defaultComment: String? = null,
    val questions: List<EvalQuestionDto> = emptyList(),
)

data class EvalAnswerRequest(
    val indexId: Long,
    val score: Double? = null,
    val text: String? = null,
    val optionId: Long? = null,
    val optionIds: List<Long>? = null,
    val values: List<String>? = null,
)

data class EvalSubmitRequest(
    val account: String? = null,
    val taskId: Long,
    val courseId: Long,
    val answers: List<EvalAnswerRequest>,
)

/** 一条教学评价提交结果 */
data class EvalResultDto(
    val courseId: Long? = null,
    val courseName: String? = null,
    val teacher: String? = null,
    val typeName: String? = null,
    val success: Boolean = false,
    val pending: Boolean = false,
    val retryable: Boolean = false,
    val skipped: Boolean = false,
    val message: String? = null,
    val score: String? = null,
)

/** 一键自动评教请求体 */
data class EvalAutoRequest(
    val account: String? = null,
    val taskId: Long? = null,
    val comment: String? = null,
)

/** 绑定请求体 */
data class BindRequest(val account: String, val password: String)

/**
 * 绑定结果：needCaptcha=true 时携带验证码图(base64)与挑战 id；
 * needMfa=true 时为安全手机短信二次验证，携带挑战 id 与掩码手机号 [phone]，需用户输短信码调 /bind/mfa。
 */
data class BindResult(
    val needCaptcha: Boolean = false,
    val challengeId: String? = null,
    val captchaImage: String? = null,
    val needMfa: Boolean = false,
    val phone: String? = null,
)

/** 验证码续登请求体 */
data class CaptchaRequest(val challengeId: String, val code: String)

/** 登录请求体 */
data class LoginRequest(val studentId: String, val password: String)

/** /api/app/latest 返回：App 最新版本元数据（应用内升级）。 */
data class AppVersionDto(
    val versionCode: Int = 0,
    val versionName: String? = null,
    val changelog: String? = null,
    val forceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 0,
    val sha256: String? = null,
    val size: Long = 0,
    val downloadUrl: String? = null,
)

/** 软协课表意见反馈 Issue。 */
data class FeedbackIssueDto(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val images: List<String> = emptyList(),
    val category: String = "OTHER",
    val categoryText: String = "其他",
    val status: String = "OPEN",
    val reporterId: Long? = null,
    val reporterName: String? = null,
    val reporterAvatar: String? = null,
    val commentCount: Int = 0,
    val mine: Boolean = false,
    val canComment: Boolean = false,
    val canClose: Boolean = false,
    val appVersionName: String? = null,
    val appVersionCode: Int? = null,
    val deviceInfo: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val closedAt: String? = null,
    val closedByName: String? = null,
    val comments: List<FeedbackCommentDto> = emptyList(),
)

data class FeedbackCommentDto(
    val id: Long = 0,
    val authorId: Long? = null,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val parentId: Long? = null,
    val adminReply: Boolean = false,
    val questioner: Boolean = false,
    val content: String = "",
    val createdAt: String? = null,
)

data class FeedbackPageDto(
    val records: List<FeedbackIssueDto> = emptyList(),
    val total: Long = 0,
    val current: Long = 1,
    val size: Long = 20,
)

data class FeedbackIssueCreateRequest(
    val title: String,
    val content: String,
    val category: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val deviceInfo: String? = null,
    val images: List<String> = emptyList(),
)

data class FeedbackCommentCreateRequest(
    val content: String,
    val parentId: Long? = null,
)
