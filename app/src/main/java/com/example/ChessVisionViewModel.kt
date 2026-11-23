package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.BoardPalette
import com.example.chess.ChessBoard
import com.example.chess.ChessMove
import com.example.chess.ChessPiece
import com.example.chess.ChessPresets
import com.example.chess.GameStatus
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.Square
import com.example.chess.StockfishUciEngine
import com.example.chess.TacticalPreset
import com.example.chess.UciEngineState
import com.example.vision.BoardVisionProcessor
import com.example.vision.ChessVisionAiRepository
import com.example.vision.CommentaryLanguage
import com.example.vision.CommentaryRequest
import com.example.vision.LiveBoardDetectionState
import com.example.vision.OnDeviceGridAnalysis
import com.example.vision.OpenCvBoardDetectionService
import com.example.vision.PlaceholderChessVisionAiRepository
import com.example.vision.VisionAnalysisResult
import com.example.voice.TacticalVoiceCoach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChessVisionUiState(
    val board: ChessBoard = ChessBoard.startPosition(),
    val moveHistorySan: List<String> = emptyList(),
    val selectedSquare: Square? = null,
    val legalTargets: List<ChessMove> = emptyList(),
    val lastMove: Pair<Square, Square>? = null,
    val gameStatus: GameStatus = GameStatus.IN_PROGRESS,
    val isFlipped: Boolean = false,
    val isEditMode: Boolean = false,
    val editorSelectedPiece: ChessPiece? = ChessPiece(PieceColor.WHITE, PieceType.QUEEN),
    val boardPalette: BoardPalette = BoardPalette.TOURNAMENT_EMERALD,
    val uciState: UciEngineState = UciEngineState(),
    val commentaryText: String = "",
    val commentaryIsPlaceholder: Boolean = true,
    val commentaryLanguage: CommentaryLanguage = CommentaryLanguage.ARABIC,
    val autoSpeakEnabled: Boolean = false,
    val isSpeaking: Boolean = false,
    val liveDetection: LiveBoardDetectionState = LiveBoardDetectionState(),
    val visionGridAnalysis: OnDeviceGridAnalysis? = null,
    val visionResult: VisionAnalysisResult? = null,
    val isVisionProcessing: Boolean = false,
    val visionBlackPerspective: Boolean = false,
    val visionBounds: RectF = RectF(0.08f, 0.08f, 0.92f, 0.92f)
)

class ChessVisionViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = StockfishUciEngine()
    val openCvService = OpenCvBoardDetectionService()
    private val aiRepository: ChessVisionAiRepository = PlaceholderChessVisionAiRepository()
    private val voiceCoach = TacticalVoiceCoach(application.applicationContext)

    private val boardHistory = ArrayDeque<Pair<ChessBoard, Pair<Square, Square>?>>()

    private val _uiState = MutableStateFlow(ChessVisionUiState())
    val uiState: StateFlow<ChessVisionUiState> = _uiState.asStateFlow()

    private var lastEngineFinishedDepth = -1
    private var lastAnalyzedFen = ""

    init {
        viewModelScope.launch {
            combine(engine.state, voiceCoach.isSpeaking) { uci, speaking -> uci to speaking }
                .collect { (uci, speaking) ->
                    _uiState.update { it.copy(uciState = uci, isSpeaking = speaking) }

                    val currentFen = _uiState.value.board.toFen()
                    if (!uci.isSearching && uci.lines.isNotEmpty() &&
                        (uci.currentDepth != lastEngineFinishedDepth || currentFen != lastAnalyzedFen)
                    ) {
                        lastEngineFinishedDepth = uci.currentDepth
                        lastAnalyzedFen = currentFen
                        refreshCommentary(autoSpeak = _uiState.value.autoSpeakEnabled)
                    }
                }
        }
        viewModelScope.launch {
            openCvService.detectionState.collect { live ->
                _uiState.update { it.copy(liveDetection = live) }
            }
        }
        analyzeCurrentPosition()
    }

    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        if (state.isEditMode) {
            val nextBoard = state.board.withPieceAt(square, state.editorSelectedPiece)
            _uiState.update {
                it.copy(
                    board = nextBoard,
                    selectedSquare = null,
                    legalTargets = emptyList(),
                    gameStatus = nextBoard.gameStatus()
                )
            }
            return
        }

        val currentBoard = state.board
        val currentSelected = state.selectedSquare
        val matchingMove = state.legalTargets.firstOrNull { it.to == square }

        if (matchingMove != null) {
            // Default auto-promotion to Queen for crisp flow
            val promoMove = state.legalTargets.firstOrNull {
                it.to == square && (it.promotion == null || it.promotion == PieceType.QUEEN)
            } ?: matchingMove
            executeMove(promoMove)
            return
        }

        val tappedPiece = currentBoard.pieceAt(square)
        if (tappedPiece != null && tappedPiece.color == currentBoard.sideToMove && square != currentSelected) {
            val moves = currentBoard.legalMovesFrom(square)
            _uiState.update {
                it.copy(selectedSquare = square, legalTargets = moves)
            }
        } else {
            _uiState.update {
                it.copy(selectedSquare = null, legalTargets = emptyList())
            }
        }
    }

    private fun executeMove(move: ChessMove) {
        val state = _uiState.value
        val san = state.board.toSan(move)
        boardHistory.addLast(state.board to state.lastMove)
        val nextBoard = state.board.makeMove(move)
        _uiState.update {
            it.copy(
                board = nextBoard,
                moveHistorySan = it.moveHistorySan + san,
                selectedSquare = null,
                legalTargets = emptyList(),
                lastMove = move.from to move.to,
                gameStatus = nextBoard.gameStatus()
            )
        }
        analyzeCurrentPosition()
    }

    fun playBestEngineMove() {
        val bestUci = _uiState.value.uciState.bestMoveUci ?: return
        val board = _uiState.value.board
        val from = Square.fromAlgebraic(bestUci.take(2)) ?: return
        val to = Square.fromAlgebraic(bestUci.drop(2).take(2)) ?: return
        val move = board.generateLegalMoves().firstOrNull {
            it.from == from && it.to == to && (it.promotion == null || it.promotion == PieceType.QUEEN)
        } ?: return
        executeMove(move)
    }

    fun undoMove() {
        if (boardHistory.isEmpty()) return
        val (prevBoard, prevLastMove) = boardHistory.removeLast()
        _uiState.update {
            it.copy(
                board = prevBoard,
                moveHistorySan = it.moveHistorySan.dropLast(1),
                selectedSquare = null,
                legalTargets = emptyList(),
                lastMove = prevLastMove,
                gameStatus = prevBoard.gameStatus()
            )
        }
        analyzeCurrentPosition()
    }

    fun resetBoard() {
        boardHistory.clear()
        val start = ChessBoard.startPosition()
        _uiState.update {
            it.copy(
                board = start,
                moveHistorySan = emptyList(),
                selectedSquare = null,
                legalTargets = emptyList(),
                lastMove = null,
                isEditMode = false,
                gameStatus = start.gameStatus()
            )
        }
        analyzeCurrentPosition()
    }

    fun clearBoardForEditing() {
        boardHistory.clear()
        val empty = ChessBoard.empty()
        _uiState.update {
            it.copy(
                board = empty,
                moveHistorySan = emptyList(),
                selectedSquare = null,
                legalTargets = emptyList(),
                lastMove = null,
                isEditMode = true,
                gameStatus = GameStatus.IN_PROGRESS
            )
        }
        engine.stopSearch()
    }

    fun loadPreset(preset: TacticalPreset) {
        loadFen(preset.fen)
    }

    fun loadFen(fen: String): Boolean {
        val parsed = ChessBoard.fromFen(fen) ?: return false
        boardHistory.clear()
        _uiState.update {
            it.copy(
                board = parsed,
                moveHistorySan = emptyList(),
                selectedSquare = null,
                legalTargets = emptyList(),
                lastMove = null,
                isEditMode = false,
                gameStatus = parsed.gameStatus()
            )
        }
        analyzeCurrentPosition()
        return true
    }

    fun toggleFlipBoard() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun toggleEditMode() {
        val enteringEdit = !_uiState.value.isEditMode
        _uiState.update {
            it.copy(
                isEditMode = enteringEdit,
                selectedSquare = null,
                legalTargets = emptyList()
            )
        }
        if (enteringEdit) {
            engine.stopSearch()
        } else {
            analyzeCurrentPosition()
        }
    }

    fun selectEditorPiece(piece: ChessPiece?) {
        _uiState.update { it.copy(editorSelectedPiece = piece) }
    }

    fun toggleSideToMoveInEditor() {
        val next = _uiState.value.board.withSideToMove(_uiState.value.board.sideToMove.opposite())
        _uiState.update { it.copy(board = next, gameStatus = next.gameStatus()) }
    }

    fun setBoardPalette(palette: BoardPalette) {
        _uiState.update { it.copy(boardPalette = palette) }
    }

    fun analyzeCurrentPosition() {
        lastEngineFinishedDepth = -1
        engine.analyzeBoard(_uiState.value.board)
    }

    fun stopAnalysis() {
        engine.stopSearch()
    }

    fun updateEngineSettings(depth: Int, multiPv: Int) {
        engine.updateSettings(depth, multiPv)
        analyzeCurrentPosition()
    }

    fun sendRawUciCommand(command: String) {
        engine.sendCommand(command)
    }

    fun setCommentaryLanguage(lang: CommentaryLanguage) {
        _uiState.update { it.copy(commentaryLanguage = lang) }
        refreshCommentary(autoSpeak = false)
    }

    fun toggleAutoSpeak() {
        val next = !_uiState.value.autoSpeakEnabled
        _uiState.update { it.copy(autoSpeakEnabled = next) }
        if (!next) voiceCoach.stop()
    }

    fun speakCurrentCommentary() {
        val text = _uiState.value.commentaryText
        if (text.isNotBlank()) {
            voiceCoach.speak(text, _uiState.value.commentaryLanguage)
        }
    }

    fun stopSpeaking() {
        voiceCoach.stop()
    }

    private fun refreshCommentary(autoSpeak: Boolean) {
        val state = _uiState.value
        val uci = state.uciState
        val topLine = uci.lines.firstOrNull()
        viewModelScope.launch {
            val result = aiRepository.generateTacticalCommentary(
                CommentaryRequest(
                    fen = state.board.toFen(),
                    evalWhiteCp = uci.evalWhiteCp,
                    mateInWhite = uci.mateInWhite,
                    bestMoveSan = uci.bestMoveSan,
                    pvSan = topLine?.pvSan ?: emptyList(),
                    sideToMoveWhite = state.board.sideToMove == PieceColor.WHITE,
                    language = state.commentaryLanguage
                )
            )
            _uiState.update {
                it.copy(
                    commentaryText = result.commentaryText,
                    commentaryIsPlaceholder = result.isPlaceholder
                )
            }
            if (autoSpeak && result.commentaryText.isNotBlank()) {
                voiceCoach.speak(result.commentaryText, state.commentaryLanguage)
            }
        }
    }

    fun toggleVisionPerspective() {
        _uiState.update { it.copy(visionBlackPerspective = !it.visionBlackPerspective) }
    }

    fun updateVisionBounds(bounds: RectF) {
        _uiState.update { it.copy(visionBounds = bounds) }
    }

    fun processVisionBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVisionProcessing = true) }
            try {
                val bounds = _uiState.value.visionBounds
                val grid = openCvService.analyzeBoardBitmap(bitmap, bounds)
                val result = aiRepository.analyzeBoardImage(
                    bitmap = grid.previewBitmap,
                    gridAnalysis = grid,
                    blackPerspective = _uiState.value.visionBlackPerspective
                )
                _uiState.update {
                    it.copy(
                        visionGridAnalysis = grid,
                        visionResult = result,
                        isVisionProcessing = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isVisionProcessing = false) }
            }
        }
    }

    fun applyVisionFenToAnalyzer(): Boolean {
        val fen = _uiState.value.visionResult?.detectedFen ?: return false
        return loadFen(fen)
    }

    override fun onCleared() {
        super.onCleared()
        engine.stopSearch()
        voiceCoach.shutdown()
    }
}
