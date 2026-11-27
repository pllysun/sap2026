package edu.csuft.sap.data.repository

import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.apiUnit
import edu.csuft.sap.data.remote.dto.McpCreateKeyRequest

class McpKeyRepository(private val api: ApiService) {
    suspend fun settings() = apiData { api.mcpKeySettings() }

    suspend fun create(request: McpCreateKeyRequest) = apiData { api.createMcpKey(request) }

    suspend fun destroy(id: Long) = apiUnit { api.deleteMcpKey(id) }
}
