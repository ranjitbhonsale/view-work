package com.example.dualcamdirector.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dualcamdirector.camera.DualCamState

@Composable
fun CameraInfoDialog(
    camState: DualCamState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("Got It", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                text = "Dual-Cam Director Specs",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow(label = "App Version", value = "v1.2 (Aspect Calibrated)")
                InfoRow(label = "Hardware Mode", value = camState.hardwareConcurrencyMessage)
                InfoRow(
                    label = "Concurrent HW",
                    value = if (camState.isConcurrentHardwareActive) "Supported (Dual ISP)" else "Simulated Secondary / Single ISP"
                )
                InfoRow(label = "Main Camera", value = "${camState.mainRole.name} (${camState.mainPreviewSize.width}x${camState.mainPreviewSize.height})")
                InfoRow(label = "Inset Camera", value = "${camState.insetRole.name} (${camState.insetPreviewSize.width}x${camState.insetPreviewSize.height})")
                InfoRow(label = "Back Camera IDs", value = camState.availableBackCameras.joinToString(", ").ifEmpty { "0" })
                InfoRow(label = "Front Camera IDs", value = camState.availableFrontCameras.joinToString(", ").ifEmpty { "1" })

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Controls & Gestures:",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "• Drag inset anywhere (even while recording)\n• Double tap inset to swap cameras\n• Tap zoom presets (.6x, 1x, 2x, 5x, 10x) or continuous slider\n• Toggle fancy frames via the magic wand button",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        containerColor = Color(0xFF1E1E28),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
