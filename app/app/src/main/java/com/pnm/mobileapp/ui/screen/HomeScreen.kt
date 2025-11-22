package com.pnm.mobileapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.data.model.Voucher
import com.pnm.mobileapp.ui.component.SoftwareFallbackBanner
import com.pnm.mobileapp.ui.viewmodel.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onShowSlipDialog: (com.pnm.mobileapp.data.model.Slip, String) -> Unit,
    activity: androidx.fragment.app.FragmentActivity?
) {
    val wallet by viewModel.wallet.collectAsState()
    val showSoftwareFallbackWarning by viewModel.showSoftwareFallbackWarning.collectAsState()
    val usdcBalance by viewModel.usdcBalance.collectAsState()
    val isLoadingBalance by viewModel.isLoadingBalance.collectAsState()
    var showCreateSlipDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    var remainingBalance by remember { mutableStateOf(0.0) }
    val vaultBalance by viewModel.vaultBalance.collectAsState()
    val isLoadingVaultBalance by viewModel.isLoadingVaultBalance.collectAsState()

    // Refresh balances when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.fetchUSDCBalance()
        viewModel.fetchVaultBalance()
    }
    
    // Update remaining balance when vault balance or cumulative changes
    LaunchedEffect(vaultBalance) {
        remainingBalance = viewModel.getRemainingBalance().toDouble() / 1_000_000.0 // Convert to USDC
    }
    
    // Refresh USDC balance when wallet changes
    LaunchedEffect(wallet?.ethAddress) {
        if (wallet?.ethAddress != null && !wallet!!.ethAddress.startsWith("0x0000")) {
            viewModel.fetchUSDCBalance()
            viewModel.fetchVaultBalance()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Software Fallback Warning
        if (showSoftwareFallbackWarning) {
            SoftwareFallbackBanner()
        }

        // Balance Cards - Side by Side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // USDC Balance Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF10B981)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "USDC",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isLoadingBalance) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = usdcBalance?.let { "$it" } ?: "0.0",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 24.sp
                                    )
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "USDC",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            // Offline Balance Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF6366F1)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isLoadingVaultBalance) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = String.format("%.2f", remainingBalance),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 24.sp
                                    )
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Wallet,
                            contentDescription = "Wallet",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Quick Actions
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B),
                fontSize = 18.sp
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pay Action Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showCreateSlipDialog = true },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = "Pay",
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFF6366F1)
                    )
                    Text(
                        text = "Pay",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B),
                            fontSize = 16.sp
                        )
                    )
                }
            }

            // Deposit Action Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showDepositDialog = true },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Deposit",
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFF6366F1)
                    )
                    Text(
                        text = "Deposit",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B),
                            fontSize = 16.sp
                        )
                    )
                }
            }
        }
        
        // Create Slip Dialog
        if (showCreateSlipDialog) {
            AlertDialog(
                onDismissRequest = { showCreateSlipDialog = false },
                title = {
                    Text(
                        text = "Create Payment Slip",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it },
                            label = { Text("Amount") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            wallet?.let { w ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        val amountLong = amountInput.toLongOrNull()
                                        if (amountLong == null) {
                                            Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }

                                        if (!viewModel.canSign(amountLong)) {
                                            Toast.makeText(context, "Offline limit exceeded", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val currentCumulative = viewModel.getCumulative()
                                        val currentCounter = viewModel.counter.value
                                        
                                        val slipId = UUID.randomUUID().toString()
                                        val timestamp = System.currentTimeMillis()
                                        val publicKey = viewModel.getPublicKeyHex()

                                        val voucher = Voucher(
                                            slipId = slipId,
                                            payer = w.address,
                                            amount = amountInput,
                                            cumulative = currentCumulative + amountLong,
                                            counter = currentCounter + 1,
                                            publicKey = publicKey,
                                            signature = "",
                                            timestamp = timestamp
                                        )
                                        
                                        val voucherJson = Gson().toJson(voucher)
                                        val signature = viewModel.signAndIncrement(voucherJson, amountLong)
                                        
                                        val signedVoucher = voucher.copy(signature = signature)
                                        val signedVoucherJson = Gson().toJson(signedVoucher)
                                        
                                        val slip = Slip(
                                            slipId = slipId,
                                            payer = w.address,
                                            amount = amountInput,
                                            userAddress = w.address,
                                            cumulative = currentCumulative + amountLong,
                                            counter = currentCounter + 1,
                                            publicKey = publicKey,
                                            signature = signature,
                                            rawJson = signedVoucherJson,
                                            timestamp = timestamp
                                        )
                                        
                                        showCreateSlipDialog = false
                                        amountInput = ""
                                        
                                        // Refresh balance after creating slip
                                        remainingBalance = viewModel.getRemainingBalance().toDouble() / 1_000_000.0
                                        
                                        onShowSlipDialog(slip, signedVoucherJson)
                                    } catch (e: IllegalStateException) {
                                        Toast.makeText(context, e.message ?: "Offline limit exceeded", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = wallet != null && amountInput.isNotBlank()
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateSlipDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Deposit Dialog
        if (showDepositDialog) {
            var depositAmountInput by remember { mutableStateOf("") }
            var isDepositing by remember { mutableStateOf(false) }
            var depositError by remember { mutableStateOf<String?>(null) }
            
            AlertDialog(
                onDismissRequest = { 
                    showDepositDialog = false
                    depositAmountInput = ""
                    depositError = null
                },
                title = {
                    Text(
                        text = "Deposit to Vault",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (depositError != null) {
                            Text(
                                text = depositError!!,
                                color = Color(0xFFEF4444),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedTextField(
                            value = depositAmountInput,
                            onValueChange = { depositAmountInput = it },
                            label = { Text("Amount (USDC)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isDepositing
                        )
                        if (usdcBalance != null) {
                            Text(
                                text = "Available: $usdcBalance USDC",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B)
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = depositAmountInput.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                depositError = "Please enter a valid amount"
                                return@Button
                            }
                            
                            isDepositing = true
                            depositError = null
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = viewModel.depositToVault(amount)
                                if (result.isSuccess) {
                                    showDepositDialog = false
                                    depositAmountInput = ""
                                    Toast.makeText(context, result.getOrNull() ?: "Deposit successful", Toast.LENGTH_LONG).show()
                                    // Refresh balance
                                    viewModel.fetchUSDCBalance()
                                } else {
                                    depositError = result.exceptionOrNull()?.message ?: "Deposit failed"
                                    isDepositing = false
                                }
                            }
                        },
                        enabled = !isDepositing && depositAmountInput.isNotBlank() && depositAmountInput.toDoubleOrNull() != null
                    ) {
                        if (isDepositing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Depositing...")
                        } else {
                            Text("Deposit")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showDepositDialog = false
                            depositAmountInput = ""
                            depositError = null
                        },
                        enabled = !isDepositing
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

