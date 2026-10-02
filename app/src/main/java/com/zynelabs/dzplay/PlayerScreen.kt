package com.zynelabs.dzplay

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.zynelabs.dzplay.data.Cats
import com.zynelabs.dzplay.data.Channel
import com.zynelabs.dzplay.data.ChannelRepo
import com.zynelabs.dzplay.data.PlAccount
import com.zynelabs.dzplay.data.PlaylistCache
import com.zynelabs.dzplay.data.Store
import com.zynelabs.dzplay.data.StreamRecorder
import com.zynelabs.dzplay.data.XtreamClient
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.zynelabs.dzplay.data.EpgCache

private val CardBg = Color(0xFF141C2E)
private val Purple = Color(0xFF6D5BD0)
// Accent, Ink, Dim, Card, CardSel, Bg come from MainActivity.kt (internal shared).

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Full player v2 — reference-style UI:
 * top bar, side brightness/volume sliders, center -10/+10,
 * bottom labeled buttons (Aspect/Speed/Subtitles), right settings panel,
 * left channel drawer, back-press popup, recording, catch-up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    channels: List<Channel>,
    initial: Channel,
    accountId: String,
    mini: Boolean = false,
    onChannelSelected: (Channel) -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit = {},
    onExpand: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenMultiview: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()
    val audioMgr = remember {
        ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var current by remember { mutableStateOf(initial) }
    var showControls by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showChannels by remember { mutableStateOf(false) }
    var showCatchup by remember { mutableStateOf(false) }
    var drawerCat by remember { mutableStateOf<String?>(null) }
    var drawerCountry by remember { mutableStateOf<String?>(null) }
    // Drawer can browse other providers without disturbing playback.
    var drawerChannels by remember { mutableStateOf(channels) }
    var drawerAccountId by remember { mutableStateOf(accountId) }
    var drawerAccounts by remember { mutableStateOf<List<PlAccount>>(emptyList()) }
    var drawerLoading by remember { mutableStateOf(false) }
    var showProviderPick by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }

    // player state
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var aspectMode by remember { mutableIntStateOf(0) }
    var videoScale by remember { mutableFloatStateOf(1f) }
    var showScaleDialog by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var showRecTimer by remember { mutableStateOf(false) }
    var recRemainSec by remember { mutableIntStateOf(0) } // 0 = no timer
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableIntStateOf(audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var catchupProgs by remember { mutableStateOf<List<XtreamClient.Program>?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf("") }
    var muted by remember { mutableStateOf(false) }
    var swipeSide by remember { mutableStateOf<Int?>(null) } // 0=brightness 1=volume
    var swipeSeq by remember { mutableIntStateOf(0) }
    var epgTick by remember { mutableIntStateOf(0) }
    // edge strip auto-hides shortly after the swipe ends
    LaunchedEffect(swipeSeq) {
        if (swipeSeq > 0) {
            delay(2500)
            swipeSide = null
        }
    }
    // manual surface layout (ZyneLabs-style): aspect + scale by sizing
    // the SurfaceView itself — no GPU-layer transforms (they glitch
    // SurfaceViews on some devices).
    var videoW by remember { mutableIntStateOf(0) }
    var videoH by remember { mutableIntStateOf(0) }
    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }

    val maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val isPortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val topIconSize: Dp = if (isPortrait) 34.dp else 40.dp
    val topIconGap: Dp = if (isPortrait) 4.dp else 8.dp

    // init brightness from window
    LaunchedEffect(Unit) {
        val lp = activity?.window?.attributes
        val b = lp?.screenBrightness
        if (b != null && b >= 0) brightness = b
        isFavorite = try {
            Store(ctx).isFav(accountId, current.name)
        } catch (_: Exception) { false }
        videoScale = try { Store(ctx).videoScale() } catch (_: Exception) { 1f }
    }
    // track for Continue Watching on the home dashboard
    LaunchedEffect(current) {
        try { Store(ctx).addRecent(accountId, current.url) } catch (_: Exception) {}
    }

    val player = remember(current) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(current.url))
            setVolume(if (muted) 0f else 1f)
            prepare()
            play()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
            }
            override fun onTracksChanged(t: Tracks) {
                tracks = t
                quality = try {
                    val h = t.groups
                        .filter { it.type == C.TRACK_TYPE_VIDEO }
                        .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
                        .maxOrNull() ?: 0
                    when {
                        h >= 2160 -> "4K"
                        h >= 1080 -> "Full HD"
                        h >= 720 -> "HD"
                        h > 0 -> "SD"
                        else -> ""
                    }
                } catch (_: Exception) { "" }
            }
            override fun onVideoSizeChanged(vs: VideoSize) {
                videoW = vs.width
                videoH = vs.height
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = e.message ?: "Playback error"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            if (isRecording) stopRecording()
            recRemainSec = 0
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            if (d > 0) duration = d
            delay(500)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !showSettings && !showChannels) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            player.pause()
            sleepMin = 0
        }
    }

    // recording timer: counts down each second, auto-stops at zero
    LaunchedEffect(isRecording) {
        while (isRecording && recRemainSec > 0) {
            delay(1000)
            recRemainSec--
            if (recRemainSec <= 0) {
                stopRecording()
                isRecording = false
            }
        }
    }

    val isLive = duration <= 0L

    fun setBrightness(v: Float) {
        brightness = v.coerceIn(0.05f, 1f)
        activity?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = brightness
            w.attributes = lp
        }
    }

    fun setVolume(v: Int) {
        volume = v.coerceIn(0, maxVolume)
        audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    fun cycleAspect() {
        aspectMode = (aspectMode + 1) % 3
        val names = listOf("Fit", "Fill", "Zoom")
        toast(ctx, "Aspect: " + names[aspectMode] + "  •  Scale: " +
            Math.round(videoScale * 100) + "% (hold ASPECT to change)")
    }

    fun setVideoScale(v: Float) {
        videoScale = v
        try { Store(ctx).setVideoScale(v) } catch (_: Exception) {}
        toast(ctx, "Scale: " + Math.round(v * 100) + "%")
    }

    fun cycleSpeed() {
        val i = SPEEDS.indexOf(speed)
        val n = SPEEDS[(i + 1) % SPEEDS.size]
        speed = n
        player.setPlaybackSpeed(n)
    }

    fun enterPip(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9)).build()
                ) == true
            } catch (_: Exception) { false }
        } else false
    }

    fun switchChannel(ch: Channel) {
        showChannels = false
        current = ch
        onChannelSelected(ch)
        // reset per-channel state
        position = 0; duration = 0; error = null
        speed = 1f; isBuffering = true; quality = ""
        epgTick++ // re-read schedule for the new channel
        scope.launch {
            isFavorite = try { Store(ctx).isFav(drawerAccountId, ch.name) }
            catch (_: Exception) { false }
        }
    }

    /** Load another provider's channels into the drawer (playback untouched). */
    fun loadDrawerProvider(acc: PlAccount) {
        drawerAccountId = acc.id
        drawerCat = null
        drawerCountry = null
        drawerLoading = true
        scope.launch(Dispatchers.IO) {
            var live: List<Channel> = emptyList()
            var vod: List<Channel> = emptyList()
            var series: List<Channel> = emptyList()
            try {
                PlaylistCache.load(ctx.filesDir, acc.id)?.let {
                    live = it.live; vod = it.vod; series = it.series
                }
            } catch (_: Exception) {}
            if (live.isEmpty() && vod.isEmpty() && series.isEmpty()) {
                val done = CompletableDeferred<Unit>()
                try {
                    ChannelRepo.load(ctx, acc, object : ChannelRepo.Callback {
                        override fun onResult(
                            l: List<Channel>, v: List<Channel>,
                            s: List<Channel>, err: String?
                        ) {
                            if (err == null) {
                                live = l; vod = v; series = s
                                try {
                                    PlaylistCache.save(ctx.filesDir, acc.id, l, v, s)
                                } catch (_: Exception) {}
                            }
                            done.complete(Unit)
                        }
                    })
                } catch (_: Exception) { done.complete(Unit) }
                try { done.await() } catch (_: Exception) {}
            }
            try {
                if (live.isNotEmpty()) Cats.tagAll(live)
                if (vod.isNotEmpty()) Cats.tagAll(vod)
                if (series.isNotEmpty()) Cats.tagAll(series)
            } catch (_: Exception) {}
            val all = live + vod + series
            withContext(Dispatchers.Main) {
                drawerChannels = all
                drawerLoading = false
            }
        }
    }

    // account list for the drawer's provider switcher (enabled only)
    LaunchedEffect(showChannels) {
        if (showChannels && drawerAccounts.isEmpty()) {
            drawerAccounts = try { Store(ctx).enabledAccounts() }
            catch (_: Exception) { emptyList() }
        }
    }

    fun toggleMute() {
        muted = !muted
        try { player.setVolume(if (muted) 0f else 1f) } catch (_: Exception) {}
    }

    // warm the shared EPG cache so the info box can show Now/Next
    LaunchedEffect(accountId) {
        if (accountId.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val acc = Store(ctx).account(accountId)
                    if (acc != null && acc.isXtream()) {
                        EpgCache.ensure(ctx, acc, channels) { epgTick++ }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun prevChannel() {
        if (channels.isEmpty()) return
        val i = channels.indexOfFirst { it.url == current.url && it.name == current.name }
        switchChannel(channels[(i - 1 + channels.size) % channels.size])
    }

    fun nextChannel() {
        if (channels.isEmpty()) return
        val i = channels.indexOfFirst { it.url == current.url && it.name == current.name }
        switchChannel(channels[(i + 1) % channels.size])
    }

    fun toggleFavorite() {
        scope.launch(Dispatchers.IO) {
            try {
                val s = Store(ctx)
                s.toggleFav(accountId, current.name)
                val fav = s.isFav(accountId, current.name)
                withContext(Dispatchers.Main) { isFavorite = fav }
            } catch (_: Exception) {}
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            stopRecording()
            isRecording = false
            recRemainSec = 0
        } else {
            showRecTimer = true
        }
    }

    fun startRecordingWithTimer(min: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                startRecording(ctx, current)
                withContext(Dispatchers.Main) {
                    isRecording = true
                    recRemainSec = if (min > 0) min * 60 else 0
                }
            } catch (_: Exception) {}
        }
    }

    fun loadCatchup() {
        showCatchup = true
        catchupProgs = null
        scope.launch(Dispatchers.IO) {
            val progs = try { XtreamClient.catchupEpg(current) }
            catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) { catchupProgs = progs }
        }
    }

    BackHandler {
        // mini: back closes the pop-up. fullscreen: back minimizes to in-app pop-up.
        if (mini) onClose() else onMinimize()
    }

    // tap toggles controls; vertical swipe on left = brightness,
    // on right = volume (ZyneLabs-style). An edge strip with the live
    // value appears on the swiped side while swiping.
    val brightnessRef = rememberUpdatedState(brightness)
    val volumeRef = rememberUpdatedState(volume)
    val gestureModifier = if (!mini) Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var swiping = false
            var brightSide = false
            var b0 = 0f
            var v0 = 0
            var lifted = false
            while (!lifted) {
                val event = awaitPointerEvent()
                if (event.changes.all { !it.pressed }) { lifted = true; break }
                val cur = event.changes[0]
                val dy = cur.position.y - down.position.y
                val dx = cur.position.x - down.position.x
                if (!swiping && abs(dy) > 16.dp.toPx() && abs(dy) > abs(dx) * 1.3f) {
                    swiping = true
                    brightSide = down.position.x < size.width / 2f
                    swipeSide = if (brightSide) 0 else 1
                    if (brightSide) b0 = brightnessRef.value else v0 = volumeRef.value
                }
                if (swiping) {
                    val frac = (down.position.y - cur.position.y) / (size.height * 0.8f)
                    if (brightSide) {
                        setBrightness((b0 + frac).coerceIn(0.01f, 1f))
                    } else {
                        setVolume((v0 + (frac * maxVolume).roundToInt()).coerceIn(0, maxVolume))
                    }
                    cur.consume()
                }
            }
            if (swiping) swipeSeq++ else showControls = !showControls
        }
    } else Modifier

    Box(Modifier.fillMaxSize().background(if (mini) Color.Transparent else Color.Black)) {
        // video — manual ZyneLabs-style surface layout: the SurfaceView is
        // sized for aspect mode + video scale inside a clipping box, so no
        // GPU-layer transforms are needed (they glitch SurfaceViews on some
        // devices). The same call site keeps the player alive across
        // mini <-> fullscreen toggles.
        Box(
            Modifier.fillMaxSize()
                .clipToBounds()
                .then(gestureModifier)
                .onSizeChanged { boxW = it.width; boxH = it.height }
        ) {
            val density = LocalDensity.current
            val (sw, sh) = remember(videoW, videoH, boxW, boxH, aspectMode, videoScale) {
                if (videoW <= 0 || videoH <= 0 || boxW <= 0 || boxH <= 0) {
                    boxW.coerceAtLeast(1) to boxH.coerceAtLeast(1)
                } else {
                    val vr = videoW / videoH.toFloat()
                    val cr = boxW / boxH.toFloat()
                    val wh = when (aspectMode) {
                        1 -> boxW to boxH // Fill: stretch to container
                        0 -> if (vr > cr) boxW to (boxW / vr).toInt()
                             else (boxH * vr).toInt() to boxH // Fit: letterbox
                        else -> if (vr > cr) (boxH * vr).toInt() to boxH
                             else boxW to (boxW / vr).toInt() // Zoom: crop overflow
                    }
                    (wh.first * videoScale).toInt().coerceAtLeast(1) to
                        (wh.second * videoScale).toInt().coerceAtLeast(1)
                }
            }
            AndroidView(
                factory = { c -> SurfaceView(c).also { player.setVideoSurfaceView(it) } },
                modifier = Modifier.align(Alignment.Center)
                    .size(with(density) { sw.toDp() }, with(density) { sh.toDp() })
            )
        }

        if (mini) {
            // ===== floating mini pop-up overlay =====
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            ) {
                Text(
                    current.name, color = Color.White, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp, end = 44.dp)
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .size(30.dp).clip(CircleShape).background(Color(0xAA000000))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Text("×", color = Color.White, fontSize = 16.sp) }
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .size(30.dp).clip(CircleShape).background(Color(0xAA000000))
                        .clickable { onExpand() },
                    contentAlignment = Alignment.Center
                ) { Text("⛶", color = Color.White, fontSize = 14.sp) }
            }
        } else {

            if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Playback failed", color = Color.White, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(error ?: "", color = Dim, fontSize = 14.sp)
                    }
                }
            }
            if (isBuffering && error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }

            // recording indicator — red dot + "rec" only.
            // portrait: top (transport cluster sits at the bottom there)
            if (isRecording) {
                Row(
                    modifier = if (isPortrait)
                        Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 84.dp)
                    else
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 160.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
                    Spacer(Modifier.width(6.dp))
                    Text("rec" + if (recRemainSec > 0) " " + fmtTime(recRemainSec * 1000L) else "",
                        color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium)
                }
            }

            if (showControls && !locked) {
                // ===== top bar =====
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xAA000000))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Purple)
                            .clickable { onMinimize() },
                        contentAlignment = Alignment.Center
                    ) { Text("←", color = Color.White, fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.name, color = Color.White, fontSize = 17.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLive) {
                                val blink = rememberInfiniteTransition(label = "liveDot")
                                val dotAlpha by blink.animateFloat(
                                    initialValue = 1f, targetValue = 0.2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(650, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "blink"
                                )
                                Text(
                                    "●", color = Color(0xFFFF3B30).copy(alpha = dotAlpha),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    " LIVE", color = Accent, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    // favorite (hidden in "All providers" mode — no single account)
                    if (accountId.isNotEmpty()) {
                        TopIcon(if (isFavorite) "★" else "☆", CardSel, size = topIconSize) { toggleFavorite() }
                        Spacer(Modifier.width(topIconGap))
                    }
                    // lock
                    TopIcon("", CardSel, size = topIconSize, icon = { LockIcon() }) { locked = true }
                    Spacer(Modifier.width(topIconGap))
                    // picture-in-picture pill
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(CardSel)
                            .clickable { enterPip() }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "pip", color = Color.White,
                            fontSize = if (isPortrait) 9.sp else 10.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(topIconGap))
                    // settings
                    TopIcon("⚙", CardSel, size = topIconSize) { showSettings = true }
                }

                // ===== center transport: -10s | prev | play/pause | next | rec | +10s =====
                // portrait: sit just above the bottom bar so the 6 buttons never
                // collide with the side sliders
                val transSize: Dp = if (isPortrait) 46.dp else 60.dp
                Row(
                    modifier = if (isPortrait)
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp)
                    else
                        Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 8.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeekBtn(icon = { PrevIcon() }, size = transSize) { prevChannel() }
                    SeekBtn(icon = { if (isPlaying) PauseIcon() else PlayIcon() }, size = transSize) {
                        if (isPlaying) player.pause() else player.play()
                    }
                    SeekBtn(icon = { NextIcon() }, size = transSize) { nextChannel() }
                }

                // ===== bottom info box (fully transparent, round action buttons) =====
                // soft shadow keeps the floating text readable over video
                val videoTextStyle = TextStyle(
                    shadow = Shadow(
                        color = Color(0xCC000000),
                        offset = Offset(1f, 2f), blurRadius = 6f
                    )
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // channel + quality
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val n = channels.indexOf(current).takeIf { it >= 0 }?.plus(1)
                        if (n != null) {
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF22314F))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "Channel $n", color = Dim, fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            current.name, color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = videoTextStyle,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (quality.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1D2A44))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    quality, color = Color.White, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    // schedule (Now/Next via shared EPG cache)
                    @Suppress("UNUSED_VARIABLE")
                    val epgRead = epgTick
                    run {
                        val now =
                            if (accountId.isNotEmpty()) EpgCache.now(accountId, EpgCache.key(current))
                            else null
                        val next =
                            if (accountId.isNotEmpty()) EpgCache.next(accountId, EpgCache.key(current))
                            else null
                        val tf = java.text.SimpleDateFormat(
                            "HH:mm", java.util.Locale.getDefault()
                        )
                        val txt = when {
                            now != null -> "Now  ${tf.format(java.util.Date(now.start))}–${tf.format(java.util.Date(now.stop))}  ${now.title}"
                            next != null -> "Next  ${tf.format(java.util.Date(next.start))}  ${next.title}"
                            else -> "No schedule information"
                        }
                        Text(
                            txt, color = if (now != null) Color.White else Dim,
                            fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = videoTextStyle
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(position), color = Color.White, fontSize = 13.sp,
                            style = videoTextStyle)
                        Spacer(Modifier.width(12.dp))
                        var sliderPos by remember { mutableFloatStateOf(0f) }
                        LaunchedEffect(position) {
                            sliderPos = if (!isLive && duration > 0)
                                (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                        }
                        Slider(
                            value = sliderPos,
                            onValueChange = { sliderPos = it },
                            onValueChangeFinished = {
                                if (!isLive) player.seekTo((sliderPos * duration).toLong())
                            },
                            enabled = !isLive,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Accent,
                                inactiveTrackColor = Color(0xFF3A4556)
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(if (isLive) "LIVE" else fmtTime(duration),
                            color = Color.White, fontSize = 13.sp,
                            style = videoTextStyle)
                    }
                    Spacer(Modifier.height(3.dp))
                    // actions — round buttons with abbreviated labels, centered
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(
                            10.dp, Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val aspectNames = listOf("Fit", "Fill", "Zoom")
                        RoundBtn(icon = { SpeakerIcon(muted = muted, iconSize = 20.dp) }) { toggleMute() }
                        RoundBtn("EPG") { onOpenGuide() }
                        RoundBtn("Multi") { onOpenMultiview() }
                        RoundBtn(
                            "Rec",
                            labelColor = if (isRecording) Color.Red else Color.White
                        ) { toggleRecording() }
                        if (current.streamId != 0) {
                            RoundBtn("C-UP") { loadCatchup() }
                        }
                        RoundBtn(
                            aspectNames[aspectMode],
                            onLongClick = { showScaleDialog = true }
                        ) { cycleAspect() }
                        RoundBtn(if (speed == 1f) "1x" else "${speed}x") { cycleSpeed() }
                        RoundBtn("Sub") { showSettings = true }
                    }
                }

                // channel drawer button — tucked under the back arrow (top-left)
                Box(
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(top = 80.dp, start = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xAA1D2A44))
                        .clickable { showChannels = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("›", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }

            // edge strip: brightness (left) / volume (right) while swiping.
            // Outside showControls so it appears on swipe even when the
            // info box is hidden.
            val side = swipeSide
            if (side != null) {
                val frac = if (side == 0) brightness
                else volume.toFloat() / maxVolume.toFloat()
                val pctText = if (side == 0) "${(brightness * 100).roundToInt()}%"
                else "$volume"
                Box(
                    Modifier.align(if (side == 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (side == 0) SunIcon() else SpeakerIcon()
                        Spacer(Modifier.height(6.dp))
                        Text(
                            pctText, color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.width(8.dp).height(140.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF3A4556))
                        ) {
                            Box(
                                Modifier.width(8.dp)
                                    .fillMaxHeight(frac.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Accent)
                                    .align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }

            // locked: tap to unlock
            if (locked) {
                Box(
                    Modifier.fillMaxSize().clickable { locked = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.clip(CircleShape).background(Color(0xAA000000))
                            .padding(20.dp)
                    ) { LockIcon(iconSize = 28.dp) }
                }
            }

            // recording timer picker
            if (showRecTimer) {
                AlertDialog(
                    onDismissRequest = { showRecTimer = false },
                    title = { Text("Record", color = Color.White) },
                    text = {
                        Column {
                            Text("Stop recording after…", color = Dim, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            listOf(
                                0 to "No timer",
                                15 to "15 min", 30 to "30 min",
                                60 to "60 min", 90 to "90 min", 120 to "120 min"
                            ).forEach { (m, label) ->
                                TextButton(onClick = {
                                    showRecTimer = false
                                    startRecordingWithTimer(m)
                                }) { Text(label, color = Accent) }
                            }
                        }
                    },
                    confirmButton = {},
                    containerColor = CardBg
                )
            }

            // video scale mode (ZyneLabs-style): 70%–140% zoom on top of aspect
            if (showScaleDialog) {
                val scales = (0 until 15).map { 0.70f + it * 0.05f }
                AlertDialog(
                    onDismissRequest = { showScaleDialog = false },
                    title = { Text("Video scale mode", color = Color.White) },
                    text = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            scales.forEach { v ->
                                val sel = abs(v - videoScale) < 0.001f
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Accent else CardSel)
                                        .clickable {
                                            showScaleDialog = false
                                            setVideoScale(v)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${(v * 100).roundToInt()}%",
                                        color = if (sel) Bg else Ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showScaleDialog = false }) {
                            Text("Close", color = Dim)
                        }
                    },
                    containerColor = CardBg
                )
            }

            // ===== right settings panel (transparent, narrow) =====
            if (showSettings) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x40000000))
                        .clickable { showSettings = false }
                )
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f)
                        .background(Color.Transparent)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Purple)
                                .clickable { showSettings = false },
                            contentAlignment = Alignment.Center
                        ) { Text("←", color = Color.White, fontSize = 20.sp) }
                        Spacer(Modifier.width(12.dp))
                        Text("Settings", color = Color.White, fontSize = 17.sp,
                            fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // playback speed
                        item {
                            SettingsSection("Playback speed")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SPEEDS.forEach { s ->
                                    Chip(
                                        if (s == 1f) "Normal" else "${s}x",
                                        speed == s
                                    ) {
                                        speed = s
                                        player.setPlaybackSpeed(s)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        // aspect
                        item {
                            SettingsSection("Aspect ratio")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Fit", "Fill", "Zoom").forEachIndexed { i, l ->
                                    Chip(l, aspectMode == i) {
                                        aspectMode = i
                                        player.videoScalingMode = if (i == 0)
                                            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                                        else C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        // audio tracks
                        val audioGroups = tracks?.groups
                            ?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList()
                        if (audioGroups.size > 1) {
                            item {
                                SettingsSection("Audio Tracks")
                                audioGroups.forEach { g ->
                                    for (ti in 0 until g.length) {
                                        val f = g.getTrackFormat(ti)
                                        TrackRow(
                                            "♪",
                                            f.label ?: f.language ?: "Track ${ti + 1}",
                                            g.isTrackSelected(ti)
                                        ) {
                                            player.trackSelectionParameters =
                                                player.trackSelectionParameters.buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(g.mediaTrackGroup, ti)
                                                    ).build()
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                        // subtitle tracks
                        val textGroups = tracks?.groups
                            ?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()
                        if (textGroups.isNotEmpty()) {
                            item {
                                SettingsSection("Subtitle Tracks")
                                textGroups.forEach { g ->
                                    for (ti in 0 until g.length) {
                                        val f = g.getTrackFormat(ti)
                                        TrackRow(
                                            "CC",
                                            f.label ?: f.language ?: "CC ${ti + 1}",
                                            g.isTrackSelected(ti)
                                        ) {
                                            player.trackSelectionParameters =
                                                player.trackSelectionParameters.buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(g.mediaTrackGroup, ti)
                                                    ).build()
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                        // sleep timer
                        item {
                            SettingsSection("Sleep timer")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0, 15, 30, 60, 90).forEach { m ->
                                    Chip(if (m == 0) "Off" else "${m}m", sleepMin == m) {
                                        sleepMin = m
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===== left channel drawer (transparent, narrow, text-only) =====
            if (showChannels) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x40000000))
                        .clickable { showChannels = false }
                )
                // lists computed before the header so the "Now" button can use them
                val drawerCats = remember(drawerChannels) {
                    drawerCategories(drawerChannels)
                }
                val drawerCountries = remember(drawerChannels) {
                    drawerCountries(drawerChannels)
                }
                val drawerList = remember(drawerChannels, drawerCat, drawerCountry) {
                    val dc = drawerCat
                    val dco = drawerCountry
                    when {
                        dco != null -> drawerChannels.filter { it.getCountry() == dco }
                        dc != null -> drawerChannels.filter { it.getSmartCat() == dc }
                        else -> drawerChannels
                    }
                }
                val listState = rememberLazyListState()
                val drawerAccountName = remember(drawerAccounts, drawerAccountId) {
                    drawerAccounts.find { it.id == drawerAccountId }
                        ?.let { if (it.name.isNotEmpty()) it.name else "Playlist" }
                        ?: "Playlist"
                }
                Column(
                    modifier = Modifier.align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(if (isPortrait) 0.78f else 0.5f)
                        .background(Color(0xF2141419))
                ) {
                    // header: title + jump back to the playing channel
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Channels", color = Color.White, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(CardSel)
                                .clickable {
                                    drawerCat = null
                                    drawerCountry = null
                                    val idx = drawerChannels.indexOfFirst {
                                        it.url == current.url && it.name == current.name
                                    }
                                    if (idx >= 0) scope.launch {
                                        try { listState.animateScrollToItem(idx) }
                                        catch (_: Exception) {}
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "◎ Now", color = Accent, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // provider switch row
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(10.dp)).background(CardSel)
                            .clickable { showProviderPick = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Provider", color = Dim, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                drawerAccountName, color = Ink, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text("▾", color = Accent, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // category chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Chip(
                                "All",
                                drawerCat == null && drawerCountry == null
                            ) { drawerCat = null; drawerCountry = null }
                        }
                        items(drawerCats) { (name, _) ->
                            Chip(name, drawerCat == name) {
                                drawerCat = if (drawerCat == name) null else name
                                drawerCountry = null
                            }
                        }
                    }
                    // country chips
                    if (drawerCountries.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(drawerCountries) { (name, count) ->
                                Chip("$name · $count", drawerCountry == name) {
                                    drawerCountry =
                                        if (drawerCountry == name) null else name
                                    drawerCat = null
                                }
                            }
                        }
                    }
                    if (drawerLoading) {
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = Accent) }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(drawerList, key = { it.name + it.url }) { ch ->
                                val sel =
                                    ch.url == current.url && ch.name == current.name
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(
                                            if (sel) CardSel else Color.Transparent
                                        )
                                        .clickable { switchChannel(ch) }
                                        .padding(
                                            horizontal = 16.dp, vertical = 12.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (sel) {
                                        Box(
                                            Modifier.size(8.dp).clip(CircleShape)
                                                .background(Accent)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        ch.name,
                                        color = if (sel) Accent else Ink,
                                        fontSize = 15.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (sel) FontWeight.Bold
                                        else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
                // provider picker dialog
                if (showProviderPick) {
                    AlertDialog(
                        onDismissRequest = { showProviderPick = false },
                        title = { Text("Provider", color = Ink) },
                        text = {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(drawerAccounts, key = { it.id }) { acc ->
                                    val accName =
                                        if (acc.name.isNotEmpty()) acc.name
                                        else "Playlist"
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .tvFocus(corner = 8.dp)
                                            .focusable()
                                            .clickable {
                                                showProviderPick = false
                                                if (acc.id != drawerAccountId)
                                                    loadDrawerProvider(acc)
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            accName, color = Ink, fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (acc.id == drawerAccountId) Text(
                                            "✓", color = Accent, fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        containerColor = CardBg
                    )
                }
            }

            // ===== catch-up dialog =====
            if (showCatchup) {
                AlertDialog(
                    onDismissRequest = { showCatchup = false },
                    title = { Text("Catch-up", color = Ink) },
                    text = {
                        when (val progs = catchupProgs) {
                            null -> Box(
                                Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(color = Accent) }
                            else -> if (progs.isEmpty()) {
                                Text("No catch-up available for this channel.",
                                    color = Dim)
                            } else {
                                LazyColumn(
                                    modifier = Modifier.height(300.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(progs) { p ->
                                        Column(
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardSel)
                                                .clickable {
                                                    playCatchup(player, current, p)
                                                    showCatchup = false
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Text(p.title ?: "", color = Ink,
                                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${p.start} - ${p.stop}", color = Dim,
                                                fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCatchup = false }) {
                            Text("Close", color = Accent)
                        }
                    },
                    containerColor = CardBg
                )
            }
        }

    }
}

/** Genre categories for the in-player channel drawer (mirrors MainActivity.buildCategories). */
private fun drawerCategories(channels: List<Channel>): List<Pair<String, Int>> {
    val counts = linkedMapOf<String, Int>()
    for (c in channels) {
        val cat = c.getSmartCat()
        counts[cat] = (counts[cat] ?: 0) + 1
    }
    return counts.entries.sortedByDescending { it.value }
        .map { it.key to it.value }
}

/** Country buckets for the in-player channel drawer. */
private fun drawerCountries(channels: List<Channel>): List<Pair<String, Int>> {
    val counts = linkedMapOf<String, Int>()
    for (c in channels) {
        val co = c.getCountry()
        counts[co] = (counts[co] ?: 0) + 1
    }
    return counts.entries.sortedByDescending { it.value }
        .map { it.key to it.value }
}

private fun playCatchup(player: ExoPlayer, ch: Channel, p: XtreamClient.Program) {
    try {
        val startMs = XtreamClient.parseEpgTime(p.start ?: "")
        val stopMs = XtreamClient.parseEpgTime(p.stop ?: "")
        if (startMs > 0 && stopMs > startMs) {
            val durMin = ((stopMs - startMs) / 60000).coerceAtLeast(1L)
            val url = XtreamClient.timeshiftUrl(ch, startMs, durMin)
            if (url.isNotEmpty()) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
            }
        }
    } catch (_: Exception) {}
}

// ---- recording helpers (StreamRecorder is Java) ----
private var recorder: StreamRecorder? = null
private fun startRecording(ctx: Context, ch: Channel) {
    try {
        val r = StreamRecorder()
        val fileName = StreamRecorder.fileNameFor(ch.name)
        r.start(ctx, ch.url, fileName, object : StreamRecorder.Listener {
            override fun onStarted(f: String) {}
            override fun onStopped(f: String, bytes: Long) {}
            override fun onError(msg: String) {}
        })
        recorder = r
    } catch (_: Exception) {}
}
private fun stopRecording() {
    try { recorder?.stop() } catch (_: Exception) {}
    recorder = null
}

@Composable
private fun TopIcon(
    label: String,
    bg: Color,
    small: Boolean = false,
    size: Dp = 40.dp,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) icon()
        else Text(label, color = Color.White,
            fontSize = if (small) 11.sp else 18.sp,
            fontWeight = FontWeight.Bold)
    }
}

/** Transport icons drawn with Canvas — plain white, matches the UI
 *  (the ⏸/⏺/⏮/⏭ text glyphs render as orange emoji on some phones). */
@Composable
private fun PlayIcon(color: Color = Color.White) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        drawPath(Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.78f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.82f)
            close()
        }, color)
    }
}

@Composable
private fun PauseIcon(color: Color = Color.White) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        drawRect(color, Offset(w * 0.26f, h * 0.2f), Size(w * 0.18f, h * 0.6f))
        drawRect(color, Offset(w * 0.56f, h * 0.2f), Size(w * 0.18f, h * 0.6f))
    }
}

@Composable
private fun PrevIcon(color: Color = Color.White) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        drawRect(color, Offset(w * 0.14f, h * 0.22f), Size(w * 0.13f, h * 0.56f))
        drawPath(Path().apply {
            moveTo(w * 0.84f, h * 0.2f)
            lineTo(w * 0.32f, h * 0.5f)
            lineTo(w * 0.84f, h * 0.8f)
            close()
        }, color)
    }
}

@Composable
private fun NextIcon(color: Color = Color.White) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        drawPath(Path().apply {
            moveTo(w * 0.16f, h * 0.2f)
            lineTo(w * 0.68f, h * 0.5f)
            lineTo(w * 0.16f, h * 0.8f)
            close()
        }, color)
        drawRect(color, Offset(w * 0.73f, h * 0.22f), Size(w * 0.13f, h * 0.56f))
    }
}

/** Padlock drawn with Canvas in the UI theme color — the 🔒 emoji renders
 *  yellow on some phones and clashes with the theme. */
@Composable
private fun LockIcon(iconSize: Dp = 20.dp, color: Color = Color.White) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width; val h = size.height
        val sw = w * 0.15f
        // shackle
        drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.08f), size = Size(w * 0.52f, h * 0.46f),
            style = Stroke(sw))
        // body
        drawRoundRect(color, Offset(w * 0.15f, h * 0.42f), Size(w * 0.7f, h * 0.52f),
            CornerRadius(w * 0.12f))
        // keyhole
        drawCircle(color, w * 0.075f, Offset(w * 0.5f, h * 0.62f))
        drawRect(color, Offset(w * 0.5f - sw * 0.32f, h * 0.63f),
            Size(sw * 0.64f, h * 0.2f))
    }
}

/** Speaker icon drawn with Canvas (IPTV Smarters style) — no emoji. */
@Composable
private fun SpeakerIcon(
    color: Color = Color.White,
    iconSize: Dp = 24.dp,
    muted: Boolean = false
) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.10f, h * 0.42f)
            lineTo(w * 0.38f, h * 0.42f)
            lineTo(w * 0.58f, h * 0.14f)
            lineTo(w * 0.58f, h * 0.86f)
            lineTo(w * 0.38f, h * 0.58f)
            lineTo(w * 0.10f, h * 0.58f)
            close()
        }
        drawPath(body, color)
        if (muted) {
            // X mark
            drawLine(
                color, Offset(w * 0.66f, h * 0.36f), Offset(w * 0.92f, h * 0.64f),
                strokeWidth = w * 0.08f, cap = StrokeCap.Round
            )
            drawLine(
                color, Offset(w * 0.92f, h * 0.36f), Offset(w * 0.66f, h * 0.64f),
                strokeWidth = w * 0.08f, cap = StrokeCap.Round
            )
        } else {
            // sound waves
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.60f, h * 0.30f),
                size = Size(w * 0.24f, h * 0.40f),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.74f, h * 0.20f),
                size = Size(w * 0.24f, h * 0.60f),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
            )
        }
    }
}

