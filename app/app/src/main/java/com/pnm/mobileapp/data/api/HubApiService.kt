package com.pnm.mobileapp.data.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.data.model.Voucher
import com.pnm.mobileapp.refill.RefillFlow
import com.pnm.mobileapp.util.Constants
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HubApiService {
    @POST("/api/v1/validate")
    suspend fun validateSlip(@Body request: VoucherRequest): Response<ValidateResponse>

    @POST("/api/v1/redeem")
    suspend fun redeemSlip(@Body request: VoucherRequest): Response<RedeemResponse>

    @POST("/api/v1/requestRefill")
    suspend fun requestRefill(@Body request: RefillFlow.RefillRequest): Response<RefillResponse>

    @GET("/api/v1/balance/{address}")
    suspend fun getBalance(
        @Path("address") address: String,
        @Query("chainId") chainId: Int? = null
    ): Response<BalanceResponse>

    @GET("/api/v1/vaultBalance/{address}")
    suspend fun getVaultBalance(
        @Path("address") address: String,
        @Query("chainId") chainId: Int? = null
    ): Response<BalanceResponse>

    @POST("/api/v1/deposit")
    suspend fun deposit(@Body request: DepositRequest): Response<DepositResponse>
}

/**
 * Request wrapper that matches hub server's expected format
 * Hub expects: { "voucher": { ... }, "preferredChainId": <optional> }
 */
data class VoucherRequest(
    @SerializedName("voucher")
    val voucher: HubVoucher,
    @SerializedName("preferredChainId")
    val preferredChainId: Int? = null // Optional: merchant's preferred chain for redemption
)

/**
 * Voucher format expected by hub server
 * Note: publicKey is optional for P-256 signature verification
 */
data class HubVoucher(
    @SerializedName("payerAddress")
    val payerAddress: String,
    @SerializedName("payeeAddress")
    val payeeAddress: String,
    @SerializedName("amount")
    val amount: Long, // Number, not string
    @SerializedName("chainId")
    val chainId: Int? = null, // Optional: for chain-agnostic vouchers (not in signature)
    @SerializedName("cumulative")
    val cumulative: Long,
    @SerializedName("counter")
    val counter: Int,
    @SerializedName("expiry")
    val expiry: Long, // Unix timestamp in seconds
    @SerializedName("slipId")
    val slipId: String,
    @SerializedName("contractAddress")
    val contractAddress: String? = null, // Optional: for chain-agnostic vouchers (not in signature)
    @SerializedName("signature")
    val signature: String,
    @SerializedName("publicKey")
    val publicKey: String? = null, // P-256 public key for signature verification
    @SerializedName("originalVoucherJson")
    val originalVoucherJson: String? = null // Original JSON that was signed (for P-256 verification)
)

/**
 * Ensure address is in valid Ethereum format (0x followed by 40 hex chars)
 */
private fun String.ensureEthAddressFormat(): String {
    var addr = this.trim()
    // Remove 0x prefix if present
    if (addr.startsWith("0x")) {
        addr = addr.substring(2)
    }
    // Pad or truncate to 40 hex characters
    addr = addr.lowercase().take(40).padStart(40, '0')
    return "0x$addr"
}

/**
 * Ensure signature has 0x prefix (required by hub schema)
 */
private fun String.ensureSignatureFormat(): String {
    val sig = this.trim()
    return if (sig.startsWith("0x")) {
        sig
    } else {
        "0x$sig"
    }
}

/**
 * Ensure public key has 0x prefix and is 130 hex chars (65 bytes: 0x04 + x + y)
 */
private fun String?.ensurePublicKeyFormat(): String? {
    if (this == null) return null
    var key = this.trim()
    // Remove 0x if present
    if (key.startsWith("0x")) {
        key = key.substring(2)
    }
    // Ensure it's exactly 130 hex chars (65 bytes)
    if (key.length != 130) {
        // If too short, pad with zeros; if too long, truncate
        key = key.take(130).padStart(130, '0')
    }
    return "0x$key"
}

/**
 * Extension function to convert Slip to HubVoucher
 * @param merchantAddress The merchant's Ethereum address (payee)
 * @param payerEthAddress Optional payer's Ethereum address to use if Slip.ethAddress is empty (for old vouchers)
 */
