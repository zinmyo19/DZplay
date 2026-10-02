package com.zynelabs.dzplay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zynelabs.dzplay.data.Channel
import com.zynelabs.dzplay.data.ChannelRepo
import com.zynelabs.dzplay.data.EpgCache
import com.zynelabs.dzplay.data.ImageLoader
import com.zynelabs.dzplay.data.PlAccount
import com.zynelabs.dzplay.data.PlaylistCache
import com.zynelabs.dzplay.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        toast(ctx, "Could not open link")
    }
}

// ---------- shared scaffolding ----------

@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // Own backdrop: the bg image stays visible, but a dark veil hides the
    // screen underneath so titles and rows never double up / overlap.
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Box(Modifier.fillMaxSize().background(Color(0x660B1220)))
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(CardSel)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                content = content
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text, color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Dim, fontSize = 13.sp)
            }
        }
        if (badge.isNotEmpty()) Text(badge, color = Accent, fontSize = 13.sp)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation =
            if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink, unfocusedTextColor = Ink,
            focusedBorderColor = Accent, unfocusedBorderColor = Dim,
            focusedLabelColor = Accent, unfocusedLabelColor = Dim,
            cursorColor = Accent
        )
    )
}

@Composable
fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Accent else CardSel)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = if (selected) Bg else Ink
        )
    }
}

fun typeLabel(t: String) = when (t) {
    "xtream" -> "Xtream"
    "m3u_url" -> "M3U link"
    "m3u_file" -> "M3U file"
    "stalker" -> "Stalker"
    else -> t
}

fun normServer(s: String): String {
    var u = s.trim()
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
    return u.trimEnd('/')
}

// ================= PLAYLISTS / ACCOUNTS =================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(listOf<PlAccount>()) }
    var activeId by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlAccount?>(null) }
    var renaming by remember { mutableStateOf<PlAccount?>(null) }
    var propsFor by remember { mutableStateOf<PlAccount?>(null) }
    var menuFor by remember { mutableStateOf<PlAccount?>(null) }
    var confirmDelete by remember { mutableStateOf<PlAccount?>(null) }
    var reloadingId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        val s = Store(ctx)
        val list = s.accounts()
        accounts = list
        val aid = s.activeAccountId()
        activeId = if (list.any { it.id == aid }) aid else list.firstOrNull()?.id ?: ""
    }

    fun switchTo(acc: PlAccount) {
        scope.launch(Dispatchers.IO) {
            try { Store(ctx).setActiveAccountId(acc.id) } catch (_: Exception) {}
            withContext(Dispatchers.Main) { refresh(); onChanged() }
        }
    }

    fun toggleEnabled(acc: PlAccount) {
        val disabling = acc.enabled
        scope.launch(Dispatchers.IO) {
            try {
                val s = Store(ctx)
                s.setAccountEnabled(acc.id, !disabling)
                if (disabling && s.activeAccountId() == acc.id) {
                    val next = s.enabledAccounts().firstOrNull()?.id ?: ""
                    s.setActiveAccountId(next)
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) { refresh(); onChanged() }
        }
    }

    fun reloadData(acc: PlAccount) {
        reloadingId = acc.id
        ChannelRepo.load(ctx, acc, object : ChannelRepo.Callback {
            override fun onResult(
                live: List<Channel>, vod: List<Channel>,
                series: List<Channel>, err: String?
            ) {
                scope.launch(Dispatchers.IO) {
                    if (err == null) {
                        try {
                            PlaylistCache.save(ctx.filesDir, acc.id, live, vod, series)
                        } catch (_: Exception) {}
                    }
                    withContext(Dispatchers.Main) {
                        reloadingId = null
                        toast(
                            ctx,
                            if (err != null) "Reload failed: $err"
                            else "Reloaded ${live.size + vod.size + series.size} items"
                        )
                        if (err == null && acc.id == activeId) onChanged()
                    }
                }
            }
        })
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenScaffold(title = "Playlists", onBack = onBack) {
        if (accounts.isEmpty()) {
            Text(
                "No providers yet. Add your first playlist below.",
                color = Dim, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        accounts.forEachIndexed { index, acc ->
            val isActive = acc.id == activeId
            val isReloading = reloadingId == acc.id
            // slim OTT-style row (no cards)
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .tvFocus(corner = 8.dp)
                    .focusable()
                    .combinedClickable(
                        onClick = { menuFor = acc },
                        onLongClick = { menuFor = acc }
                    )
                    .alpha(if (acc.enabled) 1f else 0.45f)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // enabled check
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .border(
                            2.dp, if (acc.enabled) Accent else Dim, CircleShape
                        )
                        .background(
                            if (acc.enabled) Accent.copy(alpha = 0.25f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (acc.enabled) Text(
                        "✓", color = Accent, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        acc.name.ifEmpty { "Playlist" },
                        color = Ink, fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildString {
                            append(typeLabel(acc.type))
                            if (isActive) append(" · active")
                            if (!acc.enabled) append(" · disabled")
                        },
                        color = if (isActive) Accent else Dim, fontSize = 12.sp
                    )
                }
                if (isReloading) {
                    CircularProgressIndicator(
                        color = Accent,
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                    )
                }
            }
            if (index < accounts.size - 1) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .height(1.dp).background(Color(0xFF1E1E26))
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Accent)
                .tvFocus(corner = 12.dp)
                .focusable()
                .clickable { adding = true }.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+ Add provider", color = Bg, fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap a provider for options",
            color = Dim, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    // ===== OTT-style provider menu (tap the row) =====
    val mf = menuFor
    if (mf != null) {
        val mfActive = mf.id == activeId
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = {
                Text(
                    mf.name.ifEmpty { "Playlist" }, color = Ink,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OttMenuRow("✎", "Edit", autoFocus = true) {
                        menuFor = null; editing = mf
                    }
                    OttMenuRow("ℹ", "Provider properties") {
                        menuFor = null; propsFor = mf
                    }
                    OttMenuRow("✏", "Rename") { menuFor = null; renaming = mf }
                    if (!mfActive && mf.enabled) OttMenuRow("▶", "Set as active") {
                        menuFor = null; switchTo(mf)
                    }
                    OttMenuRow("⟳", "Reload data") { menuFor = null; reloadData(mf) }
                    OttMenuRow(
                        if (mf.enabled) "⊘" else "✓",
                        if (mf.enabled) "Disable" else "Enable"
                    ) { menuFor = null; toggleEnabled(mf) }
                    OttMenuRow("🗑", "Delete", danger = true) {
                        menuFor = null; confirmDelete = mf
                    }
                }
            },
            confirmButton = {},
            containerColor = Card
        )
    }

    // rename dialog
    val rn = renaming
    if (rn != null) {
        var newName by remember(rn.id) { mutableStateOf(rn.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename", color = Ink) },
            text = { Field(newName, { newName = it }, "Provider name") },
            confirmButton = {
                TextButton(onClick = {
                    renaming = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val s = Store(ctx)
                            val a = s.account(rn.id)
                            if (a != null) {
                                a.name = newName.trim()
                                s.updateAccount(a)
                            }
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) { refresh(); onChanged() }
                    }
                }) { Text("Save", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) {
                    Text("Cancel", color = Dim)
                }
            },
            containerColor = Card
        )
    }

    // provider properties dialog
    val pf = propsFor
    if (pf != null) {
        ProviderPropertiesDialog(acc = pf, onDismiss = { propsFor = null })
    }

    val del = confirmDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete playlist?", color = Ink) },
            text = {
                Text(
                    "\"${del.name.ifEmpty { "Playlist" }}\" and its cached channels will be removed.",
                    color = Dim, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val s = Store(ctx)
                            s.removeAccount(del.id)
                            PlaylistCache.clear(ctx.filesDir, del.id)
                            if (s.activeAccountId() == del.id) {
                                val next =
                                    s.enabledAccounts().firstOrNull()?.id ?: ""
                                s.setActiveAccountId(next)
                            }
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) { refresh(); onChanged() }
                    }
                }) { Text("Delete", color = Color(0xFFF87171)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Cancel", color = Dim)
                }
            },
            containerColor = Card
        )
    }

    val ed = editing
    if (ed != null) {
        EditAccountDialog(
            acc = ed,
            onDismiss = { editing = null },
            onSaved = { editing = null; refresh(); onChanged() }
        )
    }

    if (adding) {
        AddAccountDialog(
            onDismiss = { adding = false },
            onQr = { adding = false; showQr = true },
            onAdded = { adding = false; refresh(); onChanged() }
        )
    }
    if (showQr) {
        QrSetupScreen(
            onBack = { showQr = false },
            onAdded = { showQr = false; refresh(); onChanged() }
        )
    }
}

