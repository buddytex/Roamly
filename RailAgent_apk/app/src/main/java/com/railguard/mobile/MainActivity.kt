package com.railguard.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.content.ContextCompat

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import com.railguard.mobile.perception.*
import com.railguard.mobile.network.*
import com.railguard.mobile.telemetry.TelemetryLogger
import com.railguard.mobile.profiling.PipelineProfiler
import com.railguard.mobile.ui.ImuVisualization
import com.railguard.mobile.ui.LidarMapScreen
import com.railguard.mobile.ui.theme.RailGuardTheme

import java.util.concurrent.Executors


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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailGuardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isMonitoring by remember { mutableStateOf(false) }
    var latestAnomaly by remember { mutableStateOf<AnomalyResult?>(null) }
    var sectionNumber by remember { mutableIntStateOf(1) }

    val sheetState = rememberModalBottomSheetState()
    var showDiagnostics by remember { mutableStateOf(false) }
    var networkTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingNetwork by remember { mutableStateOf(false) }

    var cameraPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var locationPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> cameraPermissionGranted = granted }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
        if (!locationPermissionGranted) locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    var processedFrames by remember { mutableLongStateOf(0L) }
    var processingFPS by remember { mutableIntStateOf(0) }
    var railStatus by remember { mutableStateOf("INITIALIZING") }

    val lidarDriver = remember { YdLidarX2Driver(context) }
    val lidarScan by lidarDriver.scanData.collectAsStateWithLifecycle()
    val lidarStatus by lidarDriver.status.collectAsStateWithLifecycle()

    val imuManager = remember { ImuManager(context) }
    val orientation by imuManager.orientation.collectAsStateWithLifecycle()

    val locationManager = remember { LocationManager(context) }
    val gpsData by locationManager.gpsData.collectAsStateWithLifecycle()

    val networkManager = remember { NetworkManager(context) }
    val isServerOnline by networkManager.isServerOnline.collectAsStateWithLifecycle()

    val telemetryLogger = remember { TelemetryLogger(context) }
    var logCount by remember { mutableIntStateOf(telemetryLogger.getLogFiles().size) }

    var showLidarMap by remember { mutableStateOf(false) }

    fun toggleLidar() {
        if (!showLidarMap) {
            val connected = lidarDriver.connect()
            if (connected) {
                showLidarMap = true
            }
        } else {
            showLidarMap = false
            lidarDriver.disconnect()
        }
    }

    // Auto-show map when connected if we were waiting for it
    LaunchedEffect(lidarStatus) {
        if (lidarStatus == "CONNECTED") {
            showLidarMap = true
        }
    }

    val profiler = remember { PipelineProfiler() }
    var executionProvider by remember { mutableStateOf(RailDetector.ExecutionProvider.CPU) }
    val detector = remember { RailDetector(context) }
    
    // SENSOR LIFECYCLE
    LaunchedEffect(Unit) {
        imuManager.start()
        if (locationPermissionGranted) locationManager.startUpdates()
    }

    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) locationManager.startUpdates()
    }

    DisposableEffect(Unit) {
        onDispose {
            lidarDriver.disconnect()
            imuManager.stop()
            locationManager.stopUpdates()
            detector.close()
        }
    }

    LaunchedEffect(executionProvider) {
        detector.setExecutionProvider(context, executionProvider)
        detector.updateProfilerInfo(profiler)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            // HEADER
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "RAILGUARD", 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { if (showLidarMap) toggleLidar() }
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDiagnostics = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Diagnostics", tint = if (isServerOnline) Color(0xFF16A34A) else Color.Gray)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // LIDAR Toggle Button
                    Box(
                        modifier = Modifier
                            .background(
                                if (showLidarMap) Color(0xFF2563EB) else Color.DarkGray,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { toggleLidar() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "LIDAR",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isMonitoring) Color(0xFF22C55E) else Color.Gray, RoundedCornerShape(50))
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (isMonitoring) "LIVE" else "ONLINE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (!showLidarMap) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            ) {
                if (showLidarMap) {
                    // LIDAR VIEW - FULL SCREEN MAPPING
                    LidarMapScreen(
                        scan = lidarScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = "STATUS: $lidarStatus | Points: ${lidarScan.points.size} (Buffered)",
                        color = Color.Yellow,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    // MAIN INSPECTION UI
                    Card(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), 
                        shape = RoundedCornerShape(16.dp), 
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        if (cameraPermissionGranted) {
                            CameraPreview(
                                monitoring = isMonitoring,
                                processedFramesCount = processedFrames,
                                onFrameProcessed = { processedFrames++ },
                                onFpsUpdated = { fps -> processingFPS = fps },
                                onRailStatusUpdated = { status -> railStatus = status },
                                onAnomalyUpdated = { result -> latestAnomaly = result },
                                networkManager = networkManager,
                                profiler = profiler,
                                detector = detector,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Real-time AI Status Card
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (isMonitoring) Color(0xFF16A34A).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🧠", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Neural Engine", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(if (isMonitoring) "Processing at $processingFPS FPS" else "Inference Standby", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Text(text = if (isMonitoring) "ACTIVE" else "STANDBY", fontWeight = FontWeight.Black, color = if (isMonitoring) Color(0xFF16A34A) else Color.Gray)
                        }
                    }

                    if (isMonitoring) {
                        Spacer(modifier = Modifier.height(14.dp))
                        when (latestAnomaly?.state) {
                            AnomalyState.ANOMALY -> {
                                ResultCard(title = "⚠ ANOMALY DETECTED", message = "Reason: ${latestAnomaly?.reason?.name}", color = Color(0xFFDC2626))
                            }
                            AnomalyState.WARNING -> {
                                ResultCard(title = "⚠ GEOMETRY WARNING", message = "Slight tracking variation detected.", color = Color(0xFFF59E0B))
                            }
                            else -> {
                                ResultCard(title = "✓ TRACK NORMAL", message = "AI model actively detecting track corridor.", color = Color(0xFF16A34A))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // GPS & Speed Card
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Speed", fontSize = 12.sp, color = Color.Gray)
                                Text("%.1f km/h".format(gpsData.speed * 3.6f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Altitude", fontSize = 12.sp, color = Color.Gray)
                                Text("%.1f m".format(gpsData.altitude), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Orientation Card
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Geometry Orientation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(text = "Pitch: %.1f° | Roll: %.1f°".format(orientation.pitch, orientation.roll), fontSize = 12.sp, color = Color.Gray)
                            }
                            ImuVisualization(orientation = orientation, modifier = Modifier.size(60.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (isMonitoring) {
                        Button(onClick = { isMonitoring = false }, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STOP MONITORING", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(onClick = { isMonitoring = true }, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("START MONITORING", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (showDiagnostics) {
            ModalBottomSheet(onDismissRequest = { showDiagnostics = false }, containerColor = Color(0xFF1C1C1E)) {
                DiagnosticsPanel(
                    isServerOnline = isServerOnline,
                    lidarStatus = lidarStatus,
                    railStatus = railStatus,
                    processingFPS = processingFPS,
                    processedFrames = processedFrames,
                    gpsData = gpsData,
                    logCount = logCount,
                    executionProvider = executionProvider,
                    onProviderChange = { executionProvider = it },
                    profiler = profiler,
                    networkManager = networkManager,
                    networkTestResult = networkTestResult,
                    isTestingNetwork = isTestingNetwork,
                    onTestNetwork = {
                        scope.launch {
                            isTestingNetwork = true
                            networkTestResult = networkManager.testConnection()
                            isTestingNetwork = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CameraPreview(
    monitoring: Boolean,
    processedFramesCount: Long,
    onFrameProcessed: () -> Unit,
    onFpsUpdated: (Int) -> Unit,
    onRailStatusUpdated: (String) -> Unit,
    onAnomalyUpdated: (AnomalyResult) -> Unit = {},
    networkManager: NetworkManager? = null,
    profiler: PipelineProfiler? = null,
    detector: RailDetector,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val monitoringState = remember { mutableStateOf(monitoring) }
    var primaryTrack by remember { mutableStateOf<Pair<RailCurve, RailCurve>?>(null) }
    var secondaries by remember { mutableStateOf<List<Pair<RailCurve, RailCurve>>>(emptyList()) }
    var lastAnomalyState by remember { mutableStateOf<AnomalyResult?>(null) }
    val tracker = remember { RailTracker() }
    val anomalyEngine = remember { AnomalyEngine() }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    LaunchedEffect(monitoring) { 
        monitoringState.value = monitoring
        if (!monitoring) { primaryTrack = null; secondaries = emptyList(); lastAnomalyState = null }
    }

    // Ensure camera is unbound when this composable is removed (e.g. switching to LiDAR)
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {}
        }
    }

    Box(modifier = modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val cameraExecutor = Executors.newSingleThreadExecutor()
            
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    
                    // CRITICAL: Unbind everything before re-binding to avoid "Too many use cases" crash
                    cameraProvider.unbindAll()

                    val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(640, 480))
                        .build()
                    
                    var frameCounter = 0
                    var lastFpsTime = SystemClock.elapsedRealtime()

                    imageAnalysis.setAnalyzer(cameraExecutor) { image ->
                        if (monitoringState.value) {
                            val t0 = android.os.SystemClock.elapsedRealtimeNanos()
                            profiler?.startFrame(processedFramesCount, t0)
                            
                            val result = detector.detect(image, processedFramesCount, profiler)
                            val tracking = tracker.update(result.primaryTrack?.first, result.primaryTrack?.second)
                            
                            primaryTrack = if (tracking.left != null && tracking.right != null) tracking.left!! to tracking.right!! else null
                            secondaries = result.secondaryTracks
                            val anomaly = anomalyEngine.update(tracking)
                            lastAnomalyState = anomaly
                            onAnomalyUpdated(anomaly)
                            onFrameProcessed()
                            
                            frameCounter++
                            if (SystemClock.elapsedRealtime() - lastFpsTime >= 1000) { onFpsUpdated(frameCounter); frameCounter = 0; lastFpsTime = SystemClock.elapsedRealtime() }
                            profiler?.endFrame(processedFramesCount, context)
                        }
                        image.close()
                    }
                    
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        })

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height

            // 1. Draw Secondary Tracks (Amber)
            secondaries.forEach { (left, right) ->
                if (left.points.size >= 2 && right.points.size >= 2) {
                    val p = Path().apply {
                        moveTo(left.points[0].x * w, left.points[0].y * h)
                        left.points.forEach { lineTo(it.x * w, it.y * h) }
                        right.points.reversed().forEach { lineTo(it.x * w, it.y * h) }
                        close()
                    }
                    drawPath(p, Color(0x22F59E0B), style = Fill)
                    
                    val lp = Path().apply { moveTo(left.points[0].x * w, left.points[0].y * h); left.points.forEach { lineTo(it.x * w, it.y * h) } }
                    drawPath(lp, Color(0x88F59E0B), style = Stroke(width = 3f))
                    val rp = Path().apply { moveTo(right.points[0].x * w, right.points[0].y * h); right.points.forEach { lineTo(it.x * w, it.y * h) } }
                    drawPath(rp, Color(0x88F59E0B), style = Stroke(width = 3f))
                }
            }

            // 2. Draw Primary Ego Track
            primaryTrack?.let { (left, right) ->
                if (left.points.size >= 2 && right.points.size >= 2) {
                    val p = Path().apply {
                        moveTo(left.points[0].x * w, left.points[0].y * h)
                        left.points.forEach { lineTo(it.x * w, it.y * h) }
                        right.points.reversed().forEach { lineTo(it.x * w, it.y * h) }
                        close()
                    }
                    drawPath(p, Color(0x4428C850), style = Fill)
                    val lp = Path().apply { moveTo(left.points[0].x * w, left.points[0].y * h); left.points.forEach { lineTo(it.x * w, it.y * h) } }
                    drawPath(lp, Color(0xFF00E5FF), style = Stroke(width = 6f))
                    val rp = Path().apply { moveTo(right.points[0].x * w, right.points[0].y * h); right.points.forEach { lineTo(it.x * w, it.y * h) } }
                    drawPath(rp, Color(0xFFE040FB), style = Stroke(width = 6f))
                }
            }
        }

        // Legends & Anomaly Banner
        Column(modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
            if (primaryTrack != null) {
                Surface(color = Color(0xCC141418), shape = RoundedCornerShape(8.dp)) {
                    Text(text = "● EGO LOCKED", color = Color(0xFF00FF66), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            } else if (monitoringState.value) {
                Surface(color = Color(0xCC141418), shape = RoundedCornerShape(8.dp)) {
                    Text(text = "● SEARCHING", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            lastAnomalyState?.let { anomaly ->
                if (anomaly.state == AnomalyState.ANOMALY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = Color(0xE6DC2626), shape = RoundedCornerShape(8.dp)) {
                        Text(text = "⚠ ANOMALY: ${anomaly.reason.name}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsPanel(
    isServerOnline: Boolean,
    lidarStatus: String,
    railStatus: String,
    processingFPS: Int,
    processedFrames: Long,
    gpsData: GpsData,
    logCount: Int,
    executionProvider: RailDetector.ExecutionProvider,
    onProviderChange: (RailDetector.ExecutionProvider) -> Unit,
    profiler: PipelineProfiler,
    networkManager: NetworkManager,
    networkTestResult: String?,
    isTestingNetwork: Boolean,
    onTestNetwork: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("System Diagnostics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        
        DiagnosticRow("Pi Server", if (isServerOnline) "REACHABLE" else "OFFLINE", if (isServerOnline) Color.Green else Color.Red)
        DiagnosticRow("AI Engine", railStatus, Color.Green)
        DiagnosticRow("Inference", "$processingFPS FPS", Color.Cyan)
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Network Configuration", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        
        DiagnosticRow("Phone IP", NetworkManager.getLocalIpAddress() ?: "Unknown", Color.White)
        DiagnosticRow("Target IP", networkManager.getBaseUrl().substringAfter("//").substringBefore(":"), Color.White)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        var ipInput by remember { mutableStateOf(networkManager.getBaseUrl().substringAfter("//").substringBefore(":")) }
        OutlinedTextField(
            value = ipInput,
            onValueChange = { 
                ipInput = it
                networkManager.setBaseUrl(it)
            },
            label = { Text("Edit Pi IP Address", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onTestNetwork,
            enabled = !isTestingNetwork,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isTestingNetwork) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Testing...")
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ping Pi Server")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // New button to verify if the browser can reach it
        OutlinedButton(
            onClick = { 
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(networkManager.getBaseUrl() + "docs"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Open Pi Docs in Browser")
        }
        
        networkTestResult?.let { result ->
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = if (result.contains("SUCCESS")) Color.Green else Color(0xFFF87171)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Benchmarking", fontSize = 14.sp, color = Color.Gray)
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onProviderChange(RailDetector.ExecutionProvider.CPU) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (executionProvider == RailDetector.ExecutionProvider.CPU) Color(0xFF2563EB) else Color.DarkGray), shape = RoundedCornerShape(8.dp)) { Text("CPU") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onProviderChange(RailDetector.ExecutionProvider.NNAPI) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (executionProvider == RailDetector.ExecutionProvider.NNAPI) Color(0xFF2563EB) else Color.DarkGray), shape = RoundedCornerShape(8.dp)) { Text("NNAPI") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Live Performance Profile", fontSize = 14.sp, color = Color.Gray)
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
            Text(text = profiler.lastGeneratedReport, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color.Green)
        }
    }
}

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
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message, fontSize = 13.sp)
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}


