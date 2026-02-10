package com.thecrazylegs.keplayer.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ke_player_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_ROOM_ID = "room_id"
        private const val KEY_HISTORY_IDS = "history_ids"
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var userId: Int
        get() = prefs.getInt(KEY_USER_ID, -1)
        set(value) = prefs.edit().putInt(KEY_USER_ID, value).apply()

    var isAdmin: Boolean
        get() = prefs.getBoolean(KEY_IS_ADMIN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ADMIN, value).apply()

    var roomId: Int
        get() = prefs.getInt(KEY_ROOM_ID, -1)
        set(value) = prefs.edit().putInt(KEY_ROOM_ID, value).apply()

    /**
     * Persisted history of played queueIds (for round-robin)
     * Stored as JSON array string: "[1, 2, 3]"
     */
    var historyIds: List<Int>
        get() {
            val json = prefs.getString(KEY_HISTORY_IDS, "[]") ?: "[]"
            return try {
                val jsonArray = org.json.JSONArray(json)
                (0 until jsonArray.length()).map { jsonArray.getInt(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val jsonArray = org.json.JSONArray()
            value.forEach { jsonArray.put(it) }
            prefs.edit().putString(KEY_HISTORY_IDS, jsonArray.toString()).apply()
        }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY_IDS).apply()
    }

    fun isLoggedIn(): Boolean = token != null && serverUrl != null

    fun clear() {
        prefs.edit().clear().apply()
    }
}
