package com.thecrazylegs.keplayer.data.api

import com.thecrazylegs.keplayer.data.model.LoginRequest
import com.thecrazylegs.keplayer.data.model.PairCodeResponse
import com.thecrazylegs.keplayer.data.model.PairStatusResponse
import com.thecrazylegs.keplayer.data.model.UserResponse
import com.thecrazylegs.keplayer.data.model.RoomsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface KaraokeApi {

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<UserResponse>

    @GET("api/rooms")
    suspend fun getRooms(@Header("Cookie") cookie: String): RoomsResponse

    @POST("api/pair/code")
    suspend fun requestPairCode(): PairCodeResponse

    @GET("api/pair/status/{pairId}")
    suspend fun getPairStatus(@Path("pairId") pairId: String): PairStatusResponse
}
