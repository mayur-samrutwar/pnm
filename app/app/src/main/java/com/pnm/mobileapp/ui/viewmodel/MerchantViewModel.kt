package com.pnm.mobileapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnm.mobileapp.data.api.HubApiService
import com.pnm.mobileapp.data.api.VoucherRequest
import com.pnm.mobileapp.data.api.toHubVoucher
import com.pnm.mobileapp.data.dao.PendingSlipDao
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.data.model.SlipStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MerchantViewModel(
    private val pendingSlipDao: PendingSlipDao,
    private val hubApiService: HubApiService,
    private val merchantEthAddress: String? = null, // Merchant's Ethereum address for receiving payments
    private val payerEthAddress: String? = null // Payer's Ethereum address (for fixing old vouchers)
) : ViewModel() {
    private val _syncResponse = MutableStateFlow<String?>(null)
    val syncResponse: StateFlow<String?> = _syncResponse

    val pendingSlips = pendingSlipDao.getAllSlips()

    suspend fun saveSlip(slip: Slip) {
        pendingSlipDao.insertSlip(slip)
    }

    fun syncWithHub(slip: Slip, isOnline: Boolean, merchantAddress: String? = null, payerAddress: String? = null) {
        viewModelScope.launch {
            try {
                // Use provided merchant address, or fallback to stored one, or use device address
                val payeeAddress = merchantAddress ?: merchantEthAddress ?: slip.userAddress
                
                if (payeeAddress == "0x0000000000000000000000000000000000000000" || payeeAddress.isEmpty()) {
                    _syncResponse.value = "Error: Merchant address not configured. Please set your Ethereum address in settings."
                    return@launch
                }
                
                // Use provided payer address, or fallback to stored one, or use constructor parameter
                // This fixes old vouchers that don't have ethAddress set
                // NOTE: payerAddress should be the voucher creator's Ethereum address, NOT the merchant's address
                val finalPayerAddress = payerAddress ?: payerEthAddress
                android.util.Log.d("MerchantViewModel", "syncWithHub: slip.ethAddress=${slip.ethAddress}, payerAddress=$payerAddress, payerEthAddress=$payerEthAddress, finalPayerAddress=$finalPayerAddress")
                
                // Convert Slip to HubVoucher with merchant's address as payee
                // Pass finalPayerAddress to fix old vouchers that don't have ethAddress set
                val hubVoucher = slip.toHubVoucher(payeeAddress, finalPayerAddress)
                android.util.Log.d("MerchantViewModel", "syncWithHub: Created HubVoucher with payerAddress=${hubVoucher.payerAddress}, payeeAddress=${hubVoucher.payeeAddress}")
                val request = VoucherRequest(voucher = hubVoucher)
                
                if (isOnline) {
                    val response = hubApiService.validateSlip(request)
                    _syncResponse.value = if (response.isSuccessful) {
                        // Update slip status
                        val updatedSlip = slip.copy(status = SlipStatus.VALIDATED)
                        pendingSlipDao.updateSlip(updatedSlip)
                        response.body()?.message ?: "Success"
                    } else {
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        "Error: $errorBody"
                    }
                } else {
                    val response = hubApiService.redeemSlip(request)
                    _syncResponse.value = if (response.isSuccessful) {
                        val redeemResponse = response.body()
                        // Only mark as REDEEMED if status is "redeemed" (on-chain redemption succeeded)
                        val newStatus = when (redeemResponse?.status?.lowercase()) {
                            "redeemed" -> SlipStatus.REDEEMED
                            "validated" -> SlipStatus.VALIDATED
                            else -> SlipStatus.VALIDATED // Default to validated if status unclear
                        }
                        val updatedSlip = slip.copy(status = newStatus)
                        pendingSlipDao.updateSlip(updatedSlip)
                        redeemResponse?.message ?: redeemResponse?.status ?: "Success"
                    } else {
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        "Error: $errorBody"
                    }
                }
            } catch (e: Exception) {
                _syncResponse.value = "Error: ${e.message}"
            }
        }
    }

    fun syncSelectedSlips(slips: List<Slip>, isOnline: Boolean, merchantAddress: String? = null, payerAddress: String? = null) {
        viewModelScope.launch {
            slips.forEach { slip ->
                syncWithHub(slip, isOnline, merchantAddress, payerAddress)
            }
        }
    }
}

