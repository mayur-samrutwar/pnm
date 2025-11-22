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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.ui.viewmodel.AppViewModel
import com.pnm.mobileapp.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    viewModel: AppViewModel,
    onShowSlipDialog: (Slip) -> Unit
) {
    val wallet by viewModel.wallet.collectAsState()
    var amount by remember { mutableStateOf("") }
    var showDepositInfo by remember { mutableStateOf(false) }
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
                                val voucherJson = """{"amount":"$amount","userAddress":"${w.address}","timestamp":${System.currentTimeMillis()}}"""
                                val signature = viewModel.signVoucher(voucherJson)
                                val publicKey = viewModel.getPublicKeyHex()
                                val slip = Slip(
                                    amount = amount,
                                    userAddress = w.address,
                                    publicKey = publicKey,
                                    signature = signature
                                )
                                onShowSlipDialog(slip)
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

