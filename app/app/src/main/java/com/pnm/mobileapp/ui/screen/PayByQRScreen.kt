package com.pnm.mobileapp.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.pnm.mobileapp.util.VoucherValidator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayByQRScreen(
    onBack: () -> Unit,
    onQRScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
                result?.text?.let { text ->
                    if (scannedResult == null) {
                        scannedResult = text
                        isScanning = false
                        
                        // Validate the scanned QR code
                        coroutineScope.launch {
                            if (VoucherValidator.validateSchema(text)) {
                                onQRScanned(text)
                            } else {
                                Toast.makeText(context, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                                scannedResult = null
                                isScanning = true
                            }
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
                        text = "Pay by QR",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
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
                .background(Color.Black)
        ) {
            if (hasCameraPermission && isScanning) {
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
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Please grant camera permission to scan QR codes",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.7f)
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
        }
    }
}

