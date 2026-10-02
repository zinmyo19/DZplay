package com.zynelabs.dzplay

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.zynelabs.dzplay.data.Channel

/**
 * 2x2 multiview (ZyneLabs-style): pick a channel per tile, tap a tile to
 * make it audible (others muted), ✕ clears, ⛶ opens fullscreen.
 */
@Composable
fun MultiviewScreen(
    channels: List<Channel>,
    onBack: () -> Unit,
    onFullscreen: (Channel) -> Unit
) {
    val ctx = LocalContext.current
    val slots = remember { List(4) { mutableStateOf<Channel?>(null) } }
    var audible by remember { mutableIntStateOf(0) }
    var pickerFor by remember { mutableIntStateOf(-1) }
    val live = remember(channels) { channels.filter { it.kind == Channel.LIVE } }

    ScreenScaffold(title = "Multiview", onBack = onBack) {
        Text(
            "Tap a tile for sound — only one tile is audible at a time.",
            color = Dim, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tileW = (maxWidth - 8.dp) / 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 2) {
                            val i = row * 2 + col
                            MultiTile(
                                ch = slots[i].value,
                                audible = audible == i && slots[i].value != null,
                                onPick = { pickerFor = i },
                                onTap = { audible = i },
                                onClear = { slots[i].value = null },
                                onFullscreen = { c -> onFullscreen(c) },
                                modifier = Modifier.width(tileW).aspectRatio(16f / 9f)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tip: pick the same channel twice to compare streams.",
            color = Dim, fontSize = 12.sp
        )
    }

    if (pickerFor >= 0) {
        ChannelPickerDialog(
            channels = live,
            onPick = { ch ->
                slots[pickerFor].value = ch
                pickerFor = -1
            },
            onDismiss = { pickerFor = -1 }
        )
    }
}

@Composable
private fun MultiTile(
    ch: Channel?,
    audible: Boolean,
    onPick: () -> Unit,
    onTap: () -> Unit,
    onClear: () -> Unit,
    onFullscreen: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .clickable { if (ch != null) onTap() else onPick() }
    ) {
        val c = ch
        if (c == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+ Pick channel", color = Accent, fontSize = 13.sp)
            }
        } else {
            val player = remember(c) {
                ExoPlayer.Builder(ctx).build().apply {
                    setMediaItem(MediaItem.fromUri(c.url))
                    prepare()
                    play()
                }
            }
            DisposableEffect(c) {
                onDispose { try { player.release() } catch (_: Exception) {} }
            }
            LaunchedEffect(audible) {
                try { player.setVolume(if (audible) 1f else 0f) } catch (_: Exception) {}
            }
            AndroidView(
                factory = { v -> SurfaceView(v).also { player.setVideoSurfaceView(it) } },
                modifier = Modifier.fillMaxSize()
            )
            // top overlay: name + actions
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (audible) Text("♪ ", color = Accent, fontSize = 12.sp)
                Text(
                    c.name, color = Color.White, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "⛶", color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.clickable { onFullscreen(c) }
                        .padding(horizontal = 6.dp)
                )
                Text(
                    "✕", color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.clickable { onClear() }
                        .padding(horizontal = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelPickerDialog(
    channels: List<Channel>,
    onPick: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, channels) {
        val query = q.trim().lowercase()
        if (query.isEmpty()) channels.take(200)
        else channels.filter { it.name.lowercase().contains(query) }.take(200)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick channel", color = Ink, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Search", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink, unfocusedTextColor = Ink,
                        focusedBorderColor = Accent, unfocusedBorderColor = Dim,
                        focusedLabelColor = Accent, unfocusedLabelColor = Dim,
                        cursorColor = Accent
                    )
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(shown, key = { it.key }) { ch ->
                        Text(
                            ch.name, color = Ink, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onPick(ch) }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Dim) }
        },
        containerColor = PanelBg
    )
}
