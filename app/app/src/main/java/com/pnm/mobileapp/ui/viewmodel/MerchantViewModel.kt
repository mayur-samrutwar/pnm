package com.pnm.mobileapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnm.mobileapp.data.api.HubApiService
import com.pnm.mobileapp.data.dao.PendingSlipDao
import com.pnm.mobileapp.data.model.Slip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MerchantViewModel(
    private val pendingSlipDao: PendingSlipDao,
    private val hubApiService: HubApiService
) : ViewModel() {
    private val _syncResponse = MutableStateFlow<String?>(null)
    val syncResponse: StateFlow<String?> = _syncResponse

    suspend fun saveSlip(slip: Slip) {
        pendingSlipDao.insertSlip(slip)
    }

    fun syncWithHub(slip: Slip, isOnline: Boolean) {
        viewModelScope.launch {
            try {
                if (isOnline) {
                    val response = hubApiService.validateSlip(slip)
                    _syncResponse.value = if (response.isSuccessful) {
                        response.body()?.message ?: "Success"
                    } else {
                        "Error: ${response.message()}"
                    }
                } else {
                    val response = hubApiService.redeemSlip(slip)
                    _syncResponse.value = if (response.isSuccessful) {
                        response.body()?.message ?: "Success"
                    } else {
                        "Error: ${response.message()}"
                    }
                }
            } catch (e: Exception) {
                _syncResponse.value = "Error: ${e.message}"
            }
        }
    }
}

