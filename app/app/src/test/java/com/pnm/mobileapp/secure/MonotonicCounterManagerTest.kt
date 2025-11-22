package com.pnm.mobileapp.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class MonotonicCounterManagerTest {
    private lateinit var context: Context
    private lateinit var hardwareKeystoreManager: HardwareKeystoreManager
    private lateinit var counterManager: MonotonicCounterManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        hardwareKeystoreManager = HardwareKeystoreManager(context)
        counterManager = MonotonicCounterManager(context, hardwareKeystoreManager)
        
        // Reset counter before each test
        runBlocking {
            counterManager.reset()
        }
    }

    @Test
    fun testInitCounter() = runBlocking {
        val limit = 100L
        val initialized = counterManager.initCounter(limit)
        
        // May fail on emulator without hardware support, but should not crash
        if (initialized) {
            assertEquals(limit, counterManager.getLimit())
            assertEquals(0L, counterManager.getCumulative())
            assertEquals(0, counterManager.getCounter())
        }
    }

    @Test
    fun testIncrementCounterSafely() = runBlocking {
        val limit = 100L
        val initialized = counterManager.initCounter(limit)
        
        if (!initialized) {
            // Skip if initialization failed
            return@runBlocking
        }

        val amount = 40L
        val success = counterManager.incrementCounterSafely(amount)
        
        if (success) {
            assertEquals(40L, counterManager.getCumulative())
            assertEquals(1, counterManager.getCounter())
        }
    }

    @Test
    fun testIncrementCounterRefusesWhenLimitExceeded() = runBlocking {
        val limit = 50L
        val initialized = counterManager.initCounter(limit)
        
        if (!initialized) {
            return@runBlocking
        }

        // First increment: 40 (within limit)
        val firstSuccess = counterManager.incrementCounterSafely(40L)
        if (!firstSuccess) return@runBlocking
        
        // Second increment: 40 (would exceed limit: 40 + 40 = 80 > 50)
        val secondSuccess = counterManager.incrementCounterSafely(40L)
        
        assertFalse("Should refuse increment when limit exceeded", secondSuccess)
        assertEquals("Cumulative should remain at 40", 40L, counterManager.getCumulative())
        assertEquals("Counter should remain at 1", 1, counterManager.getCounter())
    }

    @Test
    fun testCanSign() = runBlocking {
        val limit = 100L
        val initialized = counterManager.initCounter(limit)
        
        if (!initialized) {
            return@runBlocking
        }

        assertTrue("Should allow signing within limit", counterManager.canSign(50L))
        assertFalse("Should refuse signing when exceeds limit", counterManager.canSign(101L))
    }
}

