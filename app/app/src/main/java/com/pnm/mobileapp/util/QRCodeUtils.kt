package com.pnm.mobileapp.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QRCodeUtils {
    /**
     * Generate QR code bitmap from UTF-8 string
     */
    fun generateQRCode(text: String, width: Int = 512, height: Int = 512): Bitmap {
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    /**
     * Decode QR code from string (UTF-8)
     * Returns the decoded string or null if invalid
     */
    fun decodeQRCode(encodedText: String): String? {
        // For QR code scanning, the text is already decoded by the scanner
        // This is a utility function for validation
        return try {
            if (encodedText.isBlank()) null else encodedText
        } catch (e: Exception) {
            null
        }
    }
}

