package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.*
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ClassSyncBatchTest {
    private fun selections(n: Int) = (1..n).map { ClassSyncSelection("$it", "term", "college", "grade", "major", "class$it", "v1") }
    private fun response(request: ClassSyncRequest, changed: Boolean) = Outcome.Success(ClassSyncResponse(changed,
        request.selections.map { ClassSyncItem(it.key, "v2", if (changed) ScheduleData(term = it.term) else null) }))
    @Test fun unchangedMakesOneProbeWithoutDownloading() = runTest {
        var calls = 0
        assertNull(fetchClassSyncBatch(selections(4)) { calls++; response(it, false) })
        assertEquals(1, calls)
    }
    @Test fun changedReturnsAllCachedClasses() = runTest {
        assertEquals(4, fetchClassSyncBatch(selections(4)) { response(it, true) }!!.size)
    }
    @Test fun changeInOneBatchForcesRefreshOfOtherBatchesSequentially() = runTest {
        val calls = mutableListOf<ClassSyncRequest>()
        val result = fetchClassSyncBatch(selections(101)) {
            calls += it
            response(it, it.force || it.selections.first().key == "101")
        }
        assertEquals(101, result!!.size)
        assertEquals(listOf(false, false, true), calls.map { it.force })
        assertEquals(listOf(100, 1, 100), calls.map { it.selections.size })
    }
    @Test fun partialFailureOrIncompleteResponseNeverReplacesCaches() = runTest {
        var call = 0
        assertNull(fetchClassSyncBatch(selections(101)) {
            if (++call == 1) response(it, true) else Outcome.Error("offline")
        })
        assertNull(fetchClassSyncBatch(selections(2)) {
            Outcome.Success(ClassSyncResponse(true, listOf(ClassSyncItem("1", "v2", ScheduleData()))))
        })
    }
}
