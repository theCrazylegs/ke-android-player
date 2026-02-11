package com.thecrazylegs.keplayer.data.model

data class PairCodeResponse(
    val pairId: String,
    val code: String
)

data class PairStatusResponse(
    val status: String,    // "pending" | "confirmed" | "expired"
    val token: String?     // JWT token when confirmed
)
