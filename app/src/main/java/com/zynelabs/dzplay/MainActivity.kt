package com.zynelabs.dzplay

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zynelabs.dzplay.data.Cats
import com.zynelabs.dzplay.data.Channel
import com.zynelabs.dzplay.data.ChannelRepo
import com.zynelabs.dzplay.data.ImageLoader
import com.zynelabs.dzplay.data.PlAccount
import com.zynelabs.dzplay.data.PlaylistCache
import com.zynelabs.dzplay.data.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- DZplay premium design system ----------
// Deep dark Netflix/AppleTV-inspired theme. `Accent` keeps its historic name so
// the shared player/settings code compiles unchanged — its value is the new
// neon purple accent.
internal val Bg = Color(0xFF0B0B0E)
internal val Card = Color(0xE616161D)
internal val CardSel = Color(0xE620202B)
internal val BtnBg = Color(0x8020202B)
internal val PanelBg = Color(0x9916161D)
/** Accent color choices: key -> (label, color). */
internal val ACCENTS = linkedMapOf(
    "purple" to ("Neon Purple" to Color(0xFF8A2BE2)),
    "cyan" to ("Neon Cyan" to Color(0xFF22D3EE)),
    "blend" to ("Violet Frost" to Color(0xFF818CF8))
)
internal var AccentName by mutableStateOf("blend")
/**
 * Current accent color. Backed by [AccentName] state, so every composable
 * reading [Accent] recomposes live when the user picks another accent —
 * no restart needed.
 */
internal val Accent: Color get() = ACCENTS[AccentName]?.second ?: Color(0xFF8A2BE2)
internal fun accentLabel(): String = ACCENTS[AccentName]?.first ?: "Neon Purple"
/** Content view mode: "grid" or "list". Backed by state so tabs re-render live. */
internal var ViewMode by mutableStateOf("grid")
internal val Ink = Color(0xFFF2F0FA)
internal val Dim = Color(0xFF8F8FA3)

/** Background tick (kept for shared settings code). */
object AppBg { var tick by mutableStateOf(0) }

/** Plain background (DZplay has no custom image picker yet). */
@Composable
fun AppBackground() {
    Box(Modifier.fillMaxSize().background(Bg))
}

/** TV-mode flag, set once in onCreate from UiModeManager. */
object DzTv { var isTv = false }

object AppCtx { var ctx: Context? = null }

/**
 * TV D-pad focus treatment: scale up to 1.1x, glowing neon-purple border and
 * an elevation shadow while focused. On mobile (touch) it only reports focus
 * changes and renders no visuals — touch uses press states instead.
 */
fun Modifier.tvFocus(
    corner: Dp = 16.dp,
    onFocused: (Boolean) -> Unit = {}
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val tv = DzTv.isTv
    val scale by animateFloatAsState(
        if (tv && focused) 1.1f else 1f, label = "dz_focus_scale"
    )
    this
        .onFocusChanged {
            focused = it.isFocused
            onFocused(it.isFocused)
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = if (tv && focused) 18.dp.toPx() else 0f
            shape = RoundedCornerShape(corner)
            clip = false
        }
        .border(
            width = if (tv && focused) 2.dp else 0.dp,
            color = if (tv && focused) Accent else Color.Transparent,
            shape = RoundedCornerShape(corner)
        )
}

