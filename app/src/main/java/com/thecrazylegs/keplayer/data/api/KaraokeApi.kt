package com.thecrazylegs.keplayer.data.api

import com.thecrazylegs.keplayer.data.model.LoginRequest
import com.thecrazylegs.keplayer.data.model.UserResponse
import com.thecrazylegs.keplayer.data.model.RoomsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface KaraokeApi {

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<UserResponse>

    @GET("api/rooms")
    suspend fun getRooms(@Header("Cookie") cookie: String): RoomsResponse
}
