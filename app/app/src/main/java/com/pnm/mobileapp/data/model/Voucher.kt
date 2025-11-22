package com.pnm.mobileapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * Voucher JSON structure for QR encoding
 */
data class Voucher(
    @SerializedName("slipId")
    val slipId: String,
    @SerializedName("payer")
    val payer: String, // userAddress/publicKey
    @SerializedName("amount")
    val amount: String,
    @SerializedName("cumulative")
    val cumulative: Long,
    @SerializedName("counter")
    val counter: Int,
    @SerializedName("publicKey")
    val publicKey: String? = null,
    @SerializedName("signature")
    val signature: String,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

