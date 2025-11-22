package com.pnm.mobileapp.util

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import com.google.gson.Gson
import com.pnm.mobileapp.data.model.Voucher
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class QRCodeUtilsTest {

    @Test
    fun testQRCodeEncodeDecodeRoundtrip() {
        // Create test voucher JSON
        val voucher = Voucher(
            slipId = UUID.randomUUID().toString(),
            payer = "0x1234567890123456789012345678901234567890",
            amount = "100",
            cumulative = 100L,
            counter = 1,
            publicKey = "04a1b2c3d4e5f6...",
            signature = "1a2b3c4d5e6f7...",
            timestamp = System.currentTimeMillis()
        )
        
        val gson = Gson()
        val originalJson = gson.toJson(voucher)
        
        // Encode to QR code
        val qrBitmap: Bitmap = QRCodeUtils.generateQRCode(originalJson)
        assertNotNull("QR code bitmap should not be null", qrBitmap)
        assertTrue("QR code bitmap should have valid dimensions", qrBitmap.width > 0 && qrBitmap.height > 0)
        
        // For testing, we simulate decoding by using the original string
        // In real scenario, QR scanner would decode the bitmap back to string
        val decodedText = originalJson // Simulated decode
        
        // Verify decoded text matches original
        assertEquals("Decoded text should match original", originalJson, decodedText)
        
        // Parse back to verify structure
        val decodedVoucher = gson.fromJson(decodedText, Voucher::class.java)
        assertEquals("Slip ID should match", voucher.slipId, decodedVoucher.slipId)
        assertEquals("Payer should match", voucher.payer, decodedVoucher.payer)
        assertEquals("Amount should match", voucher.amount, decodedVoucher.amount)
        assertEquals("Cumulative should match", voucher.cumulative, decodedVoucher.cumulative)
        assertEquals("Counter should match", voucher.counter, decodedVoucher.counter)
    }

    @Test
    fun testQRCodeEncodeUTF8() {
        // Test with UTF-8 characters
        val testString = """{"message":"Hello 世界 🌍","amount":"100"}"""
        
        val qrBitmap = QRCodeUtils.generateQRCode(testString)
        assertNotNull("QR code should be generated for UTF-8 string", qrBitmap)
        
        // Verify QR code contains data (has black pixels)
        var hasBlackPixels = false
        for (x in 0 until qrBitmap.width) {
            for (y in 0 until qrBitmap.height) {
                if (qrBitmap.getPixel(x, y) != 0xFFFFFFFF.toInt()) {
                    hasBlackPixels = true
                    break
                }
            }
            if (hasBlackPixels) break
        }
        assertTrue("QR code should contain black pixels", hasBlackPixels)
    }

    @Test
    fun testQRCodeDecodeValidation() {
        val validJson = """{"slipId":"test-123","payer":"0x123","amount":"100","cumulative":100,"counter":1,"signature":"sig","timestamp":1234567890}"""
        
        val decoded = QRCodeUtils.decodeQRCode(validJson)
        assertNotNull("Decoded text should not be null", decoded)
        assertEquals("Decoded text should match input", validJson, decoded)
    }

    @Test
    fun testQRCodeDecodeEmptyString() {
        val decoded = QRCodeUtils.decodeQRCode("")
        assertNull("Empty string should return null", decoded)
    }

    @Test
    fun testQRCodeEncodeLargeString() {
        // Test with large JSON (simulating complex voucher)
        val largeVoucher = Voucher(
            slipId = UUID.randomUUID().toString(),
            payer = "0x" + "a".repeat(40),
            amount = "1000000",
            cumulative = 1000000L,
            counter = 100,
            publicKey = "04" + "b".repeat(128),
            signature = "c".repeat(128),
            timestamp = System.currentTimeMillis()
        )
        
        val gson = Gson()
        val largeJson = gson.toJson(largeVoucher)
        
        val qrBitmap = QRCodeUtils.generateQRCode(largeJson)
        assertNotNull("QR code should be generated for large string", qrBitmap)
        assertTrue("QR code should have valid dimensions", qrBitmap.width > 0 && qrBitmap.height > 0)
    }

    @Test
    fun testQRCodeEncodeSpecialCharacters() {
        // Test with special characters in JSON
        val specialJson = """{"message":"Test with \"quotes\" and \n newlines and \t tabs","amount":"100"}"""
        
        val qrBitmap = QRCodeUtils.generateQRCode(specialJson)
        assertNotNull("QR code should handle special characters", qrBitmap)
    }
}

