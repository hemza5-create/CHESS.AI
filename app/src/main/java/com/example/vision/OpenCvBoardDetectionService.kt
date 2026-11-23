package com.example.vision

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LiveBoardDetectionState(
    val isOpenCvReady: Boolean = false,
    val boardDetected: Boolean = false,
    val normalizedCorners: List<PointF> = emptyList(),
    val confidencePercent: Int = 0,
    val occupiedSquaresCount: Int = 0,
    val processingTimeMs: Long = 0L,
    val streamFps: Int = 0,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val latestWarpedBitmap: Bitmap? = null
)

/**
 * Real-time OpenCV computer vision service for CameraX stream & image chessboard detection.
 * Performs Canny edge extraction, quadrilateral contour approximation, perspective warping,
 * and 8x8 square occupancy/polarity analysis via OpenCV native Mats.
 */
class OpenCvBoardDetectionService : ImageAnalysis.Analyzer {

    private val _detectionState = MutableStateFlow(LiveBoardDetectionState())
    val detectionState: StateFlow<LiveBoardDetectionState> = _detectionState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private var openCvInitialized = false
    private var smoothedCorners: Array<PointF>? = null
    private var lastFrameTimestampMs = 0L

    init {
        ensureOpenCvInitialized()
    }

    fun ensureOpenCvInitialized(): Boolean {
        if (openCvInitialized) return true
        openCvInitialized = try {
            val loaded = OpenCVLoader.initLocal()
            Log.i(TAG, "OpenCVLoader.initLocal() -> $loaded")
            loaded
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "OpenCV native library unavailable", e)
            false
        } catch (e: Throwable) {
            Log.e(TAG, "OpenCV initialization error", e)
            false
        }
        _detectionState.value = _detectionState.value.copy(isOpenCvReady = openCvInitialized)
        return openCvInitialized
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (!ensureOpenCvInitialized()) {
            imageProxy.close()
            return
        }
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val startMs = System.currentTimeMillis()
        try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees

