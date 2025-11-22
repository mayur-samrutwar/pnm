package com.pnm.mobileapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.pnm.mobileapp.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(viewModel: AppViewModel) {
    val wallet by viewModel.wallet.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingEthWallet by remember { mutableStateOf(false) }
    
    // Check if ETH address is placeholder (failed generation)
    val isEthAddressPlaceholder = wallet?.ethAddress?.startsWith("0x0000000000000000000000000000000000000000") == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Profile Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = Color(0xFF6366F1),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = "Profile",
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
                Text(
                    text = "Wallet Profile",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        fontSize = 20.sp
                    )
                )
            }
        }

        // Device Wallet Address Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Device Wallet Address",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color(0xFF1E293B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "For offline voucher signing",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = wallet?.address ?: "Not generated",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF1E293B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            wallet?.address?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "Device address copied!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Ethereum Wallet Address Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFEEF2FF)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ethereum Wallet Address",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "For deposits and transactions",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = Color(0xFF6366F1),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ETH",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = wallet?.ethAddress ?: "Not generated",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isEthAddressPlaceholder) Color(0xFFEF4444) else Color(0xFF1E293B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (!isEthAddressPlaceholder) {
                        IconButton(
                            onClick = {
                                wallet?.ethAddress?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    Toast.makeText(context, "Ethereum address copied!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // Show retry button if ETH address is placeholder
                if (isEthAddressPlaceholder) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isGeneratingEthWallet = true
                            coroutineScope.launch {
                                viewModel.regenerateEthereumWallet()
                                // Wait a bit for wallet to update
                                kotlinx.coroutines.delay(500)
                                isGeneratingEthWallet = false
                                Toast.makeText(
                                    context,
                                    if (viewModel.wallet.value?.ethAddress?.startsWith("0x0000") == false) {
                                        "Ethereum wallet generated successfully!"
                                    } else {
                                        "Failed to generate. Please try again."
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !isGeneratingEthWallet,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        if (isGeneratingEthWallet) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Generate",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Ethereum Wallet")
                        }
                    }
                }
            }
        }

        // Additional Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Wallet Information",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B),
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "Your wallet uses hardware-backed security for maximum protection. All keys are stored securely in the Android Keystore.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

