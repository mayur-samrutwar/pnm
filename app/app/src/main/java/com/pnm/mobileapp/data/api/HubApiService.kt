package com.pnm.mobileapp.data.api

import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.refill.RefillFlow
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface HubApiService {
    @POST("/api/v1/validate")
    suspend fun validateSlip(@Body slip: Slip): Response<ValidateResponse>

    @POST("/api/v1/redeem")
    suspend fun redeemSlip(@Body slip: Slip): Response<RedeemResponse>

    @POST("/api/v1/requestRefill")
    suspend fun requestRefill(@Body request: RefillFlow.RefillRequest): Response<RefillResponse>
}

data class ValidateResponse(
    val valid: Boolean,
    val message: String
)

data class RedeemResponse(
    val success: Boolean,
    val message: String,
    val transactionHash: String? = null
)

data class RefillResponse(
    val success: Boolean,
    val message: String,
    val refillToken: String? = null
)

