package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.BoardPalette
import com.example.chess.ChessBoard
import com.example.chess.ChessMove
import com.example.chess.ChessPiece
import com.example.chess.PieceColor
import com.example.chess.Square
import com.example.ui.theme.GrandmasterGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TacticalEmerald
import com.example.ui.theme.ThreatCoral
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChessBoardCanvas(
    board: ChessBoard,
    palette: BoardPalette,
    selectedSquare: Square?,
    legalTargets: List<ChessMove>,
    lastMove: Pair<Square, Square>?,
    bestMoveUci: String?,
    secondaryMoveUci: String?,
    isFlipped: Boolean,
    onSquareTap: (Square) -> Unit,
    modifier: Modifier = Modifier
) {
    val legalTargetMap = legalTargets.associateBy { it.to }
    val kingInCheckSquare = if (board.isInCheck(board.sideToMove)) {
        board.findKing(board.sideToMove)
    } else null

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .testTag("chessboard_canvas")
    ) {
        val boardSizeDp = maxWidth
        val squareSizeDp = boardSizeDp / 8f

        // 8x8 Interactive Grid
        Column(modifier = Modifier.fillMaxSize()) {
            for (displayRow in 0..7) {
                Row(modifier = Modifier.weight(1f)) {
                    for (displayCol in 0..7) {
                        val boardRow = if (isFlipped) 7 - displayRow else displayRow
                        val boardCol = if (isFlipped) 7 - displayCol else displayCol
                        val square = Square.of(boardRow, boardCol)
                        val piece = board.pieceAt(square)

                        val baseColor = if (square.isLightSquare()) palette.lightSquare else palette.darkSquare
                        val isSelected = selectedSquare == square
                        val isLastFrom = lastMove?.first == square
                        val isLastTo = lastMove?.second == square
                        val legalMove = legalTargetMap[square]
                        val isCheckKing = kingInCheckSquare == square

                        val overlayColor = when {
                            isSelected -> TacticalEmerald.copy(alpha = 0.55f)
                            isLastTo -> palette.highlightTo
                            isLastFrom -> palette.highlightFrom
                            else -> Color.Transparent
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(baseColor)
                                .background(overlayColor)
                                .clickable { onSquareTap(square) }
                                .testTag("square_${square.toAlgebraic()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            // Check glow
                            if (isCheckKing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    ThreatCoral.copy(alpha = 0.85f),
                                                    ThreatCoral.copy(alpha = 0.0f)
                                                )
                                            )
                                        )
                                )
                            }

                            // Rank & File Coordinates
                            if (displayCol == 0) {
                                Text(
                                    text = ('1' + square.rank).toString(),
                                    fontFamily = MonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (square.isLightSquare()) palette.darkSquare else palette.lightSquare,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(2.dp)
                                )
                            }
                            if (displayRow == 7) {
                                Text(
                                    text = ('a' + square.file).toString(),
                                    fontFamily = MonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (square.isLightSquare()) palette.darkSquare else palette.lightSquare,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                )
                            }

                            // Piece Glyph with high-contrast silhouette
                            if (piece != null) {
                                ChessPieceGlyph(
                                    piece = piece,
                                    fontSizeSp = (squareSizeDp.value * 0.68f).coerceIn(18f, 44f)
                                )
                            }

                            // Legal move indicator
                            if (legalMove != null) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    if (legalMove.isCapture || piece != null) {
                                        drawCircle(
                                            color = TacticalEmerald.copy(alpha = 0.85f),
                                            radius = size.minDimension * 0.42f,
                                            style = Stroke(width = size.minDimension * 0.09f)
                                        )
                                    } else {
                                        drawCircle(
                                            color = TacticalEmerald.copy(alpha = 0.78f),
                                            radius = size.minDimension * 0.16f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Engine Move Arrows Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            secondaryMoveUci?.let { uci ->
                drawUciArrow(
                    uci = uci,
                    isFlipped = isFlipped,
                    color = GrandmasterGold.copy(alpha = 0.45f),
                    strokeScale = 0.13f
                )
            }
            bestMoveUci?.let { uci ->
                drawUciArrow(
                    uci = uci,
                    isFlipped = isFlipped,
                    color = TacticalEmerald.copy(alpha = 0.86f),
                    strokeScale = 0.17f
                )
            }
        }
    }
}

@Composable
private fun ChessPieceGlyph(
    piece: ChessPiece,
    fontSizeSp: Float
) {
    val glyph = piece.unicodeGlyph()
    // Render crisp high-contrast shadow + piece fill so white pieces pop on cream squares
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            fontSize = fontSizeSp.sp,
            color = if (piece.color == PieceColor.WHITE) Color(0xCC0B1018) else Color(0x66FFFFFF),
            modifier = Modifier.padding(top = 1.5.dp, start = 1.dp)
        )
        Text(
            text = glyph,
            fontSize = fontSizeSp.sp,
            color = if (piece.color == PieceColor.WHITE) Color(0xFFFFFFFF) else Color(0xFF111827)
        )
    }
}

private fun DrawScope.drawUciArrow(
    uci: String,
    isFlipped: Boolean,
    color: Color,
    strokeScale: Float
) {
    if (uci.length < 4) return
    val fromSq = Square.fromAlgebraic(uci.substring(0, 2)) ?: return
    val toSq = Square.fromAlgebraic(uci.substring(2, 4)) ?: return

    val cellSize = size.width / 8f
    fun centerOf(sq: Square): Offset {
        val dc = if (isFlipped) 7 - sq.col else sq.col
        val dr = if (isFlipped) 7 - sq.row else sq.row
        return Offset(
            x = dc * cellSize + cellSize / 2f,
            y = dr * cellSize + cellSize / 2f
        )
    }

    val start = centerOf(fromSq)
    val end = centerOf(toSq)
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val headLen = cellSize * 0.38f
    val shaftEnd = Offset(
        x = end.x - (headLen * 0.65f * cos(angle)).toFloat(),
        y = end.y - (headLen * 0.65f * sin(angle)).toFloat()
    )

    drawLine(
        color = color,
        start = start,
        end = shaftEnd,
        strokeWidth = cellSize * strokeScale,
        cap = StrokeCap.Round
    )

    val p1 = Offset(
        x = end.x - (headLen * cos(angle - Math.PI / 6.0)).toFloat(),
        y = end.y - (headLen * sin(angle - Math.PI / 6.0)).toFloat()
    )
    val p2 = Offset(
        x = end.x - (headLen * cos(angle + Math.PI / 6.0)).toFloat(),
        y = end.y - (headLen * sin(angle + Math.PI / 6.0)).toFloat()
    )

    val headPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path = headPath, color = color)
}
