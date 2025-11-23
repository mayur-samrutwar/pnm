package com.pnm.mobileapp.ui.screen

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.pnm.mobileapp.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.data.model.Voucher
import com.pnm.mobileapp.ui.component.SoftwareFallbackBanner
import com.pnm.mobileapp.ui.viewmodel.AppViewModel
import com.pnm.mobileapp.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onShowSlipDialog: (com.pnm.mobileapp.data.model.Slip, String) -> Unit,
    activity: androidx.fragment.app.FragmentActivity?,
    onNavigateToRequest: () -> Unit = {}
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
    val cumulative by viewModel.cumulative.collectAsState()

    // Refresh balances when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.fetchUSDCBalance()
        viewModel.fetchVaultBalance()
    }
    
    // Update remaining balance when vault balance or cumulative changes
    LaunchedEffect(vaultBalance, cumulative) {
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
        // Welcome Message
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Welcome back,",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF64748B),
                    fontSize = 20.sp
                )
            )
            Text(
                text = "astro",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    fontSize = 28.sp
                )
            )
        }

        // Software Fallback Warning
        if (showSoftwareFallbackWarning) {
            SoftwareFallbackBanner()
        }

        // Balance Cards - Side by Side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // USDC Balance Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2775FF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Icon at top - no background
                    Image(
                        painter = painterResource(id = R.drawable.usdc),
                        contentDescription = "USDC",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    
                    // Balance and label at bottom
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isLoadingBalance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = usdcBalance?.let { 
                                    val amount = it.toDoubleOrNull() ?: 0.0
                                    String.format("%.2f", amount)
                                } ?: "0.00",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                        }
                        Text(
                            text = "USDC",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
            }

            // Offline Balance Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF7C3AED)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Icon at top - no background
                    Icon(
                        imageVector = Icons.Default.Wallet,
                        contentDescription = "Wallet",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                    
                    // Balance and label at bottom
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isLoadingVaultBalance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = String.format("%.0f", remainingBalance),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                        }
                        Text(
                            text = "OFFLINE USDC",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
            }
        }

        // Action Cards - 2x2 Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // First Row: Transfer and Receive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Transfer Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clickable { /* TODO: Implement transfer */ },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Transfer",
                            modifier = Modifier.size(28.dp),
                            tint = Color(0xFF6366F1)
                        )
                        Text(
                            text = "Transfer",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                // Receive Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clickable { /* TODO: Implement receive */ },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallReceived,
                            contentDescription = "Receive",
                            modifier = Modifier.size(28.dp),
                            tint = Color(0xFF10B981)
                        )
                        Text(
                            text = "Receive",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }

            // Second Row: Pay and Deposit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pay Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clickable { showCreateSlipDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = "Pay",
                            modifier = Modifier.size(28.dp),
                            tint = Color(0xFFEF4444)
                        )
                        Text(
                            text = "Pay",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                // Deposit Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clickable { showDepositDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "Deposit",
                            modifier = Modifier.size(28.dp),
                            tint = Color(0xFF10B981)
                        )
                        Text(
                            text = "Deposit",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
            
            // Third Row: Request
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Request Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clickable { onNavigateToRequest() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.RequestQuote,
                            contentDescription = "Request",
                            modifier = Modifier.size(28.dp),
                            tint = Color(0xFF8B5CF6)
                        )
                        Text(
                            text = "Request",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
        }
        
        // Create Slip Dialog
        if (showCreateSlipDialog) {
            Dialog(
                onDismissRequest = { showCreateSlipDialog = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Create Payment",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    fontSize = 22.sp
                                )
                            )
                            IconButton(
                                onClick = { showCreateSlipDialog = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }
                        
                        // Amount Input
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it },
                            label = { 
                                Text(
                                    "Amount (USDC)",
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedLabelColor = Color(0xFF6366F1),
                                unfocusedLabelColor = Color(0xFF64748B)
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        
                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCreateSlipDialog = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF64748B)
                                )
                            ) {
                                Text(
                                    "Cancel",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            Button(
                                onClick = {
                                    wallet?.let { w ->
                                        CoroutineScope(Dispatchers.Main).launch {
                                            try {
                                                val amountDouble = amountInput.toDoubleOrNull()
                                                if (amountDouble == null) {
                                                    Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }

                                                // Convert USDC to micro USDC (6 decimals)
                                                val amountInMicroUSDC = (amountDouble * 1_000_000).toLong()
                                                
                                                if (!viewModel.canSign(amountInMicroUSDC)) {
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
                                                    ethAddress = w.ethAddress,
                                                    amount = amountInMicroUSDC.toString(),
                                                    cumulative = currentCumulative + amountInMicroUSDC,
                                                    counter = currentCounter + 1,
                                                    publicKey = publicKey,
                                                    signature = "",
                                                    timestamp = timestamp
                                                )
                                                
                                                val voucherJson = Gson().toJson(voucher)
                                                val signature = viewModel.signAndIncrement(voucherJson, amountInMicroUSDC)
                                                
                                                val signedVoucher = voucher.copy(signature = signature)
                                                val signedVoucherJson = Gson().toJson(signedVoucher)
                                                
                                                val slip = Slip(
                                                    slipId = slipId,
                                                    payer = w.address,
                                                    amount = amountInput,
                                                    userAddress = w.address,
                                                    ethAddress = w.ethAddress,
                                                    cumulative = currentCumulative + amountInMicroUSDC,
                                                    counter = currentCounter + 1,
                                                    publicKey = publicKey,
                                                    signature = signature,
                                                    rawJson = signedVoucherJson,
                                                    timestamp = timestamp
                                                )
                                                
                                                showCreateSlipDialog = false
                                                amountInput = ""
                                                
                                                onShowSlipDialog(slip, signedVoucherJson)
                                            } catch (e: IllegalStateException) {
                                                Toast.makeText(context, e.message ?: "Offline limit exceeded", Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = wallet != null && amountInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6366F1),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    "Create",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Deposit Dialog
        if (showDepositDialog) {
            var depositAmountInput by remember { mutableStateOf("") }
            var isDepositing by remember { mutableStateOf(false) }
            var depositError by remember { mutableStateOf<String?>(null) }
            var selectedChainId by remember { mutableStateOf<Int>(Constants.CHAIN_ID_BASE_SEPOLIA) }
            var chainDropdownExpanded by remember { mutableStateOf(false) }
            val supportedChains: List<Int> = Constants.getSupportedChainIds()
            
            Dialog(
                onDismissRequest = { 
                    if (!isDepositing) {
                        showDepositDialog = false
                        depositAmountInput = ""
                        depositError = null
                    }
                }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Deposit to Vault",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    fontSize = 22.sp
                                )
                            )
                            IconButton(
                                onClick = { 
                                    if (!isDepositing) {
                                        showDepositDialog = false
                                        depositAmountInput = ""
                                        depositError = null
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                                enabled = !isDepositing
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }
                        
                        // Error Message
                        if (depositError != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFEF2F2)
                                )
                            ) {
                                Text(
                                    text = depositError!!,
                                    color = Color(0xFFDC2626),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        
                        // Amount Input
                        OutlinedTextField(
                            value = depositAmountInput,
                            onValueChange = { depositAmountInput = it },
                            label = { 
                                Text(
                                    "Amount (USDC)",
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isDepositing,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedLabelColor = Color(0xFF10B981),
                                unfocusedLabelColor = Color(0xFF64748B),
                                disabledBorderColor = Color(0xFFE2E8F0),
                                disabledLabelColor = Color(0xFF94A3B8)
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        
                        // Chain Selection
                        ExposedDropdownMenuBox(
                            expanded = chainDropdownExpanded,
                            onExpandedChange = { chainDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = Constants.getChainName(selectedChainId),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Deposit to Chain") },
                                trailingIcon = { 
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = chainDropdownExpanded
                                    ) 
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = !isDepositing,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    focusedLabelColor = Color(0xFF10B981),
                                    unfocusedLabelColor = Color(0xFF64748B),
                                    disabledBorderColor = Color(0xFFE2E8F0),
                                    disabledLabelColor = Color(0xFF94A3B8)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = chainDropdownExpanded,
                                onDismissRequest = { chainDropdownExpanded = false }
                            ) {
                                supportedChains.forEach { chainId ->
                                    DropdownMenuItem(
                                        text = { Text(Constants.getChainName(chainId)) },
                                        onClick = {
                                            selectedChainId = chainId
                                            chainDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Available Balance
                        if (usdcBalance != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Available Balance",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF64748B)
                                    )
                                )
                                Text(
                                    text = "$usdcBalance USDC",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFF1E293B),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                        
                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    showDepositDialog = false
                                    depositAmountInput = ""
                                    depositError = null
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isDepositing,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF64748B)
                                )
                            ) {
                                Text(
                                    "Cancel",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
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
                                        val result = viewModel.depositToVault(amount, selectedChainId)
                                        if (result.isSuccess) {
                                            showDepositDialog = false
                                            depositAmountInput = ""
                                            Toast.makeText(context, result.getOrNull() ?: "Deposit successful", Toast.LENGTH_LONG).show()
                                            viewModel.fetchUSDCBalance()
                                            viewModel.fetchVaultBalance() // Refresh vault balance after deposit
                                        } else {
                                            depositError = result.exceptionOrNull()?.message ?: "Deposit failed"
                                            isDepositing = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isDepositing && depositAmountInput.isNotBlank() && depositAmountInput.toDoubleOrNull() != null,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                )
                            ) {
                                if (isDepositing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Depositing...",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                } else {
                                    Text(
                                        "Deposit",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

