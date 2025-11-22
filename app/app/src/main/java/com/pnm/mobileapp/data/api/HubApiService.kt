package com.pnm.mobileapp.data.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.data.model.Voucher
import com.pnm.mobileapp.refill.RefillFlow
import com.pnm.mobileapp.util.Constants
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface HubApiService {
    @POST("/api/v1/validate")
    suspend fun validateSlip(@Body request: VoucherRequest): Response<ValidateResponse>

    @POST("/api/v1/redeem")
    suspend fun redeemSlip(@Body request: VoucherRequest): Response<RedeemResponse>

    @POST("/api/v1/requestRefill")
    suspend fun requestRefill(@Body request: RefillFlow.RefillRequest): Response<RefillResponse>
}

/**
 * Request wrapper that matches hub server's expected format
 * Hub expects: { "voucher": { ... } }
 */
data class VoucherRequest(
    @SerializedName("voucher")
    val voucher: HubVoucher
)

/**
 * Voucher format expected by hub server
 */
data class HubVoucher(
    @SerializedName("payerAddress")
    val payerAddress: String,
    @SerializedName("payeeAddress")
    val payeeAddress: String,
    @SerializedName("amount")
    val amount: Long, // Number, not string
    @SerializedName("chainId")
    val chainId: Int = 1,
    @SerializedName("cumulative")
    val cumulative: Long,
    @SerializedName("counter")
    val counter: Int,
    @SerializedName("expiry")
    val expiry: Long, // Unix timestamp in seconds
    @SerializedName("slipId")
    val slipId: String,
    @SerializedName("contractAddress")
    val contractAddress: String,
    @SerializedName("signature")
    val signature: String,
    @SerializedName("publicKey")
    val publicKey: String? = null
)

/**
 * Extension function to convert Slip to HubVoucher
 */
fun Slip.toHubVoucher(merchantAddress: String = "0x0000000000000000000000000000000000000000"): HubVoucher {
    // Use rawJson if available, otherwise construct from Slip fields
    return if (rawJson.isNotEmpty()) {
        try {
            val gson = Gson()
            val voucher = gson.fromJson(rawJson, Voucher::class.java)
            // Convert mobile app Voucher to hub HubVoucher
            HubVoucher(
                payerAddress = voucher.payer,
                payeeAddress = merchantAddress, // Merchant address (could be passed as parameter)
                amount = voucher.amount.toLongOrNull() ?: 0L,
                chainId = 1,
                cumulative = voucher.cumulative,
                counter = voucher.counter,
                expiry = (voucher.timestamp / 1000) + (30 * 24 * 60 * 60), // 30 days from timestamp
                slipId = voucher.slipId,
                contractAddress = Constants.VAULT_CONTRACT_ADDRESS,
                signature = voucher.signature,
                publicKey = voucher.publicKey
            )
        } catch (e: Exception) {
            // Fallback: construct from Slip fields
            createHubVoucherFromSlip(merchantAddress)
        }
    } else {
        createHubVoucherFromSlip(merchantAddress)
    }
}

private fun Slip.createHubVoucherFromSlip(merchantAddress: String): HubVoucher {
    return HubVoucher(
        payerAddress = payer.ifEmpty { userAddress },
        payeeAddress = merchantAddress,
        amount = amount.toLongOrNull() ?: 0L,
        chainId = 1,
        cumulative = cumulative,
        counter = counter,
        expiry = (timestamp / 1000) + (30 * 24 * 60 * 60), // 30 days from timestamp
        slipId = slipId,
        contractAddress = Constants.VAULT_CONTRACT_ADDRESS,
        signature = signature,
        publicKey = publicKey
    )
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