// ---------- thin-line vector icons (no emoji) ----------
@Composable
private fun DzIcon(kind: String, color: Color = Ink, iconSize: Dp = 24.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width; val h = size.height
        val sw = w * 0.075f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2),
                strokeWidth = sw, cap = StrokeCap.Round)
        when (kind) {
            "home" -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.52f); lineTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.88f, h * 0.52f); moveTo(w * 0.22f, h * 0.46f)
                    lineTo(w * 0.22f, h * 0.86f); lineTo(w * 0.78f, h * 0.86f)
                    lineTo(w * 0.78f, h * 0.46f)
                }
                drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round))
            }
            "live" -> {
                drawRoundRect(color, Offset(w * 0.08f, h * 0.2f),
                    Size(w * 0.84f, h * 0.55f), CornerRadius(w * 0.08f),
                    style = Stroke(sw))
                line(0.38f, 0.75f, 0.62f, 0.75f); line(0.5f, 0.75f, 0.5f, 0.88f)
                line(0.32f, 0.88f, 0.68f, 0.88f)
            }
            "film" -> {
                drawRoundRect(color, Offset(w * 0.1f, h * 0.18f),
                    Size(w * 0.8f, h * 0.64f), CornerRadius(w * 0.06f),
                    style = Stroke(sw))
                line(0.3f, 0.18f, 0.3f, 0.82f); line(0.7f, 0.18f, 0.7f, 0.82f)
            }
            "series" -> {
                drawRoundRect(color, Offset(w * 0.14f, h * 0.3f),
                    Size(w * 0.72f, h * 0.56f), CornerRadius(w * 0.06f),
                    style = Stroke(sw))
                drawRoundRect(color, Offset(w * 0.14f, h * 0.14f),
                    Size(w * 0.72f, h * 0.56f), CornerRadius(w * 0.06f),
                    style = Stroke(sw))
            }
            "settings" -> {
                line(0.15f, 0.3f, 0.85f, 0.3f); line(0.15f, 0.5f, 0.85f, 0.5f)
                line(0.15f, 0.7f, 0.85f, 0.7f)
                drawCircle(color, w * 0.09f, Offset(w * 0.62f, h * 0.3f))
                drawCircle(color, w * 0.09f, Offset(w * 0.35f, h * 0.5f))
                drawCircle(color, w * 0.09f, Offset(w * 0.68f, h * 0.7f))
            }
            "play" -> {
                val p = Path().apply {
                    moveTo(w * 0.35f, h * 0.25f); lineTo(w * 0.75f, h * 0.5f)
                    lineTo(w * 0.35f, h * 0.75f); close()
                }
                drawPath(p, color)
            }
        }
    }
}

/** Async network image via the shared ImageLoader. */
@Composable
fun DzImage(url: String, modifier: Modifier = Modifier, fit: Boolean = false) {
    AndroidView(
        factory = { c ->
            android.widget.ImageView(c).apply {
                scaleType =
                    if (fit) android.widget.ImageView.ScaleType.FIT_CENTER
                    else android.widget.ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { iv -> ImageLoader.load(url, iv, 0) },
        modifier = modifier
    )
}

// ---------- data ----------
private data class DzUi(
    val loading: Boolean = true,
    val account: PlAccount? = null,
    val title: String = "",
    val live: List<Channel> = emptyList(),
    val vod: List<Channel> = emptyList(),
    val series: List<Channel> = emptyList(),
    val error: String? = null
)

private fun loadDz(): DzUi {
    val ctx = AppCtx.ctx ?: return DzUi(loading = false, error = "No context")
    return try {
        val store = Store(ctx)
        val acc = store.account(store.activeAccountId())
            ?.takeIf { it.enabled }
            ?: store.enabledAccounts().firstOrNull()
            ?: return DzUi(loading = false)
        val data = PlaylistCache.load(ctx.filesDir, acc.id)
            ?: return DzUi(
                loading = false, account = acc,
                title = acc.name.ifEmpty { "DZplay" },
                error = "No cached data — tap Reload"
            )
        val locked = store.adultLocked()
        fun filt(l: List<Channel>) =
            if (locked) l.filter { it.getSmartCat() != Cats.ADULT } else l
        DzUi(
            loading = false, account = acc,
            title = acc.name.ifEmpty { "DZplay" },
            live = filt(data.live), vod = filt(data.vod), series = filt(data.series)
        )
    } catch (t: Throwable) {
        DzUi(loading = false, error = "Load failed: ${t.message}")
    }
}

// ---------- activity ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCtx.ctx = applicationContext
        // restore the saved accent before first composition (no flash)
        try {
            val saved = Store(applicationContext).accent()
            AccentName = if (ACCENTS.containsKey(saved)) saved else "blend"
        } catch (_: Exception) { AccentName = "blend" }
        // restore the saved content view mode (grid is the DZplay default)
        try {
            val vm = Store(applicationContext).viewMode()
            ViewMode = if (vm == "list") "list" else "grid"
        } catch (_: Exception) { ViewMode = "grid" }
        val um = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        DzTv.isTv = um.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        if (DzTv.isTv) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        hideStatusBar()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg, surface = Card,
                    primary = Accent, onBackground = Ink, onSurface = Ink
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(
                        Modifier.fillMaxSize().background(Bg)
                    ) { DzApp() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar()
    }

    private fun hideStatusBar() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (_: Exception) {}
    }
}

