package com.pnm.mobileapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
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

@Composable
fun ProfileScreen(viewModel: AppViewModel) {
    val wallet by viewModel.wallet.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    var userName by remember { mutableStateOf("astro") }
    var isEditingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(userName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Name Card
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    } else {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                fontSize = 24.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isEditingName) {
                                if (nameInput.isNotBlank()) {
                                    userName = nameInput
                                }
                                isEditingName = false
                            } else {
                                nameInput = userName
                                isEditingName = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isEditingName) "Save" else "Edit",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Address Card
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = wallet?.ethAddress ?: "Not generated",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF1E293B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                IconButton(
                    onClick = {
                        wallet?.ethAddress?.let {
                            clipboardManager.setText(AnnotatedString(it))
                            Toast.makeText(context, "Address copied!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
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
}

