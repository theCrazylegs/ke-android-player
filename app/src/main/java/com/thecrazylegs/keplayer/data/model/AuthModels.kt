package com.thecrazylegs.keplayer.data.model

data class LoginRequest(
    val username: String,
    val password: String,
    val roomId: Int? = null
)

// Server returns user directly on successful login
data class UserResponse(
    val userId: Int,
    val username: String,
    val name: String,
    val isAdmin: Boolean,
    val isGuest: Boolean,
    val roomId: Int?,
    val dateCreated: Long?,
    val dateUpdated: Long?
)

data class Room(
    val roomId: Int,
    val name: String,
    val status: String
)

data class RoomsResponse(
    val rooms: List<Room>
)
