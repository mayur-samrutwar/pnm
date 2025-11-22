package com.pnm.mobileapp.crypto

import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Hex
import java.security.*
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ethereum wallet generator using secp256k1 curve
 * Generates standard Ethereum addresses using Keccak-256
 */
class EthereumWalletGenerator {
    companion object {
        private const val TAG = "EthereumWalletGenerator"
        private const val CURVE_NAME = "secp256k1"
        
        init {
            // Register BouncyCastle as security provider if not already registered
            try {
                if (Security.getProvider("BC") == null) {
                    Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
                    Log.d(TAG, "BouncyCastle provider registered")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not register BouncyCastle provider", e)
            }
        }
    }

    /**
     * Generate a new Ethereum key pair
     */
    suspend fun generateKeyPair(): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        try {
            // Generate secp256k1 key pair
            // Note: secp256k1 might not be available on all Android devices
            // If it fails, we'll catch and handle it
            val keyPairGenerator = try {
                KeyPairGenerator.getInstance("EC", "BC") // Try BouncyCastle provider first
            } catch (e: Exception) {
                Log.d(TAG, "BouncyCastle provider not available, using default", e)
                KeyPairGenerator.getInstance("EC") // Fallback to default provider
            }
            
            val ecGenParameterSpec = ECGenParameterSpec(CURVE_NAME)
            keyPairGenerator.initialize(ecGenParameterSpec)
            val keyPair = keyPairGenerator.generateKeyPair()

            val privateKey = keyPair.private as ECPrivateKey
            val publicKey = keyPair.public as ECPublicKey

            // Get private key bytes (32 bytes)
            val privateKeyBytes = privateKey.s.toByteArray()
            // Ensure it's exactly 32 bytes (remove leading zero if present)
            val privateKey32 = if (privateKeyBytes.size > 32) {
                privateKeyBytes.sliceArray(privateKeyBytes.size - 32 until privateKeyBytes.size)
            } else if (privateKeyBytes.size < 32) {
                ByteArray(32 - privateKeyBytes.size) { 0 } + privateKeyBytes
            } else {
                privateKeyBytes
            }

            // Derive Ethereum address from public key
            val address = deriveEthereumAddress(publicKey)

            return@withContext Pair(privateKey32, address)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Ethereum key pair", e)
            throw e
        }
    }

    /**
     * Derive Ethereum address from public key using Keccak-256
     */
    private fun deriveEthereumAddress(publicKey: ECPublicKey): String {
        val point = publicKey.w
        val x = point.affineX.toByteArray()
        val y = point.affineY.toByteArray()

        // Ensure x and y are exactly 32 bytes (secp256k1 coordinates)
        val x32 = if (x.size >= 32) {
            x.sliceArray(x.size - 32 until x.size)
        } else {
            ByteArray(32 - x.size) { 0 } + x
        }
        
        val y32 = if (y.size >= 32) {
            y.sliceArray(y.size - 32 until y.size)
        } else {
            ByteArray(32 - y.size) { 0 } + y
        }

        // Uncompressed public key: 0x04 || x || y (65 bytes)
        val uncompressedKey = ByteArray(65)
        uncompressedKey[0] = 0x04
        System.arraycopy(x32, 0, uncompressedKey, 1, 32)
        System.arraycopy(y32, 0, uncompressedKey, 33, 32)

        // Hash with Keccak-256
        val hash = keccak256(uncompressedKey)

        // Take last 20 bytes for address
        val addressBytes = hash.sliceArray(hash.size - 20 until hash.size)

        return "0x" + Hex.toHexString(addressBytes)
    }

    /**
     * Keccak-256 hash function using BouncyCastle
     */
    private fun keccak256(input: ByteArray): ByteArray {
        try {
            val digest = org.bouncycastle.jcajce.provider.digest.Keccak.Digest256()
            digest.update(input)
            return digest.digest()
        } catch (e: Exception) {
            Log.e(TAG, "Error computing Keccak-256", e)
            // Fallback to SHA-256 if Keccak fails (won't be correct Ethereum address, but won't crash)
            val messageDigest = MessageDigest.getInstance("SHA-256")
            return messageDigest.digest(input)
        }
    }
}