fun Slip.toHubVoucher(
    merchantAddress: String = "0x0000000000000000000000000000000000000000",
    payerEthAddress: String? = null
): HubVoucher {
    // Use rawJson if available, otherwise construct from Slip fields
    return if (rawJson.isNotEmpty()) {
        try {
            val gson = Gson()
            val voucher = gson.fromJson(rawJson, Voucher::class.java)
            // Convert mobile app Voucher to hub HubVoucher
            // Reconstruct the original JSON that was signed (without signature field)
            val originalVoucher = Voucher(
                slipId = voucher.slipId,
                payer = voucher.payer,
                ethAddress = voucher.ethAddress, // Include ethAddress in original voucher for P-256 verification
                amount = voucher.amount,
                cumulative = voucher.cumulative,
                counter = voucher.counter,
                publicKey = voucher.publicKey,
                signature = "", // Empty signature - this is what was signed
                timestamp = voucher.timestamp
            )
            val originalJson = gson.toJson(originalVoucher)
            
            // Use Ethereum address from Slip, or provided payerEthAddress, or fallback to voucher.payer (device address)
            val finalPayerEthAddress = when {
                this.ethAddress.isNotEmpty() -> {
                    android.util.Log.d("HubApiService", "toHubVoucher: Using Slip.ethAddress=${this.ethAddress}")
                    this.ethAddress
                }
                payerEthAddress != null && payerEthAddress.isNotEmpty() -> {
                    android.util.Log.d("HubApiService", "toHubVoucher: Using provided payerEthAddress=$payerEthAddress")
                    payerEthAddress
                }
                else -> {
                    android.util.Log.w("HubApiService", "toHubVoucher: WARNING - No Ethereum address found! Using device address voucher.payer=${voucher.payer} (this will likely fail on-chain redemption)")
                    voucher.payer
                }
            }
            android.util.Log.d("HubApiService", "toHubVoucher: Slip.ethAddress=${this.ethAddress}, provided payerEthAddress=$payerEthAddress, voucher.payer=${voucher.payer}, using finalPayerEthAddress=$finalPayerEthAddress")
            
            android.util.Log.d("HubApiService", "toHubVoucher: Creating HubVoucher with payerAddress=$finalPayerEthAddress, payeeAddress=$merchantAddress")
            
            HubVoucher(
                payerAddress = finalPayerEthAddress.ensureEthAddressFormat(), // Use Ethereum address if available
                payeeAddress = merchantAddress.ensureEthAddressFormat(),
                amount = voucher.amount.toLongOrNull() ?: 0L,
                chainId = null, // Chain-agnostic: not included in signature
                cumulative = voucher.cumulative,
                counter = voucher.counter,
                expiry = (voucher.timestamp / 1000) + (30 * 24 * 60 * 60), // 30 days from timestamp
                slipId = voucher.slipId,
                contractAddress = null, // Chain-agnostic: not included in signature
                signature = voucher.signature.ensureSignatureFormat(),
                publicKey = voucher.publicKey?.ensurePublicKeyFormat(),
                originalVoucherJson = originalJson // Use reconstructed original JSON (without signature)
            )
        } catch (e: Exception) {
            // Fallback: construct from Slip fields
            createHubVoucherFromSlip(merchantAddress, payerEthAddress)
        }
    } else {
        createHubVoucherFromSlip(merchantAddress, payerEthAddress)
    }
}

private fun Slip.createHubVoucherFromSlip(merchantAddress: String, payerEthAddress: String? = null): HubVoucher {
    fun String.ensureEthAddressFormat(): String {
        var addr = this.trim()
        if (addr.startsWith("0x")) {
            addr = addr.substring(2)
        }
        addr = addr.lowercase().take(40).padStart(40, '0')
        return "0x$addr"
    }
    
    fun String.ensureSignatureFormat(): String {
        val sig = this.trim()
        return if (sig.startsWith("0x")) {
            sig
        } else {
            "0x$sig"
        }
    }
    
    // Prefer Ethereum address from Slip, or provided payerEthAddress, or fallback to payer/userAddress
    val finalPayerEthAddress = when {
        ethAddress.isNotEmpty() -> ethAddress
        payerEthAddress != null && payerEthAddress.isNotEmpty() -> payerEthAddress
        else -> payer.ifEmpty { userAddress }
    }
    android.util.Log.d("HubApiService", "createHubVoucherFromSlip: ethAddress=$ethAddress, provided payerEthAddress=$payerEthAddress, payer=$payer, userAddress=$userAddress, using finalPayerEthAddress=$finalPayerEthAddress")
    
    return HubVoucher(
        payerAddress = finalPayerEthAddress.ensureEthAddressFormat(), // Prefer Ethereum address
        payeeAddress = merchantAddress.ensureEthAddressFormat(),
        amount = amount.toLongOrNull() ?: 0L,
        chainId = null, // Chain-agnostic: not included in signature
        cumulative = cumulative,
        counter = counter,
        expiry = (timestamp / 1000) + (30 * 24 * 60 * 60), // 30 days from timestamp
        slipId = slipId,
        contractAddress = null, // Chain-agnostic: not included in signature
        signature = signature.ensureSignatureFormat(),
        publicKey = publicKey?.ensurePublicKeyFormat(),
        originalVoucherJson = rawJson // Include original JSON for P-256 verification
    )
}

data class ValidateResponse(
    val valid: Boolean,
    val message: String
)

data class RedeemResponse(
    val status: String, // "redeemed", "validated", or "error"
    val message: String? = null,
    val txHash: String? = null
)

data class RefillResponse(
    val success: Boolean,
    val message: String,
    val refillToken: String? = null
)

data class BalanceResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("balance")
    val balance: String,
    @SerializedName("balanceFormatted")
    val balanceFormatted: String,
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("tokenAddress")
    val tokenAddress: String? = null
)

data class DepositRequest(
    @SerializedName("userAddress")
    val userAddress: String,
    @SerializedName("amount")
    val amount: String,
    @SerializedName("signedApproveTx")
    val signedApproveTx: String,
    @SerializedName("signedDepositTx")
    val signedDepositTx: String,
    @SerializedName("chainId")
    val chainId: Int? = null // Optional: chain ID for multichain support
)

data class DepositResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("approveTxHash")
    val approveTxHash: String? = null,
    @SerializedName("depositTxHash")
    val depositTxHash: String? = null
)