/** One row inside the OTT-style provider menu — DZplay dark theme. */
@Composable
private fun OttMenuRow(
    icon: String,
    label: String,
    danger: Boolean = false,
    autoFocus: Boolean = false,
    onClick: () -> Unit
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            try { fr.requestFocus() } catch (_: Exception) {}
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .focusRequester(fr)
            .tvFocus(corner = 8.dp)
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            icon, color = if (danger) Color(0xFFF87171) else Accent,
            fontSize = 16.sp, modifier = Modifier.width(32.dp)
        )
        Text(
            label, color = if (danger) Color(0xFFF87171) else Ink,
            fontSize = 15.sp
        )
    }
}

/** Read-only provider details: credentials summary, expiry, cached counts. */
@Composable
private fun ProviderPropertiesDialog(acc: PlAccount, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var counts by remember(acc.id) { mutableStateOf<PlaylistCache.Counts?>(null) }
    LaunchedEffect(acc.id) {
        counts = withContext(Dispatchers.IO) {
            try { PlaylistCache.readCounts(ctx.filesDir, acc.id) }
            catch (_: Exception) { null }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                acc.name.ifEmpty { "Playlist" }, color = Ink,
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                PropRow("Type", typeLabel(acc.type))
                when (acc.type) {
                    "xtream" -> {
                        PropRow("Server", acc.server)
                        PropRow("Username", acc.user)
                        PropRow("Password", "••••••••")
                        if (acc.expDate > 0) PropRow("Expires", fmtExpDate(acc.expDate))
                    }
                    "m3u_url" -> PropRow("Playlist URL", acc.url)
                    "m3u_file" -> PropRow("File", acc.file)
                    "stalker" -> {
                        PropRow("Portal", acc.url)
                        PropRow("MAC", acc.mac)
                    }
                }
                val c = counts
                if (c != null) {
                    PropRow(
                        "Channels",
                        "${c.live} live · ${c.vod} movies · ${c.series} series"
                    )
                    PropRow("Last cached", PlaylistCache.ago(c.savedAt))
                } else {
                    PropRow("Channels", "not cached yet")
                }
                PropRow("Status", if (acc.enabled) "Enabled" else "Disabled")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Accent) }
        },
        containerColor = Card
    )
}

@Composable
private fun PropRow(label: String, value: String) {
    Column {
        Text(label, color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value.ifEmpty { "—" }, color = Ink, fontSize = 14.sp)
    }
}

private fun fmtExpDate(epochSec: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        sdf.format(java.util.Date(epochSec * 1000))
    } catch (_: Exception) { "—" }
}

