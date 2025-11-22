package com.pnm.mobileapp.ui.viewmodel

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnm.mobileapp.crypto.CounterManager
import com.pnm.mobileapp.crypto.Signer
import com.pnm.mobileapp.data.model.Wallet
import com.pnm.mobileapp.secure.BiometricAuthHelper
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
            // Try to load existing wallet from Keystore
            loadExistingWallet()
        }
    }

    /**
     * Load existing wallet from Android Keystore if it exists
     */
    private suspend fun loadExistingWallet() {
        try {
            // Check if key exists in Keystore
            val existingKeyPair = signer.getExistingKeyPair()
            if (existingKeyPair != null) {
                keyPair = existingKeyPair
                val address = signer.deriveAddress(existingKeyPair)
                _wallet.value = Wallet(existingKeyPair, address)
            }
        } catch (e: Exception) {
            // No existing wallet found, user needs to generate one
            // This is expected on first launch
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
            val address = signer.deriveAddress(keyPair)
            _wallet.value = Wallet(keyPair!!, address)
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

    suspend fun getOfflineLimit(): Long {
        return counterManager.getLimit()
    }

    suspend fun getRemainingBalance(): Long {
        val limit = counterManager.getLimit()
        val cumulative = counterManager.getCumulative()
        return maxOf(0L, limit - cumulative)
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

