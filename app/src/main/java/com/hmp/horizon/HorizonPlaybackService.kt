package com.hmp.horizon

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlin.math.cos
import kotlin.math.sin

class HorizonPlaybackService : MediaSessionService() {
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    private lateinit var session: MediaSession
    private var active: ExoPlayer? = null
    private var standby: ExoPlayer? = null
    private var crossfading = false
    private var crossfadeMs = 5_000L
    private var queue: List<MediaItem> = emptyList()
    private var queueIndex = 0
    private var fadeStart = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val watcher = object : Runnable {
        override fun run() {
            val p = active
            if (p != null && p.isPlaying && !crossfading && crossfadeMs > 0 && queueIndex + 1 < queue.size) {
                val duration = p.duration
                val remaining = if (duration == C.TIME_UNSET || duration <= 0L) Long.MAX_VALUE else duration - p.currentPosition
                if (remaining in 1..crossfadeMs) crossfadeToNext()
            }
            handler.postDelayed(this, 180L)
        }
    }
    private val fadeTick = object : Runnable {
        override fun run() {
            if (!crossfading) return
            val t = ((SystemClock.elapsedRealtime() - fadeStart).toFloat() / crossfadeMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            active?.volume = cos(t * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
            standby?.volume = sin(t * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
            if (t >= 1f) finishCrossfade() else handler.postDelayed(this, 30L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        playerA = ExoPlayer.Builder(this).build().apply { setAudioAttributes(attrs, true) }
        playerB = ExoPlayer.Builder(this).build().apply { setAudioAttributes(attrs, true) }
        active = playerA
        standby = playerB
        session = MediaSession.Builder(this, playerA).build()
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val p = active ?: return
                if (!crossfading && p.mediaItemCount > 0) {
                    queue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }
                    queueIndex = p.currentMediaItemIndex.coerceAtLeast(0)
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!crossfading) queueIndex = (active?.currentMediaItemIndex ?: queueIndex).coerceAtLeast(0)
            }
        }
        playerA.addListener(listener)
        playerB.addListener(listener)
        handler.post(watcher)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    private fun crossfadeToNext() {
        val to = standby ?: return
        if (crossfading || queueIndex + 1 >= queue.size || crossfadeMs <= 0L) return
        crossfading = true
        to.volume = 0f
        to.setMediaItems(queue.drop(queueIndex + 1), 0, 0L)
        to.prepare()
        to.play()
        fadeStart = SystemClock.elapsedRealtime()
        handler.post(fadeTick)
    }
    private fun finishCrossfade() {
        handler.removeCallbacks(fadeTick)
        val old = active ?: return
        val next = standby ?: return
        old.pause()
        old.clearMediaItems()
        old.volume = 1f
        active = next
        standby = old
        queueIndex += 1
        session.player = next
        crossfading = false
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session.release()
        playerA.release()
        playerB.release()
        super.onDestroy()
    }
}
