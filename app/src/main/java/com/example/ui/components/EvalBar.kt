package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TacticalEmerald
import kotlin.math.abs
import kotlin.math.tanh

@Composable
fun EvalBar(
    evalWhiteCp: Int,
    mateInWhite: Int?,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    val targetFraction = when {
        mateInWhite != null -> if (mateInWhite > 0) 0.96f else 0.04f
        else -> {
            // Smooth sigmoid mapping from centipawns to [0.05..0.95]
            val normalized = 0.5f + 0.45f * tanh(evalWhiteCp / 420.0).toFloat()
            normalized.coerceIn(0.05f, 0.95f)
        }
    }

    val animatedWhiteFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eval_bar_anim"
    )

    val label = when {
        mateInWhite != null -> if (mateInWhite > 0) "#M$mateInWhite" else "#-M${abs(mateInWhite)}"
        else -> String.format("%+.2f", evalWhiteCp / 100.0)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // White side bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedWhiteFraction)
                    .fillMaxHeight()
                    .background(Color(0xFFF1F5F9))
            )
            // Center tick
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .background(TacticalEmerald.copy(alpha = 0.5f))
                    .padding(horizontal = 0.75.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (animatedWhiteFraction >= 0.35f) label else "",
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = if (animatedWhiteFraction < 0.35f) label else if (isSearching) "UCI…" else "UCI",
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (animatedWhiteFraction > 0.88f) Color(0xFF0F172A) else Color(0xFFE2E8F0)
                )
            }
        }
    }
}
