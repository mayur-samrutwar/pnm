package com.pnm.mobileapp.secure

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.Mac

/**
 * Manages monotonic counter and cumulative with encrypted storage and signed state
 */
class MonotonicCounterManager(
    private val context: Context,
    private val hardwareKeystoreManager: HardwareKeystoreManager
) {
    companion object {
        private const val TAG = "MonotonicCounterManager"
        private const val PREFS_NAME = "pnm_counter_secure"
        private const val KEY_OFFLINE_LIMIT = "offline_limit"
        private const val KEY_CUMULATIVE = "cumulative"
        private const val KEY_COUNTER = "counter"
        private const val KEY_STATE_SIGNATURE = "state_signature"
        private const val KEY_ALIAS = "pnm_counter_key"
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

    private val gson = Gson()

    /**
     * Initialize counter with limit and generate signing key
     */
    suspend fun initCounter(limit: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // Generate hardware key for state signing if not exists
            if (!encryptedPrefs.contains(KEY_ALIAS)) {
                val keyGenerated = hardwareKeystoreManager.generateHardwareKey(KEY_ALIAS)
                if (!keyGenerated) {
                    Log.e(TAG, "Failed to generate hardware key for counter")
                    return@withContext false
                }
                encryptedPrefs.edit().putString(KEY_ALIAS, KEY_ALIAS).apply()
            }

            // Initialize counter values
            val state = CounterState(
                cumulative = 0L,
                counter = 0,
                limit = limit
            )
            
            val signature = signState(state) ?: return@withContext false

            encryptedPrefs.edit()
                .putLong(KEY_OFFLINE_LIMIT, limit)
                .putLong(KEY_CUMULATIVE, 0L)
                .putInt(KEY_COUNTER, 0)
                .putString(KEY_STATE_SIGNATURE, signature)
                .apply()

            Log.d(TAG, "Counter initialized with limit: $limit")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize counter: ${e.message}", e)
            false
        }
    }

    /**
     * Get current cumulative amount
     */
    suspend fun getCumulative(): Long = withContext(Dispatchers.IO) {
        encryptedPrefs.getLong(KEY_CUMULATIVE, 0L)
    }

    /**
     * Get current counter value
     */
    suspend fun getCounter(): Int = withContext(Dispatchers.IO) {
        encryptedPrefs.getInt(KEY_COUNTER, 0)
    }

    /**
     * Get offline limit
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
     * Increment counter safely with signed state verification
     * Refuses to sign if updated cumulative > limit
     * @return true if increment was successful, false if limit would be exceeded
     */
    suspend fun incrementCounterSafely(amount: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentCumulative = getCumulative()
            val currentCounter = getCounter()
            val limit = getLimit()

            // Check limit before incrementing
            if (currentCumulative + amount > limit) {
                Log.w(TAG, "Cannot increment: cumulative ($currentCumulative) + amount ($amount) > limit ($limit)")
                return@withContext false
            }

            // Verify current state signature
            val currentState = CounterState(
                cumulative = currentCumulative,
                counter = currentCounter,
                limit = limit
            )
            val currentSignature = encryptedPrefs.getString(KEY_STATE_SIGNATURE, null)
            if (currentSignature == null || !verifyState(currentState, currentSignature)) {
                Log.e(TAG, "State signature verification failed - possible tampering")
                return@withContext false
            }

            // Create new state
            val newState = CounterState(
                cumulative = currentCumulative + amount,
                counter = currentCounter + 1,
                limit = limit
            )

            // Sign new state
            val newSignature = signState(newState) ?: return@withContext false

            // Atomically update state
            encryptedPrefs.edit()
                .putLong(KEY_CUMULATIVE, newState.cumulative)
                .putInt(KEY_COUNTER, newState.counter)
                .putString(KEY_STATE_SIGNATURE, newSignature)
                .apply()

            Log.d(TAG, "Counter incremented: cumulative=${newState.cumulative}, counter=${newState.counter}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increment counter: ${e.message}", e)
            false
        }
    }

    /**
     * Sign counter state using hardware key
     */
    private suspend fun signState(state: CounterState): String? {
        return try {
            val stateJson = gson.toJson(state)
            val stateBytes = stateJson.toByteArray(Charsets.UTF_8)
            val signature = hardwareKeystoreManager.signWithHardwareKey(KEY_ALIAS, stateBytes)
            signature?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign state: ${e.message}", e)
            null
        }
    }

    /**
     * Verify counter state signature
     */
    private suspend fun verifyState(state: CounterState, signatureBase64: String): Boolean {
        return try {
            val stateJson = gson.toJson(state)
            val stateBytes = stateJson.toByteArray(Charsets.UTF_8)
            val signature = Base64.decode(signatureBase64, Base64.NO_WRAP)

            val publicKey = getPublicKey() ?: return false
            val sig = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(stateBytes)
            }
            sig.verify(signature)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify state: ${e.message}", e)
            false
        }
    }

    /**
     * Get public key from keystore
     */
    private suspend fun getPublicKey(): PublicKey? = withContext(Dispatchers.IO) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                return@withContext null
            }
            val privateKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            privateKeyEntry.certificate.publicKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key: ${e.message}", e)
            null
        }
    }

    /**
     * Reset counter with new limit (for refill)
     */
    suspend fun reset(
        cumulative: Long = 0L,
        counter: Int = 0,
        newLimit: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val limit = newLimit ?: encryptedPrefs.getLong(KEY_OFFLINE_LIMIT, 0L)
            
            val state = CounterState(
                cumulative = cumulative,
                counter = counter,
                limit = limit
            )
            
            val signature = signState(state) ?: return@withContext false

            encryptedPrefs.edit()
                .putLong(KEY_OFFLINE_LIMIT, limit)
                .putLong(KEY_CUMULATIVE, cumulative)
                .putInt(KEY_COUNTER, counter)
                .putString(KEY_STATE_SIGNATURE, signature)
                .apply()

            Log.d(TAG, "Counter reset: cumulative=$cumulative, counter=$counter, limit=$limit")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset counter: ${e.message}", e)
            false
        }
    }

    /**
     * Counter state data class
     */
    private data class CounterState(
        val cumulative: Long,
        val counter: Int,
        val limit: Long
    )
}

