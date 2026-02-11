package com.thecrazylegs.keplayer.ui.welcome

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thecrazylegs.keplayer.data.api.ApiClient
import com.thecrazylegs.keplayer.data.network.NsdDiscoveryManager
import com.thecrazylegs.keplayer.data.network.ServerInfo
import com.thecrazylegs.keplayer.data.storage.TokenStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "WelcomeViewModel"
private const val POLL_INTERVAL_MS = 3000L
private const val DISCOVERY_TIMEOUT_MS = 15_000L

data class WelcomeUiState(
    val discoveryStatus: String = "discovering",
    // discovering | not_found | requesting_code | showing_code | confirmed | error
    val serverUrl: String = "",
    val code: String = "",
    val pairId: String = "",
    val error: String? = null,
    val isPaired: Boolean = false,
    val discoveredServers: List<ServerInfo> = emptyList()
)

class WelcomeViewModel(
    private val tokenStorage: TokenStorage,
    context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val nsdManager = NsdDiscoveryManager(context)
    private var pollingJob: Job? = null
    private var discoveryJob: Job? = null

    init {
        // Pre-fill serverUrl if saved
        tokenStorage.serverUrl?.let { savedUrl ->
            _uiState.value = _uiState.value.copy(serverUrl = savedUrl)
        }

        startDiscovery()
    }

    private fun startDiscovery() {
        nsdManager.startDiscovery()

        // Watch for resolved servers (jmDNS gives us IP directly)
        discoveryJob = viewModelScope.launch {
            nsdManager.discoveredServers.collect { servers ->
                _uiState.value = _uiState.value.copy(discoveredServers = servers)

                if (servers.isNotEmpty() && _uiState.value.code.isBlank()
                    && _uiState.value.discoveryStatus == "discovering") {
                    val server = servers.first()
                    val url = "${server.host}:${server.port}"
                    Log.i(TAG, "Server discovered: $url")
                    _uiState.value = _uiState.value.copy(serverUrl = url)
                    tokenStorage.serverUrl = url
                    requestCode()
                }
            }
        }

        // Timeout: if no server found after 15s, fallback to saved URL or show not_found
        viewModelScope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            if (_uiState.value.discoveryStatus == "discovering") {
                if (_uiState.value.serverUrl.isNotBlank() && _uiState.value.code.isBlank()) {
                    Log.i(TAG, "Discovery timeout, trying saved URL: ${_uiState.value.serverUrl}")
                    requestCode()
                } else {
                    _uiState.value = _uiState.value.copy(discoveryStatus = "not_found")
                }
            }
        }
    }

    fun setServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
        if (url.isNotBlank()) {
            tokenStorage.serverUrl = url
        }
    }

    fun requestCode() {
        val url = _uiState.value.serverUrl
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                discoveryStatus = "error",
                error = "No server URL"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            discoveryStatus = "requesting_code",
            error = null,
            code = "",
            pairId = ""
        )

        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(url)
                val response = api.requestPairCode()

                _uiState.value = _uiState.value.copy(
                    code = response.code,
                    pairId = response.pairId,
                    discoveryStatus = "showing_code"
                )

                startPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request pair code", e)
                _uiState.value = _uiState.value.copy(
                    discoveryStatus = "error",
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
                        "pending" -> { /* continue polling */ }
                        "confirmed" -> {
                            val token = response.token
                            if (token != null) {
                                saveTokenAndUser(token)
                                _uiState.value = _uiState.value.copy(
                                    discoveryStatus = "confirmed",
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
                discoveryStatus = "error",
                error = "Failed to process token: ${e.message}"
            )
        }
    }

    fun retryDiscovery() {
        nsdManager.stopDiscovery()
        pollingJob?.cancel()
        _uiState.value = WelcomeUiState(
            serverUrl = _uiState.value.serverUrl
        )
        startDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        discoveryJob?.cancel()
        nsdManager.stopDiscovery()
    }
}
