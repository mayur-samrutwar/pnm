package com.pnm.mobileapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnm.mobileapp.crypto.Signer
import com.pnm.mobileapp.data.model.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.KeyPair

class AppViewModel(private val context: Context) : ViewModel() {
    private val signer = Signer(context)
    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet
    private var keyPair: KeyPair? = null

    fun generateWallet() {
        viewModelScope.launch {
            keyPair = signer.generateKeyPair()
            val publicKeyHex = signer.exportPublicKeyHex(keyPair)
            _wallet.value = Wallet(keyPair!!, publicKeyHex)
        }
    }

    suspend fun signVoucher(voucherJson: String): String {
        return signer.signVoucher(voucherJson, keyPair)
    }

    suspend fun getPublicKeyHex(): String {
        return signer.exportPublicKeyHex(keyPair)
    }
}

