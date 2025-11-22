package com.pnm.mobileapp.refill

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.pnm.mobileapp.secure.MonotonicCounterManager
import com.pnm.mobileapp.secure.HardwareKeystoreManager
import com.pnm.mobileapp.data.api.HubApiService
import com.pnm.mobileapp.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.math.BigInteger

/**
 * Manages refill flow: request refill token from hub and reset counter
 */
class RefillFlow(
    private val context: Context,
    private val hubApiService: HubApiService,
    private val monotonicCounterManager: MonotonicCounterManager
) {
    companion object {
        private const val TAG = "RefillFlow"
        
        // Hub public key (hardcoded for dev - should be configurable in production)
        // This is the public key corresponding to the hub's private key used for signing refill tokens
        // Format: uncompressed EC P-256 public key (0x04 || x || y)
        private const val HUB_PUBLIC_KEY_HEX = "04" + // Replace with actual hub public key
            "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456" + // x coordinate
            "fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321" // y coordinate
        
        // For development, use a test key. In production, this should be:
        // 1. Fetched from hub's well-known endpoint
        // 2. Or embedded in app during build from secure source
    }

    /**
     * Request refill from hub server
     * @param userAddress User's wallet address
     * @param proof Optional proof (e.g., attestation certificate)
     * @return true if refill was successful and counter was reset
     */
    suspend fun requestRefill(
        userAddress: String,
        proof: String? = null
    ): RefillResult = withContext(Dispatchers.IO) {
        try {
            // Call hub API
            val response = hubApiService.requestRefill(
                RefillRequest(
                    userAddress = userAddress,
                    proof = proof
                )
            )

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Refill request failed: $errorMsg")
                return@withContext RefillResult(
                    success = false,
                    message = "Refill request failed: $errorMsg"
                )
            }

            val refillResponse = response.body()!!
            val refillToken = refillResponse.refillToken

            // Verify hub signature
            val isValid = verifyRefillToken(refillToken, userAddress)
            if (!isValid) {
                Log.e(TAG, "Refill token signature verification failed")
                return@withContext RefillResult(
                    success = false,
                    message = "Invalid refill token signature"
                )
            }

            // Parse refill token
            val tokenData = parseRefillToken(refillToken)
            if (tokenData == null) {
                Log.e(TAG, "Failed to parse refill token")
                return@withContext RefillResult(
                    success = false,
                    message = "Failed to parse refill token"
                )
            }

            // Check expiry
            if (tokenData.expiry < System.currentTimeMillis() / 1000) {
                Log.e(TAG, "Refill token expired")
                return@withContext RefillResult(
                    success = false,
                    message = "Refill token expired"
                )
            }

            // Reset counter with new limit
            val resetSuccess = monotonicCounterManager.reset(
                cumulative = 0L,
                counter = 0,
                newLimit = tokenData.newLimit
            )

            if (!resetSuccess) {
                Log.e(TAG, "Failed to reset counter")
                return@withContext RefillResult(
                    success = false,
                    message = "Failed to reset counter"
                )
            }

            Log.d(TAG, "Refill successful: new limit = ${tokenData.newLimit}")
            RefillResult(
                success = true,
                message = "Refill successful",
                newLimit = tokenData.newLimit
            )
        } catch (e: Exception) {
            Log.e(TAG, "Refill request error: ${e.message}", e)
            RefillResult(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Verify refill token signature using hub's public key
     */
    private fun verifyRefillToken(token: String, userAddress: String): Boolean {
        return try {
            // Decode JWT-like token (format: header.payload.signature)
            val parts = token.split(".")
            if (parts.size != 3) {
                Log.e(TAG, "Invalid token format")
                return false
            }

            val payload = parts[1]
            val signature = Base64.decode(parts[2], Base64.URL_SAFE or Base64.NO_WRAP)

            // Verify signature
            val message = "${parts[0]}.${parts[1]}".toByteArray()
            val publicKey = getHubPublicKey() ?: return false

            val sig = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(message)
            }

            sig.verify(signature)
        } catch (e: Exception) {
            Log.e(TAG, "Token verification error: ${e.message}", e)
            false
        }
    }

    /**
     * Parse refill token payload
     */
    private fun parseRefillToken(token: String): RefillTokenData? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payloadJson = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP))
            Gson().fromJson(payloadJson, RefillTokenData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse token: ${e.message}", e)
            null
        }
    }

    /**
     * Get hub public key from hardcoded hex string
     */
    private fun getHubPublicKey(): PublicKey? {
        return try {
            val keyBytes = hexStringToByteArray(HUB_PUBLIC_KEY_HEX)
            if (keyBytes.size != 65 || keyBytes[0] != 0x04.toByte()) {
                Log.e(TAG, "Invalid public key format")
                return null
            }

            val x = BigInteger(1, keyBytes.sliceArray(1..32))
            val y = BigInteger(1, keyBytes.sliceArray(33..64))

            val ecPoint = ECPoint(x, y)
            val ecParameterSpec = java.security.spec.ECParameterSpec(
                java.security.spec.EllipticCurve(
                    java.security.spec.ECFieldFp(BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)),
                    BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16),
                    BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16)
                ),
                java.security.spec.ECPoint(
                    BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16),
                    BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16)
                ),
                BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16),
                1
            )

            val keySpec = ECPublicKeySpec(ecPoint, ecParameterSpec)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(keySpec) as ECPublicKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create public key: ${e.message}", e)
            null
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Refill request data
     */
    data class RefillRequest(
        val userAddress: String,
        val proof: String?
    )

    /**
     * Refill token payload
     */
    data class RefillTokenData(
        val user: String,
        val newLimit: Long,
        val expiry: Long, // Unix timestamp
        val nonce: String
    )

    /**
     * Refill result
     */
    data class RefillResult(
        val success: Boolean,
        val message: String,
        val newLimit: Long? = null
    )
}

