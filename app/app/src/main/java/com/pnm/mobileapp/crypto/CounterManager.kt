package com.pnm.mobileapp.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages offline payment counter and cumulative amount with encrypted storage
 */
class CounterManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "pnm_counter_prefs"
        private const val KEY_OFFLINE_LIMIT = "offline_limit"
        private const val KEY_CUMULATIVE = "cumulative"
        private const val KEY_COUNTER = "counter"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Initialize the counter with an offline limit
     */
    suspend fun initCounter(limit: Long) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit()
            .putLong(KEY_OFFLINE_LIMIT, limit)
            .putLong(KEY_CUMULATIVE, 0L)
            .putInt(KEY_COUNTER, 0)
            .apply()
    }

    /**
     * Get the current cumulative amount
     */
    suspend fun getCumulative(): Long = withContext(Dispatchers.IO) {
        encryptedPrefs.getLong(KEY_CUMULATIVE, 0L)
    }

    /**
     * Get the current counter value
     */
    suspend fun getCounter(): Int = withContext(Dispatchers.IO) {
        encryptedPrefs.getInt(KEY_COUNTER, 0)
    }

    /**
     * Get the offline limit
     */
    suspend fun getLimit(): Long = withContext(Dispatchers.IO) {
        encryptedPrefs.getLong(KEY_OFFLINE_LIMIT, 0L)
    }

    /**
     * Check if signing is allowed for the given amount
     */
    suspend fun canSign(amount: Long): Boolean = withContext(Dispatchers.IO) {
        val cumulative = getCumulative()
        val limit = getLimit()
        cumulative + amount <= limit
    }

    /**
     * Sign the voucher and increment counter/cumulative
     * @throws IllegalStateException if limit would be exceeded
     */
    suspend fun signAndIncrement(
        voucherJson: String,
        amount: Long,
        signer: Signer,
        keyPair: java.security.KeyPair
    ): String = withContext(Dispatchers.IO) {
        if (!canSign(amount)) {
            throw IllegalStateException("Offline limit exceeded: cannot sign amount $amount")
        }

        // Sign the voucher
        val signature = signer.signVoucher(voucherJson, keyPair)

        // Increment cumulative and counter atomically
        val currentCumulative = getCumulative()
        val currentCounter = getCounter()
        
        encryptedPrefs.edit()
            .putLong(KEY_CUMULATIVE, currentCumulative + amount)
            .putInt(KEY_COUNTER, currentCounter + 1)
            .apply()

        signature
    }

    /**
     * Reset counter and cumulative (for testing or reset functionality)
     */
    suspend fun reset() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit()
            .putLong(KEY_CUMULATIVE, 0L)
            .putInt(KEY_COUNTER, 0)
            .apply()
    }
}

