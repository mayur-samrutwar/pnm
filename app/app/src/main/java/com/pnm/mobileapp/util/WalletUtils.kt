package com.pnm.mobileapp.util

import com.pnm.mobileapp.data.model.Wallet
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

object WalletUtils {
    fun generateWallet(): Wallet {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecGenParameterSpec = ECGenParameterSpec("secp256r1") // P-256
        keyPairGenerator.initialize(ecGenParameterSpec)
        val keyPair: KeyPair = keyPairGenerator.generateKeyPair()
        
        val publicKey = keyPair.public as ECPublicKey
        val address = publicKeyToAddress(publicKey)
        
        // Note: This utility doesn't generate ETH wallet
        // For full wallet generation, use AppViewModel.generateWallet()
        return Wallet(keyPair, address, "", null)
    }
    
    private fun publicKeyToAddress(publicKey: ECPublicKey): String {
        val encoded = publicKey.encoded
        return encoded.joinToString("") { "%02x".format(it) }
    }
    
    fun signData(data: ByteArray, @Suppress("UNUSED_PARAMETER") privateKey: java.security.PrivateKey): String {
        // Placeholder for actual signing implementation
        // In production, use proper ECDSA signing
        return "signature_placeholder_${data.contentHashCode()}"
    }
}

