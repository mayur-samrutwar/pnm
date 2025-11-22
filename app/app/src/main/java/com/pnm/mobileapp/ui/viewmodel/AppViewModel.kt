package com.pnm.mobileapp.ui.viewmodel

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnm.mobileapp.crypto.CounterManager
import com.pnm.mobileapp.crypto.EthereumWalletGenerator
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
    private val ethereumWalletGenerator = EthereumWalletGenerator()
    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet
    private var keyPair: KeyPair? = null
    private var ethPrivateKey: ByteArray? = null

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
     * Note: Ethereum wallet is not persisted, so it will be regenerated
     */
    private suspend fun loadExistingWallet() {
        try {
            // Check if key exists in Keystore
            val existingKeyPair = signer.getExistingKeyPair()
            if (existingKeyPair != null) {
                keyPair = existingKeyPair
                val address = signer.deriveAddress(existingKeyPair)
                // Generate Ethereum wallet (not persisted, so regenerate)
                val (ethPrivKey, ethAddress) = try {
                    ethereumWalletGenerator.generateKeyPair()
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModel", "Failed to generate ETH wallet, using placeholder", e)
                    // Use placeholder if ETH wallet generation fails
                    Pair(ByteArray(32), "0x0000000000000000000000000000000000000000")
                }
                ethPrivateKey = ethPrivKey
                _wallet.value = Wallet(existingKeyPair, address, ethAddress, ethPrivKey)
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
            
            // Generate device wallet (P-256 for vouchers)
            keyPair = signer.generateKeyPair()
            val address = signer.deriveAddress(keyPair)
            
            // Generate Ethereum wallet (secp256k1 for deposits)
            // Wrap in try-catch to prevent app crash if ETH wallet generation fails
            val (ethPrivKey, ethAddress) = try {
                ethereumWalletGenerator.generateKeyPair()
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to generate ETH wallet, using placeholder", e)
                // Use placeholder if ETH wallet generation fails
                Pair(ByteArray(32), "0x0000000000000000000000000000000000000000")
            }
            ethPrivateKey = ethPrivKey
            
            _wallet.value = Wallet(keyPair!!, address, ethAddress, ethPrivKey)
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

    /**
     * Regenerate Ethereum wallet (useful if initial generation failed)
     */
    fun regenerateEthereumWallet() {
        viewModelScope.launch {
            try {
                val (ethPrivKey, ethAddress) = ethereumWalletGenerator.generateKeyPair()
                ethPrivateKey = ethPrivKey
                
                // Update wallet with new ETH address
                val currentWallet = _wallet.value
                if (currentWallet != null) {
                    _wallet.value = currentWallet.copy(
                        ethAddress = ethAddress,
                        ethPrivateKey = ethPrivKey
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to regenerate ETH wallet", e)
                // Error is logged, user can try again
            }
        }
    }
}

