package com.example.chess

import androidx.compose.ui.graphics.Color

enum class PieceColor {
    WHITE,
    BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val fenChar: Char, val symbolWhite: String, val symbolBlack: String, val valueCp: Int) {
    PAWN('p', "♙", "♟", 100),
    KNIGHT('n', "♘", "♞", 320),
    BISHOP('b', "♗", "♝", 330),
    ROOK('r', "♖", "♜", 500),
    QUEEN('q', "♕", "♛", 900),
    KING('k', "♔", "♚", 20000);

    fun toFenChar(color: PieceColor): Char =
        if (color == PieceColor.WHITE) fenChar.uppercaseChar() else fenChar

    companion object {
        fun fromFenChar(c: Char): Pair<PieceColor, PieceType>? {
            val color = if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
            val type = entries.firstOrNull { it.fenChar == c.lowercaseChar() } ?: return null
            return color to type
        }
    }
}

data class ChessPiece(
    val color: PieceColor,
    val type: PieceType
) {
    fun toFenChar(): Char = type.toFenChar(color)

    fun unicodeGlyph(): String =
        if (color == PieceColor.WHITE) type.symbolWhite else type.symbolBlack
}

/**
 * Square index 0..63 where 0 = a8 (row 0, col 0), 7 = h8, 56 = a1, 63 = h1.
 */
@JvmInline
value class Square(val index: Int) {
    val rank: Int get() = 7 - (index / 8) // 0 = rank 1, 7 = rank 8
    val file: Int get() = index % 8       // 0 = a, 7 = h
    val row: Int get() = index / 8        // 0 = top (rank 8), 7 = bottom (rank 1)
    val col: Int get() = index % 8

    fun toAlgebraic(): String {
        val f = ('a' + file)
        val r = ('1' + rank)
        return "$f$r"
    }

    fun isLightSquare(): Boolean = (row + col) % 2 == 0

    companion object {
        fun of(row: Int, col: Int): Square = Square(row * 8 + col)

        fun fromAlgebraic(str: String): Square? {
            if (str.length < 2) return null
            val f = str[0] - 'a'
            val r = str[1] - '1'
            if (f !in 0..7 || r !in 0..7) return null
            val row = 7 - r
            return Square(row * 8 + f)
        }
    }
}

data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
) {
    fun toFenString(): String {
        val sb = StringBuilder()
        if (whiteKingSide) sb.append('K')
        if (whiteQueenSide) sb.append('Q')
        if (blackKingSide) sb.append('k')
        if (blackQueenSide) sb.append('q')
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    companion object {
        fun fromFen(str: String): CastlingRights {
            if (str == "-") return CastlingRights(false, false, false, false)
            return CastlingRights(
                whiteKingSide = 'K' in str,
                whiteQueenSide = 'Q' in str,
                blackKingSide = 'k' in str,
                blackQueenSide = 'q' in str
            )
        }
    }
}

data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isCapture: Boolean = false,
    val isEnPassant: Boolean = false,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false
) {
    fun toUci(): String {
        val promo = promotion?.fenChar?.toString() ?: ""
        return "${from.toAlgebraic()}${to.toAlgebraic()}$promo"
    }
}

enum class GameStatus {
    IN_PROGRESS,
    CHECK,
    CHECKMATE,
    STALEMATE,
    DRAW_FIFTY_MOVE,
    DRAW_INSUFFICIENT_MATERIAL
}

enum class BoardPalette(
    val lightSquare: Color,
    val darkSquare: Color,
    val highlightFrom: Color,
    val highlightTo: Color
) {
    TOURNAMENT_EMERALD(
        lightSquare = Color(0xFFEEEED2),
        darkSquare = Color(0xFF6B9362),
        highlightFrom = Color(0x66F59E0B),
        highlightTo = Color(0x8810B981)
    ),
    CLASSIC_WALNUT(
        lightSquare = Color(0xFFF0D9B5),
        darkSquare = Color(0xFFB58863),
        highlightFrom = Color(0x66F59E0B),
        highlightTo = Color(0x8810B981)
    ),
    OBSIDIAN_SLATE(
        lightSquare = Color(0xFF8FA1B3),
        darkSquare = Color(0xFF3E4D5E),
        highlightFrom = Color(0x66F59E0B),
        highlightTo = Color(0x8810B981)
    )
}

data class TacticalPreset(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val fen: String
)

object ChessPresets {
    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    val tacticalPresets = listOf(
        TacticalPreset(
            id = "start",
            titleAr = "الوضعية الافتتاحية",
            titleEn = "Starting Position",
            descriptionAr = "بداية المباراة القياسية",
            fen = START_FEN
        ),
        TacticalPreset(
            id = "lucena_tactic",
            titleAr = "تضحية فيل قاتلة",
            titleEn = "Greek Gift Sacrifice",
            descriptionAr = "تضحية Bxh7+ التقليدية لكسر قلعة الأسود",
            fen = "r1bq1rk1/pppn1ppp/4pn2/3p4/1b1P4/2NBPN2/PP3PPP/R1BQK2R w KQ - 2 8"
        ),
        TacticalPreset(
            id = "back_rank",
            titleAr = "مات الصف الخلفي",
            titleEn = "Back Rank Tactic",
            descriptionAr = "ضغط حاسم على الصف الثامن عبر التشتيت",
            fen = "3r2k1/5ppp/8/8/8/5Q2/5PPP/3R2K1 w - - 0 1"
        ),
        TacticalPreset(
            id = "fork_knight",
            titleAr = "شوكة الحصان الملكية",
            titleEn = "Royal Knight Fork",
            descriptionAr = "مناورة تكتيكية لاستغلال المربعات الضعيفة",
            fen = "r2qkb1r/ppp2ppp/2n1bn2/3pp3/4P3/2NP1N2/PPP2PPP/R1BQKB1R w KQkq - 0 6"
        ),
        TacticalPreset(
            id = "endgame_lucena",
            titleAr = "نهاية رخ وبيدق",
            titleEn = "Rook Endgame Precision",
            descriptionAr = "اختبار دقيق لحسابات النهايات",
            fen = "4k3/4P3/4K3/8/8/8/4R3/4r3 w - - 0 1"
        )
    )
}
