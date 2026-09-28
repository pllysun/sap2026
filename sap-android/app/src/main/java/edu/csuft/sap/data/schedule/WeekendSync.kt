package edu.csuft.sap.data.schedule

/** 只在同步成功后的提交点调用。加载缓存、切换课表、手动编辑均不能调用。 */
fun ScheduleRoot.withSyncedWeekend(accountKey: String, syncedTerms: Set<String>): ScheduleRoot {
    val data = accounts[accountKey] ?: return this
    val profile = data.profiles.firstOrNull { it.id == data.activeProfileId } ?: return this
    if (profile.kind != ProfileKind.TERM || profile.termValue !in syncedTerms) return this
    val courses = data.termCourses[profile.termValue] ?: return this
    val display = displaySettings ?: profile.settings.toDisplaySettings()
    return withDisplaySettings(display.copy(showWeekend = courses.any { it.day in 6..7 }))
}
