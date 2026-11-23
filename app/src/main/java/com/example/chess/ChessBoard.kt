package com.example.chess

import kotlin.math.abs

class ChessBoard private constructor(
    val squares: Array<ChessPiece?>,
    val sideToMove: PieceColor,
    val castling: CastlingRights,
    val enPassantSquare: Square?,
    val halfMoveClock: Int,
    val fullMoveNumber: Int
) {
    fun pieceAt(square: Square): ChessPiece? = squares[square.index]
    fun pieceAt(row: Int, col: Int): ChessPiece? =
        if (row in 0..7 && col in 0..7) squares[row * 8 + col] else null

    fun copyWithSquares(
        newSquares: Array<ChessPiece?>,
        newSide: PieceColor = sideToMove,
        newCastling: CastlingRights = castling,
        newEp: Square? = enPassantSquare,
        newHalfMove: Int = halfMoveClock,
        newFullMove: Int = fullMoveNumber
    ): ChessBoard = ChessBoard(
        squares = newSquares,
        sideToMove = newSide,
        castling = newCastling,
        enPassantSquare = newEp,
        halfMoveClock = newHalfMove,
        fullMoveNumber = newFullMove
    )

    fun withPieceAt(square: Square, piece: ChessPiece?): ChessBoard {
        val next = squares.copyOf()
        next[square.index] = piece
        return copyWithSquares(next, newEp = null)
    }

    fun withSideToMove(color: PieceColor): ChessBoard =
        copyWithSquares(squares.copyOf(), newSide = color)

    fun toFen(): String {
        val sb = StringBuilder()
        for (row in 0..7) {
            var empty = 0
            for (col in 0..7) {
                val p = squares[row * 8 + col]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(p.toFenChar())
                }
            }
            if (empty > 0) sb.append(empty)
            if (row < 7) sb.append('/')
        }
        sb.append(' ')
        sb.append(if (sideToMove == PieceColor.WHITE) 'w' else 'b')
        sb.append(' ')
        sb.append(castling.toFenString())
        sb.append(' ')
        sb.append(enPassantSquare?.toAlgebraic() ?: "-")
        sb.append(' ')
        sb.append(halfMoveClock)
        sb.append(' ')
        sb.append(fullMoveNumber)
        return sb.toString()
    }

    fun findKing(color: PieceColor): Square? {
        for (i in 0..63) {
            val p = squares[i]
            if (p != null && p.color == color && p.type == PieceType.KING) {
                return Square(i)
            }
        }
        return null
    }

    fun isInCheck(color: PieceColor = sideToMove): Boolean {
        val kingSq = findKing(color) ?: return false
        return isSquareAttacked(kingSq, color.opposite())
    }

    fun isSquareAttacked(target: Square, byColor: PieceColor): Boolean {
        val tr = target.row
        val tc = target.col

        // Pawn attacks
        val pawnDir = if (byColor == PieceColor.WHITE) 1 else -1
        val pawnRow = tr + pawnDir
        if (pawnRow in 0..7) {
            for (dc in intArrayOf(-1, 1)) {
                val pc = tc + dc
                if (pc in 0..7) {
                    val p = squares[pawnRow * 8 + pc]
                    if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
                }
            }
        }

        // Knight attacks
        for (i in KNIGHT_DR.indices) {
            val nr = tr + KNIGHT_DR[i]
            val nc = tc + KNIGHT_DC[i]
            if (nr in 0..7 && nc in 0..7) {
                val p = squares[nr * 8 + nc]
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        // King attacks
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val kr = tr + dr
                val kc = tc + dc
                if (kr in 0..7 && kc in 0..7) {
                    val p = squares[kr * 8 + kc]
                    if (p != null && p.color == byColor && p.type == PieceType.KING) return true
                }
            }
        }

        // Orthogonal rays (Rook / Queen)
        for (d in ORTHO_DIRS) {
            var r = tr + d[0]
            var c = tc + d[1]
            while (r in 0..7 && c in 0..7) {
                val p = squares[r * 8 + c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                r += d[0]
                c += d[1]
            }
        }

        // Diagonal rays (Bishop / Queen)
        for (d in DIAG_DIRS) {
            var r = tr + d[0]
            var c = tc + d[1]
            while (r in 0..7 && c in 0..7) {
                val p = squares[r * 8 + c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                r += d[0]
                c += d[1]
            }
        }

        return false
    }

    fun generateLegalMoves(): List<ChessMove> {
        val pseudo = generatePseudoLegalMoves()
        val legal = ArrayList<ChessMove>(pseudo.size)
        for (move in pseudo) {
            val next = makeMoveUnchecked(move)
            if (!next.isInCheck(sideToMove)) {
                legal.add(move)
            }
        }
        return legal
    }

    fun generateCaptureMoves(): List<ChessMove> {
        val pseudo = generatePseudoLegalMoves()
        val legal = ArrayList<ChessMove>()
        for (move in pseudo) {
            if (!move.isCapture && move.promotion == null) continue
            val next = makeMoveUnchecked(move)
            if (!next.isInCheck(sideToMove)) {
                legal.add(move)
            }
        }
        return legal
    }

    fun legalMovesFrom(from: Square): List<ChessMove> =
        generateLegalMoves().filter { it.from == from }

    fun makeMove(move: ChessMove): ChessBoard {
        val legal = generateLegalMoves()
        val matched = legal.firstOrNull {
            it.from == move.from && it.to == move.to && it.promotion == move.promotion
        } ?: return this
        return makeMoveUnchecked(matched)
    }

    fun makeUciMove(uci: String): ChessBoard? {
        if (uci.length < 4) return null
        val from = Square.fromAlgebraic(uci.substring(0, 2)) ?: return null
        val to = Square.fromAlgebraic(uci.substring(2, 4)) ?: return null
        val promo = if (uci.length >= 5) {
            PieceType.fromFenChar(uci[4].lowercaseChar())?.second
        } else null
        val legal = generateLegalMoves()
        val matched = legal.firstOrNull {
            it.from == from && it.to == to && (it.promotion == promo || (promo == null && it.promotion == PieceType.QUEEN))
        } ?: return null
        return makeMoveUnchecked(matched)
    }

    internal fun makeMoveUnchecked(move: ChessMove): ChessBoard {
        val next = squares.copyOf()
        val moving = next[move.from.index] ?: return this
        val targetPiece = next[move.to.index]

        next[move.from.index] = null

        // En passant capture
        if (move.isEnPassant) {
            val capRow = move.from.row
            val capCol = move.to.col
            next[capRow * 8 + capCol] = null
        }

        // Castling rook move
        if (move.isCastleKingSide) {
            val row = move.from.row
            next[row * 8 + 5] = next[row * 8 + 7]
            next[row * 8 + 7] = null
        } else if (move.isCastleQueenSide) {
            val row = move.from.row
            next[row * 8 + 3] = next[row * 8 + 0]
            next[row * 8 + 0] = null
        }

        // Place moved (or promoted) piece
        next[move.to.index] = if (move.promotion != null) {
            ChessPiece(moving.color, move.promotion)
        } else {
            moving
        }

        // Update castling rights
        var wks = castling.whiteKingSide
        var wqs = castling.whiteQueenSide
        var bks = castling.blackKingSide
        var bqs = castling.blackQueenSide

        if (moving.type == PieceType.KING) {
            if (moving.color == PieceColor.WHITE) {
                wks = false
                wqs = false
            } else {
                bks = false
                bqs = false
            }
        }
        if (move.from.index == 63 || move.to.index == 63) wks = false
        if (move.from.index == 56 || move.to.index == 56) wqs = false
        if (move.from.index == 7 || move.to.index == 7) bks = false
        if (move.from.index == 0 || move.to.index == 0) bqs = false

        // Update en passant square
        val newEp = if (moving.type == PieceType.PAWN && abs(move.to.row - move.from.row) == 2) {
            Square.of((move.from.row + move.to.row) / 2, move.from.col)
        } else null

        val newHalf = if (moving.type == PieceType.PAWN || targetPiece != null || move.isEnPassant) {
            0
        } else {
            halfMoveClock + 1
        }
        val newFull = if (sideToMove == PieceColor.BLACK) fullMoveNumber + 1 else fullMoveNumber

        return ChessBoard(
            squares = next,
            sideToMove = sideToMove.opposite(),
            castling = CastlingRights(wks, wqs, bks, bqs),
            enPassantSquare = newEp,
            halfMoveClock = newHalf,
            fullMoveNumber = newFull
        )
    }

    fun toSan(move: ChessMove): String {
        val piece = pieceAt(move.from) ?: return move.toUci()
        if (move.isCastleKingSide) return withCheckSuffix("O-O", move)
        if (move.isCastleQueenSide) return withCheckSuffix("O-O-O", move)

        val sb = StringBuilder()
        if (piece.type == PieceType.PAWN) {
            if (move.isCapture) {
                sb.append('a' + move.from.file)
                sb.append('x')
            }
            sb.append(move.to.toAlgebraic())
            if (move.promotion != null) {
                sb.append('=')
                sb.append(pieceLetter(move.promotion))
            }
        } else {
            sb.append(pieceLetter(piece.type))
            val samePeers = generateLegalMoves().filter {
                it.to == move.to && it.from != move.from && pieceAt(it.from)?.type == piece.type
            }
            if (samePeers.isNotEmpty()) {
                val shareFile = samePeers.any { it.from.file == move.from.file }
                val shareRank = samePeers.any { it.from.rank == move.from.rank }
                if (!shareFile) {
                    sb.append('a' + move.from.file)
                } else if (!shareRank) {
                    sb.append('1' + move.from.rank)
                } else {
                    sb.append(move.from.toAlgebraic())
                }
            }
            if (move.isCapture) sb.append('x')
            sb.append(move.to.toAlgebraic())
        }
        return withCheckSuffix(sb.toString(), move)
    }

    private fun withCheckSuffix(base: String, move: ChessMove): String {
        val next = makeMoveUnchecked(move)
        if (!next.isInCheck(next.sideToMove)) return base
        val hasReply = next.generateLegalMoves().isNotEmpty()
        return if (hasReply) "$base+" else "$base#"
    }

    private fun pieceLetter(type: PieceType): Char = when (type) {
        PieceType.KING -> 'K'
        PieceType.QUEEN -> 'Q'
        PieceType.ROOK -> 'R'
        PieceType.BISHOP -> 'B'
        PieceType.KNIGHT -> 'N'
        PieceType.PAWN -> ' '
    }

    fun gameStatus(): GameStatus {
        val legal = generateLegalMoves()
        val inCheck = isInCheck(sideToMove)
        if (legal.isEmpty()) {
            return if (inCheck) GameStatus.CHECKMATE else GameStatus.STALEMATE
        }
        if (halfMoveClock >= 100) return GameStatus.DRAW_FIFTY_MOVE
        if (isInsufficientMaterial()) return GameStatus.DRAW_INSUFFICIENT_MATERIAL
        return if (inCheck) GameStatus.CHECK else GameStatus.IN_PROGRESS
    }

    private fun isInsufficientMaterial(): Boolean {
        val nonKings = mutableListOf<Pair<Square, ChessPiece>>()
        for (i in 0..63) {
            val p = squares[i] ?: continue
            if (p.type != PieceType.KING) nonKings.add(Square(i) to p)
        }
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1) {
            val t = nonKings[0].second.type
            return t == PieceType.BISHOP || t == PieceType.KNIGHT
        }
        if (nonKings.size == 2) {
            val (sq1, p1) = nonKings[0]
            val (sq2, p2) = nonKings[1]
            if (p1.type == PieceType.BISHOP && p2.type == PieceType.BISHOP &&
                sq1.isLightSquare() == sq2.isLightSquare()
            ) {
                return true
            }
        }
        return false
    }

    fun zobristKey(): Long {
        var h = 0L
        for (i in 0..63) {
            val p = squares[i] ?: continue
            val pieceIdx = p.type.ordinal + if (p.color == PieceColor.BLACK) 6 else 0
            h = h xor ZOBRIST_PIECES[i][pieceIdx]
        }
        if (sideToMove == PieceColor.BLACK) h = h xor ZOBRIST_SIDE
        if (castling.whiteKingSide) h = h xor ZOBRIST_CASTLING[0]
        if (castling.whiteQueenSide) h = h xor ZOBRIST_CASTLING[1]
        if (castling.blackKingSide) h = h xor ZOBRIST_CASTLING[2]
        if (castling.blackQueenSide) h = h xor ZOBRIST_CASTLING[3]
        enPassantSquare?.let { h = h xor ZOBRIST_EP[it.file] }
        return h
    }

    private fun generatePseudoLegalMoves(): List<ChessMove> {
        val moves = ArrayList<ChessMove>(40)
        val enemy = sideToMove.opposite()
        val pawnDir = if (sideToMove == PieceColor.WHITE) -1 else 1
        val pawnStartRow = if (sideToMove == PieceColor.WHITE) 6 else 1
        val promoRow = if (sideToMove == PieceColor.WHITE) 0 else 7

        for (i in 0..63) {
            val piece = squares[i] ?: continue
            if (piece.color != sideToMove) continue
            val from = Square(i)
            val r = from.row
            val c = from.col

            when (piece.type) {
                PieceType.PAWN -> {
                    val nr = r + pawnDir
                    if (nr in 0..7) {
                        val oneStep = nr * 8 + c
                        if (squares[oneStep] == null) {
                            if (nr == promoRow) {
                                addPromotions(moves, from, Square(oneStep), isCapture = false)
                            } else {
                                moves.add(ChessMove(from, Square(oneStep)))
                                if (r == pawnStartRow) {
                                    val twoStep = (r + 2 * pawnDir) * 8 + c
                                    if (squares[twoStep] == null) {
                                        moves.add(ChessMove(from, Square(twoStep)))
                                    }
                                }
                            }
                        }
                        for (dc in intArrayOf(-1, 1)) {
                            val nc = c + dc
                            if (nc in 0..7) {
                                val targetSq = Square.of(nr, nc)
                                val targetPiece = squares[targetSq.index]
                                if (targetPiece != null && targetPiece.color == enemy) {
                                    if (nr == promoRow) {
                                        addPromotions(moves, from, targetSq, isCapture = true)
                                    } else {
                                        moves.add(ChessMove(from, targetSq, isCapture = true))
                                    }
                                } else if (enPassantSquare == targetSq) {
                                    moves.add(ChessMove(from, targetSq, isCapture = true, isEnPassant = true))
                                }
                            }
                        }
                    }
                }

                PieceType.KNIGHT -> {
                    for (k in KNIGHT_DR.indices) {
                        val nr = r + KNIGHT_DR[k]
                        val nc = c + KNIGHT_DC[k]
                        if (nr in 0..7 && nc in 0..7) {
                            val targetSq = Square.of(nr, nc)
                            val tp = squares[targetSq.index]
                            if (tp == null) {
                                moves.add(ChessMove(from, targetSq))
                            } else if (tp.color == enemy) {
                                moves.add(ChessMove(from, targetSq, isCapture = true))
                            }
                        }
                    }
                }

                PieceType.BISHOP -> addSlidingMoves(moves, from, DIAG_DIRS, enemy)
                PieceType.ROOK -> addSlidingMoves(moves, from, ORTHO_DIRS, enemy)
                PieceType.QUEEN -> {
                    addSlidingMoves(moves, from, DIAG_DIRS, enemy)
                    addSlidingMoves(moves, from, ORTHO_DIRS, enemy)
                }

                PieceType.KING -> {
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            val nr = r + dr
                            val nc = c + dc
                            if (nr in 0..7 && nc in 0..7) {
                                val targetSq = Square.of(nr, nc)
                                val tp = squares[targetSq.index]
                                if (tp == null) {
                                    moves.add(ChessMove(from, targetSq))
                                } else if (tp.color == enemy) {
                                    moves.add(ChessMove(from, targetSq, isCapture = true))
                                }
                            }
                        }
                    }
                    // Castling
                    if (!isInCheck(sideToMove)) {
                        if (sideToMove == PieceColor.WHITE && r == 7 && c == 4) {
                            if (castling.whiteKingSide &&
                                squares[61] == null && squares[62] == null &&
                                squares[63]?.let { it.color == PieceColor.WHITE && it.type == PieceType.ROOK } == true &&
                                !isSquareAttacked(Square(61), PieceColor.BLACK) &&
                                !isSquareAttacked(Square(62), PieceColor.BLACK)
                            ) {
                                moves.add(ChessMove(from, Square(62), isCastleKingSide = true))
                            }
                            if (castling.whiteQueenSide &&
                                squares[59] == null && squares[58] == null && squares[57] == null &&
                                squares[56]?.let { it.color == PieceColor.WHITE && it.type == PieceType.ROOK } == true &&
                                !isSquareAttacked(Square(59), PieceColor.BLACK) &&
                                !isSquareAttacked(Square(58), PieceColor.BLACK)
                            ) {
                                moves.add(ChessMove(from, Square(58), isCastleQueenSide = true))
                            }
                        } else if (sideToMove == PieceColor.BLACK && r == 0 && c == 4) {
                            if (castling.blackKingSide &&
                                squares[5] == null && squares[6] == null &&
                                squares[7]?.let { it.color == PieceColor.BLACK && it.type == PieceType.ROOK } == true &&
                                !isSquareAttacked(Square(5), PieceColor.WHITE) &&
                                !isSquareAttacked(Square(6), PieceColor.WHITE)
                            ) {
                                moves.add(ChessMove(from, Square(6), isCastleKingSide = true))
                            }
                            if (castling.blackQueenSide &&
                                squares[3] == null && squares[2] == null && squares[1] == null &&
                                squares[0]?.let { it.color == PieceColor.BLACK && it.type == PieceType.ROOK } == true &&
                                !isSquareAttacked(Square(3), PieceColor.WHITE) &&
                                !isSquareAttacked(Square(2), PieceColor.WHITE)
                            ) {
                                moves.add(ChessMove(from, Square(2), isCastleQueenSide = true))
                            }
                        }
                    }
                }
            }
        }
        return moves
    }

    private fun addPromotions(
        moves: MutableList<ChessMove>,
        from: Square,
        to: Square,
        isCapture: Boolean
    ) {
        moves.add(ChessMove(from, to, promotion = PieceType.QUEEN, isCapture = isCapture))
        moves.add(ChessMove(from, to, promotion = PieceType.ROOK, isCapture = isCapture))
        moves.add(ChessMove(from, to, promotion = PieceType.BISHOP, isCapture = isCapture))
        moves.add(ChessMove(from, to, promotion = PieceType.KNIGHT, isCapture = isCapture))
    }

    private fun addSlidingMoves(
        moves: MutableList<ChessMove>,
        from: Square,
        dirs: Array<IntArray>,
        enemy: PieceColor
    ) {
        for (d in dirs) {
            var r = from.row + d[0]
            var c = from.col + d[1]
            while (r in 0..7 && c in 0..7) {
                val targetSq = Square.of(r, c)
                val tp = squares[targetSq.index]
                if (tp == null) {
                    moves.add(ChessMove(from, targetSq))
                } else {
                    if (tp.color == enemy) {
                        moves.add(ChessMove(from, targetSq, isCapture = true))
                    }
                    break
                }
                r += d[0]
                c += d[1]
            }
        }
    }

    companion object {
        private val KNIGHT_DR = intArrayOf(-2, -2, -1, -1, 1, 1, 2, 2)
        private val KNIGHT_DC = intArrayOf(-1, 1, -2, 2, -2, 2, -1, 1)
        private val ORTHO_DIRS = arrayOf(
            intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1)
        )
        private val DIAG_DIRS = arrayOf(
            intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1)
        )

        private val ZOBRIST_PIECES: Array<LongArray>
        private val ZOBRIST_SIDE: Long
        private val ZOBRIST_CASTLING: LongArray
        private val ZOBRIST_EP: LongArray

        init {
            var seed = 0x9E3779B97F4A7C15UL.toLong()
            fun nextLong(): Long {
                seed = seed xor (seed shl 13)
                seed = seed xor (seed ushr 7)
                seed = seed xor (seed shl 17)
                return seed
            }
            ZOBRIST_PIECES = Array(64) { LongArray(12) { nextLong() } }
            ZOBRIST_SIDE = nextLong()
            ZOBRIST_CASTLING = LongArray(4) { nextLong() }
            ZOBRIST_EP = LongArray(8) { nextLong() }
        }

        fun empty(): ChessBoard = ChessBoard(
            squares = arrayOfNulls(64),
            sideToMove = PieceColor.WHITE,
            castling = CastlingRights(false, false, false, false),
            enPassantSquare = null,
            halfMoveClock = 0,
            fullMoveNumber = 1
        )

        fun startPosition(): ChessBoard =
            fromFen(ChessPresets.START_FEN) ?: empty()

        fun fromFen(fen: String): ChessBoard? {
            val parts = fen.trim().split(Regex("\\s+"))
            if (parts.isEmpty()) return null
            val ranks = parts[0].split('/')
            if (ranks.size != 8) return null

            val squares = arrayOfNulls<ChessPiece>(64)
            for (row in 0..7) {
                var col = 0
                for (ch in ranks[row]) {
                    if (ch in '1'..'8') {
                        col += (ch - '0')
                    } else {
                        val parsed = PieceType.fromFenChar(ch) ?: return null
                        if (col >= 8) return null
                        squares[row * 8 + col] = ChessPiece(parsed.first, parsed.second)
                        col++
                    }
                }
                if (col != 8) return null
            }

            val side = when (parts.getOrNull(1)) {
                "b" -> PieceColor.BLACK
                "w", null -> PieceColor.WHITE
                else -> return null
            }
            val castling = CastlingRights.fromFen(parts.getOrElse(2) { "-" })
            val ep = parts.getOrNull(3)?.takeIf { it != "-" }?.let { Square.fromAlgebraic(it) }
            val half = parts.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

            return ChessBoard(squares, side, castling, ep, half, full)
        }
    }
}
