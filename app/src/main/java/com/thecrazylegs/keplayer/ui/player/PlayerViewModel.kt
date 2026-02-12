package com.thecrazylegs.keplayer.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thecrazylegs.keplayer.data.model.QueueItem
import com.thecrazylegs.keplayer.data.model.QueueState
import com.thecrazylegs.keplayer.data.socket.SocketEvent
import com.thecrazylegs.keplayer.data.socket.SocketManager
import com.thecrazylegs.keplayer.data.storage.TokenStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

// Socket.io action types for player
private const val PLAYER_EMIT_STATUS = "server/PLAYER_EMIT_STATUS"
private const val PLAYER_EMIT_LEAVE = "server/PLAYER_EMIT_LEAVE"
private const val PLAYER_REQ_NEXT = "server/PLAYER_REQ_NEXT"

// Commands received from server
private const val CMD_PLAY = "player/cmd_play"
private const val CMD_PAUSE = "player/cmd_pause"
private const val CMD_NEXT = "player/cmd_next"
private const val CMD_VOLUME = "player/cmd_volume"
private const val CMD_REPLAY = "player/cmd_replay"

data class PlayerUiState(
    val connectionState: String = "disconnected",
    val events: List<SocketEvent> = emptyList(),
    val serverUrl: String = "",
    val token: String? = null,
    val username: String = "",
    // Player state
    val isPlaying: Boolean = false,
    val position: Double = 0.0,
    val currentQueueId: Int? = null,
    val currentItem: QueueItem? = null,
    val mediaUrl: String? = null,
    val queue: QueueState? = null,
    // Round-robin history (queueIds that have been played)
    val historyIds: List<Int> = emptyList(),
    // Waiting screen state (like Kodi addon)
    val pendingItem: QueueItem? = null,  // Item to play after NEXT (waiting for PLAY)
    val isAtQueueEnd: Boolean = false,
    // ExoPlayer state (for debug)
    val exoPlayerState: String = "IDLE",
    val exoPlayerError: String? = null,
    val exoPlayerVolume: Float = 1.0f,
    // Debug mode (default: show player, not debug)
    val showDebug: Boolean = false,
    // Room info
    val roomId: Int = -1
)

