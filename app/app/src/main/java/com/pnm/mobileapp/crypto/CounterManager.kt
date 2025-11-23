package com.pnm.mobileapp.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pnm.mobileapp.secure.HardwareKeystoreManager
import com.pnm.mobileapp.secure.MonotonicCounterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages offline payment counter and cumulative amount with encrypted storage
 * Uses MonotonicCounterManager for hardware-backed secure counter when available
 */
class CounterManager(private val context: Context) {
    companion object {
        private const val TAG = "CounterManager"
        private const val PREFS_NAME = "pnm_counter_prefs"
        private const val KEY_OFFLINE_LIMIT = "offline_limit"
        private const val KEY_CUMULATIVE = "cumulative"
        private const val KEY_COUNTER = "counter"
    }

    private val hardwareKeystoreManager = HardwareKeystoreManager(context)
    private val monotonicCounterManager = MonotonicCounterManager(context, hardwareKeystoreManager)
    private var useHardwareCounter = false

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
     * Tries to use hardware-backed counter if StrongBox available
     */
    suspend fun initCounter(limit: Long) = withContext(Dispatchers.IO) {
        Log.d(TAG, "initCounter: Starting initialization with limit: $limit (${limit / 1_000_000.0} USDC)")
        // Try to use hardware-backed counter
        val strongBoxAvailable = hardwareKeystoreManager.detectStrongBoxAvailable()
        Log.d(TAG, "initCounter: StrongBox available: $strongBoxAvailable")
        if (strongBoxAvailable) {
            val hardwareInitSuccess = monotonicCounterManager.initCounter(limit)
            if (hardwareInitSuccess) {
                useHardwareCounter = true
                Log.d(TAG, "✅ Using hardware-backed monotonic counter, limit initialized: $limit")
                // Verify it was set correctly
                val verifyLimit = monotonicCounterManager.getLimit()
                if (verifyLimit == limit) {
                    Log.d(TAG, "✅ Verified hardware counter limit: $verifyLimit")
                } else {
                    Log.e(TAG, "❌ Hardware counter limit mismatch! Expected: $limit, Got: $verifyLimit")
                }
                return@withContext
            } else {
                Log.w(TAG, "Hardware counter initialization failed, falling back to encrypted prefs")
            }
        }
        
        // Fallback to encrypted SharedPreferences
        useHardwareCounter = false
        // Use commit() instead of apply() to ensure synchronous write
        val editor = encryptedPrefs.edit()
            .putLong(KEY_OFFLINE_LIMIT, limit)
            .putLong(KEY_CUMULATIVE, 0L)
            .putInt(KEY_COUNTER, 0)
        val committed = editor.commit() // commit() is synchronous, apply() is async
        
        if (!committed) {
            Log.e(TAG, "❌ Failed to commit limit to encrypted prefs!")
        }
        
        // Verify it was saved correctly
        val savedLimit = encryptedPrefs.getLong(KEY_OFFLINE_LIMIT, 0L)
        if (savedLimit == limit) {
            Log.d(TAG, "✅ Fallback: Encrypted prefs limit initialized: $savedLimit (${savedLimit / 1_000_000.0} USDC)")
        } else {
            Log.e(TAG, "❌ Fallback: Failed to save limit! Expected: $limit, Got: $savedLimit")
            Log.e(TAG, "This may indicate a storage issue. Trying to force save again...")
            // Try one more time with commit
            encryptedPrefs.edit().putLong(KEY_OFFLINE_LIMIT, limit).commit()
            val retryLimit = encryptedPrefs.getLong(KEY_OFFLINE_LIMIT, 0L)
            if (retryLimit == limit) {
                Log.d(TAG, "✅ Retry succeeded: limit saved correctly")
            } else {
                Log.e(TAG, "❌ Retry also failed! Limit: $retryLimit")
            }
        }
    }

    /**
     * Get the current cumulative amount
     */
    suspend fun getCumulative(): Long = withContext(Dispatchers.IO) {
        if (useHardwareCounter) {
            monotonicCounterManager.getCumulative()
        } else {
            encryptedPrefs.getLong(KEY_CUMULATIVE, 0L)
        }
    }

    /**
     * Get the current counter value
     */
    suspend fun getCounter(): Int = withContext(Dispatchers.IO) {
        if (useHardwareCounter) {
            monotonicCounterManager.getCounter()
        } else {
            encryptedPrefs.getInt(KEY_COUNTER, 0)
        }
    }

    /**
     * Get the offline limit
     */
    suspend fun getLimit(): Long = withContext(Dispatchers.IO) {
        if (useHardwareCounter) {
            monotonicCounterManager.getLimit()
        } else {
            encryptedPrefs.getLong(KEY_OFFLINE_LIMIT, 0L)
        }
    }

    /**
     * Update the offline limit without resetting cumulative
     * For hardware counter, this creates a new signed state with the new limit
     * @return true if update succeeded, false otherwise
     */
    suspend fun updateLimit(newLimit: Long): Boolean = withContext(Dispatchers.IO) {
        if (useHardwareCounter) {
            // Use hardware-backed counter's updateLimit which signs the new state
            val success = monotonicCounterManager.updateLimit(newLimit)
            if (!success) {
                Log.e(TAG, "Failed to update limit in hardware counter - signature verification may have failed")
                return@withContext false
            } else {
                Log.d(TAG, "Updated hardware counter limit to: $newLimit (cumulative preserved)")
                return@withContext true
            }
        } else {
            // Update limit in encrypted prefs without touching cumulative
            encryptedPrefs.edit()
                .putLong(KEY_OFFLINE_LIMIT, newLimit)
                .apply()
            Log.d(TAG, "Updated offline limit to: $newLimit (cumulative preserved)")
            return@withContext true
        }
    }

    /**
     * Check if signing is allowed for the given amount
     */
    suspend fun canSign(amount: Long): Boolean = withContext(Dispatchers.IO) {
        if (useHardwareCounter) {
            monotonicCounterManager.canSign(amount)
        } else {
            val cumulative = encryptedPrefs.getLong(KEY_CUMULATIVE, 0L)
            val limit = encryptedPrefs.getLong(KEY_OFFLINE_LIMIT, 0L)
            cumulative + amount <= limit
        }
    }

    /**
     * Check if using hardware-backed counter
     */
    suspend fun isUsingHardwareCounter(): Boolean = withContext(Dispatchers.IO) {
        useHardwareCounter
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
        if (useHardwareCounter) {
            // Use hardware-backed monotonic counter
            val success = monotonicCounterManager.incrementCounterSafely(amount)
            if (!success) {
                throw IllegalStateException("Failed to increment hardware counter - limit exceeded or tampering detected")
            }
        } else {
            // Use encrypted SharedPreferences
            val currentCumulative = encryptedPrefs.getLong(KEY_CUMULATIVE, 0L)
            val currentCounter = encryptedPrefs.getInt(KEY_COUNTER, 0)
            
            encryptedPrefs.edit()
                .putLong(KEY_CUMULATIVE, currentCumulative + amount)
                .putInt(KEY_COUNTER, currentCounter + 1)
                .apply()
        }

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

