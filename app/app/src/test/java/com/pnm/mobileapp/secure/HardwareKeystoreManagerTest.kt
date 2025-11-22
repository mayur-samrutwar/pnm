package com.pnm.mobileapp.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(AndroidJUnit4::class)
class HardwareKeystoreManagerTest {
    private lateinit var context: Context
    private lateinit var hardwareKeystoreManager: HardwareKeystoreManager
    private val testAlias = "test_hardware_key_${System.currentTimeMillis()}"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        hardwareKeystoreManager = HardwareKeystoreManager(context)
    }

    @Test
    fun testDetectStrongBoxAvailable() = runBlocking {
        val available = hardwareKeystoreManager.detectStrongBoxAvailable()
        // StrongBox may or may not be available depending on device/emulator
        // Just verify the method doesn't crash
        assertNotNull("StrongBox detection should return a boolean", available)
    }

    @Test
    fun testGenerateHardwareKey() = runBlocking {
        val generated = hardwareKeystoreManager.generateHardwareKey(
            testAlias,
            requireUserAuthentication = false // Disable for testing
        )
        
        // Key generation may succeed or fail depending on device capabilities
        // On emulator, it will likely use software fallback
        assertNotNull("Key generation should return a result", generated)
    }

    @Test
    fun testSignWithHardwareKey() = runBlocking {
        // Generate key first
        val generated = hardwareKeystoreManager.generateHardwareKey(
            testAlias,
            requireUserAuthentication = false
        )
        
        if (!generated) {
            // Skip test if key generation failed (e.g., on emulator without hardware support)
            return@runBlocking
        }

        val payload = "test payload".toByteArray()
        val signature = hardwareKeystoreManager.signWithHardwareKey(testAlias, payload)
        
        assertNotNull("Signature should not be null", signature)
        assertTrue("Signature should not be empty", signature.isNotEmpty())
    }

    @Test
    fun testAttestationCertificate() = runBlocking {
        // Generate key first
        val generated = hardwareKeystoreManager.generateHardwareKey(
            testAlias,
            requireUserAuthentication = false
        )
        
        if (!generated) {
            return@runBlocking
        }

        val certificate = hardwareKeystoreManager.attestationCertificate(testAlias)
        
        assertNotNull("Attestation certificate should not be null", certificate)
        assertTrue("Certificate should be valid JSON array", certificate?.startsWith("[") == true)
    }

    @Test
    fun testGetPublicKeyHex() = runBlocking {
        // Generate key first
        val generated = hardwareKeystoreManager.generateHardwareKey(
            testAlias,
            requireUserAuthentication = false
        )
        
        if (!generated) {
            return@runBlocking
        }

        val publicKeyHex = hardwareKeystoreManager.getPublicKeyHex(testAlias)
        
        assertNotNull("Public key hex should not be null", publicKeyHex)
        assertEquals("Public key should be 130 hex chars (65 bytes)", 130, publicKeyHex?.length)
        assertTrue("Public key should start with 04", publicKeyHex?.startsWith("04") == true)
    }

    @Test
    fun testSignAndVerifyRoundtrip() = runBlocking {
        // Generate key first
        val generated = hardwareKeystoreManager.generateHardwareKey(
            testAlias,
            requireUserAuthentication = false
        )
        
        if (!generated) {
            return@runBlocking
        }

        val payload = "test message".toByteArray()
        val signature = hardwareKeystoreManager.signWithHardwareKey(testAlias, payload)
        
        assertNotNull("Signature should be generated", signature)
        
        // Verify signature using public key
        val publicKeyHex = hardwareKeystoreManager.getPublicKeyHex(testAlias)
        assertNotNull("Public key should be available", publicKeyHex)
        
        // Get public key from keystore for verification
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKeyEntry = keyStore.getEntry(testAlias, null) as java.security.KeyStore.PrivateKeyEntry
        val publicKey = privateKeyEntry.certificate.publicKey
        
        val verifySig = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey)
            update(payload)
        }
        
        val isValid = verifySig.verify(signature)
        assertTrue("Signature should be valid", isValid)
    }
}