@Composable
fun EditAccountDialog(
    acc: PlAccount,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(acc.id) { mutableStateOf(acc.name) }
    var server by remember(acc.id) { mutableStateOf(acc.server) }
    var user by remember(acc.id) { mutableStateOf(acc.user) }
    var pass by remember(acc.id) { mutableStateOf(acc.pass) }
    var url by remember(acc.id) { mutableStateOf(acc.url) }
    var mac by remember(acc.id) { mutableStateOf(acc.mac) }
    var fileName by remember(acc.id) { mutableStateOf(acc.file.ifEmpty { null }) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val dest = "pl_${System.currentTimeMillis()}.m3u"
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    ctx.openFileOutput(dest, Context.MODE_PRIVATE).use { outs ->
                        ins.copyTo(outs)
                    }
                }
                fileName = dest
                error = null
            } catch (_: Exception) {
                error = "Could not read that file"
            }
        }
    }

    fun save() {
        val a = PlAccount()
        a.id = acc.id
        a.type = acc.type
        when (acc.type) {
            "xtream" -> {
                if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                    error = "Server, username and password are required"; return
                }
                a.server = normServer(server); a.user = user.trim(); a.pass = pass
            }
            "m3u_url" -> {
                if (url.isBlank()) { error = "Playlist URL is required"; return }
                a.url = url.trim()
            }
            "m3u_file" -> {
                val f = fileName
                if (f == null) { error = "Pick an M3U file first"; return }
                a.file = f
            }
            "stalker" -> {
                if (url.isBlank() || mac.isBlank()) {
                    error = "Portal URL and MAC are required"; return
                }
                a.url = normServer(url); a.mac = mac.trim().uppercase()
            }
        }
        a.name = name.trim().ifEmpty {
            when (acc.type) {
                "xtream" -> a.user
                "stalker" -> "Stalker"
                else -> "Playlist"
            }
        }
        saving = true
        error = null
        scope.launch(Dispatchers.IO) {
            try {
                val s = Store(ctx)
                s.updateAccount(a)
                // credentials may have changed — drop the stale cache
                PlaylistCache.clear(ctx.filesDir, a.id)
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                saving = false
                toast(ctx, "Provider updated")
                onSaved()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit provider", color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    typeLabel(acc.type), color = Accent, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Field(name, { name = it }, "Name")
                when (acc.type) {
                    "xtream" -> {
                        Field(server, { server = it }, "Server URL  (http://host:port)")
                        Field(user, { user = it }, "Username")
                        Field(pass, { pass = it }, "Password", password = true)
                    }
                    "m3u_url" -> Field(url, { url = it }, "Playlist URL")
                    "m3u_file" -> {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(CardSel).clickable { pickFile.launch("*/*") }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                fileName ?: "Pick M3U file",
                                color = if (fileName != null) Accent else Dim,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    "stalker" -> {
                        Field(url, { url = it }, "Portal URL  (http://host:port/c/)")
                        Field(mac, { mac = it }, "MAC  (00:1A:79:..:..:..)")
                    }
                }
                if (error != null) {
                    Text(error!!, color = Color(0xFFF87171), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!saving) save() }) {
                Text(if (saving) "Saving…" else "Save", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) {
                Text("Cancel", color = Dim)
            }
        },
        containerColor = Card
    )
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onQr: () -> Unit = {},
    onAdded: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("xtream") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var portal by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val dest = "pl_${System.currentTimeMillis()}.m3u"
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    ctx.openFileOutput(dest, Context.MODE_PRIVATE).use { outs ->
                        ins.copyTo(outs)
                    }
                }
                fileName = dest
                error = null
            } catch (_: Exception) {
                error = "Could not read that file"
            }
        }
    }

    fun save() {
        val acc = PlAccount()
        acc.id = UUID.randomUUID().toString()
        acc.type = type
        when (type) {
            "xtream" -> {
                if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                    error = "Server, username and password are required"; return
                }
                acc.server = normServer(server); acc.user = user.trim(); acc.pass = pass
            }
            "m3u_url" -> {
                if (url.isBlank()) { error = "Playlist URL is required"; return }
                acc.url = url.trim()
            }
            "m3u_file" -> {
                val f = fileName
                if (f == null) { error = "Pick an M3U file first"; return }
                acc.file = f
            }
            "stalker" -> {
                if (portal.isBlank() || mac.isBlank()) {
                    error = "Portal URL and MAC are required"; return
                }
                acc.url = normServer(portal); acc.mac = mac.trim().uppercase()
            }
        }
        acc.name = name.trim().ifEmpty {
            when (type) {
                "xtream" -> acc.user
                "stalker" -> "Stalker"
                else -> "Playlist"
            }
        }
        testing = true
        error = null
        ChannelRepo.load(ctx, acc, object : ChannelRepo.Callback {
            override fun onResult(
                live: List<com.zynelabs.dzplay.data.Channel>,
                vod: List<com.zynelabs.dzplay.data.Channel>,
                series: List<com.zynelabs.dzplay.data.Channel>,
                err: String?
            ) {
                if (err != null) {
                    testing = false
                    error = err
                    return
                }
                if (live.isEmpty() && vod.isEmpty()) {
                    testing = false
                    error = "Connected, but no channels were found"
                    return
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        val s = Store(ctx)
                        s.addAccount(acc)
                        if (s.activeAccountId().isEmpty()) s.setActiveAccountId(acc.id)
                        PlaylistCache.save(ctx.filesDir, acc.id, live, vod, series)
                    } catch (_: Exception) {}
                    withContext(Dispatchers.Main) {
                        testing = false
                        toast(ctx, "Playlist added")
                        onAdded()
                    }
                }
            }
        })
    }

    AlertDialog(
        onDismissRequest = { if (!testing) onDismiss() },
        title = { Text("Add provider", color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip("Xtream", type == "xtream") { type = "xtream" }
                    TypeChip("M3U link", type == "m3u_url") { type = "m3u_url" }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip("M3U file", type == "m3u_file") { type = "m3u_file" }
                    TypeChip("Stalker", type == "stalker") { type = "stalker" }
                }
                Spacer(Modifier.height(12.dp))
                Field(name, { name = it }, "Name (optional)")
                when (type) {
                    "xtream" -> {
                        Field(server, { server = it }, "Server URL  (http://host:port)")
                        Field(user, { user = it }, "Username")
                        Field(pass, { pass = it }, "Password", password = true)
                    }
                    "m3u_url" -> Field(url, { url = it }, "Playlist URL")
                    "m3u_file" -> {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(CardSel).clickable { pickFile.launch("*/*") }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                fileName ?: "Pick M3U file",
                                color = if (fileName != null) Accent else Dim, fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    "stalker" -> {
                        Field(portal, { portal = it }, "Portal URL  (http://host:port/c/)")
                        Field(mac, { mac = it }, "MAC  (00:1A:79:..:..:..)")
                    }
                }
                if (testing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Accent, modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Testing connection…", color = Dim, fontSize = 13.sp)
                    }
                }
                val e = error
                if (e != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(e, color = androidx.compose.ui.graphics.Color(0xFFF87171), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { save() }, enabled = !testing,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Save", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { if (!testing) onQr() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("QR code", color = Accent, fontSize = 13.sp)
                }
                TextButton(
                    onClick = { if (!testing) onDismiss() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Cancel", color = Dim, fontSize = 13.sp)
                }
            }
        },
        containerColor = PanelBg
    )
}