            val rgbaMat = Mat()
            val rotatedMat = Mat()
            try {
                Utils.bitmapToMat(bitmap, rgbaMat)
                rotateMatInPlace(rgbaMat, rotatedMat, rotation)

                val detection = detectAndWarpBoard(rotatedMat, emitWarpedBitmap = true)
                val now = System.currentTimeMillis()
                val elapsed = max(1L, now - startMs)
                val fps = if (lastFrameTimestampMs > 0L) {
                    val dt = max(1L, now - lastFrameTimestampMs)
                    (1000L / dt).toInt().coerceIn(1, 60)
                } else 0
                lastFrameTimestampMs = now

                val smoothed = smoothCorners(detection.normalizedCorners)

                _detectionState.value = LiveBoardDetectionState(
                    isOpenCvReady = true,
                    boardDetected = detection.boardDetected,
                    normalizedCorners = smoothed,
                    confidencePercent = detection.confidencePercent,
                    occupiedSquaresCount = detection.occupiedSquaresCount,
                    processingTimeMs = elapsed,
                    streamFps = fps,
                    frameWidth = rotatedMat.cols(),
                    frameHeight = rotatedMat.rows(),
                    latestWarpedBitmap = detection.warpedBitmap ?: _detectionState.value.latestWarpedBitmap
                )
            } finally {
                rgbaMat.release()
                rotatedMat.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis error", e)
        } finally {
            imageProxy.close()
            isProcessingFrame.set(false)
        }
    }

    suspend fun analyzeBoardBitmap(
        source: Bitmap,
        fallbackBounds: RectF = RectF(0.08f, 0.08f, 0.92f, 0.92f)
    ): OnDeviceGridAnalysis = withContext(Dispatchers.Default) {
        if (!ensureOpenCvInitialized()) {
            return@withContext BoardVisionProcessor().analyzeGrid(source, fallbackBounds)
        }

        val rgbaMat = Mat()
        try {
            Utils.bitmapToMat(source, rgbaMat)
            val detection = detectAndWarpBoard(rgbaMat, emitWarpedBitmap = true)

            val boardBitmap = if (detection.boardDetected && detection.warpedBitmap != null) {
                detection.warpedBitmap
            } else {
                cropFallbackBitmap(source, fallbackBounds)
            }

            val warpedMat = Mat()
            try {
                Utils.bitmapToMat(boardBitmap, warpedMat)
                val grid = computeOpenCvGridMetrics(warpedMat, boardBitmap, fallbackBounds)
                _detectionState.value = _detectionState.value.copy(
                    isOpenCvReady = true,
                    boardDetected = detection.boardDetected,
                    normalizedCorners = detection.normalizedCorners,
                    confidencePercent = if (detection.boardDetected) detection.confidencePercent else grid.contrastScorePercent,
                    occupiedSquaresCount = grid.occupiedCount,
                    latestWarpedBitmap = boardBitmap
                )
                grid
            } finally {
                warpedMat.release()
            }
        } finally {
            rgbaMat.release()
        }
    }

    fun resetTracking() {
        smoothedCorners = null
        _detectionState.value = _detectionState.value.copy(
            boardDetected = false,
            normalizedCorners = emptyList(),
            confidencePercent = 0,
            occupiedSquaresCount = 0,
            latestWarpedBitmap = null
        )
    }

    private data class InternalBoardDetection(
        val boardDetected: Boolean,
        val normalizedCorners: List<PointF>,
        val confidencePercent: Int,
        val occupiedSquaresCount: Int,
        val warpedBitmap: Bitmap?
    )

    private fun detectAndWarpBoard(
        srcRgba: Mat,
        emitWarpedBitmap: Boolean
    ): InternalBoardDetection {
        val origW = srcRgba.cols().toDouble()
        val origH = srcRgba.rows().toDouble()
        if (origW < 32 || origH < 32) {
            return InternalBoardDetection(false, emptyList(), 0, 0, null)
        }

        // Downscale to max 480px width for fast real-time contour detection
        val scale = min(1.0, 480.0 / max(origW, origH))
        val scaledW = max(64, (origW * scale).toInt())
        val scaledH = max(64, (origH * scale).toInt())

        val resized = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val contours = ArrayList<MatOfPoint>()

        try {
            Imgproc.resize(srcRgba, resized, Size(scaledW.toDouble(), scaledH.toDouble()))
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 45.0, 140.0)
            Imgproc.dilate(edges, dilated, kernel)

            Imgproc.findContours(
                dilated,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val minQuadArea = scaledW * scaledH * 0.14
            var bestQuad: Array<Point>? = null
            var bestArea = 0.0

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < minQuadArea || area <= bestArea) continue

                val curve2f = MatOfPoint2f(*contour.toArray())
                val approx2f = MatOfPoint2f()
                try {
                    val peri = Imgproc.arcLength(curve2f, true)
                    Imgproc.approxPolyDP(curve2f, approx2f, 0.025 * peri, true)
                    val pts = approx2f.toArray()
                    if (pts.size == 4) {
                        val approxPoly = MatOfPoint(*pts)
                        try {
                            if (Imgproc.isContourConvex(approxPoly) && hasValidAspectRatio(pts)) {
                                bestArea = area
                                bestQuad = pts
                            }
                        } finally {
                            approxPoly.release()
                        }
                    }
                } finally {
                    curve2f.release()
                    approx2f.release()
                }
            }

            if (bestQuad == null) {
                return InternalBoardDetection(false, emptyList(), 0, 0, null)
            }

            val orderedScaled = orderQuadCorners(bestQuad)
            val normalized = orderedScaled.map { pt ->
                PointF(
                    (pt.x / scaledW.toDouble()).toFloat().coerceIn(0f, 1f),
                    (pt.y / scaledH.toDouble()).toFloat().coerceIn(0f, 1f)
                )
            }

            // Perspective warp from full-resolution source frame to 256x256 top-down board
            val srcPtsFull = orderedScaled.map { pt ->
                Point(pt.x / scale, pt.y / scale)
            }.toTypedArray()

            val srcMat = MatOfPoint2f(*srcPtsFull)
            val dstMat = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(WARP_SIZE, 0.0),
                Point(WARP_SIZE, WARP_SIZE),
                Point(0.0, WARP_SIZE)
            )
            val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
            val warped = Mat()

            try {
                Imgproc.warpPerspective(srcRgba, warped, transform, Size(WARP_SIZE, WARP_SIZE))
                val metrics = evaluateWarpedBoard(warped)
                val areaConfidence = ((bestArea / (scaledW * scaledH)) * 120.0).toInt().coerceIn(30, 60)
                val totalConfidence = (areaConfidence + (metrics.contrastPercent * 0.4f).toInt()).coerceIn(35, 99)

                val warpedBmp = if (emitWarpedBitmap) {
                    Bitmap.createBitmap(WARP_SIZE.toInt(), WARP_SIZE.toInt(), Bitmap.Config.ARGB_8888).also {
                        Utils.matToBitmap(warped, it)
                    }
                } else null

                return InternalBoardDetection(
                    boardDetected = true,
                    normalizedCorners = normalized,
                    confidencePercent = totalConfidence,
                    occupiedSquaresCount = metrics.occupiedCount,
                    warpedBitmap = warpedBmp
                )
            } finally {
                srcMat.release()
                dstMat.release()
                transform.release()
                warped.release()
            }
        } finally {
            contours.forEach { it.release() }
            resized.release()
            gray.release()
            blurred.release()
            edges.release()
            dilated.release()
            hierarchy.release()
            kernel.release()
        }
    }

    private fun computeOpenCvGridMetrics(
        warpedRgba: Mat,
        boardBitmap: Bitmap,
        bounds: RectF
    ): OnDeviceGridAnalysis {
        val resized = Mat()
        val gray = Mat()
        val gradX = Mat()
        val gradY = Mat()
        val absGradX = Mat()
        val absGradY = Mat()
        val edgeMag = Mat()

        try {
            Imgproc.resize(warpedRgba, resized, Size(256.0, 256.0))
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Sobel(gray, gradX, CvType.CV_16S, 1, 0, 3)
            Imgproc.Sobel(gray, gradY, CvType.CV_16S, 0, 1, 3)
            Core.convertScaleAbs(gradX, absGradX)
            Core.convertScaleAbs(gradY, absGradY)
            Core.addWeighted(absGradX, 0.5, absGradY, 0.5, 0.0, edgeMag)

            val metrics = ArrayList<SquareVisionMetrics>(64)
            var minBoardLuma = 255f
            var maxBoardLuma = 0f

            for (row in 0..7) {
                for (col in 0..7) {
                    val x0 = col * 32
                    val y0 = row * 32

                    val centerGray = gray.submat(y0 + 9, y0 + 23, x0 + 9, x0 + 23)
                    val borderGrayTop = gray.submat(y0 + 2, y0 + 6, x0 + 2, x0 + 30)
                    val centerEdge = edgeMag.submat(y0 + 8, y0 + 24, x0 + 8, x0 + 24)

                    val centerMeanMat = MatOfDouble()
                    val centerStdMat = MatOfDouble()
                    val borderMeanMat = MatOfDouble()
                    val borderStdMat = MatOfDouble()
                    val edgeMeanMat = MatOfDouble()
                    val edgeStdMat = MatOfDouble()

                    try {
                        Core.meanStdDev(centerGray, centerMeanMat, centerStdMat)
                        Core.meanStdDev(borderGrayTop, borderMeanMat, borderStdMat)
                        Core.meanStdDev(centerEdge, edgeMeanMat, edgeStdMat)

                        val centerMean = centerMeanMat.get(0, 0)[0].toFloat()
                        val borderMean = borderMeanMat.get(0, 0)[0].toFloat()
                        val edgeDensity = edgeMeanMat.get(0, 0)[0].toFloat()

                        minBoardLuma = min(minBoardLuma, borderMean)
                        maxBoardLuma = max(maxBoardLuma, borderMean)

                        val lumaDelta = abs(centerMean - borderMean)
                        val isOccupied = edgeDensity > 18f || lumaDelta > 24f
                        val occupancy = when {
                            !isOccupied -> SquareOccupancy.EMPTY
                            centerMean >= borderMean + 8f || centerMean > 145f -> SquareOccupancy.LIGHT_PIECE
                            else -> SquareOccupancy.DARK_PIECE
                        }

                        metrics.add(
                            SquareVisionMetrics(
                                row = row,
                                col = col,
                                edgeDensity = edgeDensity,
                                centerMeanLuma = centerMean,
                                borderMeanLuma = borderMean,
                                occupancy = occupancy
                            )
                        )
                    } finally {
                        centerGray.release()
                        borderGrayTop.release()
                        centerEdge.release()
                        centerMeanMat.release()
                        centerStdMat.release()
                        borderMeanMat.release()
                        borderStdMat.release()
                        edgeMeanMat.release()
                        edgeStdMat.release()
                    }
                }
            }

            val contrastPercent = (((maxBoardLuma - minBoardLuma) / 255f) * 100f)
                .toInt()
                .coerceIn(10, 99)

            return OnDeviceGridAnalysis(
                squareMetrics = metrics,
                occupiedCount = metrics.count { it.occupancy != SquareOccupancy.EMPTY },
                contrastScorePercent = contrastPercent,
                boardBounds = bounds,
                previewBitmap = boardBitmap
            )
        } finally {
            resized.release()
            gray.release()
            gradX.release()
            gradY.release()
            absGradX.release()
            absGradY.release()
            edgeMag.release()
        }
    }

    private data class WarpedEval(val occupiedCount: Int, val contrastPercent: Int)

    private fun evaluateWarpedBoard(warpedRgba: Mat): WarpedEval {
        val gray = Mat()
        try {
            Imgproc.cvtColor(warpedRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            var minLuma = 255.0
            var maxLuma = 0.0
            var occupied = 0

            val cellSize = (WARP_SIZE / 8.0).toInt()
            for (r in 0..7) {
                for (c in 0..7) {
                    val y0 = r * cellSize
                    val x0 = c * cellSize
                    val center = gray.submat(y0 + 8, y0 + cellSize - 8, x0 + 8, x0 + cellSize - 8)
                    val border = gray.submat(y0 + 2, y0 + 6, x0 + 2, x0 + cellSize - 2)
                    val cMean = MatOfDouble()
                    val cStd = MatOfDouble()
                    val bMean = MatOfDouble()
                    val bStd = MatOfDouble()
                    try {
                        Core.meanStdDev(center, cMean, cStd)
                        Core.meanStdDev(border, bMean, bStd)
                        val cm = cMean.get(0, 0)[0]
                        val cs = cStd.get(0, 0)[0]
                        val bm = bMean.get(0, 0)[0]
                        minLuma = min(minLuma, bm)
                        maxLuma = max(maxLuma, bm)
                        if (cs > 18.0 || abs(cm - bm) > 24.0) {
                            occupied++
                        }
                    } finally {
                        center.release()
                        border.release()
                        cMean.release()
                        cStd.release()
                        bMean.release()
                        bStd.release()
                    }
                }
            }

            val contrast = (((maxLuma - minLuma) / 255.0) * 100.0).toInt().coerceIn(10, 99)
            return WarpedEval(occupied, contrast)
        } finally {
            gray.release()
        }
    }

    private fun smoothCorners(newCorners: List<PointF>): List<PointF> {
        if (newCorners.size != 4) {
            smoothedCorners = null
            return emptyList()
        }
        val prev = smoothedCorners
        if (prev == null) {
            val arr = newCorners.toTypedArray()
            smoothedCorners = arr
            return newCorners
        }
        val alpha = 0.38f
        val blended = Array(4) { i ->
            PointF(
                prev[i].x * (1f - alpha) + newCorners[i].x * alpha,
                prev[i].y * (1f - alpha) + newCorners[i].y * alpha
            )
        }
        smoothedCorners = blended
        return blended.toList()
    }

    private fun orderQuadCorners(pts: Array<Point>): Array<Point> {
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val topLeft = sortedBySum.first()
        val bottomRight = sortedBySum.last()
        val sortedByDiff = pts.sortedBy { it.y - it.x }
        val topRight = sortedByDiff.first()
        val bottomLeft = sortedByDiff.last()
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun hasValidAspectRatio(pts: Array<Point>): Boolean {
        val ordered = orderQuadCorners(pts)
        val topW = hypot(ordered[1].x - ordered[0].x, ordered[1].y - ordered[0].y)
        val bottomW = hypot(ordered[2].x - ordered[3].x, ordered[2].y - ordered[3].y)
        val leftH = hypot(ordered[3].x - ordered[0].x, ordered[3].y - ordered[0].y)
        val rightH = hypot(ordered[2].x - ordered[1].x, ordered[2].y - ordered[1].y)

        val avgW = (topW + bottomW) / 2.0
        val avgH = (leftH + rightH) / 2.0
        if (avgW < 20.0 || avgH < 20.0) return false
        val ratio = avgW / avgH
        return ratio in 0.55..1.85
    }

    private fun hypot(dx: Double, dy: Double): Double = kotlin.math.sqrt(dx * dx + dy * dy)

    private fun rotateMatInPlace(src: Mat, dst: Mat, rotationDegrees: Int) {
        when (rotationDegrees) {
            90 -> Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, dst, Core.ROTATE_180)
            270 -> Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> src.copyTo(dst)
        }
    }

    private fun cropFallbackBitmap(source: Bitmap, bounds: RectF): Bitmap {
        val left = (bounds.left.coerceIn(0f, 0.85f) * source.width).toInt()
        val top = (bounds.top.coerceIn(0f, 0.85f) * source.height).toInt()
        val right = (bounds.right.coerceIn(bounds.left + 0.1f, 1f) * source.width).toInt()
        val bottom = (bounds.bottom.coerceIn(bounds.top + 0.1f, 1f) * source.height).toInt()
        val w = max(64, min(right - left, source.width - left))
        val h = max(64, min(bottom - top, source.height - top))
        val cropped = Bitmap.createBitmap(
            source,
            left.coerceIn(0, source.width - 1),
            top.coerceIn(0, source.height - 1),
            w,
            h
        )
        return Bitmap.createScaledBitmap(cropped, 256, 256, true)
    }

    companion object {
        private const val TAG = "OpenCvBoardDetection"
        private const val WARP_SIZE = 256.0
    }
}
