package com.robinz.minitank.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.ui.components.SpeedCapControl
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurface
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.EmergencyRed
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary
import com.robinz.minitank.ui.theme.WarningAmber
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RoutePoint(val x: Float, val y: Float)

private val RouteStartGreen = Color(0xFF10B981)

private fun prepareRoute(points: List<RoutePoint>): List<RoutePoint> {
    if (points.size < 3) return points

    val smoothed = points.mapIndexed { index, point ->
        if (index == 0 || index == points.lastIndex) point
        else {
            val start = (index - 2).coerceAtLeast(0)
            val end = (index + 2).coerceAtMost(points.lastIndex)
            val window = points.subList(start, end + 1)
            RoutePoint(window.map { it.x }.average().toFloat(), window.map { it.y }.average().toFloat())
        }
    }

    // Drawing records points about every 16 px. Collapse hand jitter and short samples
    // into useful segments while retaining corners and curved sections.
    val simplified = mutableListOf(smoothed.first())
    var distanceSinceLast = 0f
    for (index in 1 until smoothed.lastIndex) {
        distanceSinceLast += hypot(smoothed[index].x - smoothed[index - 1].x, smoothed[index].y - smoothed[index - 1].y)
        val before = smoothed[index - 1]
        val current = smoothed[index]
        val after = smoothed[index + 1]
        val firstX = current.x - before.x
        val firstY = current.y - before.y
        val secondX = after.x - current.x
        val secondY = after.y - current.y
        val angle = atan2(firstX * secondY - firstY * secondX, firstX * secondX + firstY * secondY)
        if (distanceSinceLast >= 32f || abs(angle) >= 0.35f) {
            simplified += current
            distanceSinceLast = 0f
        }
    }
    simplified += smoothed.last()
    return simplified
}

