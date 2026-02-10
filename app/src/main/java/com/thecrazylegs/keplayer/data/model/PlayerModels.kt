package com.thecrazylegs.keplayer.data.model

import org.json.JSONObject

data class PlayerStatus(
    val isPlaying: Boolean,
    val position: Double,
    val queueId: Int?,
    val mediaType: String?,
    val volume: Double,
    val isAtQueueEnd: Boolean,
    val errorMessage: String?
) {
    companion object {
        fun fromJson(json: JSONObject): PlayerStatus {
            val payload = if (json.has("payload")) json.getJSONObject("payload") else json
            return PlayerStatus(
                isPlaying = payload.optBoolean("isPlaying", false),
                position = payload.optDouble("position", 0.0),
                queueId = if (payload.has("queueId") && !payload.isNull("queueId"))
                    payload.getInt("queueId") else null,
                mediaType = payload.optString("mediaType", null),
                volume = payload.optDouble("volume", 1.0),
                isAtQueueEnd = payload.optBoolean("isAtQueueEnd", false),
                errorMessage = payload.optString("errorMessage", null)
            )
        }
    }
}

data class QueueItem(
    val queueId: Int,
    val userId: Int,
    val userDisplayName: String,
    val title: String,
    val artist: String,
    val mediaId: Int,
    val mediaType: String,
    val coSingers: List<String> = emptyList()
)

data class QueueState(
    val result: List<Int>,
    val entities: Map<Int, QueueItem>
) {
    companion object {
        fun fromJson(json: JSONObject): QueueState {
            val payload = if (json.has("payload")) json.getJSONObject("payload") else json

            val resultArray = payload.optJSONArray("result") ?: return QueueState(emptyList(), emptyMap())
            val result = (0 until resultArray.length()).map { resultArray.getInt(it) }

            val entitiesObj = payload.optJSONObject("entities") ?: return QueueState(result, emptyMap())
            val entities = mutableMapOf<Int, QueueItem>()

            for (key in entitiesObj.keys()) {
                val itemJson = entitiesObj.getJSONObject(key)
                val queueId = key.toIntOrNull() ?: continue

                entities[queueId] = QueueItem(
                    queueId = queueId,
                    userId = itemJson.optInt("userId", -1),
                    userDisplayName = itemJson.optString("userDisplayName", ""),
                    title = itemJson.optString("title", "Unknown"),
                    artist = itemJson.optString("artist", "Unknown"),
                    mediaId = itemJson.optInt("mediaId", -1),
                    mediaType = itemJson.optString("mediaType", "video/mp4"),
                    coSingers = parseCoSingers(itemJson)
                )
            }

            return QueueState(result, entities)
        }

        private fun parseCoSingers(json: JSONObject): List<String> {
            val coSingersArray = json.optJSONArray("coSingers") ?: return emptyList()
            return (0 until coSingersArray.length()).map { coSingersArray.getString(it) }
        }
    }

    fun getCurrentItem(queueId: Int?): QueueItem? {
        if (queueId == null) return null
        return entities[queueId]
    }

    fun getOrderedItems(): List<QueueItem> {
        return result.mapNotNull { entities[it] }
    }
}
