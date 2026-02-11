package com.thecrazylegs.keplayer.ui.pairing

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thecrazylegs.keplayer.data.api.ApiClient
import com.thecrazylegs.keplayer.data.storage.TokenStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "PairingViewModel"
private const val POLL_INTERVAL_MS = 3000L

data class PairingUiState(
    val serverUrl: String = "",
    val code: String = "",
    val pairId: String = "",
    val status: String = "loading", // loading | pending | confirmed | expired | error
    val error: String? = null,
    val isPaired: Boolean = false
)

class PairingViewModel(
    private val tokenStorage: TokenStorage,
    serverUrl: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState(serverUrl = serverUrl))
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        requestCode()
    }

    fun requestCode() {
        val state = _uiState.value

        _uiState.value = state.copy(status = "loading", error = null, code = "")

        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(state.serverUrl)
                val response = api.requestPairCode()

                _uiState.value = _uiState.value.copy(
                    code = response.code,
                    pairId = response.pairId,
                    status = "pending"
                )

                startPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request pair code", e)
                _uiState.value = _uiState.value.copy(
                    status = "error",
                    error = "Connection error: ${e.message}"
                )
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val api = ApiClient.getApi(_uiState.value.serverUrl)

            while (isActive) {
                delay(POLL_INTERVAL_MS)

                try {
                    val pairId = _uiState.value.pairId
                    if (pairId.isBlank()) break

                    val response = api.getPairStatus(pairId)

                    when (response.status) {
                        "pending" -> {
                            // continue polling
                        }
                        "confirmed" -> {
                            val token = response.token
                            if (token != null) {
                                saveTokenAndUser(token)
                                _uiState.value = _uiState.value.copy(
                                    status = "confirmed",
                                    isPaired = true
                                )
                            }
                            break
                        }
                        "expired" -> {
                            Log.i(TAG, "Pair code expired, requesting new one")
                            requestCode()
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error", e)
                    // don't break on transient errors, keep polling
                }
            }
        }
    }

    private fun saveTokenAndUser(token: String) {
        try {
            val parts = token.split(".")
            if (parts.size < 2) throw IllegalArgumentException("Invalid JWT format")

            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP))
            val json = JSONObject(payload)

            tokenStorage.serverUrl = _uiState.value.serverUrl
            tokenStorage.token = token
            tokenStorage.userId = json.optInt("userId", -1)
            tokenStorage.username = json.optString("username", "")
            tokenStorage.isAdmin = json.optBoolean("isAdmin", false)
            tokenStorage.roomId = json.optInt("roomId", -1)

            Log.i(TAG, "Paired as ${json.optString("name")} (userId=${json.optInt("userId")}, room=${json.optInt("roomId")})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode JWT", e)
            _uiState.value = _uiState.value.copy(
                status = "error",
                error = "Failed to process token: ${e.message}"
            )
        }
    }

    fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelPolling()
    }
}
