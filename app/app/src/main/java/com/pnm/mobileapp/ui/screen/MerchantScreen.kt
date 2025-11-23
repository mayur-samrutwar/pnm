package com.pnm.mobileapp.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.launch
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.data.model.SlipStatus
import com.pnm.mobileapp.ui.viewmodel.MerchantViewModel
import com.pnm.mobileapp.util.Constants
import com.pnm.mobileapp.util.VoucherValidator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantScreen(
    viewModel: MerchantViewModel,
    merchantEthAddress: String? = null, // Merchant's Ethereum address for receiving payments
    payerEthAddress: String? = null, // Payer's Ethereum address (for fixing old vouchers)
    @Suppress("UNUSED_PARAMETER") onScanQR: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isOnline by remember { mutableStateOf(false) }
    var isSlipsExpanded by remember { mutableStateOf(true) }
    var preferredChainId by remember { mutableStateOf<Int?>(Constants.CHAIN_ID_BASE_SEPOLIA) }
    var chainDropdownExpanded by remember { mutableStateOf(false) }
    val syncResponse by viewModel.syncResponse.collectAsState()
    val pendingSlips by viewModel.pendingSlips.collectAsState(initial = emptyList())
    
    // Scanner state
    var hasCameraPermission by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var scannedResult by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            isScanning = true
        } else {
            Toast.makeText(context, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        hasCameraPermission = hasPermission
        if (hasPermission) {
            isScanning = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val barcodeCallback = remember {
        object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.text?.let { json ->
                    if (scannedResult == null) {
                        scannedResult = json
                        isScanning = false
                        
                        // Validate and process the scanned QR code
                        coroutineScope.launch {
                            // Validate JSON schema
                            if (!VoucherValidator.validateSchema(json)) {
                                Toast.makeText(context, "Invalid voucher JSON schema", Toast.LENGTH_SHORT).show()
                                scannedResult = null
                                isScanning = true
                                return@launch
                            }
                            
                            // Parse voucher
                            val voucher = VoucherValidator.parseVoucher(json)
                            if (voucher == null) {
                                Toast.makeText(context, "Failed to parse voucher", Toast.LENGTH_SHORT).show()
                                scannedResult = null
                                isScanning = true
                                return@launch
                            }
                            
                            // Create Slip from Voucher
                            val ethAddress = voucher.ethAddress ?: ""
                            android.util.Log.d("MerchantScreen", "Scanned voucher: slipId=${voucher.slipId}, payer=${voucher.payer}, ethAddress=$ethAddress, rawJson=$json")
                            val slip = Slip(
                                slipId = voucher.slipId,
                                payer = voucher.payer,
                                amount = voucher.amount,
                                userAddress = voucher.payer,
                                ethAddress = ethAddress,
                                cumulative = voucher.cumulative,
                                counter = voucher.counter,
                                publicKey = voucher.publicKey,
                                signature = voucher.signature,
                                rawJson = json,
                                timestamp = voucher.timestamp,
                                status = SlipStatus.PENDING
                            )
                            android.util.Log.d("MerchantScreen", "Created Slip: ethAddress=${slip.ethAddress}")
                            
                            // Save to Room database
                            viewModel.saveSlip(slip)
                            Toast.makeText(context, "Voucher scanned and saved", Toast.LENGTH_SHORT).show()
                            
                            // Reset scanner after a delay
                            kotlinx.coroutines.delay(2000)
                            scannedResult = null
                            isScanning = true
                        }
                    }
                }
            }

            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {
                // Optional: Handle result points for UI feedback
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan Payment",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scanner View (full screen)
            if (hasCameraPermission && isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // QR Scanner View
                    AndroidView(
                        factory = { ctx ->
                            DecoratedBarcodeView(ctx).apply {
                                barcodeView.decoderFactory = DefaultDecoderFactory(
                                    listOf(BarcodeFormat.QR_CODE)
                                )
                                // Hide default UI elements
                                setStatusText("")
                                barcodeView.resume()
                                decodeContinuous(barcodeCallback)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            if (isScanning) {
                                view.barcodeView.resume()
                            } else {
                                view.barcodeView.pause()
                            }
                        }
                    )

                    // Overlay with square frame
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Square frame (scanner area) - transparent center
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .border(
                                    width = 3.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(24.dp)
                                )
                        ) {
                            // Corner indicators
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(30.dp)
                                    .border(
                                        width = 4.dp,
                                        color = Color(0xFF6366F1),
                                        shape = RoundedCornerShape(topStart = 24.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(30.dp)
                                    .border(
                                        width = 4.dp,
                                        color = Color(0xFF6366F1),
                                        shape = RoundedCornerShape(topEnd = 24.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .size(30.dp)
                                    .border(
                                        width = 4.dp,
                                        color = Color(0xFF6366F1),
                                        shape = RoundedCornerShape(bottomStart = 24.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(30.dp)
                                    .border(
                                        width = 4.dp,
                                        color = Color(0xFF6366F1),
                                        shape = RoundedCornerShape(bottomEnd = 24.dp)
                                    )
                            )
                        }
                    }

                    // Instructions text
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Position QR code within the frame",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Scanning will happen automatically",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (!hasCameraPermission) {
                // Permission denied state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color(0xFF1E293B),
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Please grant camera permission to scan QR codes",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF64748B)
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Text(
                            "Grant Permission",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
            
            // Controls (shown when not scanning)
            if (!isScanning || !hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(bottom = 80.dp), // Add bottom padding to avoid nav menu
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Start Scanning Button
                    if (hasCameraPermission && !isScanning) {
                        Button(
                            onClick = { isScanning = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366F1)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Start Scanning",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                    
                    // Sync Controls Section
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
                
                // Chain Selection (only show when redeeming, not validating)
                if (!isOnline) {
                    val supportedChains = Constants.getSupportedChainIds()
                    
                    ExposedDropdownMenuBox(
                        expanded = chainDropdownExpanded,
                        onExpandedChange = { chainDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = preferredChainId?.let { Constants.getChainName(it) } ?: "Select Chain",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Preferred Chain") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = chainDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = chainDropdownExpanded,
                            onDismissRequest = { chainDropdownExpanded = false }
                        ) {
                            supportedChains.forEach { chainId ->
                                DropdownMenuItem(
                                    text = { Text(Constants.getChainName(chainId)) },
                                    onClick = {
                                        preferredChainId = chainId
                                        chainDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
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
                            viewModel.syncSelectedSlips(syncableSlips, isOnline, merchantEthAddress, payerEthAddress, preferredChainId)
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
        }
    }
}

