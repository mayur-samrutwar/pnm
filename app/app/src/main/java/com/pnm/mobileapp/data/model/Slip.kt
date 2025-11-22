package com.pnm.mobileapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "pending_slips")
data class Slip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @SerializedName("amount")
    val amount: String,
    @SerializedName("userAddress")
    val userAddress: String,
    @SerializedName("publicKey")
    val publicKey: String? = null,
    @SerializedName("signature")
    val signature: String,
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

