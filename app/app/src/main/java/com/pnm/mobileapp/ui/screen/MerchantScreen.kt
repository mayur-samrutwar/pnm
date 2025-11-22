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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.ui.viewmodel.MerchantViewModel
import com.pnm.mobileapp.util.QRCodeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantScreen(
    viewModel: MerchantViewModel,
    @Suppress("UNUSED_PARAMETER") onScanQR: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var scannedSlip by remember { mutableStateOf<Slip?>(null) }
    var isOnline by remember { mutableStateOf(false) }
    val syncResponse by viewModel.syncResponse.collectAsState()

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { json ->
            scannedSlip = QRCodeUtils.parseSlipFromQR(json)
            scannedSlip?.let { slip ->
                coroutineScope.launch {
                    viewModel.saveSlip(slip)
                }
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
                    Text("Amount: ${slip.amount}")
                    Text("Address: ${slip.userAddress}")
                    Text("Signature: ${slip.signature.take(20)}...")
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
                            viewModel.syncWithHub(it, isOnline)
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
    }
}

