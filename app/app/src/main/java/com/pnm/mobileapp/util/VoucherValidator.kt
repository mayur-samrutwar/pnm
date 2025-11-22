package com.pnm.mobileapp.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pnm.mobileapp.data.model.Voucher

/**
 * Validates voucher JSON schema
 */
object VoucherValidator {
    private val gson = Gson()

    /**
     * Validate voucher JSON against required schema
     * Required fields: slipId, payer, amount, cumulative, counter, signature, timestamp
     */
    fun validateSchema(json: String): Boolean {
        return try {
            val jsonObject = JsonParser.parseString(json).asJsonObject
            
            // Check required fields
            val requiredFields = listOf(
                "slipId", "payer", "amount", 
                "cumulative", "counter", "signature", "timestamp"
            )
            
            val hasAllFields = requiredFields.all { field ->
                jsonObject.has(field) && !jsonObject.get(field).isJsonNull
            }
            
            if (!hasAllFields) {
                return false
            }
            
            // Validate field types
            jsonObject.get("slipId").asString.isNotBlank() &&
            jsonObject.get("payer").asString.isNotBlank() &&
            jsonObject.get("amount").asString.isNotBlank() &&
            jsonObject.get("cumulative").asLong >= 0 &&
            jsonObject.get("counter").asInt >= 0 &&
            jsonObject.get("signature").asString.isNotBlank() &&
            jsonObject.get("timestamp").asLong > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parse voucher JSON string to Voucher object
     */
    fun parseVoucher(json: String): Voucher? {
        return try {
            if (!validateSchema(json)) {
                return null
            }
            gson.fromJson(json, Voucher::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