class PlayerViewModel(
    private val tokenStorage: TokenStorage
) : ViewModel() {

    private val socketManager = SocketManager()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var statusEmitJob: Job? = null

    init {
        // Load persisted history from storage
        val persistedHistory = tokenStorage.historyIds
        Log.d("PlayerViewModel", "Loaded persisted history: ${persistedHistory.size} items")

        _uiState.value = _uiState.value.copy(
            serverUrl = tokenStorage.serverUrl ?: "",
            token = tokenStorage.token,
            username = tokenStorage.username ?: "",
            historyIds = persistedHistory,
            roomId = tokenStorage.roomId
        )

        // Collect socket events
        viewModelScope.launch {
            socketManager.events.collect { event ->
                // Add to debug list (Chrome-style grouping for identical events)
                val currentEvents = _uiState.value.events.toMutableList()
                val lastEvent = currentEvents.firstOrNull()

                if (lastEvent != null && lastEvent.isSameAs(event)) {
                    // Same event type - increment counter instead of adding new entry
                    currentEvents[0] = lastEvent.copy(
                        count = lastEvent.count + 1,
                        timestamp = event.timestamp,
                        data = event.data  // Keep latest data
                    )
                } else {
                    // New event type - add to list
                    currentEvents.add(0, event)
                    if (currentEvents.size > 100) {
                        currentEvents.removeAt(currentEvents.lastIndex)
                    }
                }
                _uiState.value = _uiState.value.copy(events = currentEvents)

                // Parse relevant events
                if (event.type == "ACTION") {
                    parseAction(event.data)
                }
            }
        }

        // Collect connection state
        viewModelScope.launch {
            socketManager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)

                // Start/stop status emission based on connection state
                if (state == "connected") {
                    startStatusEmission()
                } else {
                    stopStatusEmission()
                }
            }
        }

        // Auto-connect
        connectSocket()
    }

    private fun parseAction(data: String) {
        try {
            val json = JSONObject(data)
            val type = json.optString("type", "").lowercase()

            when {
                // Player commands from server
                type == CMD_PLAY -> {
                    Log.d("PlayerViewModel", "CMD_PLAY received")
                    handlePlay()
                }
                type == CMD_PAUSE -> {
                    Log.d("PlayerViewModel", "CMD_PAUSE received")
                    handlePause()
                }
                type == CMD_NEXT -> {
                    Log.d("PlayerViewModel", "CMD_NEXT received")
                    handleNext()
                }
                type == CMD_VOLUME -> {
                    val volume = json.optDouble("payload", 1.0).toFloat().coerceIn(0f, 1f)
                    Log.d("PlayerViewModel", "CMD_VOLUME: $volume")
                    handleVolume(volume)
                }
                type == CMD_REPLAY -> {
                    val queueId = json.optInt("payload", -1)
                    if (queueId >= 0) {
                        Log.d("PlayerViewModel", "CMD_REPLAY: queueId=$queueId")
                        handleReplay(queueId)
                    }
                }
                // Queue updates
                type.contains("queue/push") || type.contains("queue_push") -> {
                    val queue = QueueState.fromJson(json)
                    updateQueue(queue)
                    Log.d("PlayerViewModel", "Queue updated: ${queue.result.size} items")
                }
                // Ignore PLAYER_STATUS - it's our own status echoed back
                type.contains("player_status") -> {
                    Log.d("PlayerViewModel", "PLAYER_STATUS received (ignored - it's our echo)")
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error parsing action: ${e.message}", e)
        }
    }

    /**
     * Handle play command - start playback
     */
    private fun handlePlay() {
        val state = _uiState.value

        // If an item is pending (after NEXT), play it
        if (state.pendingItem != null) {
            val item = state.pendingItem
            Log.d("PlayerViewModel", "Playing pending item: ${item.title}")
            _uiState.value = state.copy(
                currentQueueId = item.queueId,
                currentItem = item,
                mediaUrl = buildMediaUrl(item.mediaId),
                isPlaying = true,
                position = 0.0,
                pendingItem = null,
                isAtQueueEnd = false
            )
            return
        }

        // If we have a current item but no mediaUrl yet (waiting for PLAY), start it
        if (state.currentItem != null && state.mediaUrl == null) {
            Log.d("PlayerViewModel", "Starting playback with current item: ${state.currentItem.title}")
            _uiState.value = state.copy(
                mediaUrl = buildMediaUrl(state.currentItem.mediaId),
                isPlaying = true,
                position = 0.0,
                isAtQueueEnd = false
            )
            return
        }

        // If no current item but queue has items, start with first in round-robin order
        if (state.currentQueueId == null && state.queue != null && state.queue.result.isNotEmpty()) {
            val roundRobinOrder = getRoundRobinOrder(state)
            if (roundRobinOrder.isNotEmpty()) {
                val firstQueueId = roundRobinOrder.first()
                val firstItem = state.queue.entities[firstQueueId]
                if (firstItem != null) {
                    _uiState.value = state.copy(
                        currentQueueId = firstQueueId,
                        currentItem = firstItem,
                        mediaUrl = buildMediaUrl(firstItem.mediaId),
                        isPlaying = true,
                        position = 0.0,
                        isAtQueueEnd = false
                    )
                    Log.d("PlayerViewModel", "Starting playback with first item (round-robin): ${firstItem.title}")
                    return
                }
            }
        }
        // Otherwise just set playing (resume)
        _uiState.value = state.copy(isPlaying = true)
    }

    /**
     * Handle pause command
     */
    private fun handlePause() {
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    /**
     * Handle volume command from web player (0-1 float)
     */
    private fun handleVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(exoPlayerVolume = volume)
    }

    /**
     * Handle replay command - replay a specific queue item
     * Truncates history at the replayed item (like web player)
     */
    private fun handleReplay(queueId: Int) {
        val state = _uiState.value
        val queue = state.queue ?: return
        val item = queue.entities[queueId] ?: return

        // Truncate history: remove everything from the replayed item onward
        val historyIndex = state.historyIds.indexOf(queueId)
        val newHistory = if (historyIndex >= 0) {
            persistHistory(state.historyIds.take(historyIndex))
        } else {
            state.historyIds
        }

        Log.d("PlayerViewModel", "REPLAY: ${item.title} by ${item.userDisplayName}, history truncated to $newHistory")
        _uiState.value = state.copy(
            currentQueueId = queueId,
            currentItem = item,
            pendingItem = item,
            mediaUrl = null,
            isPlaying = false,
            isAtQueueEnd = false,
            historyIds = newHistory
        )
    }

    /**
     * Persist history to storage and return updated history list
     */
    private fun persistHistory(history: List<Int>): List<Int> {
        tokenStorage.historyIds = history
        Log.d("PlayerViewModel", "Persisted history: ${history.size} items - $history")
        return history
    }

    /**
     * Handle next command - skip to next item and show waiting screen
     * Like Kodi addon: stops playback, stores pending item, waits for PLAY
     */
    private fun handleNext() {
        val state = _uiState.value

        // Add current item to history (if not already there) and persist
        val newHistory = if (state.currentQueueId != null && !state.historyIds.contains(state.currentQueueId)) {
            persistHistory(state.historyIds + state.currentQueueId)
        } else {
            state.historyIds
        }

        // Update state with new history before getting next item
        val stateWithHistory = state.copy(historyIds = newHistory)

        // Get the next item (using updated history)
        val nextItem = getNextQueueItem(stateWithHistory)

        if (nextItem == null) {
            // End of queue
            Log.d("PlayerViewModel", "NEXT: End of queue")
            _uiState.value = stateWithHistory.copy(
                isPlaying = false,
                isAtQueueEnd = true,
                pendingItem = null,
                mediaUrl = null  // Stop video
            )
            return
        }

        // Store the pending item (will be played on next PLAY)
        Log.d("PlayerViewModel", "NEXT: Pending item - ${nextItem.userDisplayName} - ${nextItem.title}")
        _uiState.value = stateWithHistory.copy(
            // Advance queueId so the waiting screen shows the right info
            currentQueueId = nextItem.queueId,
            isPlaying = false,
            isAtQueueEnd = false,
            position = 0.0,
            pendingItem = nextItem,
            mediaUrl = null  // Stop video to show waiting screen
        )
    }

    /**
     * Calculate round-robin queue order (like KaraokeEternal web player)
     * Returns list of queueIds in fair play order: history first, then upcoming in round-robin order
     */
    private fun getRoundRobinOrder(state: PlayerUiState): List<Int> {
        val queue = state.queue ?: return emptyList()
        if (queue.result.isEmpty()) return emptyList()

        // Filter history to only include items still in queue
        val history = state.historyIds.filter { queue.result.contains(it) }.toMutableList()

        // Add current item to history if not already there
        state.currentQueueId?.let { curId ->
            if (queue.entities.containsKey(curId) && !history.contains(curId)) {
                history.add(curId)
            }
        }

        // Get upcoming items (not in history)
        val upcomingIds = queue.result.filter { !history.contains(it) }
        if (upcomingIds.isEmpty()) {
            return history
        }

        // Build round-robin order for upcoming items
        // Track which users have sung and in what order
        val resultByUser = history.mapNotNull { queueId ->
            queue.entities[queueId]?.userId
        }.toMutableList()

        // Group upcoming items by userId
        val itemsByUser = mutableMapOf<Int, MutableList<Int>>()
        for (queueId in upcomingIds) {
            val userId = queue.entities[queueId]?.userId ?: continue
            itemsByUser.getOrPut(userId) { mutableListOf() }.add(queueId)
        }

        // Round-robin: pick user with greatest "distance" since last song
        val upcoming = mutableListOf<Int>()
        while (itemsByUser.isNotEmpty()) {
            var maxDistance = -1
            var maxUserId: Int? = null

            for (userId in itemsByUser.keys) {
                val lastIndex = resultByUser.lastIndexOf(userId)
                val distance = if (lastIndex == -1) Int.MAX_VALUE else resultByUser.size - lastIndex

                if (distance > maxDistance) {
                    maxDistance = distance
                    maxUserId = userId
                }
            }

            if (maxUserId == null) break

            val userItems = itemsByUser[maxUserId]!!
            val queueId = userItems.removeAt(0)

            if (userItems.isEmpty()) {
                itemsByUser.remove(maxUserId)
            }

            resultByUser.add(maxUserId)
            upcoming.add(queueId)
        }

        Log.d("PlayerViewModel", "Round-robin order: history=${history.size}, upcoming=${upcoming.size}")
        return history + upcoming
    }

    /**
     * Get the next item in the queue after current (using round-robin order)
     */
    private fun getNextQueueItem(state: PlayerUiState): QueueItem? {
        val queue = state.queue ?: return null
        val roundRobinOrder = getRoundRobinOrder(state)
        if (roundRobinOrder.isEmpty()) return null

        val currentIndex = state.currentQueueId?.let { roundRobinOrder.indexOf(it) } ?: -1
        val nextIndex = currentIndex + 1

        if (nextIndex < roundRobinOrder.size) {
            val nextQueueId = roundRobinOrder[nextIndex]
            return queue.entities[nextQueueId]
        }
        return null
    }

    /**
     * Get the item to display on waiting screen
     * Priority: pendingItem > currentItem (if not playing) > nextItem
     */
    fun getWaitingScreenItem(): QueueItem? {
        val state = _uiState.value
        // End of queue: show "WAITING FOR A SINGER..."
        if (state.isAtQueueEnd) return null
        // If we have a pending item (after NEXT/playback ended), show that
        if (state.pendingItem != null) return state.pendingItem
        // If we haven't started playing yet (waiting for PLAY), show current item
        if (state.mediaUrl == null && state.currentItem != null) return state.currentItem
        // Otherwise show the next item (shouldn't happen in waiting screen context)
        return getNextQueueItem(state)
    }

    /**
     * Get the number of items remaining in queue after current (using round-robin order)
     */
    fun getQueueRemainingCount(): Int {
        val state = _uiState.value
        val roundRobinOrder = getRoundRobinOrder(state)
        if (roundRobinOrder.isEmpty()) return 0

        val currentIndex = state.currentQueueId?.let { roundRobinOrder.indexOf(it) } ?: -1
        return if (currentIndex >= 0) {
            roundRobinOrder.size - currentIndex - 1
        } else {
            roundRobinOrder.size
        }
    }

    private fun updateQueue(queue: QueueState) {
        val currentState = _uiState.value

        // Clean up history: remove queueIds that no longer exist in queue
        val cleanedHistory = currentState.historyIds.filter { queue.result.contains(it) }

        // Persist if history changed (items were removed from queue)
        if (cleanedHistory.size != currentState.historyIds.size) {
            persistHistory(cleanedHistory)
        }

        // If we have no current item but queue has items, prepare first item (but don't set mediaUrl yet)
        if (currentState.currentQueueId == null && queue.result.isNotEmpty()) {
            // Use round-robin order to get the first item
            val stateWithQueue = currentState.copy(queue = queue, historyIds = cleanedHistory)
            val roundRobinOrder = getRoundRobinOrder(stateWithQueue)
            if (roundRobinOrder.isNotEmpty()) {
                val firstQueueId = roundRobinOrder.first()
                val firstItem = queue.entities[firstQueueId]
                if (firstItem != null) {
                    // Don't set mediaUrl here - wait for PLAY command
                    // This ensures waiting screen is shown until playback starts
                    _uiState.value = stateWithQueue.copy(
                        currentQueueId = firstQueueId,
                        currentItem = firstItem
                        // mediaUrl stays null until handlePlay()
                    )
                    Log.d("PlayerViewModel", "Queue received, prepared first item (round-robin): ${firstItem.title}")
                    return
                }
            }
        }

        // Update queue and current item if it still exists
        val currentItem = queue.getCurrentItem(currentState.currentQueueId)
        // Only keep mediaUrl if we're currently playing (mediaUrl was already set)
        val mediaUrl = if (currentState.isPlaying && currentItem != null) {
            currentState.mediaUrl ?: buildMediaUrl(currentItem.mediaId)
        } else {
            currentState.mediaUrl
        }

        _uiState.value = currentState.copy(
            queue = queue,
            currentItem = currentItem,
            mediaUrl = mediaUrl,
            historyIds = cleanedHistory
        )
    }

    private fun buildMediaUrl(mediaId: Int): String {
        val baseUrl = tokenStorage.serverUrl?.let { url ->
            var normalized = url.trim()
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "http://$normalized"
            }
            if (!normalized.endsWith("/")) {
                normalized = "$normalized/"
            }
            normalized
        } ?: return ""

        // Use the standard media endpoint with type=video for MP4 files (requires admin user)
        // CDG files would use type=audio, but Android player focuses on MP4 videos
        return "${baseUrl}api/media/${mediaId}?type=video"
    }

    /**
     * Update ExoPlayer state (called from VideoPlayer)
     */
    fun updateExoPlayerState(state: String) {
        _uiState.value = _uiState.value.copy(exoPlayerState = state, exoPlayerError = null)
        Log.d("PlayerViewModel", "ExoPlayer state: $state")
    }

    /**
     * Update ExoPlayer error (called from VideoPlayer)
     */
    fun updateExoPlayerError(error: String) {
        _uiState.value = _uiState.value.copy(exoPlayerError = error)
        Log.e("PlayerViewModel", "ExoPlayer error: $error")
    }

    /**
     * Update ExoPlayer volume (called from VideoPlayer)
     */
    fun updateExoPlayerVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(exoPlayerVolume = volume)
    }

    fun connectSocket() {
        val serverUrl = tokenStorage.serverUrl
        val token = tokenStorage.token

        if (serverUrl != null) {
            socketManager.connect(serverUrl, token)
        }
    }

    fun disconnectSocket() {
        stopStatusEmission()
        emitPlayerLeave()
        socketManager.disconnect()
    }

    fun clearEvents() {
        _uiState.value = _uiState.value.copy(events = emptyList())
    }

    fun toggleDebug() {
        _uiState.value = _uiState.value.copy(showDebug = !_uiState.value.showDebug)
    }

    /**
     * Update player position from ExoPlayer
     */
    fun updatePosition(position: Double) {
        _uiState.value = _uiState.value.copy(position = position)
    }

    /**
     * Update playing state from ExoPlayer
     */
    fun updatePlayingState(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
    }

    /**
     * Called when playback ends naturally (video finished)
     * Immediately shows waiting screen, then sends NEXT to server.
     * Server will broadcast CMD_NEXT back, handled by handleNext().
     */
    fun onPlaybackEnded() {
        val state = _uiState.value
        Log.d("PlayerViewModel", "Playback ended - sending REQ_NEXT to server (currentQueueId=${state.currentQueueId})")

        // Immediately show waiting screen (don't wait for server round-trip)
        _uiState.value = state.copy(
            isPlaying = false,
            mediaUrl = null  // Stop video to show waiting screen
        )

        // Tell the server to advance to next song
        // Server will broadcast CMD_NEXT back to all players in the room
        socketManager.emit(PLAYER_REQ_NEXT)
    }

    /**
     * Start periodic status emission to server
     */
    private fun startStatusEmission() {
        statusEmitJob?.cancel()
        statusEmitJob = viewModelScope.launch {
            // Emit immediately on connect
            emitPlayerStatus()
            Log.d("PlayerViewModel", "Started status emission")

            // Then emit every 1 second
            while (isActive) {
                delay(1000)
                emitPlayerStatus()
            }
        }
    }

    /**
     * Stop status emission
     */
    private fun stopStatusEmission() {
        statusEmitJob?.cancel()
        statusEmitJob = null
        Log.d("PlayerViewModel", "Stopped status emission")
    }

    /**
     * Emit current player status to server
     * Includes historyJSON so clients can reorder queue display (round-robin)
     */
    private fun emitPlayerStatus() {
        val state = _uiState.value

        // Build historyJSON array (queueIds that have been played)
        val historyJsonArray = org.json.JSONArray()
        for (queueId in state.historyIds) {
            historyJsonArray.put(queueId)
        }

        // Find next user (different from current user) for "locking in"
        val nextUserId = getNextUserId(state)

        val payload = JSONObject().apply {
            put("queueId", state.currentQueueId ?: JSONObject.NULL)
            put("isPlaying", state.isPlaying)
            put("position", state.position)
            put("isAtQueueEnd", state.isAtQueueEnd)
            put("mediaType", state.currentItem?.mediaType ?: "mp4")
            put("volume", (state.exoPlayerVolume * 100).toInt())
            // historyJSON is sent as a JSON string (array of queueIds)
            put("historyJSON", historyJsonArray.toString())
            // nextUserId helps "lock in" the next singer (prevents reordering)
            put("nextUserId", nextUserId ?: JSONObject.NULL)
        }

        // Debug log every 10 seconds to avoid spam
        if (state.position.toInt() % 10 == 0) {
            Log.d("PlayerViewModel", "EMIT STATUS: historyIds=${state.historyIds}, historyJSON=${historyJsonArray}, queueId=${state.currentQueueId}")
        }

        socketManager.emit(PLAYER_EMIT_STATUS, payload)
    }

    /**
     * Find the next user who isn't the current user (for "locking in")
     */
    private fun getNextUserId(state: PlayerUiState): Int? {
        val queue = state.queue ?: return null
        val currentUserId = state.currentItem?.userId ?: return null
        val roundRobinOrder = getRoundRobinOrder(state)

        val currentIndex = state.currentQueueId?.let { roundRobinOrder.indexOf(it) } ?: -1

        // Look for next item with a different user
        for (i in (currentIndex + 1) until roundRobinOrder.size) {
            val queueId = roundRobinOrder[i]
            val item = queue.entities[queueId]
            if (item != null && item.userId != currentUserId) {
                return item.userId
            }
        }
        return null
    }

    /**
     * Emit player leave event before disconnecting
     */
    private fun emitPlayerLeave() {
        socketManager.emit(PLAYER_EMIT_LEAVE)
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusEmission()
        emitPlayerLeave()
        socketManager.disconnect()
    }
}
