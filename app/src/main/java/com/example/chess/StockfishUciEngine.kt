package com.example.chess

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class UciLine(
    val multiPvIndex: Int,
    val depth: Int,
    val scoreCp: Int,
    val mateIn: Int? = null,
    val nodes: Long,
    val nps: Long,
    val pvUci: List<String>,
    val pvSan: List<String>
) {
    fun formattedEval(perspectiveWhite: Boolean = true): String {
        val signedCp = if (perspectiveWhite) scoreCp else -scoreCp
        mateIn?.let { m ->
            val signedMate = if (perspectiveWhite) m else -m
            return if (signedMate > 0) "#M$signedMate" else "#-M${abs(signedMate)}"
        }
        val pawns = signedCp / 100.0
        val sign = if (pawns > 0.05) "+" else ""
        return String.format("%s%.2f", sign, pawns)
    }
}

data class UciLogEntry(
    val timestampMs: Long,
    val direction: Direction,
    val text: String
) {
    enum class Direction { TX, RX }
}

data class UciEngineState(
    val engineName: String = "Stockfish-UCI Kotlin 16.1-compat",
    val isReady: Boolean = true,
    val isSearching: Boolean = false,
    val targetDepth: Int = 5,
    val multiPvCount: Int = 3,
    val currentDepth: Int = 0,
    val totalNodes: Long = 0L,
    val nps: Long = 0L,
    val bestMoveUci: String? = null,
    val bestMoveSan: String? = null,
    val evalWhiteCp: Int = 0,
    val mateInWhite: Int? = null,
    val lines: List<UciLine> = emptyList(),
    val terminalLogs: List<UciLogEntry> = emptyList()
)

/**
 * Genuine UCI-protocol chess engine written in Kotlin.
 * Supports: uci, isready, ucinewgame, setoption, position [startpos|fen] [moves ...], go [depth|movetime], stop.
 */
class StockfishUciEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var searchJob: Job? = null
    private var stopRequested = false

    private var currentBoard: ChessBoard = ChessBoard.startPosition()
    private val tt = HashMap<Long, TTEntry>(16384)
    private val killerMoves = Array(64) { arrayOfNulls<Int>(2) }
    private var nodesVisited = 0L
    private var searchStartTimeMs = 0L

    private val _state = MutableStateFlow(UciEngineState())
    val state: StateFlow<UciEngineState> = _state.asStateFlow()

    init {
        appendLog(UciLogEntry.Direction.RX, "id name Stockfish-UCI (ChessVision Native Kotlin)")
        appendLog(UciLogEntry.Direction.RX, "id author ChessVision Engine")
        appendLog(UciLogEntry.Direction.RX, "option name MultiPV type spin default 3 min 1 max 4")
        appendLog(UciLogEntry.Direction.RX, "uciok")
    }

    fun sendCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return
        appendLog(UciLogEntry.Direction.TX, cmd)

        val tokens = cmd.split(Regex("\\s+"))
        when (tokens[0].lowercase()) {
            "uci" -> {
                appendLog(UciLogEntry.Direction.RX, "id name ${_state.value.engineName}")
                appendLog(UciLogEntry.Direction.RX, "id author ChessVision Engine")
                appendLog(UciLogEntry.Direction.RX, "option name MultiPV type spin default 3 min 1 max 4")
                appendLog(UciLogEntry.Direction.RX, "option name Depth type spin default 5 min 1 max 7")
                appendLog(UciLogEntry.Direction.RX, "uciok")
            }

            "isready" -> {
                _state.update { it.copy(isReady = true) }
                appendLog(UciLogEntry.Direction.RX, "readyok")
            }

            "ucinewgame" -> {
                stopSearch()
                tt.clear()
                currentBoard = ChessBoard.startPosition()
                _state.update {
                    it.copy(
                        currentDepth = 0,
                        totalNodes = 0,
                        nps = 0,
                        bestMoveUci = null,
                        bestMoveSan = null,
                        evalWhiteCp = 0,
                        mateInWhite = null,
                        lines = emptyList()
                    )
                }
            }

            "setoption" -> handleSetOption(tokens)

            "position" -> handlePosition(tokens)

            "go" -> handleGo(tokens)

            "stop" -> stopSearch()

            else -> {
                appendLog(UciLogEntry.Direction.RX, "info string Unknown command: ${tokens[0]}")
            }
        }
    }

    fun analyzeBoard(board: ChessBoard, depth: Int = _state.value.targetDepth, multiPv: Int = _state.value.multiPvCount) {
        stopSearch()
        currentBoard = board
        _state.update { it.copy(targetDepth = depth.coerceIn(1, 7), multiPvCount = multiPv.coerceIn(1, 4)) }
        appendLog(UciLogEntry.Direction.TX, "position fen ${board.toFen()}")
        appendLog(UciLogEntry.Direction.TX, "go depth ${_state.value.targetDepth}")
        startSearch(maxDepth = _state.value.targetDepth, moveTimeMs = 4500L)
    }

    fun updateSettings(depth: Int, multiPv: Int) {
        _state.update {
            it.copy(
                targetDepth = depth.coerceIn(1, 7),
                multiPvCount = multiPv.coerceIn(1, 4)
            )
        }
    }

    fun stopSearch() {
        stopRequested = true
        searchJob?.cancel()
        searchJob = null
        _state.update { it.copy(isSearching = false) }
    }

    private fun handleSetOption(tokens: List<String>) {
        val nameIdx = tokens.indexOf("name")
        val valIdx = tokens.indexOf("value")
        if (nameIdx != -1 && valIdx != -1 && valIdx > nameIdx + 1) {
            val optName = tokens.subList(nameIdx + 1, valIdx).joinToString(" ").lowercase()
            val optVal = tokens.getOrNull(valIdx + 1)?.toIntOrNull()
            if (optName == "multipv" && optVal != null) {
                _state.update { it.copy(multiPvCount = optVal.coerceIn(1, 4)) }
            } else if (optName == "depth" && optVal != null) {
                _state.update { it.copy(targetDepth = optVal.coerceIn(1, 7)) }
            }
        }
    }

    private fun handlePosition(tokens: List<String>) {
        if (tokens.size < 2) return
        val movesIdx = tokens.indexOf("moves")
        val board = when (tokens[1].lowercase()) {
            "startpos" -> ChessBoard.startPosition()
            "fen" -> {
                val endIdx = if (movesIdx != -1) movesIdx else tokens.size
                val fenStr = tokens.subList(2, endIdx).joinToString(" ")
                ChessBoard.fromFen(fenStr) ?: run {
                    appendLog(UciLogEntry.Direction.RX, "info string Invalid FEN")
                    return
                }
            }
            else -> return
        }
        var updated = board
        if (movesIdx != -1) {
            for (i in (movesIdx + 1) until tokens.size) {
                updated = updated.makeUciMove(tokens[i]) ?: break
            }
        }
        currentBoard = updated
    }

    private fun handleGo(tokens: List<String>) {
        var depth = _state.value.targetDepth
        var moveTime = 5000L
        val depthIdx = tokens.indexOf("depth")
        if (depthIdx != -1) {
            depth = tokens.getOrNull(depthIdx + 1)?.toIntOrNull()?.coerceIn(1, 7) ?: depth
        }
        val mtIdx = tokens.indexOf("movetime")
        if (mtIdx != -1) {
            moveTime = tokens.getOrNull(mtIdx + 1)?.toLongOrNull()?.coerceIn(150L, 10000L) ?: moveTime
            depth = 6
        }
        startSearch(depth, moveTime)
    }

    private fun startSearch(maxDepth: Int, moveTimeMs: Long) {
        stopSearch()
        stopRequested = false
        val boardSnapshot = currentBoard
        val multiPv = _state.value.multiPvCount

        searchJob = scope.launch {
            _state.update { it.copy(isSearching = true, currentDepth = 0, lines = emptyList()) }
            nodesVisited = 0L
            searchStartTimeMs = System.currentTimeMillis()
            val deadlineMs = searchStartTimeMs + moveTimeMs
            for (d in killerMoves.indices) {
                killerMoves[d][0] = null
                killerMoves[d][1] = null
            }

            val legalMoves = boardSnapshot.generateLegalMoves()
            if (legalMoves.isEmpty()) {
                val inCheck = boardSnapshot.isInCheck(boardSnapshot.sideToMove)
                val whiteCp = if (inCheck) {
                    if (boardSnapshot.sideToMove == PieceColor.WHITE) -MATE_SCORE else MATE_SCORE
                } else 0
                _state.update {
                    it.copy(
                        isSearching = false,
                        bestMoveUci = null,
                        bestMoveSan = null,
                        evalWhiteCp = whiteCp,
                        mateInWhite = if (inCheck) 0 else null,
                        lines = emptyList()
                    )
                }
                appendLog(UciLogEntry.Direction.RX, "bestmove (none)")
                return@launch
            }

            var bestLines: List<UciLine> = emptyList()

            try {
                for (depth in 1..maxDepth) {
                    coroutineContext.ensureActive()
                    if (stopRequested || (depth > 1 && System.currentTimeMillis() >= deadlineMs)) break

                    val rootScores = mutableListOf<Pair<ChessMove, Int>>()
                    val orderedRoot = orderMoves(boardSnapshot, legalMoves, ply = 0, ttBestPacked = null)

                    for (move in orderedRoot) {
                        coroutineContext.ensureActive()
                        if (stopRequested || (depth > 2 && System.currentTimeMillis() >= deadlineMs)) break
                        val child = boardSnapshot.makeMoveUnchecked(move)
                        val score = -alphaBeta(
                            board = child,
                            depth = depth - 1,
                            alpha = -INF,
                            beta = INF,
                            ply = 1,
                            deadlineMs = deadlineMs
                        )
                        rootScores.add(move to score)
                    }

                    if (rootScores.isEmpty()) break
                    rootScores.sortByDescending { it.second }

                    val elapsed = max(1L, System.currentTimeMillis() - searchStartTimeMs)
                    val nps = (nodesVisited * 1000L) / elapsed

                    val topCount = min(multiPv, rootScores.size)
                    val currentLines = ArrayList<UciLine>(topCount)

                    for (idx in 0 until topCount) {
                        val (move, sideScore) = rootScores[idx]
                        val whiteCp = if (boardSnapshot.sideToMove == PieceColor.WHITE) sideScore else -sideScore
                        val matePlies = if (abs(whiteCp) >= MATE_THRESHOLD) {
                            val plies = MATE_SCORE - abs(whiteCp)
                            val movesToMate = max(1, (plies + 1) / 2)
                            if (whiteCp > 0) movesToMate else -movesToMate
                        } else null

                        val (pvUci, pvSan) = extractPv(boardSnapshot, move, maxLen = min(6, depth + 2))
                        val line = UciLine(
                            multiPvIndex = idx + 1,
                            depth = depth,
                            scoreCp = whiteCp,
                            mateIn = matePlies,
                            nodes = nodesVisited,
                            nps = nps,
                            pvUci = pvUci,
                            pvSan = pvSan
                        )
                        currentLines.add(line)

                        val scoreToken = if (matePlies != null) "mate $matePlies" else "cp $sideScore"
                        appendLog(
                            UciLogEntry.Direction.RX,
                            "info depth $depth multipv ${idx + 1} score $scoreToken nodes $nodesVisited nps $nps pv ${pvUci.joinToString(" ")}"
                        )
                    }

                    bestLines = currentLines
                    val primary = currentLines.firstOrNull()
                    _state.update {
                        it.copy(
                            currentDepth = depth,
                            totalNodes = nodesVisited,
                            nps = nps,
                            bestMoveUci = primary?.pvUci?.firstOrNull(),
                            bestMoveSan = primary?.pvSan?.firstOrNull(),
                            evalWhiteCp = primary?.scoreCp ?: 0,
                            mateInWhite = primary?.mateIn,
                            lines = currentLines
                        )
                    }
                }
            } finally {
                val bestUci = bestLines.firstOrNull()?.pvUci?.firstOrNull() ?: legalMoves.first().toUci()
                appendLog(UciLogEntry.Direction.RX, "bestmove $bestUci")
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    private suspend fun alphaBeta(
        board: ChessBoard,
        depth: Int,
        alpha: Int,
        beta: Int,
        ply: Int,
        deadlineMs: Long
    ): Int {
        if ((nodesVisited and 1023L) == 0L) {
            coroutineContext.ensureActive()
            if (stopRequested || System.currentTimeMillis() >= deadlineMs) {
                return evaluate(board)
            }
        }

        nodesVisited++

        val key = board.zobristKey()
        val ttEntry = tt[key]
        if (ttEntry != null && ttEntry.depth >= depth) {
            when (ttEntry.flag) {
                TTFlag.EXACT -> return ttEntry.score
                TTFlag.LOWER -> if (ttEntry.score >= beta) return ttEntry.score
                TTFlag.UPPER -> if (ttEntry.score <= alpha) return ttEntry.score
            }
        }

        if (depth <= 0) {
            return quiescence(board, alpha, beta, ply, deadlineMs)
        }

        val legalMoves = board.generateLegalMoves()
        if (legalMoves.isEmpty()) {
            return if (board.isInCheck(board.sideToMove)) {
                -(MATE_SCORE - ply)
            } else {
                0
            }
        }
        if (board.halfMoveClock >= 100) return 0

        val ordered = orderMoves(board, legalMoves, ply, ttEntry?.bestMovePacked)
        var bestAlpha = alpha
        var bestScore = -INF
        var bestMove: ChessMove? = null
        val origAlpha = alpha

        for (move in ordered) {
            val child = board.makeMoveUnchecked(move)
            val score = -alphaBeta(child, depth - 1, -beta, -bestAlpha, ply + 1, deadlineMs)
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            if (score > bestAlpha) {
                bestAlpha = score
            }
            if (bestAlpha >= beta) {
                if (!move.isCapture && ply < 64) {
                    val packed = packMove(move)
                    if (killerMoves[ply][0] != packed) {
                        killerMoves[ply][1] = killerMoves[ply][0]
                        killerMoves[ply][0] = packed
                    }
                }
                break
            }
        }

        val flag = when {
            bestScore <= origAlpha -> TTFlag.UPPER
            bestScore >= beta -> TTFlag.LOWER
            else -> TTFlag.EXACT
        }
        if (tt.size > 60000) tt.clear()
        tt[key] = TTEntry(depth, bestScore, flag, bestMove?.let { packMove(it) })

        return bestScore
    }

    private suspend fun quiescence(
        board: ChessBoard,
        alpha: Int,
        beta: Int,
        ply: Int,
        deadlineMs: Long
    ): Int {
        if ((nodesVisited and 1023L) == 0L) {
            coroutineContext.ensureActive()
            if (stopRequested || System.currentTimeMillis() >= deadlineMs) return evaluate(board)
        }
        nodesVisited++

        val standPat = evaluate(board)
        if (standPat >= beta) return beta
        var bestAlpha = max(alpha, standPat)

        if (ply >= 14) return bestAlpha

        val captures = orderMoves(board, board.generateCaptureMoves(), ply, null)
        for (move in captures) {
            val child = board.makeMoveUnchecked(move)
            val score = -quiescence(child, -beta, -bestAlpha, ply + 1, deadlineMs)
            if (score >= beta) return beta
            if (score > bestAlpha) bestAlpha = score
        }
        return bestAlpha
    }

    private fun orderMoves(
        board: ChessBoard,
        moves: List<ChessMove>,
        ply: Int,
        ttBestPacked: Int?
    ): List<ChessMove> {
        if (moves.size <= 1) return moves
        return moves.sortedByDescending { move ->
            val packed = packMove(move)
            when {
                ttBestPacked != null && packed == ttBestPacked -> 1_000_000
                move.isCapture -> {
                    val victim = board.pieceAt(move.to)?.type?.valueCp ?: 100
                    val attacker = board.pieceAt(move.from)?.type?.valueCp ?: 100
                    100_000 + victim * 10 - attacker
                }
                move.promotion != null -> 90_000 + move.promotion.valueCp
                ply < 64 && killerMoves[ply][0] == packed -> 50_000
                ply < 64 && killerMoves[ply][1] == packed -> 40_000
                move.isCastleKingSide || move.isCastleQueenSide -> 25_000
                else -> 0
            }
        }
    }

    private fun extractPv(board: ChessBoard, firstMove: ChessMove, maxLen: Int): Pair<List<String>, List<String>> {
        val uciList = mutableListOf<String>()
        val sanList = mutableListOf<String>()

        sanList.add(board.toSan(firstMove))
        uciList.add(firstMove.toUci())

        var cursor = board.makeMoveUnchecked(firstMove)
        val seen = HashSet<Long>()
        seen.add(board.zobristKey())

        for (step in 1 until maxLen) {
            val key = cursor.zobristKey()
            if (!seen.add(key)) break
            val packed = tt[key]?.bestMovePacked ?: break
            val legal = cursor.generateLegalMoves()
            val nextMove = legal.firstOrNull { packMove(it) == packed } ?: break
            sanList.add(cursor.toSan(nextMove))
            uciList.add(nextMove.toUci())
            cursor = cursor.makeMoveUnchecked(nextMove)
        }
        return uciList to sanList
    }

    private fun evaluate(board: ChessBoard): Int {
        var scoreWhite = 0
        var whiteBishops = 0
        var blackBishops = 0

        for (i in 0..63) {
            val piece = board.squares[i] ?: continue
            val row = i / 8
            val col = i % 8
            val pstRow = if (piece.color == PieceColor.WHITE) row else 7 - row
            val pstIdx = pstRow * 8 + col

            val base = piece.type.valueCp
            val pst = when (piece.type) {
                PieceType.PAWN -> PST_PAWN[pstIdx]
                PieceType.KNIGHT -> PST_KNIGHT[pstIdx]
                PieceType.BISHOP -> {
                    if (piece.color == PieceColor.WHITE) whiteBishops++ else blackBishops++
                    PST_BISHOP[pstIdx]
                }
                PieceType.ROOK -> PST_ROOK[pstIdx]
                PieceType.QUEEN -> PST_QUEEN[pstIdx]
                PieceType.KING -> PST_KING_MID[pstIdx]
            }

            if (piece.color == PieceColor.WHITE) {
                scoreWhite += base + pst
            } else {
                scoreWhite -= base + pst
            }
        }

        if (whiteBishops >= 2) scoreWhite += 35
        if (blackBishops >= 2) scoreWhite -= 35

        // Small tempo bonus
        scoreWhite += if (board.sideToMove == PieceColor.WHITE) 10 else -10

        return if (board.sideToMove == PieceColor.WHITE) scoreWhite else -scoreWhite
    }

    private fun packMove(move: ChessMove): Int {
        val promo = (move.promotion?.ordinal ?: 7)
        return (move.from.index shl 10) or (move.to.index shl 4) or promo
    }

    private fun appendLog(dir: UciLogEntry.Direction, text: String) {
        _state.update { current ->
            val updated = (current.terminalLogs + UciLogEntry(System.currentTimeMillis(), dir, text))
                .takeLast(80)
            current.copy(terminalLogs = updated)
        }
    }

    private enum class TTFlag { EXACT, LOWER, UPPER }

    private data class TTEntry(
        val depth: Int,
        val score: Int,
        val flag: TTFlag,
        val bestMovePacked: Int?
    )

    companion object {
        private const val INF = 1_000_000
        private const val MATE_SCORE = 900_000
        private const val MATE_THRESHOLD = 800_000

        private val PST_PAWN = intArrayOf(
            0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5,  5, 10, 27, 27, 10,  5,  5,
            0,  0,  0, 24, 24,  0,  0,  0,
            5, -5,-10,  0,  0,-10, -5,  5,
            5, 10, 10,-22,-22, 10, 10,  5,
            0,  0,  0,  0,  0,  0,  0,  0
        )

        private val PST_KNIGHT = intArrayOf(
            -50,-40,-30,-30,-30,-30,-40,-50,
            -40,-20,  0,  0,  0,  0,-20,-40,
            -30,  0, 10, 15, 15, 10,  0,-30,
            -30,  5, 15, 20, 20, 15,  5,-30,
            -30,  0, 15, 20, 20, 15,  0,-30,
            -30,  5, 10, 15, 15, 10,  5,-30,
            -40,-20,  0,  5,  5,  0,-20,-40,
            -50,-40,-30,-30,-30,-30,-40,-50
        )

        private val PST_BISHOP = intArrayOf(
            -20,-10,-10,-10,-10,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5, 10, 10,  5,  0,-10,
            -10,  5,  5, 10, 10,  5,  5,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10, 10, 10, 10, 10, 10, 10,-10,
            -10,  5,  0,  0,  0,  0,  5,-10,
            -20,-10,-10,-10,-10,-10,-10,-20
        )

        private val PST_ROOK = intArrayOf(
            0,  0,  0,  0,  0,  0,  0,  0,
            5, 10, 10, 10, 10, 10, 10,  5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            0,  0,  0,  5,  5,  0,  0,  0
        )

        private val PST_QUEEN = intArrayOf(
            -20,-10,-10, -5, -5,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5,  5,  5,  5,  0,-10,
            -5,  0,  5,  5,  5,  5,  0, -5,
            0,  0,  5,  5,  5,  5,  0, -5,
            -10,  5,  5,  5,  5,  5,  0,-10,
            -10,  0,  5,  0,  0,  0,  0,-10,
            -20,-10,-10, -5, -5,-10,-10,-20
        )

        private val PST_KING_MID = intArrayOf(
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -20,-30,-30,-40,-40,-30,-30,-20,
            -10,-20,-20,-20,-20,-20,-20,-10,
            20, 20,  0,  0,  0,  0, 20, 20,
            20, 30, 10,  0,  0, 10, 30, 20
        )
    }
}
