package com.hmp.horizon

import android.Manifest
import android.content.ContentUris
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

private val Night = Color(0xFF090C14)
private val Card = Color(0xFF171D2C)
private val Blue = Color(0xFF8AA7FF)
private val Purple = Color(0xFFB19AFF)
private val Muted = Color(0xFF9AA5BC)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, HorizonPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({ try { controller = controllerFuture?.get() } catch (_: Exception) {} }, ContextCompat.getMainExecutor(this))
        setContent { HmpScreen() }
    }

    private fun permissionName() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    private fun hasPermission() = ContextCompat.checkSelfPermission(this, permissionName()) == PackageManager.PERMISSION_GRANTED

    private fun scan(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        val result = mutableListOf<Song>()
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result += Song(id, c.getString(titleCol) ?: "Unknown title", c.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Unknown artist", c.getString(albumCol) ?: "Unknown album", c.getLong(durationCol), ContentUris.withAppendedId(collection, id))
            }
        }
        return result
    }

    @Composable
    private fun HmpScreen() {
        var songs by remember { mutableStateOf(emptyList<Song>()) }
        var query by remember { mutableStateOf("") }
        var currentId by remember { mutableLongStateOf(-1L) }
        var playing by remember { mutableStateOf(false) }
        var shuffle by remember { mutableStateOf(false) }
        var fade by remember { mutableFloatStateOf(5f) }
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        var granted by remember { mutableStateOf(hasPermission()) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; if (ok) songs = scan() }

        LaunchedEffect(Unit) { if (granted) songs = scan() else permission.launch(permissionName()) }
        DisposableEffect(controller) {
            val p = controller
            if (p == null) {
                onDispose { }
            } else {
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { currentId = mediaItem?.mediaId?.toLongOrNull() ?: -1L; duration = p.duration.coerceAtLeast(0L) }
                }
                p.addListener(listener)
                onDispose { p.removeListener(listener) }
            }
        }
        LaunchedEffect(playing, currentId) {
            while (true) {
                controller?.let { position = it.currentPosition.coerceAtLeast(0L); if (it.duration > 0) duration = it.duration }
                kotlinx.coroutines.delay(400)
            }
        }

        val filtered = songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
        val now = songs.firstOrNull { it.id == currentId }
        MaterialTheme(colorScheme = darkColorScheme(primary = Blue, secondary = Purple, background = Night, surface = Card)) {
            Surface(Modifier.fillMaxSize(), color = Night) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("HORIZON", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Text("M U S I C   P L A Y E R", color = Blue, fontSize = 9.sp, letterSpacing = 2.sp)
                        }
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Blue, Purple))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GraphicEq, null, tint = Night, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Your sound, beyond the horizon.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(17.dp), placeholder = { Text("Search songs or artists") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${filtered.size} TRACKS", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { if (granted) songs = scan() else permission.launch(permissionName()) }) { Icon(Icons.Default.Refresh, "Rescan", tint = Blue) }
                    }
                    if (!granted || filtered.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(20.dp)).background(Card).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LibraryMusic, null, tint = Blue, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(if (!granted) "Allow audio access to scan your device." else "No songs found. Add music and rescan.", color = Muted)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(filtered, key = { it.id }) { song ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(if (song.id == currentId) Color(0xFF202B43) else Color.Transparent).clickable {
                                    controller?.let { p ->
                                        val list = (if (shuffle) filtered.shuffled() else filtered).map { it.toMediaItem() }
                                        p.setMediaItems(list, list.indexOfFirst { it.mediaId == song.id.toString() }.coerceAtLeast(0), 0L)
                                        p.prepare(); p.play(); currentId = song.id; playing = true
                                    }
                                }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Color(0xFF25395D), Color(0xFF483B70)))), contentAlignment = Alignment.Center) {
                                        if (song.id == currentId && playing) Equalizer() else Icon(Icons.Default.MusicNote, null, tint = Blue)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(time(song.duration), color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    now?.let { song ->
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp).clip(RoundedCornerShape(23.dp)).background(Brush.linearGradient(listOf(Color(0xFF202B43), Color(0xFF211D34)))).padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(49.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Blue, Purple))), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = Night, modifier = Modifier.size(28.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("NOW PLAYING", color = Blue, fontSize = 9.sp, letterSpacing = 2.sp)
                                    Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, color = Muted, fontSize = 11.sp)
                                }
                                if (playing) Equalizer()
                            }
                            Slider(value = if (duration > 0) position.coerceIn(0L, duration).toFloat() else 0f, onValueChange = { controller?.seekTo(it.toLong()) }, valueRange = 0f..duration.coerceAtLeast(1L).toFloat())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(time(position), color = Muted, fontSize = 10.sp); Text(time(duration), color = Muted, fontSize = 10.sp) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle }) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) Blue else Muted) }
                                IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(30.dp)) }
                                Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Blue, Purple))), contentAlignment = Alignment.Center) { IconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Night, modifier = Modifier.size(31.dp)) } }
                                IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(30.dp)) }
                                IconButton(onClick = { fade = if (fade >= 10f) 0f else fade + 2f }) { Icon(Icons.Default.BlurOn, "Crossfade setting", tint = if (fade > 0) Purple else Muted) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("CROSSFADE", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp); Text(if (fade == 0f) "Off" else "${fade.toInt()}s", color = Purple, fontSize = 11.sp) }
                            Slider(value = fade, onValueChange = { fade = it; }, valueRange = 0f..12f)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() { controllerFuture?.let { MediaController.releaseFuture(it) }; super.onDestroy() }
}

@Composable
private fun Equalizer() {
    val transition = rememberInfiniteTransition(label = "hmp-eq")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            val h by transition.animateFloat(5f, 18f, infiniteRepeatable(tween(250 + index * 100), RepeatMode.Reverse), label = "bar")
            Box(Modifier.width(3.dp).height(h.dp).clip(CircleShape).background(Blue))
        }
    }
}

private fun time(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
