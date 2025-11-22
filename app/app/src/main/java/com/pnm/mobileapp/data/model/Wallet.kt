package com.pnm.mobileapp.data.model

import java.security.KeyPair

data class Wallet(
    val keyPair: KeyPair, // P-256 key pair for voucher signing
    val address: String, // Device address (derived from P-256)
    val ethAddress: String, // Ethereum address (derived from secp256k1)
    val ethPrivateKey: ByteArray? = null // Ethereum private key (stored securely)
)

