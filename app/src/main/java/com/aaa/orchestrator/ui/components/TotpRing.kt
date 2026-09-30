package com.aaa.orchestrator.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaa.orchestrator.engine.TotpGenerator
import com.aaa.orchestrator.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun TotpRing(
    secret: String,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(500)
        }
    }

    val remainingSeconds = TotpGenerator.getRemainingSeconds(currentTime)
    val progress = TotpGenerator.getRemainingProgress(currentTime)
    val currentCode = remember(currentTime / 30000L) {
        TotpGenerator.generateCurrentCode(secret, currentTime)
    }

    val ringColor = if (remainingSeconds <= 5) ErrorRed else SecondaryEmerald

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.size(36.dp),
                color = ringColor,
                trackColor = BorderSubtle,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = "${remainingSeconds}s",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ringColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "${currentCode.take(3)} ${currentCode.takeLast(3)}",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            ),
            color = PrimaryBlue
        )
    }
}
