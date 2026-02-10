package com.thecrazylegs.keplayer.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thecrazylegs.keplayer.data.api.ApiClient
import com.thecrazylegs.keplayer.data.model.LoginRequest
import com.thecrazylegs.keplayer.data.storage.TokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val roomId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(
    private val tokenStorage: TokenStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Pre-fill saved values if exist
        tokenStorage.serverUrl?.let { savedUrl ->
            _uiState.value = _uiState.value.copy(serverUrl = savedUrl)
        }
        tokenStorage.username?.let { savedUsername ->
            _uiState.value = _uiState.value.copy(username = savedUsername)
        }
        if (tokenStorage.roomId > 0) {
            _uiState.value = _uiState.value.copy(roomId = tokenStorage.roomId.toString())
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun onRoomIdChange(roomId: String) {
        _uiState.value = _uiState.value.copy(roomId = roomId, error = null)
    }

    fun login() {
        val state = _uiState.value

        if (state.serverUrl.isBlank()) {
            _uiState.value = state.copy(error = "Server URL is required")
            return
        }
        if (state.username.isBlank()) {
            _uiState.value = state.copy(error = "Username is required")
            return
        }
        if (state.password.isBlank()) {
            _uiState.value = state.copy(error = "Password is required")
            return
        }

        val roomIdInt = state.roomId.toIntOrNull()
        if (roomIdInt == null || roomIdInt < 1) {
            _uiState.value = state.copy(error = "Valid Room ID is required")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(state.serverUrl)
                val response = api.login(
                    LoginRequest(
                        username = state.username,
                        password = state.password,
                        roomId = roomIdInt
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!

                    // Extract token from Set-Cookie header
                    val cookies = response.headers()["Set-Cookie"]
                    val token = cookies?.let { extractToken(it) }

                    // Save credentials
                    tokenStorage.serverUrl = state.serverUrl
                    tokenStorage.token = token
                    tokenStorage.username = user.username
                    tokenStorage.userId = user.userId
                    tokenStorage.isAdmin = user.isAdmin
                    tokenStorage.roomId = user.roomId ?: roomIdInt

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Login failed"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Error ${response.code()}: $errorBody"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Connection error: ${e.message}"
                )
            }
        }
    }

    private fun extractToken(cookieHeader: String): String? {
        // Parse "keToken=xxx; Path=/; HttpOnly" format
        return cookieHeader
            .split(";")
            .firstOrNull { it.trim().startsWith("keToken=") }
            ?.substringAfter("keToken=")
            ?.trim()
    }
}
