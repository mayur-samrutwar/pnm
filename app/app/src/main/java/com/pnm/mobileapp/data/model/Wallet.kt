package com.pnm.mobileapp.data.model

import java.security.KeyPair
import java.security.PublicKey

data class Wallet(
    val keyPair: KeyPair,
    val address: String // Hex representation of public key
)

