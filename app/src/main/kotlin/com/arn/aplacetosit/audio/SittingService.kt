package com.arn.aplacetosit.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.arn.aplacetosit.MainActivity
import com.arn.aplacetosit.core.ActiveSession
import com.arn.aplacetosit.core.MeditationRecord
import com.arn.aplacetosit.core.SessionClock
import com.arn.aplacetosit.core.TimerEngine
import com.arn.aplacetosit.core.TimerEvent
import com.arn.aplacetosit.data.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sitting's home while the screen is dark: a media-playback foreground
 * service — Android's honest equivalent of the iOS app's live audio session.
 *
 * Guided sittings play their assembled program file, which carries voice,
 * gongs, and silence on one clock; the service only starts it and lets it run.
 * Silent sittings hold a partial wake lock and sound each gong at its moment
 * from the same pure timeline the tests pin down. Either way the practice
 * survives locking, and ending the service ends the audio — never held open
 * outside a session, the same contract as iOS.
 */
class SittingService : Service() {

    companion object {
        const val ACTION_START = "com.arn.aplacetosit.START"
        const val ACTION_STOP = "com.arn.aplacetosit.STOP"
        const val EXTRA_SESSION_JSON = "session"
        private const val CHANNEL = "sitting"
        var onNaturalEnd: (() -> Unit)? = null

        fun start(context: Context, session: ActiveSession) {
            val intent = Intent(context, SittingService::class.java)
                .setAction(ACTION_START)
                .putExtra(
                    EXTRA_SESSION_JSON,
                    MeditationRecord.json.encodeToString(ActiveSession.serializer(), session),
                )
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SittingService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runJob: Job? = null
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val session = intent.getStringExtra(EXTRA_SESSION_JSON)?.let {
                    MeditationRecord.json.decodeFromString(ActiveSession.serializer(), it)
                } ?: return START_NOT_STICKY
                begin(session)
            }
            ACTION_STOP -> finishService()
        }
        return START_NOT_STICKY
    }

    private fun begin(session: ActiveSession) {
        startForeground(1, notification())
        runJob?.cancel()
        releasePlayer()

        val wl = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aplacetosit:sitting")
        wl.acquire(session.preparationDurationMillis + session.plannedDurationMillis + 120_000)
        wakeLock = wl

        val guided = session.guidedMinutes
        if (guided != null) {
            playAsset("programs/guide-program-guided-$guided-v2-en.m4a", seekTo = elapsedNow(session)) {
                naturalEnd()
            }
        } else {
            runJob = scope.launch { runSilentTimeline(session) }
        }
    }

    /** Gongs at their offsets, from the same timeline the tests define. */
    private suspend fun runSilentTimeline(session: ActiveSession) {
        var last = elapsedNow(session)
        // A gong already passed (relaunch recovery) is skipped, never late.
        while (true) {
            delay(250)
            val now = elapsedNow(session)
            for (event in TimerEngine.eventsCrossed(session, last, now)) {
                when (event) {
                    is TimerEvent.MeditationStarted -> playAsset("sounds/gong_start.m4a")
                    is TimerEvent.AwarenessInterval -> playAsset("sounds/gong_start.m4a")
                    is TimerEvent.Completed -> {
                        playAsset("sounds/gong_end_triple.m4a") { naturalEnd() }
                        return
                    }
                }
            }
            last = now
        }
    }

    private fun elapsedNow(session: ActiveSession): Long =
        TimerEngine.elapsed(
            session,
            SessionClock(System.currentTimeMillis(), SystemClock.elapsedRealtime()),
        ) // timeline-relative; preparation included

    /**
     * A gong outlives its caller: nothing on the stack holds a MediaPlayer
     * once prepareAsync() returns, so a one-shot cue must be kept in a live
     * set until it finishes or it is collected mid-preparation and never
     * sounds. (It was created — the audio service logged it — and then died
     * silently, which is exactly what a locked-phone test looked like.)
     */
    private val oneShots = mutableSetOf<MediaPlayer>()

    private fun playAsset(path: String, seekTo: Long = 0, onDone: (() -> Unit)? = null) {
        if (onDone != null) releasePlayer()
        val mp = MediaPlayer()
        // Attributes must be set before the data source, or they are ignored
        // and the player lands on USAGE_UNKNOWN.
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        try {
            assets.openFd(path).use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
        } catch (error: Exception) {
            mp.release()
            return
        }
        mp.setOnPreparedListener { player ->
            if (seekTo > 0) player.seekTo(seekTo.toInt())
            player.start()
        }
        mp.setOnErrorListener { player, _, _ ->
            oneShots.remove(player)
            player.release()
            true
        }
        mp.setOnCompletionListener { player ->
            if (onDone != null) {
                onDone()
            } else {
                oneShots.remove(player)
                player.release()
            }
        }
        mp.prepareAsync()
        if (onDone != null) player = mp else oneShots.add(mp)
    }

    private fun naturalEnd() {
        onNaturalEnd?.invoke()
        finishService()
    }

    private fun finishService() {
        runJob?.cancel()
        releasePlayer()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        oneShots.forEach { runCatching { it.release() } }
        oneShots.clear()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sitting", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Sitting in progress")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        finishService()
        super.onDestroy()
    }
}
