package edu.csuft.sap.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun login(studentId: String, password: String) {
        if (studentId.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "请输入学号和密码")
            return
        }
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            _state.value = when (val r = Graph.authRepository.appLogin(studentId, password)) {
                is Outcome.Success -> UiState(success = true)
                is Outcome.Error -> UiState(error = r.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
