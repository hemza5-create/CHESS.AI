package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ChessVisionUiState
import com.example.ChessVisionViewModel
import com.example.R
import com.example.chess.BoardPalette
import com.example.chess.UciLogEntry
import com.example.ui.theme.GrandmasterGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TacticalEmerald
import kotlin.math.roundToInt

@Composable
fun UciConsoleScreen(
    uiState: ChessVisionUiState,
    viewModel: ChessVisionViewModel,
    modifier: Modifier = Modifier
) {
    var commandText by remember { mutableStateOf("") }
    val uci = uiState.uciState
    val listState = rememberLazyListState()

    LaunchedEffect(uci.terminalLogs.size) {
        if (uci.terminalLogs.isNotEmpty()) {
            listState.animateScrollToItem(uci.terminalLogs.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Engine Telemetry Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = uci.engineName,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryTile(
                                label = stringResource(R.string.depth_label),
                                value = "${uci.currentDepth}/${uci.targetDepth}",
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryTile(
                                label = stringResource(R.string.nodes_label),
                                value = "${uci.totalNodes}",
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryTile(
                                label = stringResource(R.string.nps_label),
                                value = "${uci.nps / 1000}k",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Engine & Board Preferences Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.uci_settings_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Column {
                            Text(
                                text = "${stringResource(R.string.uci_depth_setting)}: ${uci.targetDepth}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = uci.targetDepth.toFloat(),
                                onValueChange = {
                                    viewModel.updateEngineSettings(it.roundToInt(), uci.multiPvCount)
                                },
                                valueRange = 2f..6f,
                                steps = 3
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.uci_multipv_setting),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..4).forEach { count ->
                                    FilterChip(
                                        selected = uci.multiPvCount == count,
                                        onClick = { viewModel.updateEngineSettings(uci.targetDepth, count) },
                                        label = { Text("$count", fontFamily = MonoFontFamily) }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.board_theme_label),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.boardPalette == BoardPalette.TOURNAMENT_EMERALD,
                                    onClick = { viewModel.setBoardPalette(BoardPalette.TOURNAMENT_EMERALD) },
                                    label = { Text(stringResource(R.string.theme_emerald)) }
                                )
                                FilterChip(
                                    selected = uiState.boardPalette == BoardPalette.CLASSIC_WALNUT,
                                    onClick = { viewModel.setBoardPalette(BoardPalette.CLASSIC_WALNUT) },
                                    label = { Text(stringResource(R.string.theme_walnut)) }
                                )
                                FilterChip(
                                    selected = uiState.boardPalette == BoardPalette.OBSIDIAN_SLATE,
                                    onClick = { viewModel.setBoardPalette(BoardPalette.OBSIDIAN_SLATE) },
                                    label = { Text(stringResource(R.string.theme_obsidian)) }
                                )
                            }
                        }
                    }
                }
            }

            // Live UCI Terminal Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.uci_terminal_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        // Quick UCI Commands
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("uci", "isready", "go depth 4", "go movetime 1500", "stop", "ucinewgame").forEach { cmd ->
                                AssistChip(
                                    onClick = { viewModel.sendRawUciCommand(cmd) },
                                    label = { Text(cmd, fontFamily = MonoFontFamily, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Monospace Terminal Output Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF070B11))
                                .padding(10.dp)
                        ) {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(uci.terminalLogs) { entry ->
                                    val prefix = if (entry.direction == UciLogEntry.Direction.TX) ">" else "<"
                                    val color = if (entry.direction == UciLogEntry.Direction.TX) {
                                        GrandmasterGold
                                    } else {
                                        TacticalEmerald
                                    }
                                    Text(
                                        text = "$prefix ${entry.text}",
                                        fontFamily = MonoFontFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = color
                                    )
                                }
                            }
                        }

                        // Command Input Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = commandText,
                                onValueChange = { commandText = it },
                                placeholder = { Text(stringResource(R.string.uci_command_hint), fontSize = 12.sp) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("uci_command_input")
                            )
                            FilledIconButton(
                                onClick = {
                                    if (commandText.isNotBlank()) {
                                        viewModel.sendRawUciCommand(commandText)
                                        commandText = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("uci_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = stringResource(R.string.uci_send)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontFamily = MonoFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
