package com.pnm.mobileapp.ui.screen

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.pnm.mobileapp.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun GenerateWalletScreen(
    viewModel: AppViewModel,
    activity: FragmentActivity?,
    onWalletGenerated: () -> Unit
) {
    val wallet by viewModel.wallet.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    
    // Auto-navigate when wallet is generated
    LaunchedEffect(wallet) {
        if (wallet != null && isGenerating) {
            isGenerating = false
            kotlinx.coroutines.delay(300) // Small delay for smooth transition
            onWalletGenerated()
        }
    }
    
    // Use static background color (removed animation to avoid complexity)
    val backgroundColor = Color(0xFFF5F7FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = backgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(color = Color(0xFF6366F1)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security",
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
            }

            // Title and Description
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Create Your Wallet",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = Color(0xFF1E293B)
                    ),
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Generate a secure, hardware-backed wallet to start making offline payments",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 24.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Generate Button
            Button(
                onClick = {
                    isGenerating = true
                    coroutineScope.launch {
                        val fragmentActivity = activity ?: (context as? FragmentActivity)
                        viewModel.generateWallet(fragmentActivity)
                        // Wait a bit for wallet to be set
                        kotlinx.coroutines.delay(500)
                        if (viewModel.wallet.value != null) {
                            isGenerating = false
                            onWalletGenerated()
                        }
                    }
                },
                enabled = !isGenerating && wallet == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFC7D2FE),
                    disabledContentColor = Color(0xFF94A3B8)
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Generating...",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    )
                } else {
                    Text(
                        text = "Generate Wallet",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            // Security Features List
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
                    SecurityFeature(
                        title = "Hardware-Backed Security",
                        description = "Keys stored in secure hardware"
                    )
                    SecurityFeature(
                        title = "Biometric Protection",
                        description = "Protected by your fingerprint or face"
                    )
                    SecurityFeature(
                        title = "Offline Capable",
                        description = "Works without internet connection"
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityFeature(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color = Color(0xFFEEF2FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF6366F1)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E293B)
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            )
        }
    }
}