private data class NavItem(val label: String, val icon: String)

// ---------- app root ----------
@Composable
private fun DzApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(DzUi()) }
    var nav by remember { mutableIntStateOf(0) } // 0 home, 1 live, 2 movies, 3 series, 4 settings
    var playing by remember { mutableStateOf<Channel?>(null) }
    var mini by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var reloading by remember { mutableStateOf(false) }
    val items = listOf(
        NavItem("Home", "home"),
        NavItem("Live TV", "live"),
        NavItem("Movies", "film"),
        NavItem("Series", "series"),
        NavItem("Settings", "settings")
    )

    fun load() {
        scope.launch(Dispatchers.IO) {
            val fresh = loadDz()
            withContext(Dispatchers.Main) { ui = fresh }
        }
    }
    LaunchedEffect(refreshTick) { load() }

    fun reload() {
        if (reloading) return
        val accId = ui.account?.id ?: return
        reloading = true
        scope.launch(Dispatchers.IO) {
            val acc = Store(ctx).account(accId)
            var ok = false
            if (acc != null) {
                val d = CompletableDeferred<Boolean>()
                ChannelRepo.load(ctx, acc, object : ChannelRepo.Callback {
                    override fun onResult(
                        live: List<Channel>, vod: List<Channel>,
                        series: List<Channel>, err: String?
                    ) {
                        if (err == null) {
                            try {
                                PlaylistCache.save(ctx.filesDir, acc.id, live, vod, series)
                            } catch (_: Exception) {}
                            d.complete(true)
                        } else d.complete(false)
                    }
                })
                ok = d.await()
            }
            val fresh = loadDz()
            withContext(Dispatchers.Main) {
                reloading = false
                ui = if (!ok && fresh.live.isEmpty()) fresh.copy(error = "Reload failed")
                else fresh
            }
        }
    }

    fun play(ch: Channel) {
        playing = ch
        mini = false
        if (!DzTv.isTv &&
            ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        ) {
            (ctx as? android.app.Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    fun closePlayer() {
        playing = null
        mini = false
        if (!DzTv.isTv) {
            (ctx as? android.app.Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        when {
            guideOpen -> guideOpen = false
            playing != null -> closePlayer()
            nav != 0 -> nav = 0
        }
    }

    val current = playing
    // TV sidebar focus state (hoisted so content can collapse the sidebar)
    var sideFocused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (DzTv.isTv) {
            // TV: sidebar + content
            Row(Modifier.fillMaxSize()) {
                TvSidebar(
                    items = items,
                    selected = nav,
                    sideFocused = sideFocused,
                    onSideFocus = { sideFocused = it },
                    onSelect = { nav = it }
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    DzContent(
                        ui = ui, nav = nav,
                        onNav = { nav = it },
                        onPlay = ::play,
                        onReload = ::reload,
                        reloading = reloading,
                        onRefresh = { refreshTick++ },
                        onContentFocus = { sideFocused = false }
                    )
                }
            }
        } else {
            // Mobile: content + bottom nav
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    DzContent(
                        ui = ui, nav = nav,
                        onNav = { nav = it },
                        onPlay = ::play,
                        onReload = ::reload,
                        reloading = reloading,
                        onRefresh = { refreshTick++ },
                        onContentFocus = {}
                    )
                }
                MobileBottomNav(items = items, selected = nav, onSelect = { nav = it })
            }
        }

        // fullscreen player above everything
        if (current != null) {
            Box(
                modifier = if (mini)
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 96.dp)
                        .size(200.dp, 112.dp)
                else Modifier.fillMaxSize()
            ) {
                key(current.url + current.name) {
                    PlayerScreen(
                        channels = ui.live + ui.vod + ui.series,
                        initial = current,
                        accountId = ui.account?.id ?: "",
                        mini = mini,
                        onChannelSelected = { playing = it },
                        onClose = { closePlayer() },
                        onMinimize = { mini = true },
                        onExpand = { mini = false },
                        onOpenGuide = { guideOpen = true },
                        onOpenMultiview = {}
                    )
                }
            }
        }
        if (guideOpen) {
            Box(Modifier.fillMaxSize()) {
                GuideScreen(
                    accountId = ui.account?.id ?: "",
                    channels = ui.live,
                    onBack = { guideOpen = false },
                    onPlay = { ch -> playing = ch; guideOpen = false }
                )
            }
        }
    }
}

