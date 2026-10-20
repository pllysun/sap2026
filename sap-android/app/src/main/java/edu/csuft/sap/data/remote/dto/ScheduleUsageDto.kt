package edu.csuft.sap.data.remote.dto

data class ScheduleUsageEvent(val date: String, val mode: String, val event: String)
data class ScheduleUsageReport(val events: List<ScheduleUsageEvent>)