/** Modern sun/brightness icon drawn with Canvas — no emoji. */
@Composable
private fun SunIcon(color: Color = Color.White, iconSize: Dp = 22.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val cx = w / 2f
        val cy = size.height / 2f
        // center disc
        drawCircle(color, radius = w * 0.20f, center = Offset(cx, cy))
        // 8 rays
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())
            val r1 = w * 0.30f
            val r2 = w * 0.44f
            drawLine(
                color,
                Offset(
                    cx + (r1 * cos(a)).toFloat(),
                    cy + (r1 * sin(a)).toFloat()
                ),
                Offset(
                    cx + (r2 * cos(a)).toFloat(),
                    cy + (r2 * sin(a)).toFloat()
                ),
                strokeWidth = w * 0.07f, cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SeekBtn(
    label: String? = null,
    icon: (@Composable () -> Unit)? = null,
    size: Dp = 64.dp,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (active) Color(0xAAB71C1C) else Color(0xAA1D2A44))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) icon()
        else Text(label ?: "", color = Color.White, fontSize = (size.value * 0.22f).sp,
            fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundBtn(
    label: String = "",
    onLongClick: (() -> Unit)? = null,
    labelColor: Color = Color.White,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(44.dp)
            .clip(CircleShape)
            .background(Color(0xCC1D2A44))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) icon()
        else Text(
            label, color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold, maxLines = 1
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, color = Color.White, fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun TrackRow(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CardSel else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (selected) Accent else Ink, fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (selected) Text("✓", color = Accent, fontSize = 14.sp)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent else CardSel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF06202A) else Ink,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).toInt()
    val m = s / 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%02d:%02d".format(m, s)
}
