package com.example.vision

import android.graphics.Bitmap
import com.example.chess.ChessPresets
import kotlinx.coroutines.delay

enum class CommentaryLanguage {
    ARABIC,
    ENGLISH
}

data class VisionAnalysisResult(
    val detectedFen: String,
    val confidencePercent: Int,
    val summaryAr: String,
    val summaryEn: String,
    val isPlaceholder: Boolean
)

data class CommentaryRequest(
    val fen: String,
    val evalWhiteCp: Int,
    val mateInWhite: Int?,
    val bestMoveSan: String?,
    val pvSan: List<String>,
    val sideToMoveWhite: Boolean,
    val language: CommentaryLanguage
)

data class TacticalCommentaryResult(
    val commentaryText: String,
    val isPlaceholder: Boolean
)

/**
 * Abstraction for cloud AI Chess Vision (board-to-FEN) and generative tactical commentary.
 */
interface ChessVisionAiRepository {
    suspend fun analyzeBoardImage(
        bitmap: Bitmap,
        gridAnalysis: OnDeviceGridAnalysis,
        blackPerspective: Boolean
    ): VisionAnalysisResult

    suspend fun generateTacticalCommentary(
        request: CommentaryRequest
    ): TacticalCommentaryResult
}

/**
 * Local placeholder implementation used when Firebase AI Logic is not enabled.
 * Combines real on-device 8x8 occupancy metrics and real UCI engine evaluation
 * with clearly labeled sample AI responses.
 */
class PlaceholderChessVisionAiRepository : ChessVisionAiRepository {

    override suspend fun analyzeBoardImage(
        bitmap: Bitmap,
        gridAnalysis: OnDeviceGridAnalysis,
        blackPerspective: Boolean
    ): VisionAnalysisResult {
        delay(350L)

        // Pick a realistic sample tactical FEN matching the density of occupied squares detected on-device
        val sampleFen = when {
            gridAnalysis.occupiedCount >= 28 -> ChessPresets.START_FEN
            gridAnalysis.occupiedCount >= 20 -> ChessPresets.tacticalPresets[1].fen
            gridAnalysis.occupiedCount >= 10 -> ChessPresets.tacticalPresets[2].fen
            else -> ChessPresets.tacticalPresets[4].fen
        }

        val summaryAr = "[معاينة تجريبية - يتطلب Firebase AI] رصد المعالج المحلي ${gridAnalysis.occupiedCount} مربعاً مشغولاً بتباين ${gridAnalysis.contrastScorePercent}%؛ تم تحميل وضعية شطرنج نموذجية للمطابقة."
        val summaryEn = "[Preview Mode - Requires Firebase AI] Local grid detector found ${gridAnalysis.occupiedCount} occupied squares (${gridAnalysis.contrastScorePercent}% contrast); loaded matching sample FEN."

        return VisionAnalysisResult(
            detectedFen = sampleFen,
            confidencePercent = gridAnalysis.contrastScorePercent.coerceIn(45, 92),
            summaryAr = summaryAr,
            summaryEn = summaryEn,
            isPlaceholder = true
        )
    }

    override suspend fun generateTacticalCommentary(
        request: CommentaryRequest
    ): TacticalCommentaryResult {
        val bestMove = request.bestMoveSan ?: "—"
        val lineStr = request.pvSan.take(4).joinToString(" ")
        val sideAr = if (request.sideToMoveWhite) "الأبيض" else "الأسود"
        val sideEn = if (request.sideToMoveWhite) "White" else "Black"

        val evalTextAr = when {
            request.mateInWhite != null -> {
                val m = kotlin.math.abs(request.mateInWhite)
                if (request.mateInWhite > 0) "أفضلية حاسمة للأبيض بكش مات في $m نقلات"
                else "أفضلية حاسمة للأسود بكش مات في $m نقلات"
            }
            request.evalWhiteCp > 150 -> "أفضلية واضحة للأبيض (+${String.format("%.1f", request.evalWhiteCp / 100.0)})"
            request.evalWhiteCp < -150 -> "أفضلية واضحة للأسود (${String.format("%.1f", request.evalWhiteCp / 100.0)})"
            else -> "توازن تكتيكي متقارب (${String.format("%+.2f", request.evalWhiteCp / 100.0)})"
        }

        val evalTextEn = when {
            request.mateInWhite != null -> {
                val m = kotlin.math.abs(request.mateInWhite)
                if (request.mateInWhite > 0) "White has a forced mate in $m"
                else "Black has a forced mate in $m"
            }
            request.evalWhiteCp > 150 -> "Clear advantage for White (+${String.format("%.1f", request.evalWhiteCp / 100.0)})"
            request.evalWhiteCp < -150 -> "Clear advantage for Black (${String.format("%.1f", request.evalWhiteCp / 100.0)})"
            else -> "Balanced tactical position (${String.format("%+.2f", request.evalWhiteCp / 100.0)})"
        }

        val text = if (request.language == CommentaryLanguage.ARABIC) {
            "[تعليق تجريبي - يتطلب Firebase AI] دور $sideAr: $evalTextAr. أفضل نقلة يقترحها المحرك هي $bestMove" +
                if (lineStr.isNotBlank()) " عبر التفريع: $lineStr." else "."
        } else {
            "[Preview Mode - Requires Firebase AI] $sideEn to move: $evalTextEn. Top UCI engine recommendation is $bestMove" +
                if (lineStr.isNotBlank()) " following line $lineStr." else "."
        }

        return TacticalCommentaryResult(
            commentaryText = text,
            isPlaceholder = true
        )
    }
}
