package com.pnm.mobileapp.ui.viewmodel

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pnm.mobileapp.crypto.CounterManager
import com.pnm.mobileapp.crypto.EthereumWalletGenerator
import com.pnm.mobileapp.crypto.Signer
import com.pnm.mobileapp.data.api.HubApiService
import com.pnm.mobileapp.data.model.Wallet
import com.pnm.mobileapp.secure.BiometricAuthHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.KeyPair

class AppViewModel(
    private val context: Context,
    private val hubApiService: HubApiService? = null
) : ViewModel() {
    companion object {
        private const val ETH_WALLET_PREFS_NAME = "pnm_eth_wallet_secure"
        private const val KEY_ETH_PRIVATE_KEY = "eth_private_key"
        private const val KEY_ETH_ADDRESS = "eth_address"
    }

    private val signer = Signer(context)
    private val counterManager = CounterManager(context)
    private val ethereumWalletGenerator = EthereumWalletGenerator()
    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet
    private var keyPair: KeyPair? = null
    private var ethPrivateKey: ByteArray? = null

    // Encrypted storage for Ethereum wallet
    private val ethWalletMasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val ethWalletPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        ETH_WALLET_PREFS_NAME,
        ethWalletMasterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _cumulative = MutableStateFlow(0L)
    val cumulative: StateFlow<Long> = _cumulative

    private val _counter = MutableStateFlow(0)
    val counter: StateFlow<Int> = _counter

    private val _isUsingHardwareCounter = MutableStateFlow(false)
    val isUsingHardwareCounter: StateFlow<Boolean> = _isUsingHardwareCounter

    private val _showSoftwareFallbackWarning = MutableStateFlow(false)
    val showSoftwareFallbackWarning: StateFlow<Boolean> = _showSoftwareFallbackWarning

    private val _usdcBalance = MutableStateFlow<String?>(null)
    val usdcBalance: StateFlow<String?> = _usdcBalance

    private val _isLoadingBalance = MutableStateFlow(false)
    val isLoadingBalance: StateFlow<Boolean> = _isLoadingBalance

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
     * Ethereum wallet is persisted in EncryptedSharedPreferences
     */
    private suspend fun loadExistingWallet() {
        try {
            // Check if key exists in Keystore
            val existingKeyPair = signer.getExistingKeyPair()
            if (existingKeyPair != null) {
                keyPair = existingKeyPair
                val address = signer.deriveAddress(existingKeyPair)
                
                // Try to load persisted Ethereum wallet
                val savedEthPrivateKey = ethWalletPrefs.getString(KEY_ETH_PRIVATE_KEY, null)
                val savedEthAddress = ethWalletPrefs.getString(KEY_ETH_ADDRESS, null)
                
                if (savedEthPrivateKey != null && savedEthAddress != null) {
                    // Load persisted Ethereum wallet
                    try {
                        ethPrivateKey = Base64.decode(savedEthPrivateKey, Base64.DEFAULT)
                        _wallet.value = Wallet(existingKeyPair, address, savedEthAddress, ethPrivateKey)
                        android.util.Log.d("AppViewModel", "Loaded persisted Ethereum wallet: $savedEthAddress")
                    } catch (e: Exception) {
                        android.util.Log.e("AppViewModel", "Failed to load persisted ETH wallet, regenerating", e)
                        generateAndSaveEthereumWallet(existingKeyPair, address)
                    }
                } else {
                    // No persisted Ethereum wallet, generate new one
                    android.util.Log.d("AppViewModel", "No persisted ETH wallet found, generating new one")
                    generateAndSaveEthereumWallet(existingKeyPair, address)
                }
            }
        } catch (e: Exception) {
            // No existing wallet found, user needs to generate one
            // This is expected on first launch
            android.util.Log.d("AppViewModel", "No existing wallet found: ${e.message}")
        }
    }

    /**
     * Generate and persist Ethereum wallet
     */
    private suspend fun generateAndSaveEthereumWallet(deviceKeyPair: KeyPair, deviceAddress: String) {
        try {
            val (ethPrivKey, ethAddress) = ethereumWalletGenerator.generateKeyPair()
            ethPrivateKey = ethPrivKey
            
            // Persist Ethereum wallet
            ethWalletPrefs.edit()
                .putString(KEY_ETH_PRIVATE_KEY, Base64.encodeToString(ethPrivKey, Base64.DEFAULT))
                .putString(KEY_ETH_ADDRESS, ethAddress)
                .apply()
            
            _wallet.value = Wallet(deviceKeyPair, deviceAddress, ethAddress, ethPrivKey)
            android.util.Log.d("AppViewModel", "Generated and saved new Ethereum wallet: $ethAddress")
        } catch (e: Exception) {
            android.util.Log.e("AppViewModel", "Failed to generate ETH wallet, using placeholder", e)
            // Use placeholder if ETH wallet generation fails
            ethPrivateKey = ByteArray(32)
            _wallet.value = Wallet(deviceKeyPair, deviceAddress, "0x0000000000000000000000000000000000000000", ethPrivateKey)
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
            
            // Generate and persist Ethereum wallet (secp256k1 for deposits)
            generateAndSaveEthereumWallet(keyPair!!, address)
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
     * This will overwrite the existing persisted wallet
     */
    fun regenerateEthereumWallet() {
        viewModelScope.launch {
            val currentWallet = _wallet.value
            val deviceKeyPair = keyPair
            val deviceAddress = currentWallet?.address ?: signer.deriveAddress(deviceKeyPair)
            
            if (deviceKeyPair != null) {
                generateAndSaveEthereumWallet(deviceKeyPair, deviceAddress)
            } else {
                android.util.Log.e("AppViewModel", "Cannot regenerate ETH wallet: device wallet not found")
            }
        }
    }

    /**
     * Fetch USDC balance from hub server
     */
    fun fetchUSDCBalance() {
        viewModelScope.launch {
            val wallet = _wallet.value
            val ethAddress = wallet?.ethAddress
            
            if (ethAddress == null || ethAddress.startsWith("0x0000") || hubApiService == null) {
                _usdcBalance.value = null
                return@launch
            }

            _isLoadingBalance.value = true
            try {
                android.util.Log.d("AppViewModel", "Fetching USDC balance for address: $ethAddress")
                val response = hubApiService.getBalance(ethAddress)
                android.util.Log.d("AppViewModel", "Balance API response code: ${response.code()}")
                if (response.isSuccessful && response.body() != null) {
                    val balanceResponse = response.body()!!
                    android.util.Log.d("AppViewModel", "Balance response: status=${balanceResponse.status}, balance=${balanceResponse.balanceFormatted}")
                    if (balanceResponse.status == "success") {
                        _usdcBalance.value = balanceResponse.balanceFormatted
                    } else {
                        android.util.Log.e("AppViewModel", "Balance API returned error: ${balanceResponse.status}")
                        _usdcBalance.value = null
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("AppViewModel", "Failed to fetch balance: ${response.code()}, error: $errorBody")
                    _usdcBalance.value = null
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Error fetching USDC balance", e)
                e.printStackTrace()
                _usdcBalance.value = null
            } finally {
                _isLoadingBalance.value = false
            }
        }
    }
}