// ---------- TV sidebar (auto-expands on focus) ----------
@Composable
private fun TvSidebar(
    items: List<NavItem>,
    selected: Int,
    sideFocused: Boolean,
    onSideFocus: (Boolean) -> Unit,
    onSelect: (Int) -> Unit
) {
    // expands while any sidebar item holds D-pad focus; collapses when focus
    // moves into the content area
    val width by animateDpAsState(if (sideFocused) 220.dp else 76.dp, label = "dz_side")
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(400)
        try { firstFocus.requestFocus() } catch (_: Exception) {}
    }
    Column(
        Modifier.width(width).fillMaxHeight()
            .background(Color(0xFF101016))
            .padding(vertical = 24.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // brand
        Box(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (sideFocused) "DZplay" else "DZ",
                color = Accent, fontSize = if (sideFocused) 22.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = Shadow(Accent, Offset(0f, 0f), 14f)
                ),
                maxLines = 1
            )
        }
        items.forEachIndexed { i, item ->
            val isSel = selected == i
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSel) Accent.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .then(
                        if (i == 0) Modifier.focusRequester(firstFocus)
                        else Modifier
                    )
                    .focusable()
                    .tvFocus(
                        corner = 12.dp,
                        onFocused = { f -> if (f) onSideFocus(true) }
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DzIcon(
                        item.icon,
                        color = if (isSel) Accent else Dim,
                        iconSize = 26.dp
                    )
                    if (sideFocused) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.label,
                            color = if (isSel) Ink else Dim,
                            fontSize = 16.sp,
                            fontWeight = if (isSel) FontWeight.Bold
                            else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ---------- mobile bottom navigation ----------
@Composable
private fun MobileBottomNav(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF101016))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { i, item ->
            val isSel = selected == i
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DzIcon(
                    item.icon,
                    color = if (isSel) Accent else Dim,
                    iconSize = 24.dp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.label,
                    color = if (isSel) Accent else Dim,
                    fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------- dashboard content ----------
@Composable
private fun DzContent(
    ui: DzUi,
    nav: Int,
    onNav: (Int) -> Unit,
    onPlay: (Channel) -> Unit,
    onReload: () -> Unit,
    reloading: Boolean,
    onRefresh: () -> Unit,
    onContentFocus: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // quick provider switcher from the top bar
    var showSwitch by remember { mutableStateOf(false) }
    var switchAccounts by remember { mutableStateOf(listOf<PlAccount>()) }
    var switchActive by remember { mutableStateOf("") }

    fun openSwitch() {
        scope.launch(Dispatchers.IO) {
            val s = Store(ctx)
            val list = try { s.enabledAccounts() } catch (_: Exception) { emptyList() }
            val aid = try { s.activeAccountId() } catch (_: Exception) { "" }
            withContext(Dispatchers.Main) {
                switchAccounts = list
                switchActive = aid
                showSwitch = true
            }
        }
    }

    fun switchTo(id: String) {
        showSwitch = false
        scope.launch(Dispatchers.IO) {
            try { Store(ctx).setActiveAccountId(id) } catch (_: Exception) {}
            withContext(Dispatchers.Main) { onNav(0); onRefresh() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!DzTv.isTv) {
                Text(
                    "DZplay", color = Accent,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        shadow = Shadow(Accent, Offset(0f, 0f), 14f)
                    )
                )
                Spacer(Modifier.width(12.dp))
            }
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .clickable { openSwitch() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ui.title, color = Ink, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(" ▾", color = Accent, fontSize = 12.sp)
            }
            if (reloading) {
                CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                )
            } else {
                Text(
                    "Reload", color = Accent, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { onReload() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
        // quick provider switch dialog
        if (showSwitch) {
            val switchFr = remember { FocusRequester() }
            LaunchedEffect(showSwitch) {
                try { switchFr.requestFocus() } catch (_: Exception) {}
            }
            AlertDialog(
                onDismissRequest = { showSwitch = false },
                title = { Text("Switch provider", color = Ink) },
                text = {
                    if (switchAccounts.isEmpty()) {
                        Text("No providers yet.", color = Dim, fontSize = 14.sp)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(
                                switchAccounts,
                                key = { _, acc -> acc.id }
                            ) { index, acc ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (index == 0) Modifier.focusRequester(switchFr)
                                            else Modifier
                                        )
                                        .tvFocus(corner = 8.dp)
                                        .focusable()
                                        .clickable { switchTo(acc.id) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        acc.name.ifEmpty { "Playlist" },
                                        color = Ink, fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (acc.id == switchActive) Text(
                                        "✓", color = Accent, fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                containerColor = Card
            )
        }
        when {
            ui.loading -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }
            // Settings must open even with no provider, otherwise the
            // "Add Provider" button appears to do nothing.
            nav == 4 -> DzSettings(onRefresh = { onRefresh() })
            nav == 5 -> DzSettings(
                onRefresh = { onRefresh() },
                startAt = "providers"
            )
            ui.account == null -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DzIcon("live", Dim, 56.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("No provider yet", color = Ink, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add your IPTV provider to start watching",
                        color = Dim, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Accent)
                            .tvFocus(corner = 12.dp)
                            .focusable()
                            .clickable { onNav(5) }
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Add Provider", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            ui.error != null && ui.live.isEmpty() && ui.vod.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.error!!, color = Ink, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Accent)
                            .clickable { onReload() }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Reload", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            else -> when (nav) {
                0 -> DzHome(ui, onPlay, onContentFocus)
                1 -> MediaGrid(
                    "Live TV", ui.live, onPlay, onContentFocus,
                    live = true, showCountries = true
                )
                2 -> MediaGrid("Movies", ui.vod, onPlay, onContentFocus)
                3 -> MediaGrid("Series", ui.series, onPlay, onContentFocus)
                else -> AccountsScreen(
                    onBack = { onNav(0) },
                    onChanged = { onRefresh() }
                )
            }
        }
    }
}

/** True when the smart category engine flags this as adult content. */
private fun Channel.isAdult(): Boolean = getSmartCat() == Cats.ADULT

@Composable
private fun DzHome(
    ui: DzUi,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit
) {
    val ctx = LocalContext.current
    val hero = remember(ui) {
        val sfw = ui.vod.filterNot { it.isAdult() } +
            ui.series.filterNot { it.isAdult() } +
            ui.live.filterNot { it.isAdult() }
        sfw.firstOrNull { it.logo.isNotEmpty() } ?: sfw.firstOrNull()
    }
    // Home never shows adult content — it lives under its own chip in the tabs.
    val homeLive = remember(ui) { ui.live.filterNot { it.isAdult() } }
    val homeVod = remember(ui) { ui.vod.filterNot { it.isAdult() } }
    val homeSeries = remember(ui) { ui.series.filterNot { it.isAdult() } }
    val recent = remember(ui) {
        try {
            val keys = Store(ctx).recent(ui.account?.id ?: "")
            val all = homeLive + homeVod + homeSeries
            keys.mapNotNull { k -> all.find { it.url == k } }
                .distinctBy { it.url }.take(20)
        } catch (_: Exception) { emptyList() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hero != null) item(key = "hero") {
            DzHero(hero, onPlay, onContentFocus)
        }
        if (recent.isNotEmpty()) item(key = "recent") {
            DzRow("Continue Watching", recent, onPlay, onContentFocus)
        }
        item(key = "live") {
            DzRow("Live TV", homeLive.take(40), onPlay, onContentFocus, live = true)
        }
        if (homeVod.isNotEmpty()) item(key = "movies") {
            DzRow("Movies", homeVod.take(40), onPlay, onContentFocus)
        }
        if (homeSeries.isNotEmpty()) item(key = "series") {
            DzRow("Series", homeSeries.take(40), onPlay, onContentFocus)
        }
        item(key = "pad") { Spacer(Modifier.height(32.dp)) }
    }
}

/** Hero banner with bottom gradient fade (vignette). */
@Composable
private fun DzHero(
    ch: Channel,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .height(if (DzTv.isTv) 340.dp else 210.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardSel)
            .tvFocus(corner = 16.dp, onFocused = { if (it) onContentFocus() })
            .focusable()
            .clickable { onPlay(ch) }
    ) {
        if (ch.logo.isNotEmpty()) DzImage(ch.logo, Modifier.fillMaxSize())
        // vignette: smooth bottom fade into the background
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xE60B0B0E))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xB30B0B0E), Color.Transparent)
                )
            )
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            Column(Modifier.padding(20.dp).fillMaxWidth(0.85f)) {
                Text(
                    "TRENDING NOW", color = Accent,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    ch.name, color = Color.White,
                    fontSize = if (DzTv.isTv) 30.sp else 22.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Accent)
                            .clickable { onPlay(ch) }
                            .padding(horizontal = 26.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DzIcon("play", Color.White, 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Play", color = Color.White,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // glassmorphism secondary button
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0x55FFFFFF))
                            .clickable { onPlay(ch) }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "More Info", color = Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** Horizontal content row. */
@Composable
private fun DzRow(
    title: String,
    items: List<Channel>,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit,
    live: Boolean = false
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
        ) {
            items(items, key = { it.url + it.name }) { ch ->
                DzCard(
                    ch,
                    w = if (live) 112.dp else 128.dp,
                    h = if (live) 112.dp else 182.dp,
                    live = live,
                    onPlay = onPlay,
                    onContentFocus = onContentFocus
                )
            }
        }
    }
}

/** Poster/logo card with TV focus treatment. */
@Composable
private fun DzCard(
    ch: Channel,
    w: Dp,
    h: Dp,
    live: Boolean,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit
) {
    Column(Modifier.width(w)) {
        Box(
            Modifier.size(w, h)
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .tvFocus(corner = 16.dp, onFocused = { if (it) onContentFocus() })
                .focusable()
                .clickable { onPlay(ch) },
            contentAlignment = Alignment.Center
        ) {
            if (ch.logo.isNotEmpty()) DzImage(ch.logo, Modifier.fillMaxSize(), fit = live)
            else Text(
                ch.name.take(2).uppercase(), color = Dim,
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            if (live) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "● LIVE", color = Accent,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            ch.name, color = Ink, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Full grid for the Live/Movies/Series tabs. */
/** Small filter chip used by the media tabs. */
@Composable
private fun DzChip(
    label: String,
    selected: Boolean,
    onContentFocus: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent else CardSel)
            .tvFocus(corner = 20.dp, onFocused = { if (it) onContentFocus() })
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else Dim,
            fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
}

@Composable
private fun ViewModeToggle() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Card)
            .tvFocus(corner = 10.dp)
            .focusable()
            .clickable {
                ViewMode = if (ViewMode == "grid") "list" else "grid"
                val m = ViewMode
                scope.launch(Dispatchers.IO) {
                    try { Store(ctx).setViewMode(m) } catch (_: Exception) {}
                }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (ViewMode == "grid") "▦" else "☰",
            color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (ViewMode == "grid") "Grid" else "List",
            color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MediaGrid(
    title: String,
    items: List<Channel>,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit,
    live: Boolean = false,
    showCountries: Boolean = false
) {
    // Category filter: "All" shows everything except adult; adult gets its
    // own separated chip so it never mixes into the normal browsing.
    var selCat by remember(items) { mutableStateOf<String?>(null) }
    var selCountry by remember(items) { mutableStateOf<String?>(null) }
    val cats = remember(items) {
        items.filterNot { it.isAdult() }
            .groupingBy { it.getSmartCat() }.eachCount()
            .entries.sortedByDescending { it.value }
    }
    val countries = remember(items) {
        items.filterNot { it.isAdult() }
            .groupingBy { it.getCountry() }.eachCount()
            .entries.sortedByDescending { it.value }
    }
    val adultCount = remember(items) { items.count { it.isAdult() } }
    val shown = remember(items, selCat, selCountry) {
        val co = selCountry
        when (val c = selCat) {
            null -> if (co != null)
                items.filter { it.getCountry() == co && !it.isAdult() }
            else items.filterNot { it.isAdult() }
            Cats.ADULT -> items.filter { it.isAdult() }
            else -> items.filter { it.getSmartCat() == c }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title, color = Ink, fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            ViewModeToggle()
        }
        if (items.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "cat_all") {
                    DzChip(
                        "All", selCat == null && selCountry == null,
                        onContentFocus
                    ) { selCat = null; selCountry = null }
                }
                items(cats, key = { "c_" + it.key }) { (name, count) ->
                    DzChip("$name · $count", selCat == name, onContentFocus) {
                        selCat = if (selCat == name) null else name
                        selCountry = null
                    }
                }
                if (adultCount > 0) item(key = "cat_adult") {
                    DzChip(
                        "${Cats.ADULT} · $adultCount", selCat == Cats.ADULT,
                        onContentFocus
                    ) {
                        selCat = if (selCat == Cats.ADULT) null else Cats.ADULT
                        selCountry = null
                    }
                }
            }
            // country filter row (Live TV only)
            if (showCountries && countries.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp, vertical = 4.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(countries, key = { "co_" + it.key }) { (name, count) ->
                        DzChip(
                            "$name · $count", selCountry == name,
                            onContentFocus
                        ) {
                            selCountry =
                                if (selCountry == name) null else name
                            selCat = null
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = Dim, fontSize = 15.sp)
            }
        } else if (ViewMode == "list") {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.url + it.name }) { ch ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .tvFocus(corner = 10.dp)
                            .focusable()
                            .clickable { onPlay(ch) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                                .background(Card),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ch.logo.isNotEmpty())
                                DzImage(
                                    ch.logo,
                                    Modifier.fillMaxSize().padding(6.dp),
                                    fit = true
                                )
                            else Text(
                                ch.name.take(2).uppercase(), color = Dim,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.name, color = Ink, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (live) ch.getCountry()
                                    .ifEmpty { ch.getSmartCat() }
                                else ch.getSmartCat(),
                                color = Dim, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "›", color = Accent, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (DzTv.isTv) 170.dp else 118.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.url + it.name }) { ch ->
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (live) Modifier.height(120.dp)
                                    else Modifier.fillMaxWidth()
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(Card)
                                .tvFocus(
                                    corner = 16.dp,
                                    onFocused = { if (it) onContentFocus() }
                                )
                                .focusable()
                                .clickable { onPlay(ch) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!live) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(168.dp)
                                ) {
                                    if (ch.logo.isNotEmpty())
                                        DzImage(ch.logo, Modifier.fillMaxSize())
                                    else Text(
                                        ch.name.take(2).uppercase(), color = Dim,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            } else {
                                if (ch.logo.isNotEmpty())
                                    DzImage(
                                        ch.logo,
                                        Modifier.fillMaxSize().padding(16.dp),
                                        fit = true
                                    )
                                else Text(
                                    ch.name.take(2).uppercase(), color = Dim,
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            ch.name, color = Ink, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
