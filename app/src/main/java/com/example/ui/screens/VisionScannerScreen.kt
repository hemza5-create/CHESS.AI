package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ChessVisionUiState
import com.example.ChessVisionViewModel
import com.example.R
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.theme.GrandmasterGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TacticalEmerald
import com.example.vision.LiveBoardDetectionState
import com.example.vision.OpenCvBoardDetectionService
import com.example.vision.SquareOccupancy
import java.util.concurrent.Executors
import kotlin.math.max

@Composable
fun VisionScannerScreen(
    uiState: ChessVisionUiState,
    viewModel: ChessVisionViewModel,
    onAppliedToBoard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cameraEnabled by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cropMarginPercent by remember { mutableFloatStateOf(8f) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) cameraEnabled = true
    }

    // Zero-permission Android Photo Picker for screenshots and gallery photos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            decodeScaledBitmapFromUri(context, uri)?.let { bmp ->
                cameraEnabled = false
                viewModel.processVisionBitmap(bmp)
            }
        }
    }

    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.vision_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.vision_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Camera Viewfinder / Grid Preview Area
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0A0F16)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                cameraEnabled && hasCameraPermission -> {
                                    CameraXPreviewBox(
                                        openCvService = viewModel.openCvService,
                                        liveDetection = uiState.liveDetection,
                                        onImageCaptureReady = { imageCaptureRef = it },
                                        cropMarginFraction = cropMarginPercent / 100f
                                    )
                                }

                                uiState.visionGridAnalysis != null -> {
                                    val grid = uiState.visionGridAnalysis
                                    Image(
                                        bitmap = grid.previewBitmap.asImageBitmap(),
                                        contentDescription = "Analyzed chessboard crop",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Real-time 8x8 occupancy heatmap overlay
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cellW = size.width / 8f
                                        val cellH = size.height / 8f
                                        grid.squareMetrics.forEach { cell ->
                                            val x = cell.col * cellW
                                            val y = cell.row * cellH
                                            drawRect(
                                                color = Color.White.copy(alpha = 0.28f),
                                                topLeft = Offset(x, y),
                                                size = Size(cellW, cellH),
                                                style = Stroke(width = 1.dp.toPx())
                                            )
                                            when (cell.occupancy) {
                                                SquareOccupancy.LIGHT_PIECE -> {
                                                    drawRect(
                                                        color = TacticalEmerald.copy(alpha = 0.85f),
                                                        topLeft = Offset(x + 3f, y + 3f),
                                                        size = Size(cellW - 6f, cellH - 6f),
                                                        style = Stroke(width = 2.dp.toPx())
                                                    )
                                                }
                                                SquareOccupancy.DARK_PIECE -> {
                                                    drawRect(
                                                        color = GrandmasterGold.copy(alpha = 0.85f),
                                                        topLeft = Offset(x + 3f, y + 3f),
                                                        size = Size(cellW - 6f, cellH - 6f),
                                                        style = Stroke(width = 2.dp.toPx())
                                                    )
                                                }
                                                SquareOccupancy.EMPTY -> Unit
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_vision_hero),
                                        contentDescription = stringResource(R.string.vision_title),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Text(
                                            text = stringResource(R.string.vision_empty_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                            }

                            if (uiState.isVisionProcessing) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            text = stringResource(R.string.vision_analyzing),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Camera & Photo Picker Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (cameraEnabled && hasCameraPermission) {
                                Button(
                                    onClick = {
                                        val capture = imageCaptureRef ?: return@Button
                                        capture.takePicture(
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    val bmp = image.toRotatedBitmap()
                                                    image.close()
                                                    if (bmp != null) {
                                                        cameraEnabled = false
                                                        viewModel.processVisionBitmap(bmp)
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e("VisionScanner", "Capture failed", exception)
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("capture_board_button")
                                ) {
                                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.vision_capture_frame))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (hasCameraPermission) {
                                            cameraEnabled = true
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_camera_button")
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.vision_open_camera))
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_screenshot_button")
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.vision_pick_screenshot))
                            }
                        }

                        // Calibration Margin & Perspective Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "إطار القص: ${cropMarginPercent.toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = cropMarginPercent,
                                    onValueChange = { value ->
                                        cropMarginPercent = value
                                        val m = value / 100f
                                        viewModel.updateVisionBounds(RectF(m, m, 1f - m, 1f - m))
                                    },
                                    valueRange = 0f..22f
                                )
                            }

                            OutlinedButton(onClick = viewModel::toggleVisionPerspective) {
                                Icon(Icons.Rounded.FlipCameraAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.visionBlackPerspective) {
                                        stringResource(R.string.turn_black)
                                    } else {
                                        stringResource(R.string.turn_white)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // On-Device Grid Metrics & Detected FEN Card
            uiState.visionGridAnalysis?.let { grid ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.vision_grid_analysis_title),
                                style = MaterialTheme.typography.titleSmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricPill(
                                    label = stringResource(R.string.vision_occupied_squares),
                                    value = "${grid.occupiedCount} / 64",
                                    modifier = Modifier.weight(1f)
                                )
                                MetricPill(
                                    label = stringResource(R.string.vision_contrast_score),
                                    value = "${grid.contrastScorePercent}%",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            uiState.visionResult?.let { res ->
                                Text(
                                    text = res.summaryAr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                ) {
                                    Text(
                                        text = res.detectedFen,
                                        fontFamily = MonoFontFamily,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                if (res.isPlaceholder) {
                                    FirebaseAiNoticeBanner()
                                }

                                Button(
                                    onClick = {
                                        if (viewModel.applyVisionFenToAnalyzer()) {
                                            onAppliedToBoard()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("apply_vision_fen_button")
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.vision_apply_to_board))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontFamily = MonoFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CameraXPreviewBox(
    openCvService: OpenCvBoardDetectionService,
    liveDetection: LiveBoardDetectionState,
    onImageCaptureReady: (ImageCapture) -> Unit,
    cropMarginFraction: Float
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner, openCvService) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            try {
                provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                onImageCaptureReady(capture)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor, openCvService)
                    }

                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraXPreviewBox", "Failed to bind CameraX", e)
            }
        }, mainExecutor)

        onDispose {
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 8x8 Alignment Grid Reticle + Live OpenCV Quadrilateral Tracking Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = size.width * cropMarginFraction
            val top = size.height * cropMarginFraction
            val w = size.width * (1f - 2f * cropMarginFraction)
            val h = size.height * (1f - 2f * cropMarginFraction)

            drawRect(
                color = TacticalEmerald.copy(alpha = 0.55f),
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 1.5.dp.toPx())
            )

            for (i in 1..7) {
                val x = left + (w / 8f) * i
                val y = top + (h / 8f) * i
                drawLine(
                    color = TacticalEmerald.copy(alpha = 0.28f),
                    start = Offset(x, top),
                    end = Offset(x, top + h),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = TacticalEmerald.copy(alpha = 0.28f),
                    start = Offset(left, y),
                    end = Offset(left + w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (liveDetection.boardDetected && liveDetection.normalizedCorners.size == 4) {
                val pts = liveDetection.normalizedCorners.map {
                    Offset(it.x * size.width, it.y * size.height)
                }
                val quadPath = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }
                drawPath(
                    path = quadPath,
                    color = GrandmasterGold,
                    style = Stroke(width = 3.dp.toPx())
                )
                pts.forEach { corner ->
                    drawCircle(
                        color = TacticalEmerald,
                        radius = 6.dp.toPx(),
                        center = corner
                    )
                }
            }
        }

        // Live OpenCV Telemetry Pill
        Surface(
            color = Color.Black.copy(alpha = 0.68f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(
                1.dp,
                if (liveDetection.boardDetected) GrandmasterGold else TacticalEmerald.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .testTag("opencv_live_badge")
        ) {
            val statusText = when {
                !liveDetection.isOpenCvReady -> stringResource(R.string.vision_opencv_unavailable)
                liveDetection.boardDetected ->
                    "${stringResource(R.string.vision_opencv_locked)} • ${liveDetection.confidencePercent}% • ${liveDetection.processingTimeMs}ms"
                else ->
                    "${stringResource(R.string.vision_opencv_searching)} • ${liveDetection.processingTimeMs}ms"
            }
            Text(
                text = statusText,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (liveDetection.boardDetected) GrandmasterGold else Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

private fun ImageProxy.toRotatedBitmap(): Bitmap? {
    return try {
        val raw = toBitmap()
        val degrees = imageInfo.rotationDegrees.toFloat()
        if (degrees == 0f) {
            raw
        } else {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }
    } catch (_: Exception) {
        null
    }
}

private fun decodeScaledBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 1280) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    } catch (_: Exception) {
        null
    }
}
