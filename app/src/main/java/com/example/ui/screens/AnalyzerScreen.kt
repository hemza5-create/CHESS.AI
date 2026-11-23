package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ChessVisionUiState
import com.example.ChessVisionViewModel
import com.example.R
import com.example.chess.ChessPiece
import com.example.chess.ChessPresets
import com.example.chess.GameStatus
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.UciLine
import com.example.ui.components.ChessBoardCanvas
import com.example.ui.components.EvalBar
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.theme.GrandmasterGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TacticalEmerald
import com.example.ui.theme.ThreatCoral
import com.example.vision.CommentaryLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(
    uiState: ChessVisionUiState,
    viewModel: ChessVisionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPresetsSheet by remember { mutableStateOf(false) }
    var showFenDialog by remember { mutableStateOf(false) }

    val primaryUci = uiState.uciState.lines.firstOrNull()?.pvUci?.firstOrNull()
    val secondaryUci = uiState.uciState.lines.getOrNull(1)?.pvUci?.firstOrNull()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusHudRow(uiState)
                    EvalBar(
                        evalWhiteCp = uiState.uciState.evalWhiteCp,
                        mateInWhite = uiState.uciState.mateInWhite,
                        isSearching = uiState.uciState.isSearching
                    )
                    ChessBoardCanvas(
                        board = uiState.board,
                        palette = uiState.boardPalette,
                        selectedSquare = uiState.selectedSquare,
                        legalTargets = uiState.legalTargets,
                        lastMove = uiState.lastMove,
                        bestMoveUci = if (uiState.isEditMode) null else primaryUci,
                        secondaryMoveUci = if (uiState.isEditMode) null else secondaryUci,
                        isFlipped = uiState.isFlipped,
                        onSquareTap = viewModel::onSquareTapped
                    )
                    BoardActionStrip(
                        uiState = uiState,
                        onUndo = viewModel::undoMove,
                        onFlip = viewModel::toggleFlipBoard,
                        onToggleEdit = viewModel::toggleEditMode,
                        onOpenPresets = { showPresetsSheet = true },
                        onOpenFen = { showFenDialog = true },
                        onReset = viewModel::resetBoard
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (uiState.isEditMode) {
                        item {
                            BoardEditorPaletteCard(
                                selectedPiece = uiState.editorSelectedPiece,
                                sideToMove = uiState.board.sideToMove,
                                onSelectPiece = viewModel::selectEditorPiece,
                                onToggleSide = viewModel::toggleSideToMoveInEditor,
                                onClearBoard = viewModel::clearBoardForEditing,
                                onDoneEditing = viewModel::toggleEditMode
                            )
                        }
                    }
                    item {
                        EngineLinesCard(
                            uiState = uiState,
                            onAnalyze = viewModel::analyzeCurrentPosition,
                            onStop = viewModel::stopAnalysis,
                            onPlayBestMove = viewModel::playBestEngineMove
                        )
                    }
                    item {
                        VoiceCommentaryCard(
                            uiState = uiState,
                            onSpeak = viewModel::speakCurrentCommentary,
                            onStopSpeaking = viewModel::stopSpeaking,
                            onToggleAutoSpeak = viewModel::toggleAutoSpeak,
                            onLanguageChange = viewModel::setCommentaryLanguage
                        )
                    }
                    item {
                        FenStripCard(
                            fen = uiState.board.toFen(),
                            onCopy = { copyFenToClipboard(context, uiState.board.toFen()) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { StatusHudRow(uiState) }
                item {
                    EvalBar(
                        evalWhiteCp = uiState.uciState.evalWhiteCp,
                        mateInWhite = uiState.uciState.mateInWhite,
                        isSearching = uiState.uciState.isSearching
                    )
                }
                item {
                    ChessBoardCanvas(
                        board = uiState.board,
                        palette = uiState.boardPalette,
                        selectedSquare = uiState.selectedSquare,
                        legalTargets = uiState.legalTargets,
                        lastMove = uiState.lastMove,
                        bestMoveUci = if (uiState.isEditMode) null else primaryUci,
                        secondaryMoveUci = if (uiState.isEditMode) null else secondaryUci,
                        isFlipped = uiState.isFlipped,
                        onSquareTap = viewModel::onSquareTapped
                    )
                }
                item {
                    BoardActionStrip(
                        uiState = uiState,
                        onUndo = viewModel::undoMove,
                        onFlip = viewModel::toggleFlipBoard,
                        onToggleEdit = viewModel::toggleEditMode,
                        onOpenPresets = { showPresetsSheet = true },
                        onOpenFen = { showFenDialog = true },
                        onReset = viewModel::resetBoard
                    )
                }
                if (uiState.isEditMode) {
                    item {
                        BoardEditorPaletteCard(
                            selectedPiece = uiState.editorSelectedPiece,
                            sideToMove = uiState.board.sideToMove,
                            onSelectPiece = viewModel::selectEditorPiece,
                            onToggleSide = viewModel::toggleSideToMoveInEditor,
                            onClearBoard = viewModel::clearBoardForEditing,
                            onDoneEditing = viewModel::toggleEditMode
                        )
                    }
                }
                item {
                    EngineLinesCard(
                        uiState = uiState,
                        onAnalyze = viewModel::analyzeCurrentPosition,
                        onStop = viewModel::stopAnalysis,
                        onPlayBestMove = viewModel::playBestEngineMove
                    )
                }
                item {
                    VoiceCommentaryCard(
                        uiState = uiState,
                        onSpeak = viewModel::speakCurrentCommentary,
                        onStopSpeaking = viewModel::stopSpeaking,
                        onToggleAutoSpeak = viewModel::toggleAutoSpeak,
                        onLanguageChange = viewModel::setCommentaryLanguage
                    )
                }
                item {
                    FenStripCard(
                        fen = uiState.board.toFen(),
                        onCopy = { copyFenToClipboard(context, uiState.board.toFen()) }
                    )
                }
            }
        }
    }

    if (showPresetsSheet) {
        ModalBottomSheet(onDismissRequest = { showPresetsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_presets),
                    style = MaterialTheme.typography.titleLarge
                )
                ChessPresets.tacticalPresets.forEach { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.loadPreset(preset)
                                showPresetsSheet = false
                            }
                            .testTag("preset_${preset.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = preset.titleAr,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = preset.descriptionAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showFenDialog) {
        FenLoaderDialog(
            initialFen = uiState.board.toFen(),
            onDismiss = { showFenDialog = false },
            onApplyFen = { fen ->
                val ok = viewModel.loadFen(fen)
                if (ok) {
                    showFenDialog = false
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.fen_invalid_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun StatusHudRow(uiState: ChessVisionUiState) {
    val turnText = if (uiState.board.sideToMove == PieceColor.WHITE) {
        stringResource(R.string.turn_white)
    } else {
        stringResource(R.string.turn_black)
    }

    val statusText = when (uiState.gameStatus) {
        GameStatus.CHECK -> stringResource(R.string.status_check)
        GameStatus.CHECKMATE -> stringResource(R.string.status_checkmate)
        GameStatus.STALEMATE -> stringResource(R.string.status_stalemate)
        GameStatus.DRAW_FIFTY_MOVE, GameStatus.DRAW_INSUFFICIENT_MATERIAL -> stringResource(R.string.status_draw)
        GameStatus.IN_PROGRESS -> if (uiState.uciState.isSearching) {
            stringResource(R.string.engine_thinking)
        } else {
            stringResource(R.string.engine_ready)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (uiState.board.sideToMove == PieceColor.WHITE) Color.White else Color(0xFF1E293B)
                    )
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Text(
                text = turnText,
                style = MaterialTheme.typography.titleSmall
            )
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = when (uiState.gameStatus) {
                GameStatus.CHECK, GameStatus.CHECKMATE -> ThreatCoral.copy(alpha = 0.18f)
                else -> TacticalEmerald.copy(alpha = 0.14f)
            },
            border = BorderStroke(
                1.dp,
                if (uiState.gameStatus == GameStatus.CHECK || uiState.gameStatus == GameStatus.CHECKMATE) {
                    ThreatCoral.copy(alpha = 0.6f)
                } else {
                    TacticalEmerald.copy(alpha = 0.4f)
                }
            )
        ) {
            Text(
                text = "$statusText • D${uiState.uciState.currentDepth}",
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (uiState.gameStatus == GameStatus.CHECK || uiState.gameStatus == GameStatus.CHECKMATE) {
                    ThreatCoral
                } else {
                    TacticalEmerald
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun BoardActionStrip(
    uiState: ChessVisionUiState,
    onUndo: () -> Unit,
    onFlip: () -> Unit,
    onToggleEdit: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenFen: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onUndo,
            enabled = uiState.moveHistorySan.isNotEmpty() && !uiState.isEditMode,
            modifier = Modifier.testTag("undo_button")
        ) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.action_undo_move))
        }

        FilledTonalIconButton(
            onClick = onFlip,
            modifier = Modifier.testTag("flip_board_button")
        ) {
            Icon(Icons.Rounded.FlipCameraAndroid, contentDescription = stringResource(R.string.action_flip_board))
        }

        FilterChip(
            selected = uiState.isEditMode,
            onClick = onToggleEdit,
            leadingIcon = {
                Icon(
                    imageVector = if (uiState.isEditMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = {
                Text(
                    text = stringResource(
                        if (uiState.isEditMode) R.string.action_done_editing else R.string.action_edit_board
                    )
                )
            },
            modifier = Modifier.testTag("edit_board_chip")
        )

        FilledTonalButton(
            onClick = onOpenPresets,
            modifier = Modifier.testTag("presets_button")
        ) {
            Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_presets))
        }

        OutlinedButton(
            onClick = onOpenFen,
            modifier = Modifier.testTag("load_fen_button")
        ) {
            Text("FEN", fontFamily = MonoFontFamily)
        }

        FilledTonalIconButton(
            onClick = onReset,
            modifier = Modifier.testTag("reset_board_button")
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_reset_board))
        }
    }
}

@Composable
private fun BoardEditorPaletteCard(
    selectedPiece: ChessPiece?,
    sideToMove: PieceColor,
    onSelectPiece: (ChessPiece?) -> Unit,
    onToggleSide: () -> Unit,
    onClearBoard: () -> Unit,
    onDoneEditing: () -> Unit
) {
    val allPieces = remember {
        PieceType.entries.map { ChessPiece(PieceColor.WHITE, it) } +
            PieceType.entries.map { ChessPiece(PieceColor.BLACK, it) }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.editor_palette_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allPieces.forEach { piece ->
                    val isSelected = selectedPiece == piece
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) TacticalEmerald.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.5.dp,
                            if (isSelected) TacticalEmerald else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onSelectPiece(piece) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = piece.unicodeGlyph(),
                                fontSize = 24.sp,
                                color = if (piece.color == PieceColor.WHITE) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Eraser
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedPiece == null) ThreatCoral.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.5.dp,
                        if (selectedPiece == null) ThreatCoral else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onSelectPiece(null) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Eraser", tint = ThreatCoral)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleSide,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (sideToMove == PieceColor.WHITE) R.string.turn_white else R.string.turn_black
                        )
                    )
                }
                OutlinedButton(onClick = onClearBoard) {
                    Text(stringResource(R.string.action_clear_board))
                }
                Button(onClick = onDoneEditing) {
                    Text(stringResource(R.string.action_done_editing))
                }
            }
        }
    }
}

