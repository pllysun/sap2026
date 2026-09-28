package edu.csuft.sap.data.schedule

import edu.csuft.sap.data.account.AccountManager

/** 删除本地班级数据，并同步当前/上次选择；选择器和课表设置共用同一入口。 */
class ClassScheduleCache(
    private val store: ScheduleStore,
    private val accounts: AccountManager,
) {
    fun delete(selected: Set<String>) {
        val deleted = selected.filter(AccountManager::isClass).toSet()
        if (deleted.isEmpty()) return
        store.clearClassAccounts(deleted)
        accounts.forgetClassAccounts(deleted, store.cachedClassAccounts().map { it.first })
    }
}
