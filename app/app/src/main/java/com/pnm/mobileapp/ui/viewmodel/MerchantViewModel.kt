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
    private val merchantEthAddress: String? = null // Merchant's Ethereum address for receiving payments
) : ViewModel() {
    private val _syncResponse = MutableStateFlow<String?>(null)
    val syncResponse: StateFlow<String?> = _syncResponse

    val pendingSlips = pendingSlipDao.getAllSlips()

    suspend fun saveSlip(slip: Slip) {
        pendingSlipDao.insertSlip(slip)
    }

    fun syncWithHub(slip: Slip, isOnline: Boolean, merchantAddress: String? = null) {
        viewModelScope.launch {
            try {
                // Use provided merchant address, or fallback to stored one, or use device address
                val payeeAddress = merchantAddress ?: merchantEthAddress ?: slip.userAddress
                
                if (payeeAddress == "0x0000000000000000000000000000000000000000" || payeeAddress.isEmpty()) {
                    _syncResponse.value = "Error: Merchant address not configured. Please set your Ethereum address in settings."
                    return@launch
                }
                
                // Convert Slip to HubVoucher with merchant's address as payee
                val hubVoucher = slip.toHubVoucher(payeeAddress)
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
                        // Update slip status
                        val updatedSlip = slip.copy(status = SlipStatus.REDEEMED)
                        pendingSlipDao.updateSlip(updatedSlip)
                        response.body()?.message ?: "Success"
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

    fun syncSelectedSlips(slips: List<Slip>, isOnline: Boolean, merchantAddress: String? = null) {
        viewModelScope.launch {
            slips.forEach { slip ->
                syncWithHub(slip, isOnline, merchantAddress)
            }
        }
    }
}

