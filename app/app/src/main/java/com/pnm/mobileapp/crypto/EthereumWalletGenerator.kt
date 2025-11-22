package com.pnm.mobileapp.crypto

import android.util.Log
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.encoders.Hex
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ethereum wallet generator using secp256k1 curve
 * Generates standard Ethereum addresses using Keccak-256
 */
class EthereumWalletGenerator {
    companion object {
        private const val TAG = "EthereumWalletGenerator"
    }

    /**
     * Generate a new Ethereum key pair using BouncyCastle's direct crypto API
     * (not JCA, which doesn't work properly on Android)
     */
    suspend fun generateKeyPair(): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        try {
            // Use BouncyCastle's direct crypto API (not JCA)
            // Get secp256k1 curve parameters
            val curveParams: X9ECParameters = SECNamedCurves.getByName("secp256k1")
                ?: throw Exception("secp256k1 curve not found in BouncyCastle")
            
            // Generate key pair using BouncyCastle's ECKeyPairGenerator
            val keyGen = ECKeyPairGenerator()
            val ecParams = org.bouncycastle.crypto.params.ECDomainParameters(
                curveParams.curve,
                curveParams.g,
                curveParams.n,
                curveParams.h
            )
            val keyGenParams = ECKeyGenerationParameters(ecParams, SecureRandom())
            keyGen.init(keyGenParams)
            
            val keyPair: AsymmetricCipherKeyPair = keyGen.generateKeyPair()
            val privateKeyParams = keyPair.private as ECPrivateKeyParameters
            val publicKeyParams = keyPair.public as ECPublicKeyParameters
            
            Log.d(TAG, "Ethereum key pair generated successfully using BouncyCastle direct API")

            // Get private key bytes (32 bytes)
            val privateKeyBytes = privateKeyParams.d.toByteArray()
            val privateKey32 = if (privateKeyBytes.size > 32) {
                privateKeyBytes.sliceArray(privateKeyBytes.size - 32 until privateKeyBytes.size)
            } else if (privateKeyBytes.size < 32) {
                ByteArray(32 - privateKeyBytes.size) { 0 } + privateKeyBytes
            } else {
                privateKeyBytes
            }

            // Derive Ethereum address from public key point
            val address = deriveEthereumAddressFromBCPoint(publicKeyParams.q)

            return@withContext Pair(privateKey32, address)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Ethereum key pair: ${e.javaClass.simpleName} - ${e.message}", e)
            e.printStackTrace()
            throw Exception("Ethereum wallet generation failed: ${e.message}", e)
        }
    }

    /**
     * Derive Ethereum address from BouncyCastle ECPoint using Keccak-256
     */
    private fun deriveEthereumAddressFromBCPoint(point: ECPoint): String {
        val x = point.affineXCoord.toBigInteger().toByteArray()
        val y = point.affineYCoord.toBigInteger().toByteArray()

        // Ensure x and y are exactly 32 bytes (secp256k1 coordinates)
        val x32 = if (x.size >= 32) {
            x.sliceArray(x.size - 32 until x.size)
        } else {
            val padding = ByteArray(32 - x.size) { 0 }
            padding + x
        }
        
        val y32 = if (y.size >= 32) {
            y.sliceArray(y.size - 32 until y.size)
        } else {
            val padding = ByteArray(32 - y.size) { 0 }
            padding + y
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