private const val SYNC_BASE = "https://zyne-iptv-sync.zynelabs.workers.dev"

/** Render a QR code bitmap with ZXing. */
fun qrBitmap(text: String, size: Int): android.graphics.Bitmap? {
    return try {
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, size, size
        )
        val bmp = android.graphics.Bitmap.createBitmap(
            size, size, android.graphics.Bitmap.Config.RGB_565
        )
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(
                x, y,
                if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            )
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

fun httpGet(url: String): String? {
    return try {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val code = c.responseCode
        val txt = if (code == 200) c.inputStream.readBytes().toString(Charsets.UTF_8) else null
        c.disconnect()
        txt
    } catch (_: Exception) {
        null
    }
}

/**
 * OTT-style setup: shows a QR code + 6-digit code. Scan it with another
 * device, fill the playlist on the web page and Save — this screen polls
 * the sync worker and imports the account automatically.
 */
@Composable
fun QrSetupScreen(onBack: () -> Unit, onAdded: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val code = remember { String.format("%06d", java.util.Random().nextInt(1000000)) }
    val link = "$SYNC_BASE/add?code=$code"
    val qr = remember { qrBitmap(link, 560) }
    var status by remember { mutableStateOf("Waiting for your other device…") }
    var done by remember { mutableStateOf(false) }

    /** Import a payload JSON from the worker, then validate + save. */
    fun importPayload(body: String) {
        try {
            val o = JSONObject(body)
            val acc = PlAccount()
            acc.id = UUID.randomUUID().toString()
            acc.name = o.optString("name", "Playlist").ifEmpty { "Playlist" }
            val type = o.optString("type", "m3u")
            val url = o.optString("url", "").trim()
            if ("xtream" == type) {
                acc.type = "xtream"
                acc.server = url
                acc.user = o.optString("username", "")
                acc.pass = o.optString("password", "")
            } else if ("stalker" == type) {
                val mac = o.optString("mac", "").trim().uppercase()
                if (!com.zynelabs.dzplay.data.StalkerClient.validMac(mac)) {
                    status = "MAC looks wrong — try again"
                    return
                }
                acc.type = "stalker"
                acc.url = com.zynelabs.dzplay.data.StalkerClient.normPortal(url)
                acc.mac = mac
            } else {
                acc.type = "m3u_url"
                acc.url = url
            }
            val check = if ("xtream" == acc.type) acc.server else acc.url
            if (check.isNullOrEmpty()) {
                status = "Empty link — try again"
                return
            }
            // single-use: delete the code on the worker
            scope.launch(Dispatchers.IO) {
                try {
                    val c = java.net.URL("$SYNC_BASE/api/consume")
                        .openConnection() as java.net.HttpURLConnection
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.connectTimeout = 8000
                    c.outputStream.write("{\"code\":\"$code\"}".toByteArray(Charsets.UTF_8))
                    c.responseCode
                    c.disconnect()
                } catch (_: Exception) {}
            }
            status = "Playlist received — testing connection…"
            ChannelRepo.load(ctx, acc, object : ChannelRepo.Callback {
                override fun onResult(
                    live: List<com.zynelabs.dzplay.data.Channel>,
                    vod: List<com.zynelabs.dzplay.data.Channel>,
                    series: List<com.zynelabs.dzplay.data.Channel>,
                    err: String?
                ) {
                    if (err != null || (live.isEmpty() && vod.isEmpty())) {
                        status = err ?: "No channels found — try again"
                        return
                    }
                    scope.launch(Dispatchers.IO) {
                        try {
                            val s = Store(ctx)
                            s.addAccount(acc)
                            if (s.activeAccountId().isEmpty()) s.setActiveAccountId(acc.id)
                            PlaylistCache.save(ctx.filesDir, acc.id, live, vod, series)
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) {
                            done = true
                            toast(ctx, "Playlist added")
                            onAdded()
                        }
                    }
                }
            })
        } catch (_: Exception) {
            status = "Bad response — try again"
        }
    }

    LaunchedEffect(code) {
        val start = System.currentTimeMillis()
        while (!done && System.currentTimeMillis() - start < 5 * 60 * 1000L) {
            kotlinx.coroutines.delay(3000)
            val body = withContext(Dispatchers.IO) {
                httpGet("$SYNC_BASE/api/fetch?code=$code")
            }
            if (body != null) {
                importPayload(body)
                return@LaunchedEffect
            }
        }
        if (!done) status = "Code expired — go back and try again."
    }

    ScreenScaffold(title = "Add from QR", onBack = onBack) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            val bmp = qr
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "QR code",
                    modifier = Modifier.size(230.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                code.substring(0, 3) + " " + code.substring(3),
                color = Accent, fontSize = 40.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "1. Scan the QR with your other device\n" +
                    "2. Enter the code above on the page\n" +
                    "3. Fill in your playlist / Xtream / MAC and Save\n" +
                    "The playlist appears here automatically.",
                color = Dim, fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(status, color = Accent, fontSize = 14.sp,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ================= SETTINGS =================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiniBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(BtnBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BtnFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hideAdult by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var pinMode by remember { mutableStateOf(0) } // 0 none, 1 set new, 2 manage
    var confirmClearFavs by remember { mutableStateOf(false) }
    var showBg by remember { mutableStateOf(false) }

    fun refreshToggles() {
        val s = Store(ctx)
        hideAdult = s.hideAdult()
        hasPin = s.hasParentalPin()
    }
    LaunchedEffect(Unit) { refreshToggles() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val ok = writeBackup(ctx, uri)
            withContext(Dispatchers.Main) {
                toast(ctx, if (ok) "Backup saved" else "Backup failed")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val msg = readBackup(ctx, uri)
            withContext(Dispatchers.Main) {
                toast(ctx, msg)
                refreshToggles()
            }
        }
    }

    ScreenScaffold(title = "Settings", onBack = onBack) {
        SectionHeader("APPEARANCE")
        BtnFlow { MiniBtn("Background") { showBg = true } }

        SectionHeader("PLAYLISTS")
        BtnFlow { MiniBtn("Providers") { onOpenAccounts() } }

        SectionHeader("DATA")
        BtnFlow {
            MiniBtn("Backup") { exportLauncher.launch("zynelabs-iptv-backup.json") }
            MiniBtn("Restore") { importLauncher.launch("*/*") }
            MiniBtn("Clear cache") {
                ImageLoader.clear()
                toast(ctx, "Image cache cleared")
            }
            MiniBtn("Clear favorites") { confirmClearFavs = true }
        }

        SectionHeader("PARENTAL")
        BtnFlow {
            MiniBtn(if (hasPin) "PIN: set" else "Set PIN") {
                pinMode = if (hasPin) 2 else 1
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Hide adult channels", color = Ink, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
            )
            Switch(
                checked = hideAdult,
                onCheckedChange = {
                    if (!Store(ctx).hasParentalPin()) {
                        toast(ctx, "Set a parental PIN first")
                        return@Switch
                    }
                    Store(ctx).setHideAdult(it)
                    hideAdult = it
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.4f)
                )
            )
        }

        SectionHeader("ABOUT")
        BtnFlow { MiniBtn("About") { onOpenAbout() } }
    }

    if (showBg) BackgroundDialog(onDismiss = { showBg = false })

    if (pinMode == 1) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pinMode = 0 },
            title = { Text("Set parental PIN", color = Ink) },
            text = {
                Column {
                    Field(p1, { p1 = it.filter { c -> c.isDigit() }.take(8) },
                        "New PIN (min 4 digits)", password = true)
                    Field(p2, { p2 = it.filter { c -> c.isDigit() }.take(8) },
                        "Confirm PIN", password = true)
                    val e = err
                    if (e != null) Text(e,
                        color = androidx.compose.ui.graphics.Color(0xFFF87171), fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        p1.length < 4 -> err = "PIN needs at least 4 digits"
                        p1 != p2 -> err = "PINs do not match"
                        else -> {
                            Store(ctx).setParentalPin(p1)
                            pinMode = 0
                            refreshToggles()
                            toast(ctx, "Parental PIN set")
                        }
                    }
                }) { Text("Save", color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pinMode = 0 }) { Text("Cancel", color = Dim) }
            },
            containerColor = Card
        )
    }

    if (pinMode == 2) {
        var cur by remember { mutableStateOf("") }
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pinMode = 0 },
            title = { Text("Parental control", color = Ink) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Field(cur, { cur = it.filter { c -> c.isDigit() }.take(8) },
                        "Current PIN", password = true)
                    Field(p1, { p1 = it.filter { c -> c.isDigit() }.take(8) },
                        "New PIN (leave empty to keep)", password = true)
                    Field(p2, { p2 = it.filter { c -> c.isDigit() }.take(8) },
                        "Confirm new PIN", password = true)
                    val e = err
                    if (e != null) Text(e,
                        color = androidx.compose.ui.graphics.Color(0xFFF87171), fontSize = 13.sp)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val s = Store(ctx)
                        if (!s.checkParentalPin(cur)) { err = "Wrong PIN"; return@TextButton }
                        s.setParentalPin("") // remove
                        s.setHideAdult(false)
                        pinMode = 0
                        refreshToggles()
                        toast(ctx, "Parental control removed")
                    }) { Text("Remove", color = androidx.compose.ui.graphics.Color(0xFFF87171)) }
                    TextButton(onClick = {
                        val s = Store(ctx)
                        if (!s.checkParentalPin(cur)) { err = "Wrong PIN"; return@TextButton }
                        if (p1.isNotEmpty()) {
                            if (p1.length < 4) { err = "PIN needs at least 4 digits"; return@TextButton }
                            if (p1 != p2) { err = "PINs do not match"; return@TextButton }
                            s.setParentalPin(p1)
                        }
                        pinMode = 0
                        refreshToggles()
                        toast(ctx, "Parental PIN updated")
                    }) { Text("Update", color = Accent, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pinMode = 0 }) { Text("Cancel", color = Dim) }
            },
            containerColor = Card
        )
    }

    if (confirmClearFavs) {
        AlertDialog(
            onDismissRequest = { confirmClearFavs = false },
            title = { Text("Clear all favorites?", color = Ink) },
            text = { Text("Every starred channel will be removed.", color = Dim, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearFavs = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            val s = Store(ctx)
                            for (a in s.accounts()) s.clearFavorites(a.id)
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) { toast(ctx, "Favorites cleared") }
                    }
                }) { Text("Clear", color = androidx.compose.ui.graphics.Color(0xFFF87171)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearFavs = false }) {
                    Text("Cancel", color = Dim)
                }
            },
            containerColor = Card
        )
    }
}

