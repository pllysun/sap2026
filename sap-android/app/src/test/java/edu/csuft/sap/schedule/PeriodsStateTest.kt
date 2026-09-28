package edu.csuft.sap.schedule

import androidx.compose.runtime.snapshots.Snapshot
import edu.csuft.sap.data.schedule.Periods
import org.junit.Assert.*
import org.junit.Test

class PeriodsStateTest {
    @Test fun reloadingTimesNotifiesReadersAndAllViewsShareCurrentTimes() {
        val original = Periods.current
        try {
            Periods.updateCurrent(Periods.DEFAULT)
            val revision = Periods.revision
            val edited = Periods.DEFAULT.map { if (it.node == 1) it.copy(end = "08:40") else it }
            val reads = mutableSetOf<Any>()
            Snapshot.observe(readObserver = { reads.add(it) }) {
                assertEquals("08:45", Periods.period(1)?.end)
            }
            assertTrue(reads.isNotEmpty())
            Periods.updateCurrent(edited)
            assertEquals(revision + 1, Periods.revision)
            assertEquals("08:40", Periods.period(1)?.end)
            assertEquals(Periods.period(1), Periods.tableFor(10).first())
            Periods.updateCurrent(edited)
            assertEquals(revision + 1, Periods.revision)
            Periods.updateCurrent(Periods.DEFAULT)
            assertEquals("08:45", Periods.period(1)?.end)
        } finally { Periods.updateCurrent(original) }
    }
}