@Composable
private fun EngineLinesCard(
    uiState: ChessVisionUiState,
    onAnalyze: () -> Unit,
    onStop: () -> Unit,
    onPlayBestMove: () -> Unit
) {
    val uci = uiState.uciState

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.top_lines_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = "${uci.nps / 1000} kN/s",
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uci.lines.isEmpty()) {
                Text(
                    text = if (uci.isSearching) {
                        stringResource(R.string.engine_thinking)
                    } else {
                        stringResource(R.string.no_moves_available)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uci.lines.forEachIndexed { idx, line ->
                    UciLineRow(line = line, isTop = idx == 0)
                    if (idx < uci.lines.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlayBestMove,
                    enabled = uci.bestMoveUci != null && !uiState.isEditMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_best_move_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uci.bestMoveSan != null) {
                            "${stringResource(R.string.play_best_move)} (${uci.bestMoveSan})"
                        } else {
                            stringResource(R.string.play_best_move)
                        }
                    )
                }

                if (uci.isSearching) {
                    FilledTonalButton(
                        onClick = onStop,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ThreatCoral.copy(alpha = 0.2f),
                            contentColor = ThreatCoral
                        ),
                        modifier = Modifier.testTag("stop_engine_button")
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_stop))
                    }
                } else {
                    OutlinedButton(
                        onClick = onAnalyze,
                        modifier = Modifier.testTag("analyze_now_button")
                    ) {
                        Text(stringResource(R.string.action_analyze))
                    }
                }
            }
        }
    }
}

