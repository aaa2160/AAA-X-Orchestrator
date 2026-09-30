package com.aaa.orchestrator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaa.orchestrator.ui.theme.*

@Composable
fun KillSwitchHud(
    isRunning: Boolean,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isRunning) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SoftAmberTile),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(WarningAmber))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hardware Guard Active",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    color = WarningAmber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Press Volume Down anytime to abort (<1ms)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Button(
                onClick = onEmergencyStop,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = "Stop",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("HALT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
