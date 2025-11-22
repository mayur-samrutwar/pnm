package com.pnm.mobileapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnm.mobileapp.data.model.Wallet
import com.pnm.mobileapp.util.WalletUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet

    fun generateWallet() {
        viewModelScope.launch {
            _wallet.value = WalletUtils.generateWallet()
        }
    }
}

