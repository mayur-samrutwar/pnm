package com.pnm.mobileapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "pending_slips")
data class Slip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val slipId: String = "",
    val payer: String = "", // userAddress/publicKey (device address)
    @SerializedName("amount")
    val amount: String,
    @SerializedName("userAddress")
    val userAddress: String = "", // Keep for backward compatibility (device address)
    val ethAddress: String = "", // User's Ethereum address (where deposits are)
    val cumulative: Long = 0L,
    val counter: Int = 0,
    @SerializedName("publicKey")
    val publicKey: String? = null,
    @SerializedName("signature")
    val signature: String,
    val rawJson: String = "", // Original voucher JSON
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    val status: SlipStatus = SlipStatus.PENDING
)

enum class SlipStatus {
    PENDING,
    VALIDATED,
    REDEEMED,
    REJECTED
}

