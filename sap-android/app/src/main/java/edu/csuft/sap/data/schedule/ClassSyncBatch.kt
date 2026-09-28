package edu.csuft.sap.data.schedule

import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.*

/** 全部无变化/任一请求失败均不提交；某批变化时补取其余批，调用方统一原子合并。 */
internal suspend fun fetchClassSyncBatch(
    selections: List<ClassSyncSelection>,
    query: suspend (ClassSyncRequest) -> Outcome<ClassSyncResponse>,
): List<ClassSyncItem>? {
    if (selections.isEmpty()) return null
    val chunks = selections.chunked(100)
    val responses = chunks.map { chunk ->
        when (val result = query(ClassSyncRequest(chunk))) {
            is Outcome.Success -> result.data
            is Outcome.Error -> return null
        }
    }.toMutableList()
    if (responses.none { it.changed }) return null
    for (i in chunks.indices) if (!responses[i].changed) {
        responses[i] = when (val result = query(ClassSyncRequest(chunks[i], force = true))) {
            is Outcome.Success -> result.data
            is Outcome.Error -> return null
        }
    }
    val items = responses.flatMap { it.items }
    if (items.size != selections.size || items.map { it.key }.toSet() != selections.map { it.key }.toSet()
        || items.any { it.data == null }) return null
    return items
}
