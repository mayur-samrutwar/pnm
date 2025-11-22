package com.pnm.mobileapp.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ECDSA P-256 Signer using Android Keystore (hardware-backed) or software fallback
 */
class Signer(private val context: Context) {
    companion object {
        private const val TAG = "Signer"
        private const val KEYSTORE_ALIAS = "pnm_voucher_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ALGORITHM = "EC"
        private const val CURVE_NAME = "secp256r1" // P-256
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Generate a key pair, using Android Keystore if available, otherwise software-backed
     */
    suspend fun generateKeyPair(): KeyPair = withContext(Dispatchers.IO) {
        try {
            // Try to use Android Keystore (hardware-backed if available)
            if (isKeyStoreAvailable()) {
                generateKeyStoreKeyPair()
            } else {
                // Fallback to software-backed key pair
                generateSoftwareKeyPair()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate key pair with Keystore, using software fallback", e)
            generateSoftwareKeyPair()
        }
    }

    private fun isKeyStoreAvailable(): Boolean {
        return try {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun generateKeyStoreKeyPair(): KeyPair {
        // Delete existing key if present
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_NAME))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        keyPairGenerator.initialize(keyGenParameterSpec)
        keyPairGenerator.generateKeyPair()

        // Retrieve the key pair from keystore
        val privateKeyEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.PrivateKeyEntry
        return KeyPair(privateKeyEntry.certificate.publicKey, privateKeyEntry.privateKey)
    }

    private fun generateSoftwareKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM)
        val ecGenParameterSpec = ECGenParameterSpec(CURVE_NAME)
        keyPairGenerator.initialize(ecGenParameterSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Sign a voucher JSON string and return the signature as hex
     * Returns raw r||s format (64 bytes = 128 hex chars)
     */
    suspend fun signVoucher(voucherJson: String, keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val keyPairToUse = keyPair ?: getOrGenerateKeyPair()
        val privateKey = keyPairToUse.private

        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(privateKey)
            update(voucherJson.toByteArray(Charsets.UTF_8))
        }.sign()

        // Convert DER-encoded signature to raw r||s format
        val rawSignature = derToRawSignature(signature)
        bytesToHex(rawSignature)
    }

    /**
     * Export public key as hex string in uncompressed format (0x04 || x || y)
     */
    suspend fun exportPublicKeyHex(keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val keyPairToUse = keyPair ?: getOrGenerateKeyPair()
        val publicKey = keyPairToUse.public as ECPublicKey
        
        val point = publicKey.w
        val x = point.affineX.toByteArray().let { bytes ->
            // Ensure 32 bytes (P-256 coordinate size)
            if (bytes.size >= 32) bytes.takeLast(32).toByteArray()
            else ByteArray(32 - bytes.size) { 0 } + bytes
        }
        val y = point.affineY.toByteArray().let { bytes ->
            if (bytes.size >= 32) bytes.takeLast(32).toByteArray()
            else ByteArray(32 - bytes.size) { 0 } + bytes
        }
        
        // Uncompressed format: 0x04 || x || y (65 bytes total)
        val uncompressed = byteArrayOf(0x04) + x + y
        bytesToHex(uncompressed)
    }

    private suspend fun getOrGenerateKeyPair(): KeyPair {
        return try {
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val privateKeyEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.PrivateKeyEntry
                KeyPair(privateKeyEntry.certificate.publicKey, privateKeyEntry.privateKey)
            } else {
                generateKeyPair()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve key from keystore, generating new", e)
            generateKeyPair()
        }
    }

    /**
     * Convert DER-encoded ECDSA signature to raw r||s format
     * DER format: SEQUENCE { INTEGER r, INTEGER s }
     */
    private fun derToRawSignature(derSignature: ByteArray): ByteArray {
        // Simple DER parser for ECDSA signature
        // DER structure: 0x30 [length] 0x02 [r_length] [r_bytes] 0x02 [s_length] [s_bytes]
        var offset = 0
        
        // Check SEQUENCE tag (0x30)
        if (derSignature[offset++] != 0x30.toByte()) {
            throw IllegalArgumentException("Invalid DER signature format")
        }
        
        // Skip length
        val seqLength = derSignature[offset++].toInt() and 0xFF
        offset += if (seqLength > 127) 1 else 0
        
        // Read r
        if (derSignature[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature format")
        }
        var rLength = derSignature[offset++].toInt() and 0xFF
        // Handle leading zero padding
        if (derSignature[offset] == 0x00.toByte() && rLength > 32) {
            offset++
            rLength--
        }
        val r = derSignature.sliceArray(offset until offset + 32)
        offset += rLength
        
        // Read s
        if (derSignature[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature format")
        }
        var sLength = derSignature[offset++].toInt() and 0xFF
        // Handle leading zero padding
        if (derSignature[offset] == 0x00.toByte() && sLength > 32) {
            offset++
            sLength--
        }
        val s = derSignature.sliceArray(offset until offset + 32)
        
        // Ensure both r and s are exactly 32 bytes
        val rPadded = if (r.size < 32) ByteArray(32 - r.size) { 0 } + r else r.takeLast(32).toByteArray()
        val sPadded = if (s.size < 32) ByteArray(32 - s.size) { 0 } + s else s.takeLast(32).toByteArray()
        
        return rPadded + sPadded // r||s format (64 bytes)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

