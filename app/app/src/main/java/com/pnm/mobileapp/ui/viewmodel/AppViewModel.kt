package com.pnm.mobileapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnm.mobileapp.crypto.CounterManager
import com.pnm.mobileapp.crypto.Signer
import com.pnm.mobileapp.data.model.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.KeyPair

class AppViewModel(private val context: Context) : ViewModel() {
    private val signer = Signer(context)
    private val counterManager = CounterManager(context)
    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet
    private var keyPair: KeyPair? = null

    private val _cumulative = MutableStateFlow(0L)
    val cumulative: StateFlow<Long> = _cumulative

    private val _counter = MutableStateFlow(0)
    val counter: StateFlow<Int> = _counter

    private val _isUsingHardwareCounter = MutableStateFlow(false)
    val isUsingHardwareCounter: StateFlow<Boolean> = _isUsingHardwareCounter

    private val _showSoftwareFallbackWarning = MutableStateFlow(false)
    val showSoftwareFallbackWarning: StateFlow<Boolean> = _showSoftwareFallbackWarning

    init {
        viewModelScope.launch {
            _cumulative.value = counterManager.getCumulative()
            _counter.value = counterManager.getCounter()
        }
    }

    fun generateWallet(activity: FragmentActivity? = null) {
        viewModelScope.launch {
            // Request biometric authentication if available
            if (activity != null && BiometricAuthHelper.isBiometricAvailable(context)) {
                val authenticated = BiometricAuthHelper.authenticate(
                    activity,
                    title = "Generate Secure Wallet",
                    subtitle = "Authenticate to generate hardware-backed key"
                )
                if (!authenticated) {
                    // User cancelled or authentication failed
                    return@launch
                }
            }
            
            keyPair = signer.generateKeyPair()
            val publicKeyHex = signer.exportPublicKeyHex(keyPair)
            _wallet.value = Wallet(keyPair!!, publicKeyHex)
        }
    }

    suspend fun initCounter(limit: Long) {
        counterManager.initCounter(limit)
        _cumulative.value = 0L
        _counter.value = 0
        _isUsingHardwareCounter.value = counterManager.isUsingHardwareCounter()
        _showSoftwareFallbackWarning.value = !_isUsingHardwareCounter.value
    }

    suspend fun getCumulative(): Long {
        val value = counterManager.getCumulative()
        _cumulative.value = value
        return value
    }

    suspend fun canSign(amount: Long): Boolean {
        return counterManager.canSign(amount)
    }

    suspend fun signAndIncrement(voucherJson: String, amount: Long): String {
        if (keyPair == null) {
            throw IllegalStateException("Wallet not generated")
        }
        val signature = counterManager.signAndIncrement(voucherJson, amount, signer, keyPair!!)
        _cumulative.value = counterManager.getCumulative()
        _counter.value = counterManager.getCounter()
        return signature
    }

    suspend fun getPublicKeyHex(): String {
        return signer.exportPublicKeyHex(keyPair)
    }
}

