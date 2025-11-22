package com.pnm.mobileapp.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestPaymentScreen(
    onBack: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var isRequesting by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(180) } // 3 minutes = 180 seconds

    // Timer countdown
    LaunchedEffect(isRequesting) {
        if (isRequesting) {
            remainingSeconds = 180
            while (remainingSeconds > 0 && isRequesting) {
                delay(1000)
                remainingSeconds--
            }
            if (remainingSeconds == 0) {
                isRequesting = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F7FA))
        ) {
            if (isRequesting && amountInput.isNotBlank()) {
                // Full screen ripple animation with amount
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Timer at the top
                    TimerDisplay(remainingSeconds)
                    
                    // Center content
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Continuous ripple animation (full screen)
                        RippleAnimation()
                        
                        // Display amount during animation (smaller size)
                        Text(
                            text = "$${amountInput}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5CF6),
                                fontSize = 32.sp
                            )
                        )
                    }
                }
            } else {
                // Input and button view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)
                ) {
                    Spacer(modifier = Modifier.weight(0.3f))
                    
                    // Amount Input
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { 
                            if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                amountInput = it
                            }
                        },
                        label = { 
                            Text(
                                "Amount (USDC)",
                                style = MaterialTheme.typography.bodyMedium
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedLabelColor = Color(0xFF8B5CF6),
                            unfocusedLabelColor = Color(0xFF64748B)
                        ),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    )

                    // Request Button
                    Button(
                        onClick = {
                            if (amountInput.isNotBlank() && amountInput.toDoubleOrNull() != null) {
                                isRequesting = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = amountInput.isNotBlank() && amountInput.toDoubleOrNull() != null,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            "Request",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                        )
                    }
                }
            }
            
            // Back button at bottom
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun RippleAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    
    // Create multiple ripples with staggered delays for smooth continuous full-screen effect
    val ripple1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )
    
    val ripple2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 667, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )
    
    val ripple3 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1334, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple3"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Ripple 1 - outermost
        RippleCircle(
            scale = 0.3f + ripple1.value * 4f, // Start small, expand to 4x screen size
            alpha = (1f - ripple1.value * 0.8f).coerceIn(0f, 0.35f)
        )
        
        // Ripple 2 - middle
        RippleCircle(
            scale = 0.3f + ripple2.value * 4f,
            alpha = (1f - ripple2.value * 0.8f).coerceIn(0f, 0.35f)
        )
        
        // Ripple 3 - innermost
        RippleCircle(
            scale = 0.3f + ripple3.value * 4f,
            alpha = (1f - ripple3.value * 0.8f).coerceIn(0f, 0.35f)
        )
    }
}

@Composable
private fun RippleCircle(
    scale: Float,
    alpha: Float
) {
    // Use a large base size that will expand to fill screen
    Box(
        modifier = Modifier
            .size(300.dp)
            .scale(scale)
            .alpha(alpha)
            .background(
                color = Color(0xFF8B5CF6),
                shape = CircleShape
            )
    )
}

@Composable
private fun TimerDisplay(remainingSeconds: Int) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = timeString,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                fontSize = 20.sp,
                letterSpacing = 1.sp
            )
        )
    }
}

