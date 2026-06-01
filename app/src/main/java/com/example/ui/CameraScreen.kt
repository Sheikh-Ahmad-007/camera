package com.example.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import com.example.camera.DeviceCapabilities
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.db.PhotoMetadata
import com.example.viewmodel.CameraViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.roundToInt

// Elegant Dark Signature Colors
val ElegantGold = Color(0xFFFBBF24)
val DeepDarkCanvas = Color(0xFF050505)
val SolidBorderColor = Color.White.copy(0.12f)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Request vital device Camera and storage permissions
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Observe ViewModel inputs
    val currentViewingPhoto by viewModel.currentViewingPhoto.collectAsState()
    val viewingBitmap by viewModel.viewingBitmap.collectAsState()
    val isAiProcessing by viewModel.isAiProcessing.collectAsState()
    val deviceCaps by viewModel.deviceCapabilities.collectAsState()
    val capturedPhotos by viewModel.allPhotos.collectAsState()

    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepDarkCanvas
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (cameraPermissionState.status.isGranted) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar containing "AI Enhanced" badge and settings buttons
                    QuickControlTopBar(
                        viewModel = viewModel,
                        onToggleCaps = { showDiagnostics = !showDiagnostics },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    // Viewfinder area framed elegantly with rounded corners and subtle border
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, SolidBorderColor, RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        ViewfinderSection(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating telemetry over viewfinder with glass container styling
                        TelemetryOverlay(
                            viewModel = viewModel,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        )

                        // Custom corner notches and watermark overlays inside viewfinder
                        ViewfinderHUDLabels(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Tactile bottom controls tray
                    BottomControlsTray(
                        viewModel = viewModel,
                        capturedPhotos = capturedPhotos,
                        showDiagnostics = showDiagnostics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                    )
                }
            } else {
                // Permissions Missing Alert
                PermissionPlaceholder(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }

            // --- Dialog Overlays ---
            if (showDiagnostics && deviceCaps != null) {
                HardwareDiagnosticsDialog(
                    deviceCapabilities = deviceCaps!!,
                    onDismiss = { showDiagnostics = false }
                )
            }

            if (currentViewingPhoto != null && viewingBitmap != null) {
                PhotoReviewWorkspace(
                    photo = currentViewingPhoto!!,
                    bitmap = viewingBitmap!!,
                    isAiProcessing = isAiProcessing,
                    viewModel = viewModel,
                    onDismiss = { viewModel.selectViewingPhoto(null) }
                )
            }
        }
    }
}

