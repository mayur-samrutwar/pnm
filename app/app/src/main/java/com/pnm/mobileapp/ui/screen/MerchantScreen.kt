package com.pnm.mobileapp.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
    var amount by remember { mutableStateOf("") }
    var scannedSlip by remember { mutableStateOf<Slip?>(null) }
    var isOnline by remember { mutableStateOf(false) }
    var selectedSlips by remember { mutableStateOf<Set<Long>>(emptySet()) }
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
                scannedSlip = slip
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
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Amount Input
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Amount", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Enter Amount") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

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
                scannedSlip?.let { slip ->
                    Text("Scanned Slip:", style = MaterialTheme.typography.labelLarge)
                    Text("Slip ID: ${slip.slipId}")
                    Text("Amount: ${slip.amount}")
                    Text("Payer: ${slip.payer}")
                    Text("Cumulative: ${slip.cumulative}")
                    Text("Counter: ${slip.counter}")
                }
            }
        }

        // Sync with Hub Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Sync with Hub", style = MaterialTheme.typography.headlineSmall)
                
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
                    Text(if (isOnline) "Online (Validate)" else "Offline (Redeem)")
                }
                Button(
                    onClick = {
                        scannedSlip?.let {
                            viewModel.syncWithHub(it, isOnline, merchantEthAddress)
                        }
                    },
                    enabled = scannedSlip != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sync with Hub")
                }
                syncResponse?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Pending Slips List
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Pending Slips (${pendingSlips.size})", style = MaterialTheme.typography.headlineSmall)
                
                if (pendingSlips.isEmpty()) {
                    Text("No pending slips", style = MaterialTheme.typography.bodyMedium)
                } else {
                    pendingSlips.forEach { slip ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ID: ${slip.slipId.take(8)}...", style = MaterialTheme.typography.bodySmall)
                                Text("Amount: ${slip.amount}", style = MaterialTheme.typography.bodyMedium)
                                Text("Payer: ${slip.payer.take(10)}...", style = MaterialTheme.typography.bodySmall)
                                Text("Status: ${slip.status}", style = MaterialTheme.typography.bodySmall)
                            }
                            Checkbox(
                                checked = selectedSlips.contains(slip.id),
                                onCheckedChange = {
                                    selectedSlips = if (it) {
                                        selectedSlips + slip.id
                                    } else {
                                        selectedSlips - slip.id
                                    }
                                }
                            )
                        }
                        Divider()
                    }
                    
                    Button(
                        onClick = {
                            val slipsToSync = pendingSlips.filter { selectedSlips.contains(it.id) }
                            if (slipsToSync.isEmpty()) {
                                Toast.makeText(context, "No slips selected", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.syncSelectedSlips(slipsToSync, isOnline, merchantEthAddress)
                                selectedSlips = emptySet()
                            }
                        },
                        enabled = selectedSlips.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sync selected with Hub (${selectedSlips.size})")
                    }
                }
            }
        }
    }
}