@Composable
private fun UciLineRow(line: UciLine, isTop: Boolean) {
    val evalStr = line.formattedEval(perspectiveWhite = true)
    val badgeColor = when {
        line.mateIn != null && line.mateIn > 0 -> TacticalEmerald
        line.mateIn != null && line.mateIn < 0 -> ThreatCoral
        line.scoreCp >= 50 -> TacticalEmerald
        line.scoreCp <= -50 -> ThreatCoral
        else -> GrandmasterGold
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor.copy(alpha = if (isTop) 0.22f else 0.12f),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
        ) {
            Text(
                text = evalStr,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Text(
            text = line.pvSan.joinToString(" "),
            fontFamily = MonoFontFamily,
            fontSize = 12.5.sp,
            color = if (isTop) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VoiceCommentaryCard(
    uiState: ChessVisionUiState,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onLanguageChange: (CommentaryLanguage) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = stringResource(R.string.voice_commentary_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.voice_auto_speak),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = uiState.autoSpeakEnabled,
                        onCheckedChange = { onToggleAutoSpeak() },
                        modifier = Modifier.testTag("auto_speak_switch")
                    )
                }
            }

            if (uiState.commentaryText.isNotBlank()) {
                Text(
                    text = uiState.commentaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.commentaryLanguage == CommentaryLanguage.ARABIC,
                    onClick = { onLanguageChange(CommentaryLanguage.ARABIC) },
                    label = { Text(stringResource(R.string.voice_lang_ar)) }
                )
                FilterChip(
                    selected = uiState.commentaryLanguage == CommentaryLanguage.ENGLISH,
                    onClick = { onLanguageChange(CommentaryLanguage.ENGLISH) },
                    label = { Text(stringResource(R.string.voice_lang_en)) }
                )

                Spacer(modifier = Modifier.weight(1f))

                FilledTonalButton(
                    onClick = if (uiState.isSpeaking) onStopSpeaking else onSpeak,
                    enabled = uiState.commentaryText.isNotBlank(),
                    modifier = Modifier.testTag("speak_commentary_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (uiState.isSpeaking) R.string.voice_stop_speaking else R.string.voice_speak
                        )
                    )
                }
            }

            AnimatedVisibility(visible = uiState.commentaryIsPlaceholder) {
                FirebaseAiNoticeBanner()
            }
        }
    }
}

@Composable
private fun FenStripCard(
    fen: String,
    onCopy: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fen,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.testTag("copy_fen_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy_fen),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FenLoaderDialog(
    initialFen: String,
    onDismiss: () -> Unit,
    onApplyFen: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialFen) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_fen_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                maxLines = 3,
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onApplyFen(text) }) {
                Text(stringResource(R.string.dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

private fun copyFenToClipboard(context: Context, fen: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Chess FEN", fen))
    Toast.makeText(context, context.getString(R.string.fen_copied_toast), Toast.LENGTH_SHORT).show()
}