private fun routeSteering(points: List<RoutePoint>, segmentIndex: Int): Float {
    if (points.size < 3) return 0f
    val from = if (segmentIndex < points.lastIndex) points[segmentIndex - 1] else points[segmentIndex - 2]
    val pivot = if (segmentIndex < points.lastIndex) points[segmentIndex] else points[segmentIndex - 1]
    val to = if (segmentIndex < points.lastIndex) points[segmentIndex + 1] else points[segmentIndex]
    val firstX = pivot.x - from.x
    val firstY = pivot.y - from.y
    val secondX = to.x - pivot.x
    val secondY = to.y - pivot.y
    val angle = atan2(firstX * secondY - firstY * secondX, firstX * secondX + firstY * secondY)
    return when {
        angle > 0.12f -> 1f
        angle < -0.12f -> -1f
        else -> 0f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    currentSpeedCap: Int,
    onSpeedCapChanged: (Int) -> Unit,
    onIncrementSpeed: () -> Unit,
    onDecrementSpeed: () -> Unit,
    onBack: () -> Unit,
    onRouteDriveInput: (Float, Float) -> Unit,
    onRouteStop: () -> Unit
) {
    val waypoints = remember { mutableStateListOf<RoutePoint>() }
    val scope = rememberCoroutineScope()
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var nextWaypoint by remember { mutableStateOf(1) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    fun stopPlayback(clearProgress: Boolean) {
        playbackJob?.cancel()
        playbackJob = null
        isPlaying = false
        isPaused = false
        onRouteStop()
        if (clearProgress) nextWaypoint = 1
    }
    fun prepareRouteEdit() {
        if (isPaused) {
            isPaused = false
            nextWaypoint = 1
            onRouteStop()
        }
    }
    fun startPlayback() {
        val route = prepareRoute(waypoints.toList())
        if (route.size < 2 || isPlaying) return
        if (nextWaypoint >= route.size) nextWaypoint = 1
        isPlaying = true
        isPaused = false
        playbackJob = scope.launch {
            while (nextWaypoint < route.size) {
                val from = route[nextWaypoint - 1]
                val to = route[nextWaypoint]
                val dx = to.x - from.x
                val dy = to.y - from.y
                // The grid is a drawing surface, not a measured map. Treat each 40 px as
                // roughly 320 ms of movement and cap segments to keep stops responsive.
                val durationMs = (hypot(dx, dy) * 8f).toLong().coerceIn(120L, 1_500L)
                // Follow the path with forward drive; use the path's change in heading
                // to steer. Screen-space up/down changes must not become reverse commands.
                onRouteDriveInput(1f, routeSteering(route, nextWaypoint))
                delay(durationMs)
                onRouteStop()
                nextWaypoint++
            }
            isPlaying = false
            isPaused = false
            playbackJob = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { playbackJob?.cancel(); onRouteStop() }
    }
    BackHandler { stopPlayback(clearProgress = true); onBack() }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val totalPathDistance = remember(waypoints.size) {
        if (waypoints.size < 2) 0f
        else {
            var dist = 0f
            for (i in 1 until waypoints.size) {
                dist += hypot(waypoints[i].x - waypoints[i - 1].x, waypoints[i].y - waypoints[i - 1].y)
            }
            dist
        }
    }

    if (isLandscape) {
        // Landscape Responsive Layout: Canvas on Left (max space) + Controls Sidebar on Right
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left / Main: Drawing Canvas Arena
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Compact Top Bar with Back, Title, Nodes Badge, Undo, Clear
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Autonomous Route",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Nodes & length stats badge
                        Column(horizontalAlignment = Alignment.End) {
                            OutlinedButton(
                                onClick = { stopPlayback(clearProgress = true) },
                                enabled = isPlaying || isPaused || nextWaypoint > 1,
                                modifier = Modifier.height(30.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(7.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Stop", fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, OutlineBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${waypoints.size} NODES | ${totalPathDistance.toInt()} px",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = CyanAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                prepareRouteEdit()
                                if (waypoints.isNotEmpty()) waypoints.removeAt(waypoints.lastIndex)
                            },
                            enabled = waypoints.isNotEmpty() && !isPlaying,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (waypoints.isNotEmpty()) CyanAccent else TextMuted,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { prepareRouteEdit(); waypoints.clear() },
                            enabled = waypoints.isNotEmpty() && !isPlaying,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear",
                                tint = if (waypoints.isNotEmpty()) EmergencyRed else TextMuted,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Spacious Drawing Canvas Arena (occupies remaining height)
                RouteDrawingCanvasBox(
                    waypoints = waypoints,
                    enabled = !isPlaying,
                    onEdit = { prepareRouteEdit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            // Right Sidebar: Speed Cap, Calibration Lock Notice & Playback Controls
            Column(
                modifier = Modifier
                    .widthIn(min = 230.dp, max = 270.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SPEED CAP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SpeedCapControl(
                        speedCapPercent = currentSpeedCap,
                        onSpeedCapChanged = onSpeedCapChanged,
                        onIncrement = onIncrementSpeed,
                        onDecrement = onDecrementSpeed,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Notice: Route protocol locked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarningAmber.copy(alpha = 0.12f))
                            .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Estimated timing from drawn distance; test at low speed in an open area.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextPrimary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        )
                    }
                }

                // Playback Controls
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            if (isPlaying) {
                                stopPlayback(clearProgress = false)
                                isPaused = true
                            } else {
                                startPlayback()
                            }
                        },
                        enabled = waypoints.size >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                        .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RouteStartGreen,
                            contentColor = Color.White,
                            disabledContainerColor = DarkSurfaceElevated,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause route" else if (isPaused) "Resume route" else "Start route",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                }
            }
        }
    } else {
        // Portrait Responsive Layout: Compact Top Bar, Center Canvas with minimum height, Bottom controls
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Autonomous Route Planner",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                prepareRouteEdit()
                                if (waypoints.isNotEmpty()) waypoints.removeAt(waypoints.lastIndex)
                            },
                            enabled = waypoints.isNotEmpty() && !isPlaying
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (waypoints.isNotEmpty()) CyanAccent else TextMuted
                            )
                        }
                        IconButton(
                            onClick = { prepareRouteEdit(); waypoints.clear() },
                            enabled = waypoints.isNotEmpty() && !isPlaying
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear",
                                tint = if (waypoints.isNotEmpty()) EmergencyRed else TextMuted
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
            },
            containerColor = DarkBackground
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Speed Cap & Route Metrics Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeedCapControl(
                        speedCapPercent = currentSpeedCap,
                        onSpeedCapChanged = onSpeedCapChanged,
                        onIncrement = onIncrementSpeed,
                        onDecrement = onDecrementSpeed,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        OutlinedButton(
                            onClick = { stopPlayback(clearProgress = true) },
                            enabled = isPlaying || isPaused || nextWaypoint > 1,
                            modifier = Modifier.height(34.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "NODES: ${waypoints.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = CyanAccent,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = "DIST: ${totalPathDistance.toInt()} px",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                // Drawing Canvas Arena (taking remaining portrait height comfortably)
                RouteDrawingCanvasBox(
                    waypoints = waypoints,
                    enabled = !isPlaying,
                    onEdit = { prepareRouteEdit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 240.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Calibration notice & Playback buttons
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarningAmber.copy(alpha = 0.12f))
                            .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Estimated route timing — drawn distance is not a measured scale. Start slowly in an open area.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (isPlaying) {
                                stopPlayback(clearProgress = false)
                                isPaused = true
                            } else {
                                startPlayback()
                            }
                        },
                        enabled = waypoints.size >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RouteStartGreen,
                            contentColor = Color.White,
                            disabledContainerColor = DarkSurfaceElevated,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause route" else if (isPaused) "Resume route" else "Start route",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteDrawingCanvasBox(
    waypoints: MutableList<RoutePoint>,
    enabled: Boolean = true,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.5.dp, OutlineBorder, RoundedCornerShape(16.dp))
            .pointerInput(enabled) {
                if (enabled) detectDragGestures(
                    onDragStart = { offset ->
                        onEdit()
                        waypoints.add(RoutePoint(offset.x, offset.y))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val last = waypoints.lastOrNull()
                        if (last == null || hypot(change.position.x - last.x, change.position.y - last.y) > 16f) {
                            waypoints.add(RoutePoint(change.position.x, change.position.y))
                        }
                    }
                )
            }
            .testTag("route_drawing_canvas"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Tactical Grid
            val step = 40.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(OutlineBorder.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(OutlineBorder.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }

            // Drawn Path
            if (waypoints.isNotEmpty()) {
                val path = Path()
                path.moveTo(waypoints.first().x, waypoints.first().y)
                for (i in 1 until waypoints.size) {
                    path.lineTo(waypoints[i].x, waypoints[i].y)
                }

                drawPath(
                    path = path,
                    color = CyanAccent,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Start point marker
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = 8.dp.toPx(),
                    center = Offset(waypoints.first().x, waypoints.first().y)
                )

                // End point marker
                drawCircle(
                    color = CyanAccent,
                    radius = 7.dp.toPx(),
                    center = Offset(waypoints.last().x, waypoints.last().y)
                )
            }
        }

        if (waypoints.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Draw Tank Route Path Here",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Drag finger or stylus across grid to plot trajectory",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
