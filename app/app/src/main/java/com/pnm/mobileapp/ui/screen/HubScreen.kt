package com.pnm.mobileapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.pnm.mobileapp.data.api.HubApiService
import com.pnm.mobileapp.data.api.VoucherRequest
import com.pnm.mobileapp.data.api.toHubVoucher
import com.pnm.mobileapp.data.model.Slip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    hubApiService: HubApiService
) {
    var validateInput by remember { mutableStateOf("") }
    var redeemInput by remember { mutableStateOf("") }
    var validateResponse by remember { mutableStateOf<String?>(null) }
    var redeemResponse by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Hub API Testing", style = MaterialTheme.typography.headlineMedium)

        // Validate Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Validate Slip", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = validateInput,
                    onValueChange = { validateInput = it },
                    label = { Text("Slip JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(
                    onClick = {
                        try {
                            val gson = Gson()
                            val slip = gson.fromJson(validateInput, Slip::class.java)
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    // Convert Slip to VoucherRequest
                                    val hubVoucher = slip.toHubVoucher()
                                    val request = VoucherRequest(voucher = hubVoucher)
                                    val response = hubApiService.validateSlip(request)
                                    validateResponse = if (response.isSuccessful) {
                                        response.body()?.message ?: "Success"
                                    } else {
                                        val errorBody = response.errorBody()?.string() ?: response.message()
                                        "Error: $errorBody"
                                    }
                                } catch (e: Exception) {
                                    validateResponse = "Error: ${e.message}"
                                }
                            }
                        } catch (e: Exception) {
                            validateResponse = "Parse error: ${e.message}"
                        }
                    }
                ) {
                    Text("Call /api/v1/validate")
                }
                validateResponse?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Redeem Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Redeem Slip", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = redeemInput,
                    onValueChange = { redeemInput = it },
                    label = { Text("Slip JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(
                    onClick = {
                        try {
                            val gson = Gson()
                            val slip = gson.fromJson(redeemInput, Slip::class.java)
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    // Convert Slip to VoucherRequest
                                    val hubVoucher = slip.toHubVoucher()
                                    val request = VoucherRequest(voucher = hubVoucher, preferredChainId = null)
                                    val response = hubApiService.redeemSlip(request)
                                    redeemResponse = if (response.isSuccessful) {
                                        response.body()?.message ?: "Success"
                                    } else {
                                        val errorBody = response.errorBody()?.string() ?: response.message()
                                        "Error: $errorBody"
                                    }
                                } catch (e: Exception) {
                                    redeemResponse = "Error: ${e.message}"
                                }
                            }
                        } catch (e: Exception) {
                            redeemResponse = "Parse error: ${e.message}"
                        }
                    }
                ) {
                    Text("Call /api/v1/redeem")
                }
                redeemResponse?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

