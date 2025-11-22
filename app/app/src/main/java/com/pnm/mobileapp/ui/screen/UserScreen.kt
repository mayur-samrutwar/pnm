package com.pnm.mobileapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.data.model.Voucher
import com.pnm.mobileapp.ui.viewmodel.AppViewModel
import com.pnm.mobileapp.util.Constants
import com.pnm.mobileapp.util.QRCodeUtils
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    viewModel: AppViewModel,
    onShowSlipDialog: (Slip, String) -> Unit
) {
    val context = LocalContext.current
    val wallet by viewModel.wallet.collectAsState()
    val cumulative by viewModel.cumulative.collectAsState()
    val counter by viewModel.counter.collectAsState()
    var amount by remember { mutableStateOf("") }
    var offlineLimit by remember { mutableStateOf("") }
    var showDepositInfo by remember { mutableStateOf(false) }
    var showLimitSetup by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Generate Wallet Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Wallet", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { viewModel.generateWallet() }) {
                    Text("Generate Wallet")
                }
                wallet?.let {
                    Text(
                        "Address:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    SelectionContainer {
                        Text(
                            it.address,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Offline Limit Setup Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Offline Limit", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { showLimitSetup = !showLimitSetup }) {
                        Text(if (showLimitSetup) "Hide" else "Setup")
                    }
                }
                if (showLimitSetup) {
                    OutlinedTextField(
                        value = offlineLimit,
                        onValueChange = { offlineLimit = it },
                        label = { Text("Offline Limit") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            offlineLimit.toLongOrNull()?.let { limit ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    viewModel.initCounter(limit)
                                    Toast.makeText(context, "Offline limit set to $limit", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = offlineLimit.toLongOrNull() != null
                    ) {
                        Text("Set Limit")
                    }
                }
                Text("Cumulative: $cumulative", style = MaterialTheme.typography.bodyMedium)
                Text("Counter: $counter", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Deposit Instructions Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { showDepositInfo = !showDepositInfo }) {
                    Text("Deposit Instructions")
                }
                if (showDepositInfo) {
                    Text("Vault Contract:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(Constants.VAULT_CONTRACT_ADDRESS)
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(Constants.VAULT_CONTRACT_ADDRESS))
                        }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                                contentDescription = "Copy"
                            )
                        }
                    }
                    Text("USDC Token:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(Constants.USDC_TOKEN_CONTRACT)
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(Constants.USDC_TOKEN_CONTRACT))
                        }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                                contentDescription = "Copy"
                            )
                        }
                    }
                }
            }
        }

        // Create Offline Payment Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Create Offline Payment", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        wallet?.let { w ->
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val amountLong = amount.toLongOrNull()
                                    if (amountLong == null) {
                                        Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    if (!viewModel.canSign(amountLong)) {
                                        Toast.makeText(context, "Offline limit exceeded", Toast.LENGTH_LONG).show()
                                        return@launch
                                    }

                                    val cumulative = viewModel.getCumulative()
                                    val counter = viewModel.counter.value
                                    val slipId = UUID.randomUUID().toString()
                                    val timestamp = System.currentTimeMillis()
                                    
                                    // Create voucher JSON
                                    val voucher = Voucher(
                                        slipId = slipId,
                                        payer = w.address,
                                        amount = amount,
                                        cumulative = cumulative + amountLong,
                                        counter = counter + 1,
                                        publicKey = viewModel.getPublicKeyHex(),
                                        signature = "",
                                        timestamp = timestamp
                                    )
                                    
                                    val voucherJson = Gson().toJson(voucher)
                                    val signature = viewModel.signAndIncrement(voucherJson, amountLong)
                                    
                                    // Update voucher with signature
                                    val signedVoucher = voucher.copy(signature = signature)
                                    val finalVoucherJson = Gson().toJson(signedVoucher)
                                    
                                    val slip = Slip(
                                        slipId = slipId,
                                        payer = w.address,
                                        amount = amount,
                                        userAddress = w.address,
                                        cumulative = cumulative + amountLong,
                                        counter = counter + 1,
                                        publicKey = viewModel.getPublicKeyHex(),
                                        signature = signature,
                                        rawJson = finalVoucherJson,
                                        timestamp = timestamp
                                    )
                                    onShowSlipDialog(slip, finalVoucherJson)
                                } catch (e: IllegalStateException) {
                                    Toast.makeText(context, e.message ?: "Offline limit exceeded", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = wallet != null && amount.isNotBlank()
                ) {
                    Text("Create Offline Payment")
                }
            }
        }
    }
}