/** Write the zynelabs-iptv-backup v2 file. PIN is never exported. */
fun writeBackup(ctx: Context, uri: Uri): Boolean {
    return try {
        val store = Store(ctx)
        val root = JSONObject()
        root.put("format", "zynelabs-iptv-backup")
        root.put("version", 2)
        val arr = JSONArray()
        for (a in store.accounts()) arr.put(a.toJson())
        root.put("accounts", arr)
        val st = JSONObject()
        st.put("theme", store.theme())
        st.put("view_mode", store.viewMode())
        st.put("sort_mode", store.sortMode())
        st.put("buffer_secs", store.bufferSecs())
        st.put("video_scale", store.videoScale().toDouble())
        st.put("hide_adult", store.hideAdult())
        st.put("show_radio_live", store.showRadioInLive())
        root.put("settings", st)
        val favs = JSONObject()
        val hids = JSONObject()
        for (a in store.accounts()) {
            val fa = JSONArray()
            for (k in store.favorites(a.id)) fa.put(k)
            favs.put(a.id, fa)
            val ha = JSONArray()
            for (k in store.hiddenChannels(a.id)) ha.put(k)
            hids.put(a.id, ha)
        }
        root.put("favorites", favs)
        root.put("hidden", hids)
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
        }
        true
    } catch (_: Exception) {
        false
    }
}

