package edu.csuft.sap.ui.feedback

import edu.csuft.sap.data.remote.dto.FeedbackCommentDto
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackThreadTest {

    @Test
    fun groupsAllNestedRepliesUnderOneRoot() {
        val comments = listOf(
            comment(1),
            comment(2, parentId = 1),
            comment(3, parentId = 1),
            comment(4),
        )

        val threads = feedbackCommentThreads(comments)

        assertEquals(listOf(1L, 4L), threads.map { it.root.id })
        assertEquals(listOf(2L, 3L), threads.first().replies.map { it.id })
        assertEquals(emptyList<FeedbackCommentDto>(), threads.last().replies)
    }

    @Test
    fun keepsOrphanedHistoricalCommentVisibleAsRoot() {
        val orphan = comment(9, parentId = 99)

        val threads = feedbackCommentThreads(listOf(orphan))

        assertEquals(listOf(9L), threads.map { it.root.id })
    }

    @Test
    fun expandsNestedRepliesFiveAtATime() {
        assertEquals(5, FEEDBACK_REPLY_PAGE_SIZE)
        assertEquals(10, nextVisibleReplyCount(current = 5, total = 18))
        assertEquals(15, nextVisibleReplyCount(current = 10, total = 18))
        assertEquals(18, nextVisibleReplyCount(current = 15, total = 18))
    }

    private fun comment(id: Long, parentId: Long? = null) = FeedbackCommentDto(
        id = id,
        parentId = parentId,
        authorName = "用户$id",
        content = "回复$id",
    )
}
