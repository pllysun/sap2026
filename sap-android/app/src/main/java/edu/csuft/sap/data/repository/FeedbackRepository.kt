package edu.csuft.sap.data.repository

import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.dto.FeedbackCommentCreateRequest
import edu.csuft.sap.data.remote.dto.FeedbackIssueCreateRequest
import edu.csuft.sap.data.remote.dto.FeedbackIssueDto
import edu.csuft.sap.data.remote.dto.FeedbackPageDto
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class FeedbackImageUpload(
    val bytes: ByteArray,
    val filename: String,
    val mediaType: String,
)

class FeedbackRepository(private val api: ApiService) {

    suspend fun issues(status: String?, category: String?, keyword: String?, mine: Boolean): Outcome<FeedbackPageDto> =
        apiData { api.feedbackIssues(1, 100, status, category, keyword, mine) }

    suspend fun detail(id: Long): Outcome<FeedbackIssueDto> =
        apiData { api.feedbackIssue(id) }

    suspend fun create(title: String, content: String, category: String,
                       versionName: String, versionCode: Int,
                       images: List<FeedbackImageUpload>): Outcome<FeedbackIssueDto> {
        val urls = if (images.isEmpty()) {
            emptyList()
        } else {
            val parts = images.map { image ->
                val body = image.bytes.toRequestBody(image.mediaType.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("files", image.filename, body)
            }
            when (val uploaded = apiData { api.uploadFeedbackImages(parts) }) {
                is Outcome.Success -> uploaded.data
                is Outcome.Error -> return uploaded
            }
        }
        return apiData {
            api.createFeedbackIssue(
                FeedbackIssueCreateRequest(
                    title = title,
                    content = content,
                    category = category,
                    appVersionName = versionName,
                    appVersionCode = versionCode,
                    images = urls,
                )
            )
        }
    }

    suspend fun comment(id: Long, content: String, parentId: Long?): Outcome<FeedbackIssueDto> =
        apiData { api.commentFeedbackIssue(id, FeedbackCommentCreateRequest(content, parentId)) }

    suspend fun close(id: Long): Outcome<FeedbackIssueDto> =
        apiData { api.closeFeedbackIssue(id) }
}
