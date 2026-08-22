package com.railguard.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.railguard.mobile.perception.*
import com.railguard.mobile.ui.theme.RailGuardTheme
import java.util.concurrent.Executors

// =====================================================================
// RAILGUARD INSPECTION STATES
// =====================================================================

enum class InspectionState {
    READY,
    MONITORING,
    ANOMALY,
    ACKNOWLEDGED
}

// =====================================================================
// MAIN ACTIVITY
// =====================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RailGuardTheme {
                RailGuardScreen()
            }
        }
    }
}

// =====================================================================
// MAIN SCREEN
// =====================================================================

@Composable
fun RailGuardScreen() {
    val context = LocalContext.current

    var inspectionState by remember { mutableStateOf(InspectionState.READY) }
    var sectionNumber by remember { mutableStateOf(1) }

    // Real-time perception states
    var trackingResult by remember { mutableStateOf<RailTracker.TrackingResult?>(null) }
    var anomalyResult by remember { mutableStateOf<AnomalyResult?>(null) }

    var processedFrames by remember { mutableStateOf(0L) }
    var processingFPS by remember { mutableStateOf(0) }
    var latencyMs by remember { mutableStateOf(0L) }
    var railStatus by remember { mutableStateOf("READY") }

    // Camera permission
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Trigger UI Anomaly State automatically if perception detects anomaly during monitoring
    LaunchedEffect(anomalyResult?.state) {
        if (inspectionState == InspectionState.MONITORING && anomalyResult?.state == AnomalyState.ANOMALY) {
            inspectionState = InspectionState.ANOMALY
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {

            // =========================================================
            // HEADER
            // =========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RAILGUARD",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "AI v2",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                when (inspectionState) {
                                    InspectionState.MONITORING -> Color(0xFF22C55E)
                                    InspectionState.ANOMALY -> Color(0xFFDC2626)
                                    else -> Color.Gray
                                },
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = when (inspectionState) {
                            InspectionState.MONITORING -> "LIVE TRACKING"
                            InspectionState.ANOMALY -> "ANOMALY ALERT"
                            InspectionState.ACKNOWLEDGED -> "ACKNOWLEDGED"
                            InspectionState.READY -> "ONLINE"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (inspectionState) {
                            InspectionState.MONITORING -> Color(0xFF22C55E)
                            InspectionState.ANOMALY -> Color(0xFFDC2626)
                            else -> MaterialTheme.colorScheme.onBackground
                        }
                    )
                }
            }

            // =========================================================
            // LIVE 16:9 CAMERA VIEWPORT + NEURAL OVERLAY
            // =========================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .border(
                        width = if (inspectionState == InspectionState.ANOMALY) 2.dp else 1.dp,
                        color = if (inspectionState == InspectionState.ANOMALY) Color(0xFFDC2626) else Color.DarkGray,
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (cameraPermissionGranted) {
                        CameraPreview(
                            monitoring = inspectionState == InspectionState.MONITORING,
                            onPerceptionUpdated = { tracking, anomaly, latency ->
                                trackingResult = tracking
                                anomalyResult = anomaly
                                latencyMs = latency
                                processedFrames++
                                railStatus = if (tracking.trackingConfidence >= 0.5f) "TRACK LOCKED" else "SEARCHING..."
                            },
                            onFpsUpdated = { fps ->
                                processingFPS = fps
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Real-Time Neural Rail Overlay
                        RailOverlayCanvas(
                            trackingResult = trackingResult,
                            isAnomaly = inspectionState == InspectionState.ANOMALY,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top-Right Live HUD (FPS & Latency)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.70f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${processingFPS} FPS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF22C55E)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "•", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${latencyMs} ms",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        // Bottom-Start Legend
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.70f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E5FF), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LEFT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF3366), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RIGHT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CAMERA PERMISSION REQUIRED",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================
            // ANOMALY ALERT BANNER
            // =========================================================
            AnimatedVisibility(
                visible = inspectionState == InspectionState.ANOMALY,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val reasonText = when (anomalyResult?.reason) {
                    AnomalyReason.GAUGE_CHANGE -> "CRITICAL: Track gauge deviation detected outside safety tolerances."
                    AnomalyReason.CURVATURE_CHANGE -> "WARNING: Abrupt track lateral displacement / curvature irregularity."
                    AnomalyReason.INVALID_RAIL_GEOMETRY -> "ALERT: Rail alignment geometry broken or obstructed."
                    AnomalyReason.RAIL_DISCONTINUITY -> "CRITICAL: Track discontinuity or structural break suspected."
                    AnomalyReason.TEMPORAL_INSTABILITY -> "WARNING: Track vibration or rapid instability detected."
                    else -> "Track inspection alert triggered."
                }

                ResultCard(
                    title = "TRACK GEOMETRY ANOMALY",
                    message = reasonText,
                    color = Color(0xFFDC2626)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // =========================================================
            // TRACK TELEMETRY STATUS
            // =========================================================
            Text(
                text = "SECTION A-%02d".format(sectionNumber),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    val geometry = trackingResult?.geometry
                    val conf = ((trackingResult?.trackingConfidence ?: 0f) * 100).toInt()

                    StatusRow(
                        label = "Neural Tracking",
                        value = if (conf >= 50) "LOCKED ($conf%)" else "$conf% (ALIGN TRACKS)",
                        icon = "🛰️",
                        valueColor = if (conf >= 50) Color(0xFF16A34A) else Color(0xFFEAB308)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusRow(
                        label = "Track Status",
                        value = when (anomalyResult?.state) {
                            AnomalyState.ANOMALY -> "ANOMALY DETECTED"
                            AnomalyState.WARNING -> "WARNING"
                            AnomalyState.MONITORING -> "NORMAL"
                            else -> "MONITORING"
                        },
                        icon = if (inspectionState == InspectionState.ANOMALY) "⚠️" else "🛡️",
                        valueColor = when (anomalyResult?.state) {
                            AnomalyState.ANOMALY -> Color(0xFFDC2626)
                            AnomalyState.WARNING -> Color(0xFFEAB308)
                            else -> Color(0xFF16A34A)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusRow(
                        label = "Gauge Width (Near)",
                        value = if (geometry != null && geometry.valid) "%.2f (Nominal)".format(geometry.bottomGauge) else "--",
                        icon = "📏",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusRow(
                        label = "Curvature (L/R)",
                        value = if (geometry != null && geometry.valid) "%.3f / %.3f".format(geometry.leftCurvature, geometry.rightCurvature) else "--",
                        icon = "〰️",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusRow(
                        label = "Processed Frames",
                        value = processedFrames.toString(),
                        icon = "⚡",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================
            // ACTION BUTTONS
            // =========================================================
            when (inspectionState) {
                InspectionState.READY -> {
                    Button(
                        onClick = { inspectionState = InspectionState.MONITORING },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text("START REAL-TIME INSPECTION", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                InspectionState.MONITORING -> {
                    Button(
                        onClick = { inspectionState = InspectionState.READY },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308))
                    ) {
                        Text("PAUSE INSPECTION", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                InspectionState.ANOMALY -> {
                    Button(
                        onClick = { inspectionState = InspectionState.ACKNOWLEDGED },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("ACKNOWLEDGE ANOMALY", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                InspectionState.ACKNOWLEDGED -> {
                    Button(
                        onClick = {
                            sectionNumber++
                            inspectionState = InspectionState.MONITORING
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("RESUME NEXT SECTION (A-%02d)".format(sectionNumber + 1), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =====================================================================
// REAL-TIME NEURAL RAIL OVERLAY CANVAS
// =====================================================================

@Composable
fun RailOverlayCanvas(
    trackingResult: RailTracker.TrackingResult?,
    isAnomaly: Boolean,
    modifier: Modifier = Modifier
) {
    val leftCurve = trackingResult?.left
    val rightCurve = trackingResult?.right

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val leftColor = if (isAnomaly) Color(0xFFFFB703) else Color(0xFF00E5FF)
        val rightColor = if (isAnomaly) Color(0xFFFF0055) else Color(0xFFFF3366)
        val centerColor = if (isAnomaly) Color(0xFFEF4444) else Color(0xFF10B981)

        // Draw Left Rail Curve
        if (leftCurve != null && leftCurve.points.size >= 2) {
            val path = Path()
            val first = leftCurve.points.first()
            path.moveTo(first.x * width, first.y * height)

            for (i in 1 until leftCurve.points.size) {
                val pt = leftCurve.points[i]
                path.lineTo(pt.x * width, pt.y * height)
            }

            drawPath(
                path = path,
                color = leftColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Keypoint dots
            for (pt in leftCurve.points) {
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(pt.x * width, pt.y * height)
                )
                drawCircle(
                    color = leftColor,
                    radius = 2.dp.toPx(),
                    center = Offset(pt.x * width, pt.y * height)
                )
            }
        }

        // Draw Right Rail Curve
        if (rightCurve != null && rightCurve.points.size >= 2) {
            val path = Path()
            val first = rightCurve.points.first()
            path.moveTo(first.x * width, first.y * height)

            for (i in 1 until rightCurve.points.size) {
                val pt = rightCurve.points[i]
                path.lineTo(pt.x * width, pt.y * height)
            }

            drawPath(
                path = path,
                color = rightColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Keypoint dots
            for (pt in rightCurve.points) {
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(pt.x * width, pt.y * height)
                )
                drawCircle(
                    color = rightColor,
                    radius = 2.dp.toPx(),
                    center = Offset(pt.x * width, pt.y * height)
                )
            }
        }

        // Draw Centerline Trajectory
        if (leftCurve != null && rightCurve != null && leftCurve.points.size == rightCurve.points.size) {
            val count = leftCurve.points.size
            val centerPath = Path()
            val cFirstX = (leftCurve.points.first().x + rightCurve.points.first().x) / 2f
            val cFirstY = (leftCurve.points.first().y + rightCurve.points.first().y) / 2f
            centerPath.moveTo(cFirstX * width, cFirstY * height)

            for (i in 1 until count) {
                val cx = (leftCurve.points[i].x + rightCurve.points[i].x) / 2f
                val cy = (leftCurve.points[i].y + rightCurve.points[i].y) / 2f
                centerPath.lineTo(cx * width, cy * height)
            }

            drawPath(
                path = centerPath,
                color = centerColor.copy(alpha = 0.7f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )
            )
        }
    }
}

// =====================================================================
// CAMERA PREVIEW + REAL-TIME NEURAL ANALYSIS
// =====================================================================

@Composable
fun CameraPreview(
    monitoring: Boolean,
    onPerceptionUpdated: (RailTracker.TrackingResult, AnomalyResult, Long) -> Unit,
    onFpsUpdated: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val monitoringState = remember { mutableStateOf(monitoring) }

    LaunchedEffect(monitoring) {
        monitoringState.value = monitoring
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraExecutor = Executors.newSingleThreadExecutor()

            // Initialize RailDetector, Tracker & AnomalyEngine
            val railDetector = RailDetector(ctx)
            val railTracker = RailTracker(smoothingFactor = 0.55f)
            val anomalyEngine = AnomalyEngine()

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Lock to 16:9 aspect ratio matching model training
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                    .apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                var frameCounter = 0
                var lastFpsTime = SystemClock.elapsedRealtime()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        if (monitoringState.value) {
                            val t0 = SystemClock.elapsedRealtime()

                            // 1. Run MobileNetV3 Neural Rail Detection
                            val detection = railDetector.detect(imageProxy)

                            // 2. Run Temporal Smoothing & Tracking
                            val trackingResult = railTracker.update(detection?.first, detection?.second)

                            // 3. Run Geometric Anomaly Assessment
                            val anomalyResult = anomalyEngine.update(trackingResult)

                            val latency = SystemClock.elapsedRealtime() - t0

                            // 4. Update UI State on Main Thread
                            ContextCompat.getMainExecutor(ctx).execute {
                                onPerceptionUpdated(trackingResult, anomalyResult, latency)
                            }

                            // FPS calculation
                            frameCounter++
                            val currentTime = SystemClock.elapsedRealtime()
                            if (currentTime - lastFpsTime >= 1000) {
                                val fps = frameCounter
                                ContextCompat.getMainExecutor(ctx).execute {
                                    onFpsUpdated(fps)
                                }
                                frameCounter = 0
                                lastFpsTime = currentTime
                            }
                        } else {
                            railTracker.reset()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        context as androidx.lifecycle.LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

// =====================================================================
// RESULT CARD
// =====================================================================

@Composable
fun ResultCard(
    title: String,
    message: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// =====================================================================
// STATUS ROW
// =====================================================================

@Composable
fun StatusRow(
    label: String,
    value: String,
    icon: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.size(10.dp))
            Text(text = label, fontSize = 14.sp)
        }
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = valueColor
        )
    }
}
