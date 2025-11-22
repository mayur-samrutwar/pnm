package com.pnm.mobileapp.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(AndroidJUnit4::class)
class CounterManagerTest {
    private lateinit var context: Context
    private lateinit var counterManager: CounterManager
    private lateinit var signer: Signer
    private lateinit var keyPair: KeyPair

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        counterManager = CounterManager(context)
        signer = Signer(context)
        
        // Generate test key pair
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        keyPair = keyPairGenerator.generateKeyPair()

        // Reset counter before each test
        runBlocking {
            counterManager.reset()
        }
    }

    @Test
    fun testInitCounter() = runBlocking {
        counterManager.initCounter(100L)
        assertEquals(100L, counterManager.getLimit())
        assertEquals(0L, counterManager.getCumulative())
        assertEquals(0, counterManager.getCounter())
    }

    @Test
    fun testCanSignWithinLimit() = runBlocking {
        counterManager.initCounter(50L)
        assertTrue(counterManager.canSign(40L))
        assertTrue(counterManager.canSign(10L))
    }

    @Test
    fun testCanSignExceedsLimit() = runBlocking {
        counterManager.initCounter(50L)
        assertFalse(counterManager.canSign(51L))
    }

    @Test
    fun testCanSignAfterPartialUsage() = runBlocking {
        counterManager.initCounter(50L)
        val voucherJson = """{"amount":"30","timestamp":${System.currentTimeMillis()}}"""
        counterManager.signAndIncrement(voucherJson, 30L, signer, keyPair)
        
        assertTrue(counterManager.canSign(20L)) // 30 + 20 = 50, within limit
        assertFalse(counterManager.canSign(21L)) // 30 + 21 = 51, exceeds limit
    }

    @Test
    fun testSignAndIncrement() = runBlocking {
        counterManager.initCounter(100L)
        val voucherJson = """{"amount":"40","timestamp":${System.currentTimeMillis()}}"""
        
        val signature = counterManager.signAndIncrement(voucherJson, 40L, signer, keyPair)
        
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertEquals(40L, counterManager.getCumulative())
        assertEquals(1, counterManager.getCounter())
    }

    @Test
    fun testSignAndIncrementMultiple() = runBlocking {
        counterManager.initCounter(100L)
        
        val voucher1 = """{"amount":"30","timestamp":${System.currentTimeMillis()}}"""
        counterManager.signAndIncrement(voucher1, 30L, signer, keyPair)
        
        val voucher2 = """{"amount":"20","timestamp":${System.currentTimeMillis()}}"""
        counterManager.signAndIncrement(voucher2, 20L, signer, keyPair)
        
        assertEquals(50L, counterManager.getCumulative())
        assertEquals(2, counterManager.getCounter())
    }

    @Test(expected = IllegalStateException::class)
    fun testSignAndIncrementExceedsLimit() = runBlocking {
        counterManager.initCounter(50L)
        
        // First sign: 40 (within limit)
        val voucher1 = """{"amount":"40","timestamp":${System.currentTimeMillis()}}"""
        counterManager.signAndIncrement(voucher1, 40L, signer, keyPair)
        
        // Second sign: 40 (would exceed limit: 40 + 40 = 80 > 50)
        val voucher2 = """{"amount":"40","timestamp":${System.currentTimeMillis()}}"""
        try {
            counterManager.signAndIncrement(voucher2, 40L, signer, keyPair)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Offline limit exceeded") == true)
            // Verify cumulative was not incremented
            assertEquals(40L, counterManager.getCumulative())
            assertEquals(1, counterManager.getCounter())
            throw e
        }
    }

    @Test
    fun testGetCumulative() = runBlocking {
        counterManager.initCounter(100L)
        assertEquals(0L, counterManager.getCumulative())
        
        val voucherJson = """{"amount":"25","timestamp":${System.currentTimeMillis()}}"""
        counterManager.signAndIncrement(voucherJson, 25L, signer, keyPair)
        
        assertEquals(25L, counterManager.getCumulative())
    }

    @Test
    fun testCounterIncrements() = runBlocking {
        counterManager.initCounter(100L)
        assertEquals(0, counterManager.getCounter())
        
        val voucher1 = """{"amount":"10","timestamp":${System.currentTimeMillis()}}"""
        counterManager.signAndIncrement(voucher1, 10L, signer, keyPair)
        assertEquals(1, counterManager.getCounter())
        
        val voucher2 = """{"amount":"10","timestamp":${System.currentTimeMillis()}}"""
        counterManager.signAndIncrement(voucher2, 10L, signer, keyPair)
        assertEquals(2, counterManager.getCounter())
    }

    @Test
    fun testReset() = runBlocking {
        counterManager.initCounter(100L)
        val voucherJson = """{"amount":"50","timestamp":${System.currentTimeMillis()}}"""
        counterManager.signAndIncrement(voucherJson, 50L, signer, keyPair)
        
        assertEquals(50L, counterManager.getCumulative())
        assertEquals(1, counterManager.getCounter())
        
        counterManager.reset()
        
        assertEquals(0L, counterManager.getCumulative())
        assertEquals(0, counterManager.getCounter())
        assertEquals(100L, counterManager.getLimit()) // Limit should remain
    }
}