@Composable
fun ViewfinderSection(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isPeakingEnabled by viewModel.isFocusPeakingEnabled.collectAsState()
    val focusDistanceVal by viewModel.focusDistance.collectAsState()

    Box(modifier = modifier) {
        // Embed real CameraX preview binding
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay a transparent Canvas that emulates rule of thirds and focus targets
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("viewfinder_canvas")
        ) {
            val w = size.width
            val h = size.height

            // Rule of Thirds - styled with premium ultra-thin lines
            drawLine(
                color = Color(255, 255, 255, 18),
                start = Offset(w / 3f, 0f),
                end = Offset(w / 3f, h),
                strokeWidth = 0.5.dp.toPx()
            )
            drawLine(
                color = Color(255, 255, 255, 18),
                start = Offset(w * 2f / 3f, 0f),
                end = Offset(w * 2f / 3f, h),
                strokeWidth = 0.5.dp.toPx()
            )
            drawLine(
                color = Color(255, 255, 255, 18),
                start = Offset(0f, h / 3f),
                end = Offset(w, h / 3f),
                strokeWidth = 0.5.dp.toPx()
            )
            drawLine(
                color = Color(255, 255, 255, 18),
                start = Offset(0f, h * 2f / 3f),
                end = Offset(w, h * 2f / 3f),
                strokeWidth = 0.5.dp.toPx()
            )

            // Focus Target Box (Drawn with professional corner bracket notches)
            val boxSize = 80.dp.toPx()
            val left = (w - boxSize) / 2f
            val top = (h - boxSize) / 2f
            val cornerLen = 12.dp.toPx()

            val glowColor = if (focusDistanceVal > 0f) Color(0xFF00FF42) else ElegantGold.copy(0.7f)

            // Top-Left corner notch
            drawLine(glowColor, Offset(left, top), Offset(left + cornerLen, top), 1.5.dp.toPx())
            drawLine(glowColor, Offset(left, top), Offset(left, top + cornerLen), 1.5.dp.toPx())
            // Top-Right corner notch
            drawLine(glowColor, Offset(left + boxSize, top), Offset(left + boxSize - cornerLen, top), 1.5.dp.toPx())
            drawLine(glowColor, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLen), 1.5.dp.toPx())
            // Bottom-Left corner notch
            drawLine(glowColor, Offset(left, top + boxSize), Offset(left + cornerLen, top + boxSize), 1.5.dp.toPx())
            drawLine(glowColor, Offset(left, top + boxSize), Offset(left, top + boxSize - cornerLen), 1.5.dp.toPx())
            // Bottom-Right corner notch
            drawLine(glowColor, Offset(left + boxSize, top + boxSize), Offset(left + boxSize - cornerLen, top + boxSize), 1.5.dp.toPx())
            drawLine(glowColor, Offset(left + boxSize, top + boxSize), Offset(left + boxSize, top + boxSize - cornerLen), 1.5.dp.toPx())

            // Peaking highlights representation
            if (isPeakingEnabled) {
                drawCircle(
                    color = Color(0xFF00FF42).copy(alpha = 0.4f),
                    radius = 35.dp.toPx(),
                    center = Offset(w / 2f, h / 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun TelemetryOverlay(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val temp by viewModel.temperatureSensorValue.collectAsState()
    val memory by viewModel.activeMemoryUsage.collectAsState()
    val ispLoad by viewModel.dimensityIspLoad.collectAsState()

    Column(
        modifier = modifier
            .width(180.dp)
            .background(Color.Black.copy(0.45f), RoundedCornerShape(8.dp))
            .border(0.5.dp, SolidBorderColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "SYSTEM TELEMETRY",
            color = ElegantGold,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("ISP CORE LOAD", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("$ispLoad%", color = if (ispLoad > 80) Color.Red else Color(0xFF00FF42), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("TEMP SENSOR", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text(String.format("%.1f°C", temp), color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("APU MEMORY", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("$memory MB", color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ViewfinderHUDLabels(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val focusDistanceVal by viewModel.focusDistance.collectAsState()
    val resMode by viewModel.resolutionMode.collectAsState()

    Box(modifier = modifier) {
        // Under the center focus target box, show current active focus mode indicator
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 96.dp)
        ) {
            Text(
                text = if (focusDistanceVal > 0f) "MF-C PEAK" else "AF-C",
                color = if (focusDistanceVal > 0f) Color(0xFF00FF42) else ElegantGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .background(Color.Black.copy(0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Bottom right watermark labels matching Tailwind HTML mock specs:
        // `200MP ULTRA HD` & `Dimensity 7200-U Engine`
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, ElegantGold.copy(0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (resMode.contains("200MP")) "200MP ULTRA HD" else resMode.uppercase(),
                    color = ElegantGold,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "DIMENSITY 7200-U ENGINE",
                color = Color.White.copy(0.5f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun QuickControlTopBar(
    viewModel: CameraViewModel,
    onToggleCaps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shootingMode by viewModel.shootingMode.collectAsState()
    val isRawEnabled by viewModel.isRawEnabled.collectAsState()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left capsule: AI Enhanced pill with glowing dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.Black.copy(0.4f), RoundedCornerShape(100.dp))
                .border(0.5.dp, SolidBorderColor, RoundedCornerShape(100.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(ElegantGold, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AI ENHANCED",
                color = ElegantGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        // Right side settings / info gears & status options
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interactive HDR text selector
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    viewModel.shootingMode.value = "HDR"
                }
            ) {
                Text(
                    text = "HDR",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.alpha(if (shootingMode == "HDR") 1.0f else 0.5f)
                )
                if (shootingMode == "HDR") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(ElegantGold, CircleShape)
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Interactive RAW file writer status toggle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    viewModel.isRawEnabled.value = !isRawEnabled
                }
            ) {
                Text(
                    text = "RAW",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.alpha(if (isRawEnabled) 1.0f else 0.5f)
                )
                if (isRawEnabled) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(ElegantGold, CircleShape)
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Gear button to show hardware specs
            IconButton(
                onClick = onToggleCaps,
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(0.06f), CircleShape)
                    .border(0.5.dp, SolidBorderColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Specifications gear",
                    tint = Color.White.copy(0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun HistogramVisualizer(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val histogram by viewModel.histogramData.collectAsState()
    
    Canvas(
        modifier = modifier
            .background(Color.Black.copy(0.6f), RoundedCornerShape(6.dp))
            .border(0.5.dp, SolidBorderColor, RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        val pathWidth = size.width / 256f
        val maxHeight = size.height

        for (i in 0 until 256) {
            val count = histogram.getOrElse(i) { 0 }
            val heightRatio = (count / 2000f).coerceIn(0f, 1f)
            val x = i * pathWidth
            drawLine(
                color = ElegantGold.copy(0.85f),
                start = Offset(x, maxHeight),
                end = Offset(x, maxHeight - (maxHeight * heightRatio)),
                strokeWidth = pathWidth.coerceAtLeast(1f)
            )
        }
    }
}

@Composable
fun BottomControlsTray(
    viewModel: CameraViewModel,
    capturedPhotos: List<PhotoMetadata>,
    showDiagnostics: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shootingMode by viewModel.shootingMode.collectAsState()
    val isoVal by viewModel.manualIso.collectAsState()
    val evVal by viewModel.exposureCompensation.collectAsState()
    val wbKelvin by viewModel.whiteBalanceKelvin.collectAsState()

    var activeControlTab by remember { mutableStateOf("ISO") }
    var showManualControls by remember { mutableStateOf(false) }

    LaunchedEffect(shootingMode) {
        showManualControls = shootingMode == "Pro Mode"
    }

    // Shutter Speed calculation based on current ISO value
    val shutterSpeedStr = when {
        isoVal < 100 -> "1/4000"
        isoVal < 200 -> "1/2000"
        isoVal < 400 -> "1/1000"
        isoVal < 800 -> "1/500"
        isoVal < 1600 -> "1/250"
        isoVal < 3200 -> "1/125"
        else -> "1/60"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Dynamic parameters bar: displays real-time tactile metric displays
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = SolidBorderColor, RoundedCornerShape(8.dp))
                .background(Color.Black.copy(0.4f), RoundedCornerShape(8.dp))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ISO dial display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeControlTab = "ISO"; viewModel.shootingMode.value = "Pro Mode" }
            ) {
                Text("ISO", color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$isoVal",
                    color = if (activeControlTab == "ISO" && shootingMode == "Pro Mode") ElegantGold else Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // SHUTTER speed computation indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeControlTab = "ISO"; viewModel.shootingMode.value = "Pro Mode" }
            ) {
                Text("SHUTTER", color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = shutterSpeedStr,
                    color = if (activeControlTab == "ISO" && shootingMode == "Pro Mode") ElegantGold else Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // EV offset parameter dial
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeControlTab = "EV"; viewModel.shootingMode.value = "Pro Mode" }
            ) {
                Text("EV", color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format("%+.1f", evVal / 3f),
                    color = if (activeControlTab == "EV" && shootingMode == "Pro Mode") ElegantGold else Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // FOCUS tracking feedback dial
            val peakOn by viewModel.isFocusPeakingEnabled.collectAsState()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeControlTab = "FOCUS"; viewModel.shootingMode.value = "Pro Mode" }
            ) {
                Text("FOCUS", color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (peakOn) "Peaking" else "Auto",
                    color = if (peakOn) ElegantGold else if (activeControlTab == "FOCUS" && shootingMode == "Pro Mode") ElegantGold else Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // WB Kelvin temperature level dial
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeControlTab = "WB"; viewModel.shootingMode.value = "Pro Mode" }
            ) {
                Text("WB", color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${wbKelvin}K",
                    color = if (activeControlTab == "WB" && shootingMode == "Pro Mode") ElegantGold else Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real-time Histogram canvas & quick peaking/raw switches (Only shown in Pro Mode / active controls)
        AnimatedVisibility(
            visible = showManualControls,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Golden luma bar histogram representation of live viewfinder feeding
                    HistogramVisualizer(
                        viewModel = viewModel,
                        modifier = Modifier
                            .width(140.dp)
                            .fillMaxHeight()
                    )

                    // Floating manual parameters toggle chips
                    val peakOn by viewModel.isFocusPeakingEnabled.collectAsState()
                    val rawOn by viewModel.isRawEnabled.collectAsState()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.isFocusPeakingEnabled.value = !peakOn },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (peakOn) ElegantGold.copy(0.2f) else Color.White.copy(0.08f)
                            ),
                            border = BorderStroke(0.5.dp, if (peakOn) ElegantGold else Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("PEAKING", fontSize = 8.sp, color = if (peakOn) ElegantGold else Color.White, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = { viewModel.isRawEnabled.value = !rawOn },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (rawOn) ElegantGold.copy(0.2f) else Color.White.copy(0.08f)
                            ),
                            border = BorderStroke(0.5.dp, if (rawOn) ElegantGold else Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("RAW", fontSize = 8.sp, color = if (rawOn) ElegantGold else Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom sliders representing Chosen Tab ISO/WB/Focus/EV
                ManualSlidersLayout(
                    viewModel = viewModel,
                    activeControlTab = activeControlTab,
                    onActiveControlTabChange = { activeControlTab = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Shooting Modes selecting ribbon - centered matching Tailwind visual style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shootModes = listOf(
                "Night" to "Night",
                "Video" to "Standard",
                "Pro Photo" to "Pro Mode",
                "Portrait" to "Portrait",
                "More" to "More"
            )

            for ((label, modeVal) in shootModes) {
                val isSelected = (modeVal == shootingMode) || (label == "More" && showDiagnostics)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (label == "More") {
                                viewModel.triggerSpecsRead()
                            } else {
                                viewModel.shootingMode.value = modeVal
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label.uppercase(),
                        color = if (isSelected) ElegantGold else Color.White.copy(0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(ElegantGold, CircleShape)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Shutter, Quick gallery thumb review, and Ultra-HD toggler row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left thumbnail
            CapturedPhotoThumbSlot(
                photos = capturedPhotos,
                onReviewPhoto = { photo -> viewModel.selectViewingPhoto(photo) }
            )

            // Tactile Shutter circular outer trigger ring and white core
            ShutterActionButton(
                onCaptureClick = {
                    viewModel.executeCapture(context, null)
                }
            )

            // Right: MP Quick Resolution toggles styled nicely like HTML rotating wheel options
            val resMode by viewModel.resolutionMode.collectAsState()
            IconButton(
                onClick = {
                    val nextMode = when (resMode) {
                        "12.5MP Mode" -> "50MP Mode"
                        "50MP Mode" -> "200MP Ultra HD"
                        else -> "12.5MP Mode"
                    }
                    viewModel.resolutionMode.value = nextMode
                },
                modifier = Modifier
                    .size(48.dp)
                    .border(0.5.dp, SolidBorderColor, CircleShape)
                    .background(Color.White.copy(0.05f), CircleShape)
                    .testTag("mp_toggle")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            resMode.contains("200") -> "200M"
                            resMode.contains("50") -> "50M"
                            else -> "12.5M"
                        },
                        color = if (resMode.contains("200")) ElegantGold else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 10.sp
                    )
                    Text(
                        text = "UHD",
                        color = if (resMode.contains("200")) ElegantGold.copy(0.7f) else Color.White.copy(0.5f),
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 7.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ManualSlidersLayout(
    viewModel: CameraViewModel,
    activeControlTab: String,
    onActiveControlTabChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isoVal by viewModel.manualIso.collectAsState()
    val focusVal by viewModel.focusDistance.collectAsState()
    val evVal by viewModel.exposureCompensation.collectAsState()
    val wbKelvin by viewModel.whiteBalanceKelvin.collectAsState()

    val tabs = listOf("ISO", "FOCUS", "WB", "EV")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(0.8f), RoundedCornerShape(12.dp))
            .border(0.5.dp, SolidBorderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Tab selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            for (tab in tabs) {
                val selected = tab == activeControlTab
                Text(
                    text = tab,
                    color = if (selected) ElegantGold else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onActiveControlTabChange(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("slider_tab_$tab")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Slider representing chosen Tab
        when (activeControlTab) {
            "ISO" -> {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SENSOR SENSITIVITY DNG (ISO)", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("$isoVal ISO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = isoVal.toFloat(),
                        onValueChange = {
                            viewModel.manualISOEnabled.value = true
                            viewModel.manualIso.value = it.roundToInt()
                        },
                        valueRange = 50f..6400f,
                        colors = SliderDefaults.colors(
                            thumbColor = ElegantGold,
                            activeTrackColor = ElegantGold,
                            inactiveTrackColor = Color.White.copy(0.12f)
                        ),
                        modifier = Modifier.testTag("iso_slider")
                    )
                }
            }

            "FOCUS" -> {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LENS FOCUS LIMITER (f/1.65)", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (focusVal == 0f) "Auto-Focus (Infinity)" else String.format("%.2f m", focusVal),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = focusVal,
                        onValueChange = {
                            viewModel.manualFocusEnabled.value = it > 0f
                            viewModel.focusDistance.value = it
                        },
                        valueRange = 0f..10f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00FF42),
                            activeTrackColor = Color(0xFF00FF42),
                            inactiveTrackColor = Color.White.copy(0.12f)
                        ),
                        modifier = Modifier.testTag("focus_slider")
                    )
                }
            }

            "WB" -> {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("AESTHETIC TEMPERATURE (K)", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("$wbKelvin Kelvin", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = wbKelvin.toFloat(),
                        onValueChange = {
                            viewModel.whiteBalanceMode.value = "Kelvins"
                            viewModel.whiteBalanceKelvin.value = it.roundToInt()
                        },
                        valueRange = 2000f..10000f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(0.12f)
                        ),
                        modifier = Modifier.testTag("wb_slider")
                    )
                }
            }

            "EV" -> {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("EXPOSURE COMPENSATION", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = String.format("%+.2f EV", evVal / 3f),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = evVal.toFloat(),
                        onValueChange = { viewModel.exposureCompensation.value = it.roundToInt() },
                        valueRange = -12f..12f,
                        steps = 25,
                        colors = SliderDefaults.colors(
                            thumbColor = ElegantGold,
                            activeTrackColor = ElegantGold,
                            inactiveTrackColor = Color.White.copy(0.12f)
                        ),
                        modifier = Modifier.testTag("ev_slider")
                    )
                }
            }
        }
    }
}

@Composable
fun ShutterActionButton(
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shrinkFactor by animateFloatAsState(targetValue = if (isPressed) 0.88f else 1.0f)

    Box(
        modifier = modifier
            .size(76.dp)
            .scale(shrinkFactor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onCaptureClick
            )
            .testTag("shutter_button"),
        contentAlignment = Alignment.Center
    ) {
        // Outer white border ring spaced with padding exactly matching Elegant Dark design mock
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, Color.White, CircleShape)
                .padding(4.dp)
        ) {
            // Inner shutter pill core with black buffer border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, Color.Black, CircleShape)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun CapturedPhotoThumbSlot(
    photos: List<PhotoMetadata>,
    onReviewPhoto: (PhotoMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    val lastPhoto = photos.firstOrNull()

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1F1F1F))
            .border(
                width = 0.5.dp,
                color = if (lastPhoto != null) ElegantGold else SolidBorderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable {
                if (lastPhoto != null) {
                    onReviewPhoto(lastPhoto)
                }
            }
            .testTag("gallery_thumbnail"),
        contentAlignment = Alignment.Center
    ) {
        if (lastPhoto != null) {
            Image(
                painter = rememberAsyncImagePainter(model = File(lastPhoto.filePath)),
                contentDescription = "Review Last captured photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Empty photo library holder",
                tint = Color.White.copy(0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PermissionPlaceholder(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDarkCanvas)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Camera Permission Graphic Icon",
                modifier = Modifier.size(64.dp),
                tint = ElegantGold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "CAMERA ACCESS REQUIRED",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Pro Camera relies deeply on camera frames, and manual API bindings. Please grant camera access permissions to initialize tactile dng capture.",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = ElegantGold)
            ) {
                Text("PROVISION ACCESS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HardwareDiagnosticsDialog(
    deviceCapabilities: DeviceCapabilities,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "HARDWARE SPEC DIAGNOSTICS",
                color = ElegantGold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Device Model: ${deviceCapabilities.deviceModel}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("Soc Platform: ${deviceCapabilities.socPlatform}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("APU HW Layer: ${deviceCapabilities.hardwareLevel}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.1f))

                deviceCapabilities.profiles.forEach { p ->
                    Text("${p.facing} CAPABILITIES", color = ElegantGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("Sensor: ${p.sensorModel}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Size class: ${p.maxResolutionMegapixels} MP", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Pixel Matrix: ${p.maxJpegWidth} x ${p.maxJpegHeight}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Pixel Pitch: ${p.physicalPixelSize}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("RAW DND support: ${if (p.rawSupported) "YES" else "NO"}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Aperture lenses: f/1.65 fixed high-optics", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Ext resolution layers: ${p.highResolutionModes.joinToString()}", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = ElegantGold)
            }
        },
        containerColor = Color(0xFF0A0A0A),
        textContentColor = Color.White
    )
}

@Composable
fun PhotoReviewWorkspace(
    photo: PhotoMetadata,
    bitmap: Bitmap,
    isAiProcessing: Boolean,
    viewModel: CameraViewModel,
    onDismiss: () -> Unit
) {
    val aiResultReport by viewModel.aiReport.collectAsState()
    val activeLassoRect by viewModel.lassoRect.collectAsState()

    var activeAiFilterTab by remember { mutableStateOf("METADATA") }

    // Lasso Coordinates mapping helper
    var lassoStartPoint by remember { mutableStateOf<Offset?>(null) }
    var lassoEndPoint by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDarkCanvas)
            .clickable(enabled = false) {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Task top review bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI DIGITAL WORKSPACE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row {
                    IconButton(
                        onClick = { viewModel.deleteViewingPhoto() },
                        modifier = Modifier.testTag("delete_photo_button")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete photo", tint = Color.Red)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.White.copy(0.1f), CircleShape)
                            .testTag("close_review_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close reviewer", tint = Color.White)
                    }
                }
            }

            // Central Interactive Review image core
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .pointerInput(activeAiFilterTab) {
                        if (activeAiFilterTab == "ADV_AI") {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    lassoStartPoint = offset
                                    lassoEndPoint = offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    lassoEndPoint = lassoEndPoint?.plus(dragAmount)
                                },
                                onDragEnd = {
                                    if (lassoStartPoint != null && lassoEndPoint != null) {
                                        val minX = minOf(lassoStartPoint!!.x, lassoEndPoint!!.x)
                                        val maxX = maxOf(lassoStartPoint!!.x, lassoEndPoint!!.x)
                                        val minY = minOf(lassoStartPoint!!.y, lassoEndPoint!!.y)
                                        val maxY = maxOf(lassoStartPoint!!.y, lassoEndPoint!!.y)

                                        viewModel.setLassoRect(
                                            Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
                                        )
                                    }
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Review Captured Image detail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Visual Representation overlay illustrating active Lasso rectangle choice
                if (activeAiFilterTab == "ADV_AI" && activeLassoRect != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color(0xFF00FF42).copy(0.7f),
                            topLeft = Offset(activeLassoRect!!.left.toFloat(), activeLassoRect!!.top.toFloat()),
                            size = androidx.compose.ui.geometry.Size(
                                activeLassoRect!!.width().toFloat(),
                                activeLassoRect!!.height().toFloat()
                            ),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                if (isAiProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ElegantGold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "COMPUTING AI STACK...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Lower interactive dashboard console containing AI controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A))
                    .padding(16.dp)
            ) {
                // Feature tabs (Metadata, Scene Analysis, Pixel Processing controls)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val workspaceTabs = listOf("METADATA", "AI SCENE", "ADV_AI")
                    for (tab in workspaceTabs) {
                        val selected = tab == activeAiFilterTab
                        Text(
                            text = tab,
                            color = if (selected) ElegantGold else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable {
                                    activeAiFilterTab = tab
                                    viewModel.setLassoRect(null)
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Detail display according to Selected Tab
                when (activeAiFilterTab) {
                    "METADATA" -> {
                        Column {
                            Text(
                                text = "PHOTO DATA METADATA",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(aiResultReport, color = Color.White, fontSize = 11.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    "AI SCENE" -> {
                        Column {
                            Text(
                                "INTELLIGENT SCENE RECOGNIZER (GEMINI)",
                                color = ElegantGold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.triggerGeminiSceneAnalysis() },
                                colors = ButtonDefaults.buttonColors(containerColor = ElegantGold),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("RUN GEMINI ANALYSIS", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (photo.aiSceneRecognized.contains("Unknown") && aiResultReport.startsWith("Photo captured")) {
                                    "Tap button to analyze image with server-side Gemini system."
                                } else {
                                    aiResultReport
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    "ADV_AI" -> {
                        Column {
                            Text(
                                text = "MEDIATEK APU NEURAL FILTER PIPELINE",
                                color = Color(0xFF00FF42),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.applyLocalAiFilter("AI ENHANCE") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text("Enhance HDR", fontSize = 9.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.applyLocalAiFilter("AI DENOISE") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text("Denoise", fontSize = 9.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.applyLocalAiFilter("AI PORTRAIT") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text("Portrait Blr", fontSize = 9.sp, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.applyLocalAiFilter("AI UPSCALE") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text("Upscale 200MP", fontSize = 9.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.applyLocalAiFilter("AI COLOR CORRECT") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text("Color CC", fontSize = 9.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.applyLocalAiFilter("AI OBJECT REMOVAL") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeLassoRect != null) Color(0xFF00FF42).copy(alpha = 0.4f) else Color(0xFF1F1F1F)
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text(if (activeLassoRect != null) "Inpaint lasso" else "Lasso object", fontSize = 9.sp, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Object Lasso: Enable ADV_AI tab and swipe/drag over any object in the reviewer block above. Once green lasso is formed, click 'Inpaint lasso' to auto block and fill.",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