/** Read a backup file (v2 object or legacy v1 array). Returns a status message. */
fun readBackup(ctx: Context, uri: Uri): String {
    return try {
        val txt = ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ins.readBytes().toString(Charsets.UTF_8).trim()
        } ?: return "Could not read file"
        val store = Store(ctx)
        var added = 0
        if (txt.startsWith("[")) {
            val arr = JSONArray(txt)
            val cur = store.accounts().toMutableList()
            for (i in 0 until arr.length()) {
                val a = PlAccount.fromJson(arr.getJSONObject(i))
                a.id = UUID.randomUUID().toString()
                cur.add(a)
                added++
            }
            store.saveAccounts(cur)
            return "$added playlist(s) restored"
        }
        val root = JSONObject(txt)
        if (root.optString("format") != "zynelabs-iptv-backup") {
            return "Not a ZyneLabs backup file"
        }
        val idMap = mutableMapOf<String, String>()
        val cur = store.accounts().toMutableList()
        val arr = root.optJSONArray("accounts")
        if (arr != null) for (i in 0 until arr.length()) {
            val a = PlAccount.fromJson(arr.getJSONObject(i))
            val oldId = a.id
            a.id = UUID.randomUUID().toString()
            idMap[oldId] = a.id
            cur.add(a)
            added++
        }
        store.saveAccounts(cur)
        val st = root.optJSONObject("settings")
        if (st != null) {
            store.setTheme(st.optString("theme", store.theme()))
            store.setViewMode(st.optString("view_mode", store.viewMode()))
            store.setSortMode(st.optString("sort_mode", store.sortMode()))
            store.setBufferSecs(st.optInt("buffer_secs", store.bufferSecs()))
            store.setVideoScale(st.optDouble("video_scale", store.videoScale().toDouble()).toFloat())
            store.setHideAdult(st.optBoolean("hide_adult", store.hideAdult()))
            store.setShowRadioInLive(st.optBoolean("show_radio_live", store.showRadioInLive()))
        }
        val favs = root.optJSONObject("favorites")
        if (favs != null) {
            val keys = favs.keys()
            while (keys.hasNext()) {
                val oldId = keys.next()
                val newId = idMap[oldId] ?: continue
                val fa = favs.optJSONArray(oldId) ?: continue
                val set = mutableSetOf<String>()
                for (i in 0 until fa.length()) set.add(fa.optString(i))
                store.setFavorites(newId, set)
            }
        }
        val hids = root.optJSONObject("hidden")
        if (hids != null) {
            val keys = hids.keys()
            while (keys.hasNext()) {
                val oldId = keys.next()
                val newId = idMap[oldId] ?: continue
                val ha = hids.optJSONArray(oldId) ?: continue
                val set = mutableSetOf<String>()
                for (i in 0 until ha.length()) set.add(ha.optString(i))
                store.setHiddenChannels(newId, set)
            }
        }
        "$added playlist(s) + settings restored"
    } catch (_: Exception) {
        "Failed: invalid file"
    }
}

// ================= ABOUT =================

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val version = remember {
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            @Suppress("DEPRECATION")
            (pi.versionName ?: "?").toString()
        } catch (_: Exception) {
            "?"
        }
    }

    ScreenScaffold(title = "About", onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // app badge: navy circle, cyan play triangle
            Canvas(Modifier.size(84.dp)) {
                drawCircle(androidx.compose.ui.graphics.Color(0xFF141419), size.width / 2f)
                drawCircle(
                    Accent,
                    size.width / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(size.width * 0.07f)
                )
                val w = size.width; val h = size.height
                drawPath(Path().apply {
                    moveTo(w * 0.40f, h * 0.32f)
                    lineTo(w * 0.68f, h * 0.5f)
                    lineTo(w * 0.40f, h * 0.68f)
                    close()
                }, Accent)
            }
            Spacer(Modifier.height(12.dp))
            Text("DZplay", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("v$version", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Premium IPTV player for your own playlists.",
                color = Dim, fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        SectionHeader("CONTACT")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
                .padding(vertical = 4.dp)
        ) {
            ContactRow("Telegram Bot", "@Dominic_aiBot", "https://t.me/Dominic_aiBot")
            ContactRow("Telegram Group", "ZyneLabs updates", store.groupLink())
            ContactRow("GitHub", "zinmyo19", "https://github.com/zinmyo19")
            ContactRow(
                "Website", "zynelabs.is-a.dev",
                "https://dzinlabs-site.zynelabs.workers.dev/", last = true
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Made by ZyneLabs",
            color = Dim, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ContactRow(title: String, subtitle: String, url: String, last: Boolean = false) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clickable { openUrl(ctx, url) }.padding(16.dp)
    ) {
        Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Accent, fontSize = 13.sp)
    }
    if (!last) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .height(1.dp).background(CardSel)
        )
    }
}

