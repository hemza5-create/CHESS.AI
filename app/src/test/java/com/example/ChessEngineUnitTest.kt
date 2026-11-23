package com.example

import com.example.chess.ChessBoard
import com.example.chess.ChessPresets
import com.example.chess.GameStatus
import com.example.chess.StockfishUciEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineUnitTest {

    @Test
    fun startingPositionHasTwentyLegalMovesAndRoundTripsFen() {
        val board = ChessBoard.startPosition()
        assertEquals(ChessPresets.START_FEN, board.toFen())
        assertEquals(20, board.generateLegalMoves().size)
        assertEquals(GameStatus.IN_PROGRESS, board.gameStatus())
    }

    @Test
    fun uciEngineFindsForcedMateInOne() = runBlocking {
        // White Queen on f7 / Rook on d1 -> Qe8# or Qxf7#? Let's use simple back rank mate: White Qf3, Rd1, Kg1 vs Black Kg8, pawns f7,g7,h7
        val fen = "6k1/5ppp/8/8/8/5Q2/8/3R2K1 w - - 0 1"
        val board = ChessBoard.fromFen(fen)
        assertNotNull(board)

        val engine = StockfishUciEngine()
        engine.sendCommand("uci")
        engine.sendCommand("isready")
        engine.analyzeBoard(board!!, depth = 3, multiPv = 2)

        var attempts = 0
        while (engine.state.value.isSearching && attempts < 40) {
            delay(50)
            attempts++
        }
        // Wait briefly for first search emission if it hasn't started yet
        if (engine.state.value.bestMoveUci == null) {
            delay(300)
        }

        val best = engine.state.value.bestMoveUci
        assertTrue("Expected mate move d1d8 or f3a8, got $best", best == "d1d8" || best == "f3a8")
        assertNotNull(engine.state.value.mateInWhite)
    }
}
