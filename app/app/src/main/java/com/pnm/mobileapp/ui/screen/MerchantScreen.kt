package com.pnm.mobileapp.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.data.model.SlipStatus
import com.pnm.mobileapp.ui.viewmodel.MerchantViewModel
import com.pnm.mobileapp.util.QRCodeUtils
import com.pnm.mobileapp.util.VoucherValidator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantScreen(
    viewModel: MerchantViewModel,
    merchantEthAddress: String? = null, // Merchant's Ethereum address for receiving payments
    @Suppress("UNUSED_PARAMETER") onScanQR: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isOnline by remember { mutableStateOf(false) }
    var isSlipsExpanded by remember { mutableStateOf(true) }
    val syncResponse by viewModel.syncResponse.collectAsState()
    val pendingSlips by viewModel.pendingSlips.collectAsState(initial = emptyList())

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { json ->
            coroutineScope.launch {
                // Validate JSON schema
                if (!VoucherValidator.validateSchema(json)) {
                    Toast.makeText(context, "Invalid voucher JSON schema", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Parse voucher
                val voucher = VoucherValidator.parseVoucher(json)
                if (voucher == null) {
                    Toast.makeText(context, "Failed to parse voucher", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Create Slip from Voucher
                val slip = Slip(
                    slipId = voucher.slipId,
                    payer = voucher.payer,
                    amount = voucher.amount,
                    userAddress = voucher.payer,
                    cumulative = voucher.cumulative,
                    counter = voucher.counter,
                    publicKey = voucher.publicKey,
                    signature = voucher.signature,
                    rawJson = json,
                    timestamp = voucher.timestamp,
                    status = SlipStatus.PENDING
                )
                
                // Save to Room database
                viewModel.saveSlip(slip)
                Toast.makeText(context, "Voucher scanned and saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchQRScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name)
        options.setPrompt("Scan QR Code")
        options.setCameraId(0)
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        barcodeLauncher.launch(options)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchQRScanner()
        }
    }

    fun requestCameraPermissionAndScan() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchQRScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(bottom = 80.dp), // Add bottom padding to avoid nav menu
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scan QR Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Scan Payment Slip", style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = { requestCameraPermissionAndScan() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan QR")
                }
            }
        }

        // Sync Controls Section (moved to top)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Warning if merchant address not configured
                if (merchantEthAddress == null || merchantEthAddress.startsWith("0x0000") || merchantEthAddress.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "⚠️ Warning: Merchant Ethereum address not configured. Payments will not be received. Please generate a wallet first.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                } else {
                    Text(
                        text = "Receiving to: ${merchantEthAddress.take(10)}...${merchantEthAddress.takeLast(6)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = isOnline,
                        onCheckedChange = { isOnline = it }
                    )
                    Text(if (isOnline) "Validate Only (Check)" else "Redeem & Transfer Money")
                }
                
                // Sync button at the top
                // Allow syncing PENDING or VALIDATED slips (validated slips can still be redeemed)
                val syncableSlips = pendingSlips.filter { 
                    it.status == SlipStatus.PENDING || it.status == SlipStatus.VALIDATED 
                }
                val syncableCount = syncableSlips.size
                Button(
                    onClick = {
                        if (syncableSlips.isEmpty()) {
                            Toast.makeText(context, "No slips to sync", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.syncSelectedSlips(syncableSlips, isOnline, merchantEthAddress)
                            val action = if (isOnline) "Validating" else "Redeeming"
                            Toast.makeText(context, "$action ${syncableSlips.size} slip(s)...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = syncableCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val actionText = if (isOnline) "Validate All" else "Redeem All & Transfer"
                    Text("$actionText (${syncableCount})")
                }
                
                // Sync response
                syncResponse?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (it.contains("Error")) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            color = if (it.contains("Error")) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
            }
        }

        // Collapsible Pending Slips List
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Collapsible header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Pending Slips (${pendingSlips.size})",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(
                        onClick = { isSlipsExpanded = !isSlipsExpanded }
                    ) {
                        Icon(
                            imageVector = if (isSlipsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isSlipsExpanded) "Collapse" else "Expand"
                        )
                    }
                }
                
                // Collapsible content
                AnimatedVisibility(
                    visible = isSlipsExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pendingSlips.isEmpty()) {
                            Text("No pending slips", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            // Show all pending slips
                            pendingSlips.forEach { slip ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (slip.status) {
                                            SlipStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                                            SlipStatus.VALIDATED -> MaterialTheme.colorScheme.primaryContainer
                                            SlipStatus.REDEEMED -> MaterialTheme.colorScheme.tertiaryContainer
                                            SlipStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "ID: ${slip.slipId.take(8)}...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Amount: ${slip.amount}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Payer: ${slip.payer.take(10)}...${slip.payer.takeLast(6)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Status: ${slip.status}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (slip.status) {
                                                SlipStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                                                SlipStatus.VALIDATED -> MaterialTheme.colorScheme.onPrimaryContainer
                                                SlipStatus.REDEEMED -> MaterialTheme.colorScheme.onTertiaryContainer
                                                SlipStatus.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
                                            }
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
}

