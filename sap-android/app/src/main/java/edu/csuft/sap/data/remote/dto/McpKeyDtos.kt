package edu.csuft.sap.data.remote.dto

data class McpKeySettingsDto(
    val limit: Int,
    val canCreate: Boolean,
    val keys: List<McpKeyDto>,
    val bindings: List<McpBindingDto>,
)

data class McpKeyDto(
    val id: Long,
    val name: String,
    val prefix: String,
    val credentialId: Long,
    val scopes: List<String>,
    val createdAt: String,
)

data class McpBindingDto(val id: Long, val account: String, val remark: String?) {
    val label: String get() = if (remark.isNullOrBlank()) account else "$remark（$account）"
}

data class McpCreateKeyRequest(val name: String, val credentialId: Long, val scopes: List<String>)

// A plain class avoids including the one-time secret in an implicit toString().
class McpCreatedKeyDto(val key: McpKeyDto, val secret: String)
