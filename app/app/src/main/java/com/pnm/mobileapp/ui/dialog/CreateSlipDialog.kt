package com.pnm.mobileapp.ui.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.util.QRCodeUtils

@Composable
fun CreateSlipDialog(
    slip: Slip,
    onDismiss: () -> Unit
) {
    val qrBitmap = QRCodeUtils.generateQRCode(slip)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Payment Slip", style = MaterialTheme.typography.headlineSmall)
                Text("Amount: ${slip.amount}")
                Text("Address: ${slip.userAddress}")
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(300.dp)
                )
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

