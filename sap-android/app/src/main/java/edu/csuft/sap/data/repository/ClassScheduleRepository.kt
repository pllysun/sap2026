package edu.csuft.sap.data.repository

import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.dto.ClassOptionDto
import edu.csuft.sap.data.remote.dto.ClassScheduleTermDto
import edu.csuft.sap.data.remote.dto.ScheduleData

/** 班级课表只读接口；缓存由 ScheduleStore 按班级选择键隔离。 */
class ClassScheduleRepository(private val api: ApiService) {
    suspend fun terms(): Outcome<List<ClassScheduleTermDto>> = apiData { api.classScheduleTerms() }

    suspend fun classes(term: String, college: String?, major: String?, grade: String? = null): Outcome<List<ClassOptionDto>> =
        apiData { api.classScheduleClasses(term, college, major, grade) }

    suspend fun schedule(term: String, college: String, major: String, className: String, grade: String? = null): Outcome<ScheduleData> =
        apiData { api.classSchedule(term, college, major, grade, className) }
}
