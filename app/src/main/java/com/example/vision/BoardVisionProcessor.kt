package com.example.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class SquareOccupancy {
    EMPTY,
    LIGHT_PIECE,
    DARK_PIECE
}

data class SquareVisionMetrics(
    val row: Int,
    val col: Int,
    val edgeDensity: Float,
    val centerMeanLuma: Float,
    val borderMeanLuma: Float,
    val occupancy: SquareOccupancy
)

data class OnDeviceGridAnalysis(
    val squareMetrics: List<SquareVisionMetrics>,
    val occupiedCount: Int,
    val contrastScorePercent: Int,
    val boardBounds: RectF,
    val previewBitmap: Bitmap
)

/**
 * Pure on-device 8x8 chessboard vision pre-processor.
 * Computes per-square luminance, border vs center color delta, and local edge energy
 * to isolate occupied squares and piece polarity before AI piece classification.
 */
class BoardVisionProcessor {

    suspend fun analyzeGrid(
        source: Bitmap,
        normalizedBounds: RectF = RectF(0.08f, 0.08f, 0.92f, 0.92f)
    ): OnDeviceGridAnalysis = withContext(Dispatchers.Default) {
        val left = (normalizedBounds.left.coerceIn(0f, 0.85f) * source.width).toInt()
        val top = (normalizedBounds.top.coerceIn(0f, 0.85f) * source.height).toInt()
        val right = (normalizedBounds.right.coerceIn(normalizedBounds.left + 0.1f, 1f) * source.width).toInt()
        val bottom = (normalizedBounds.bottom.coerceIn(normalizedBounds.top + 0.1f, 1f) * source.height).toInt()

        val cropW = max(64, right - left)
        val cropH = max(64, bottom - top)
        val cropped = Bitmap.createBitmap(
            source,
            left.coerceIn(0, source.width - 1),
            top.coerceIn(0, source.height - 1),
            min(cropW, source.width - left),
            min(cropH, source.height - top)
        )

        // Normalize to 256x256 for fast deterministic 32x32 pixel cells per square
        val normalized = Bitmap.createScaledBitmap(cropped, 256, 256, true)
        val pixels = IntArray(256 * 256)
        normalized.getPixels(pixels, 0, 256, 0, 0, 256, 256)

        val luma = FloatArray(256 * 256)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val rawMetrics = ArrayList<SquareVisionMetrics>(64)
        var minBoardLuma = 255f
        var maxBoardLuma = 0f

        for (row in 0..7) {
            for (col in 0..7) {
                val x0 = col * 32
                val y0 = row * 32

                var borderSum = 0f
                var borderCount = 0
                var centerSum = 0f
                var centerCount = 0
                var edgeSum = 0f
                var edgeCount = 0

                for (dy in 2 until 30) {
                    val y = y0 + dy
                    for (dx in 2 until 30) {
                        val x = x0 + dx
                        val idx = y * 256 + x
                        val v = luma[idx]

                        val isBorder = dx < 7 || dx >= 25 || dy < 7 || dy >= 25
                        if (isBorder) {
                            borderSum += v
                            borderCount++
                        } else if (dx in 9..22 && dy in 9..22) {
                            centerSum += v
                            centerCount++
                            val gx = abs(luma[idx + 1] - luma[idx - 1])
                            val gy = abs(luma[idx + 256] - luma[idx - 256])
                            edgeSum += sqrt(gx * gx + gy * gy)
                            edgeCount++
                        }
                    }
                }

                val borderMean = if (borderCount > 0) borderSum / borderCount else 128f
                val centerMean = if (centerCount > 0) centerSum / centerCount else 128f
                val edgeDensity = if (edgeCount > 0) (edgeSum / edgeCount) else 0f

                minBoardLuma = min(minBoardLuma, borderMean)
                maxBoardLuma = max(maxBoardLuma, borderMean)

                val lumaDelta = abs(centerMean - borderMean)
                val isOccupied = edgeDensity > 16f || lumaDelta > 22f
                val occupancy = when {
                    !isOccupied -> SquareOccupancy.EMPTY
                    centerMean >= borderMean + 8f || centerMean > 145f -> SquareOccupancy.LIGHT_PIECE
                    else -> SquareOccupancy.DARK_PIECE
                }

                rawMetrics.add(
                    SquareVisionMetrics(
                        row = row,
                        col = col,
                        edgeDensity = edgeDensity,
                        centerMeanLuma = centerMean,
                        borderMeanLuma = borderMean,
                        occupancy = occupancy
                    )
                )
            }
        }

        val contrastPercent = (((maxBoardLuma - minBoardLuma) / 255f) * 100f)
            .toInt()
            .coerceIn(10, 99)

        OnDeviceGridAnalysis(
            squareMetrics = rawMetrics,
            occupiedCount = rawMetrics.count { it.occupancy != SquareOccupancy.EMPTY },
            contrastScorePercent = contrastPercent,
            boardBounds = normalizedBounds,
            previewBitmap = cropped
        )
    }
}
