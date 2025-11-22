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
    var showCreateSlipDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    var offlineLimit by remember { mutableStateOf(0L) }
    var remainingBalance by remember { mutableStateOf(0L) }

    // Refresh offline balance when screen is displayed
    LaunchedEffect(Unit) {
        offlineLimit = viewModel.getOfflineLimit()
        remainingBalance = viewModel.getRemainingBalance()
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

        // Offline Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF6366F1)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Offline Balance",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$remainingBalance",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 36.sp
                            )
                        )
                        if (offlineLimit > 0) {
                            Text(
                                text = "of $offlineLimit available",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Wallet,
                        contentDescription = "Wallet",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
                
                // Progress bar
                if (offlineLimit > 0) {
                    val progress = (remainingBalance.toFloat() / offlineLimit.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
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
                                        
                                        // Refresh balance after creating slip
                                        remainingBalance = viewModel.getRemainingBalance()
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
                                        remainingBalance = viewModel.getRemainingBalance()
                                        
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
        
        // Deposit Dialog (placeholder - can be implemented later)
        if (showDepositDialog) {
            AlertDialog(
                onDismissRequest = { showDepositDialog = false },
                title = {
                    Text(
                        text = "Deposit",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Text("Deposit functionality coming soon")
                },
                confirmButton = {
                    TextButton(onClick = { showDepositDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

