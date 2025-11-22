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
import com.pnm.mobileapp.crypto.EthereumDepositManager
import com.pnm.mobileapp.crypto.EthereumWalletGenerator
import com.pnm.mobileapp.crypto.Signer
import com.pnm.mobileapp.data.api.HubApiService
import com.pnm.mobileapp.data.model.Wallet
import com.pnm.mobileapp.secure.BiometricAuthHelper
import com.pnm.mobileapp.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.math.BigInteger
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
    private val ethereumDepositManager = EthereumDepositManager()
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

    private val _vaultBalance = MutableStateFlow<String?>(null)
    val vaultBalance: StateFlow<String?> = _vaultBalance

    private val _isLoadingVaultBalance = MutableStateFlow(false)
    val isLoadingVaultBalance: StateFlow<Boolean> = _isLoadingVaultBalance

    init {
        viewModelScope.launch {
            // Load cumulative and counter from storage
            val loadedCumulative = counterManager.getCumulative()
            val loadedCounter = counterManager.getCounter()
            _cumulative.value = loadedCumulative
            _counter.value = loadedCounter
            android.util.Log.d("AppViewModel", "Initialized: cumulative=$loadedCumulative, counter=$loadedCounter")
            // Try to load existing wallet from Keystore
            loadExistingWallet()
            // Fetch vault balance after wallet is loaded
            delay(500) // Small delay to ensure wallet is loaded
            fetchVaultBalance()
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
        // Always return the limit stored in the counter manager
        // This is the limit that was set when the counter was initialized/updated
        // The vault balance is only used to UPDATE the limit, not to GET it
        val limit = counterManager.getLimit()
        android.util.Log.d("AppViewModel", "getOfflineLimit: returning counterManager limit=$limit (${limit / 1_000_000.0} USDC)")
        return limit
    }

    suspend fun getRemainingBalance(): Long {
        // Always get fresh values from counter manager
        val limit = getOfflineLimit()
        val cumulative = counterManager.getCumulative()
        // Update the StateFlow so UI observes the change
        _cumulative.value = cumulative
        val remaining = maxOf(0L, limit - cumulative)
        android.util.Log.d("AppViewModel", "getRemainingBalance: vaultBalance=${_vaultBalance.value}, limit=$limit (${limit / 1_000_000.0} USDC), cumulative=$cumulative (${cumulative / 1_000_000.0} USDC), remaining=$remaining (${remaining / 1_000_000.0} USDC)")
        return remaining
    }

    /**
     * Fetch vault deposit balance from hub server
     * This is the actual balance available for offline spending
     */
    fun fetchVaultBalance() {
        viewModelScope.launch {
            android.util.Log.d("AppViewModel", "fetchVaultBalance: Starting...")
            val wallet = _wallet.value
            val ethAddress = wallet?.ethAddress
            
            if (ethAddress == null || ethAddress.startsWith("0x0000") || hubApiService == null) {
                _vaultBalance.value = null
                return@launch
            }

            _isLoadingVaultBalance.value = true
            try {
                android.util.Log.d("AppViewModel", "Fetching vault balance for address: $ethAddress")
                val response = hubApiService.getVaultBalance(ethAddress)
                android.util.Log.d("AppViewModel", "Vault balance API response code: ${response.code()}")
                if (response.isSuccessful && response.body() != null) {
                    val balanceResponse = response.body()!!
                    android.util.Log.d("AppViewModel", "Vault balance response: status=${balanceResponse.status}, balance=${balanceResponse.balanceFormatted}")
                    if (balanceResponse.status == "success") {
                        android.util.Log.d("AppViewModel", "Setting vault balance to: ${balanceResponse.balanceFormatted}")
                        _vaultBalance.value = balanceResponse.balanceFormatted
                        // Update counter manager limit with vault balance
                        val balanceDouble = balanceResponse.balanceFormatted.toDoubleOrNull()
                        if (balanceDouble != null) {
                            val limitInMicroUSDC = (balanceDouble * 1_000_000).toLong()
                            android.util.Log.d("AppViewModel", "Calculated limit: $limitInMicroUSDC from balance: $balanceDouble")
                            // Update limit without resetting cumulative
                            val currentLimit = counterManager.getLimit()
                            val currentCumulative = counterManager.getCumulative()
                            android.util.Log.d("AppViewModel", "Current limit: $currentLimit, New limit: $limitInMicroUSDC, Current cumulative: $currentCumulative")
                            
                            // If counter was never initialized (limit is 0), initialize it first
                            // BUT: if cumulative > 0, this is suspicious - don't reset it
                            if (currentLimit == 0L) {
                                if (currentCumulative > 0L) {
                                    android.util.Log.e("AppViewModel", "⚠️ Suspicious state: limit=0 but cumulative=$currentCumulative > 0. This should not happen!")
                                    android.util.Log.e("AppViewModel", "Not initializing counter to prevent data loss. Please investigate.")
                                } else {
                                    android.util.Log.d("AppViewModel", "Counter not initialized, initializing with limit: $limitInMicroUSDC")
                                    counterManager.initCounter(limitInMicroUSDC)
                                    android.util.Log.d("AppViewModel", "Counter initialized with limit: $limitInMicroUSDC")
                                }
                            } else if (currentLimit != limitInMicroUSDC) {
                                // CRITICAL: Only update limit if:
                                // 1. It's a new deposit (vault balance increased), OR
                                // 2. Cumulative is 0 (no spending yet), OR
                                // 3. Merchant redeemed (vault balance decreased) AND new limit >= cumulative
                                // Never update if cumulative > 0 and vault balance is the same (merchant hasn't redeemed)
                                // This prevents resetting the limit after spending
                                val isNewDeposit = limitInMicroUSDC > currentLimit
                                val isMerchantRedeemed = limitInMicroUSDC < currentLimit
                                val hasNoSpending = currentCumulative == 0L
                                
                                if (isNewDeposit) {
                                    android.util.Log.d("AppViewModel", "New deposit detected: vault balance increased from $currentLimit to $limitInMicroUSDC")
                                    // updateLimit is a suspend function, so we need to await it
                                    counterManager.updateLimit(limitInMicroUSDC)
                                    // Verify the update succeeded
                                    val updatedLimit = counterManager.getLimit()
                                    if (updatedLimit == limitInMicroUSDC) {
                                        android.util.Log.d("AppViewModel", "✅ Successfully updated offline limit to: $limitInMicroUSDC (from vault balance: ${balanceResponse.balanceFormatted})")
                                    } else {
                                        android.util.Log.e("AppViewModel", "❌ Failed to update limit! Expected: $limitInMicroUSDC, Got: $updatedLimit")
                                        android.util.Log.e("AppViewModel", "This might be due to signature verification failure. Counter may need to be re-initialized.")
                                    }
                                } else if (isMerchantRedeemed) {
                                    // Merchant redeemed, vault balance decreased
                                    // CRITICAL: We should NOT update the limit when merchants redeem!
                                    // The limit represents the maximum offline spending allowed, which was set
                                    // when the user deposited. When merchants redeem, the vault balance decreases,
                                    // but the limit should stay the same because:
                                    // 1. The user's offline spending limit doesn't change when merchants redeem
                                    // 2. Updating the limit would cause double deduction (cumulative already accounts for spending)
                                    // 3. The remaining balance calculation (limit - cumulative) already accounts for spending
                                    //
                                    // The limit should only be updated when:
                                    // 1. User makes a new deposit (increases limit)
                                    // 2. User hasn't spent anything yet (can reset limit to current vault balance)
                                    //
                                    // When merchants redeem, the vault balance decreases, but the limit stays the same.
                                    // The remaining balance will be: limit - cumulative, which correctly shows
                                    // how much the user can still spend offline.
                                    android.util.Log.d("AppViewModel", "Merchant redeemed: vault balance decreased from $currentLimit to $limitInMicroUSDC, cumulative=$currentCumulative")
                                    android.util.Log.d("AppViewModel", "⚠️ NOT updating limit (keeping at $currentLimit) to prevent double deduction")
                                    android.util.Log.d("AppViewModel", "The limit represents max offline spending and doesn't change when merchants redeem")
                                    android.util.Log.d("AppViewModel", "Remaining balance: $currentLimit - $currentCumulative = ${currentLimit - currentCumulative}")
                                } else if (hasNoSpending) {
                                    android.util.Log.d("AppViewModel", "No spending yet, updating limit from $currentLimit to $limitInMicroUSDC")
                                    counterManager.updateLimit(limitInMicroUSDC)
                                    val updatedLimit = counterManager.getLimit()
                                    if (updatedLimit == limitInMicroUSDC) {
                                        android.util.Log.d("AppViewModel", "✅ Successfully updated offline limit to: $limitInMicroUSDC")
                                    } else {
                                        android.util.Log.e("AppViewModel", "❌ Failed to update limit! Expected: $limitInMicroUSDC, Got: $updatedLimit")
                                    }
                                } else {
                                    // Cumulative > 0 and vault balance is the same (merchant hasn't redeemed yet)
                                    // We should NOT update the limit, as this would allow overspending
                                    android.util.Log.w("AppViewModel", "⚠️ Skipping limit update: cumulative=$currentCumulative > 0 and vault balance unchanged (limit=$currentLimit)")
                                    android.util.Log.w("AppViewModel", "This prevents overspending. Limit will be updated after merchant redeems (vault decreases) or after new deposit (vault increases).")
                                }
                            } else {
                                android.util.Log.d("AppViewModel", "Limit unchanged, no update needed")
                            }
                            // Refresh cumulative and counter display
                            val newCumulative = counterManager.getCumulative()
                            val newCounter = counterManager.getCounter()
                            val finalLimit = counterManager.getLimit()
                            _cumulative.value = newCumulative
                            _counter.value = newCounter
                            android.util.Log.d("AppViewModel", "After update - cumulative: $newCumulative, counter: $newCounter, limit: $finalLimit")
                        } else {
                            android.util.Log.e("AppViewModel", "Failed to parse balance: ${balanceResponse.balanceFormatted}")
                        }
                    } else {
                        android.util.Log.e("AppViewModel", "Vault balance API returned error: ${balanceResponse.status}")
                        _vaultBalance.value = null
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("AppViewModel", "Failed to fetch vault balance: ${response.code()}, $errorBody")
                    _vaultBalance.value = null
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Error fetching vault balance", e)
                _vaultBalance.value = null
            } finally {
                _isLoadingVaultBalance.value = false
            }
        }
    }

    suspend fun canSign(amount: Long): Boolean {
        return counterManager.canSign(amount)
    }

    suspend fun signAndIncrement(voucherJson: String, amount: Long): String {
        if (keyPair == null) {
            throw IllegalStateException("Wallet not generated")
        }
        val oldCumulative = counterManager.getCumulative()
        android.util.Log.d("AppViewModel", "signAndIncrement: before - cumulative=$oldCumulative (${oldCumulative / 1_000_000.0} USDC), amount=$amount (${amount / 1_000_000.0} USDC)")
        val signature = counterManager.signAndIncrement(voucherJson, amount, signer, keyPair!!)
        val newCumulative = counterManager.getCumulative()
        val newCounter = counterManager.getCounter()
        _cumulative.value = newCumulative
        _counter.value = newCounter
        android.util.Log.d("AppViewModel", "signAndIncrement: after - cumulative=$newCumulative (${newCumulative / 1_000_000.0} USDC), counter=$newCounter")
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
            var ethAddress = wallet?.ethAddress
            
            if (ethAddress == null || ethAddress.startsWith("0x0000") || hubApiService == null) {
                _usdcBalance.value = null
                return@launch
            }

            // If we have a private key, verify the address matches
            val ethPrivateKey = ethPrivateKey
            if (ethPrivateKey != null && wallet != null) {
                try {
                    val derivedAddress = ethereumDepositManager.deriveAddressFromPrivateKey(ethPrivateKey)
                    if (derivedAddress.lowercase() != ethAddress?.lowercase()) {
                        android.util.Log.w("AppViewModel", "Address mismatch in fetchUSDCBalance! Profile: $ethAddress, Derived: $derivedAddress. Using derived address.")
                        ethAddress = derivedAddress
                        // Update wallet with correct address
                        _wallet.value = wallet.copy(ethAddress = derivedAddress)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModel", "Error deriving address for balance fetch", e)
                }
            }

            // Ensure we have a valid address before proceeding
            val finalAddress = ethAddress ?: return@launch

            _isLoadingBalance.value = true
            try {
                android.util.Log.d("AppViewModel", "Fetching USDC balance for address: $finalAddress")
                val response = hubApiService.getBalance(finalAddress)
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

    /**
     * Deposit USDC to vault
     * @param amount Amount in USDC (will be converted to token units)
     */
    suspend fun depositToVault(amount: Double): Result<String> {
        val wallet = _wallet.value
        val ethPrivateKey = ethPrivateKey
        val ethAddress = wallet?.ethAddress

        if (wallet == null || ethPrivateKey == null || ethAddress == null || ethAddress.startsWith("0x0000")) {
            return Result.failure(Exception("Ethereum wallet not available"))
        }

        if (hubApiService == null) {
            return Result.failure(Exception("Hub API service not available"))
        }

        return try {
            // Verify that the private key matches the address
            val derivedAddress = ethereumDepositManager.deriveAddressFromPrivateKey(ethPrivateKey)
            android.util.Log.d("AppViewModel", "Profile address: $ethAddress, Derived from key: $derivedAddress")
            
            // Use the address derived from the private key (this is the actual address that will sign)
            val actualAddress = if (derivedAddress.lowercase() != ethAddress.lowercase()) {
                android.util.Log.w("AppViewModel", "Address mismatch! Profile shows $ethAddress but key derives $derivedAddress. Using derived address.")
                // Update the wallet with the correct address
                _wallet.value = wallet.copy(ethAddress = derivedAddress)
                derivedAddress
            } else {
                ethAddress
            }
            
            // Convert amount to token units (USDC has 6 decimals)
            val amountInTokenUnits = (amount * 1_000_000).toLong()
            val amountBigInt = BigInteger.valueOf(amountInTokenUnits)

            // Get nonce using the actual address
            val nonce = ethereumDepositManager.getNonce(Constants.RPC_URL, actualAddress)
            android.util.Log.d("AppViewModel", "Using nonce: $nonce for deposit with address: $actualAddress")

            // Create approve transaction
            val approveTx = ethereumDepositManager.createApproveTransaction(
                privateKey = ethPrivateKey,
                rpcUrl = Constants.RPC_URL,
                tokenAddress = Constants.USDC_TOKEN_CONTRACT,
                spenderAddress = Constants.VAULT_CONTRACT_ADDRESS,
                amount = amountBigInt,
                nonce = nonce
            )

            // Create deposit transaction (nonce + 1)
            val depositTx = ethereumDepositManager.createDepositTransaction(
                privateKey = ethPrivateKey,
                rpcUrl = Constants.RPC_URL,
                vaultAddress = Constants.VAULT_CONTRACT_ADDRESS,
                userAddress = actualAddress, // Use actual address derived from key
                tokenAddress = Constants.USDC_TOKEN_CONTRACT,
                amount = amountBigInt,
                nonce = nonce + 1
            )

            // Send to hub
            val depositRequest = com.pnm.mobileapp.data.api.DepositRequest(
                userAddress = actualAddress, // Use actual address
                amount = amountBigInt.toString(),
                signedApproveTx = approveTx,
                signedDepositTx = depositTx
            )

            val response = hubApiService.deposit(depositRequest)
            if (response.isSuccessful && response.body() != null) {
                val depositResponse = response.body()!!
                if (depositResponse.status == "success") {
                    android.util.Log.d("AppViewModel", "Deposit successful: ${depositResponse.depositTxHash}")
                    // Wait a bit for transaction to be mined
                    delay(2000) // 2 seconds delay
                    // Refresh balances after deposit
                    fetchUSDCBalance()
                    fetchVaultBalance() // Update offline limit
                    // Wait a bit more and refresh again to ensure balance is updated
                    delay(1000)
                    fetchVaultBalance()
                    Result.success("Deposit successful: ${depositResponse.depositTxHash}")
                } else {
                    Result.failure(Exception(depositResponse.message))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("Deposit failed: ${response.code()}, $errorBody"))
            }
        } catch (e: Exception) {
            android.util.Log.e("AppViewModel", "Error depositing to vault", e)
            Result.failure(e)
        }
    }
}