// ================= MEDIA LIBRARY =================

/**
 * OTT-style media library: the account's VOD catalog as genre folders,
 * tap a folder for its movies, tap a movie to play it.
 */
@Composable
fun MediaLibraryScreen(
    onBack: () -> Unit,
    onPlay: (com.zynelabs.dzplay.data.Channel, List<com.zynelabs.dzplay.data.Channel>, String) -> Unit
) {
    val ctx = LocalContext.current
    var acc by remember { mutableStateOf<PlAccount?>(null) }
    var folders by remember { mutableStateOf(listOf<Pair<String, List<com.zynelabs.dzplay.data.Channel>>>()) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val res = withContext(Dispatchers.IO) {
            val s = Store(ctx)
            val list = s.accounts()
            if (s.switchSel() == "all") {
                val v = list.flatMap {
                    PlaylistCache.load(ctx.filesDir, it.id)?.vod ?: emptyList()
                }
                Triple(null, v, v.size)
            } else {
                val aid = s.switchSel().ifEmpty { s.activeAccountId() }
                val a = list.firstOrNull { it.id == aid } ?: list.firstOrNull()
                val v = a?.let { PlaylistCache.load(ctx.filesDir, it.id)?.vod }
                    ?: emptyList()
                Triple(a, v, v.size)
            }
        }
        acc = res.first
        val vod = res.second
        when {
            res.first == null -> status = "Add a playlist first."
            vod.isEmpty() -> status = "No movies found in this playlist."
            else -> {
                val map = linkedMapOf<String, MutableList<com.zynelabs.dzplay.data.Channel>>()
                for (c in vod) {
                    val f = com.zynelabs.dzplay.data.Cats.normGroup(c.displayGroup())
                        .ifEmpty { "Other" }
                    map.getOrPut(f) { mutableListOf() }.add(c)
                }
                folders = map.entries
                    .sortedBy { it.key.lowercase() }
                    .map { it.key to it.value.toList() }
            }
        }
        loading = false
    }

    val folder = openFolder
    val movies = remember(folder, query, folders) {
        val list = folders.firstOrNull { it.first == folder }?.second ?: emptyList()
        val q = query.trim().lowercase()
        if (q.isEmpty()) list
        else list.filter { it.name.lowercase().contains(q) }
    }

    // back goes folder -> folders -> out
    androidx.activity.compose.BackHandler(enabled = folder != null) { openFolder = null }

    ScreenScaffold(
        title = if (folder != null) folder else "Media Library",
        onBack = { if (folder != null) openFolder = null else onBack() }
    ) {
        when {
            loading -> Box(
                Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }
            status.isNotEmpty() && folders.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { Text(status, color = Dim, fontSize = 14.sp) }
            folder == null -> {
                // 2-column folder grid (chunked rows — no nested scrolling)
                folders.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (name, list) ->
                            Column(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(12.dp)).background(Card)
                                    .clickable { openFolder = name }
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    name, color = Ink, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium, maxLines = 2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${list.size} movie" + if (list.size == 1) "" else "s",
                                    color = Accent, fontSize = 12.sp
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            else -> {
                Field(query, { query = it }, "Search movies…")
                Spacer(Modifier.height(4.dp))
                Column {
                    movies.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onPlay(m, movies, acc?.id ?: "")
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                m.name, color = Ink, fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    if (movies.isEmpty()) {
                        Text("Nothing found.", color = Dim, fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

// ================= SWITCH PROVIDER =================

@Composable
fun RadioDot(selected: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val r = size.width / 2f
        drawCircle(
            color = if (selected) Accent else Dim,
            radius = r,
            style = androidx.compose.ui.graphics.drawscope.Stroke(size.width * 0.12f)
        )
        if (selected) drawCircle(color = Accent, radius = r * 0.45f)
    }
}

@Composable
fun ProviderRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) Accent.copy(alpha = 0.18f) else PanelBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = if (highlight) Ink else Dim, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** OTT-style provider switcher: "All providers" combined or one provider. */
@Composable
fun SwitchProviderScreen(onBack: () -> Unit, onSwitched: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(listOf<PlAccount>()) }
    var counts by remember { mutableStateOf(mapOf<String, Int>()) }
    var sel by remember { mutableStateOf("") }
    var activeId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val s = Store(ctx)
        val list = withContext(Dispatchers.IO) { s.accounts() }
        accounts = list
        sel = s.switchSel()
        activeId = s.activeAccountId().ifEmpty { list.firstOrNull()?.id ?: "" }
        counts = withContext(Dispatchers.IO) {
            list.associate { it.id to (PlaylistCache.load(ctx.filesDir, it.id)?.live?.size ?: 0) }
        }
    }

    fun choose(v: String) {
        scope.launch(Dispatchers.IO) {
            try { Store(ctx).setSwitchSel(v) } catch (_: Exception) {}
            withContext(Dispatchers.Main) { onSwitched() }
        }
    }

    ScreenScaffold(title = "Switch provider", onBack = onBack) {
        val total = counts.values.sum()
        ProviderRow(
            selected = sel == "all",
            title = "All active providers",
            subtitle = "$total Live channels" +
                if (accounts.isNotEmpty())
                    "\n" + accounts.joinToString(" + ") {
                        it.name.ifEmpty { typeLabel(it.type) }
                    }
                else "",
            highlight = true,
            onClick = { choose("all") }
        )
        accounts.forEach { acc ->
            val isSel = if (sel.isEmpty()) acc.id == activeId else sel == acc.id
            ProviderRow(
                selected = isSel,
                title = acc.name.ifEmpty { typeLabel(acc.type) },
                subtitle = "${counts[acc.id] ?: 0} Live channels",
                onClick = { choose(acc.id) }
            )
        }
    }
}

// ================= TV GUIDE (EPG) =================

private val guideTimeFmt =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

private fun guideTimeRange(p: EpgCache.Prog): String =
    guideTimeFmt.format(java.util.Date(p.start)) + "–" +
        guideTimeFmt.format(java.util.Date(p.stop))

/** TV Guide: Now/Next per channel. Programme data comes from the shared
 *  EpgCache, which currently serves Xtream playlists. */
@Composable
fun GuideScreen(
    accountId: String,
    channels: List<Channel>,
    onBack: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var hasEpg by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(accountId) {
        val acc = withContext(Dispatchers.IO) {
            try { Store(ctx).account(accountId) } catch (_: Exception) { null }
        }
        if (acc != null && acc.isXtream()) {
            EpgCache.ensure(ctx, acc, channels) {
                hasEpg = EpgCache.get(accountId).isNotEmpty()
                loading = false
                tick++
            }
        } else {
            loading = false
        }
    }

    ScreenScaffold(title = "TV Guide", onBack = onBack) {
        // read the cache on every recomposition tick
        @Suppress("UNUSED_VARIABLE")
        val t = tick
        when {
            loading -> Box(
                Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }
            !hasEpg -> Text(
                "No programme data.\nThe guide works with Xtream playlists.\n(XMLTV for M3U is coming.)",
                color = Dim, fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
            else -> {
                val live = channels.filter { it.kind == Channel.LIVE }
                live.forEach { ch ->
                    val key = EpgCache.key(ch)
                    val now = EpgCache.now(accountId, key)
                    val next = EpgCache.next(accountId, key)
                    GuideRow(ch, now, next) { onPlay(ch) }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    ch: Channel,
    now: EpgCache.Prog?,
    next: EpgCache.Prog?,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PanelBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(ch.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        if (now != null) {
            Text(
                "Now  ${guideTimeRange(now)}  ${now.title}",
                color = Accent, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (next != null) {
            Text(
                "Next  ${guideTimeRange(next)}  ${next.title}",
                color = Dim, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (now == null && next == null) {
            Text("No programme info", color = Dim, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
}

// ================= BACKGROUND PICKER =================

@Composable
fun BackgroundDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasCustom by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasCustom = withContext(Dispatchers.IO) {
            Store(ctx).bgImagePath().isNotEmpty()
        }
    }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { ins ->
                        ctx.openFileOutput("bg_custom.jpg", Context.MODE_PRIVATE).use { outs ->
                            ins.copyTo(outs)
                        }
                    }
                    Store(ctx).setBgImagePath("bg_custom.jpg")
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        toast(ctx, "Could not use that image")
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    AppBg.tick++
                    toast(ctx, "Background updated")
                    onDismiss()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Background", color = Ink) },
        text = {
            Column {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        try { Store(ctx).setBgImagePath("") } catch (_: Exception) {}
                        withContext(Dispatchers.Main) {
                            AppBg.tick++
                            onDismiss()
                        }
                    }
                }) { Text("Default dark", color = Accent) }
                TextButton(onClick = { pickImage.launch("image/*") }) {
                    Text("Pick image…", color = Accent)
                }
                if (hasCustom) {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                ctx.deleteFile("bg_custom.jpg")
                                Store(ctx).setBgImagePath("")
                            } catch (_: Exception) {}
                            withContext(Dispatchers.Main) {
                                AppBg.tick++
                                onDismiss()
                            }
                        }
                    }) {
                        Text("Remove custom image",
                            color = androidx.compose.ui.graphics.Color(0xFFF87171))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Dim) }
        },
        containerColor = Card
    )
}

// ================= SETTINGS DASHBOARD =================

/** Settings tab root: dashboard -> providers / accent / about. */
@Composable
fun DzSettings(onRefresh: () -> Unit, startAt: String = "main") {
    var sub by remember(startAt) { mutableStateOf(startAt) }
    when (sub) {
        "providers" -> AccountsScreen(
            onBack = { sub = "main" },
            onChanged = { onRefresh() }
        )
        "about" -> AboutScreen(onBack = { sub = "main" })
        else -> SettingsMain(
            onProviders = { sub = "providers" },
            onAbout = { sub = "about" }
        )
    }
}

@Composable
private fun SettingsMain(onProviders: () -> Unit, onAbout: () -> Unit) {
    val ctx = LocalContext.current
    var providerCount by remember { mutableStateOf(0) }
    var showAccent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        providerCount = withContext(Dispatchers.IO) {
            try { Store(ctx).accounts().size } catch (_: Exception) { 0 }
        }
    }
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Box(Modifier.fillMaxSize().background(Color(0x660B1220)))
        Column(Modifier.fillMaxSize()) {
            Text(
                "Settings", color = Ink, fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp)
            ) {
                SettingsSection("PLAYLISTS")
                SettingsRow(
                    "Providers",
                    "$providerCount provider" + if (providerCount == 1) "" else "s",
                    onClick = onProviders
                )
                SettingsSection("APPEARANCE")
                SettingsRow(
                    "Accent color", accentLabel(),
                    onClick = { showAccent = true }
                )
                SettingsSection("ABOUT")
                SettingsRow(
                    "About DZplay", "Version, contact & links",
                    onClick = onAbout
                )
            }
        }
    }
    if (showAccent) {
        AccentPickerDialog(onDismiss = { showAccent = false })
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title, color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .tvFocus(corner = 8.dp)
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Ink, fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Dim, fontSize = 12.sp)
        }
        Text("›", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Live accent picker — the whole app re-themes instantly, no restart. */
@Composable
private fun AccentPickerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accent color", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ACCENTS.forEach { (key, labeled) ->
                    val (label, color) = labeled
                    val selected = AccentName == key
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable {
                                AccentName = key
                                scope.launch(Dispatchers.IO) {
                                    try { Store(ctx).setAccent(key) }
                                    catch (_: Exception) {}
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(color)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label, color = Ink, fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) Text(
                            "✓", color = Accent, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Dim) }
        },
        containerColor = Card
    )
}
