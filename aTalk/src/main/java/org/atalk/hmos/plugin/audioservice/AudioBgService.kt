/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.plugin.audioservice

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.text.TextUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.atalk.persistance.FileBackend
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.lang.Math.log10
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

class AudioBgService : Service(), OnCompletionListener {
    private val uriPlayers = ConcurrentHashMap<Uri?, MediaPlayer>()

    // Handler for media player playback status broadcast
    private var mHandlerPlayback: Handler? = null
    private var mPlayer: MediaPlayer? = null
    private var fileUri: Uri? = null
    private var playbackSpeed = 1.0f

    enum class PlaybackState {
        INITIATE, PLAY, PAUSE, STOP;
    }

    private var audioFile: File? = null
    private var mRecorder: MediaRecorder? = null
    private var startTime = 0L

    // Handler for Sound Level Meter and Record Timer
    private var mHandlerRecord: Handler? = null

    // The Google ASR input requirements state that audio input sensitivity should be set such
    // that 90 dB SPL_LEVEL at 1000 Hz yields RMS of 2500 for 16-bit samples,
    // i.e. 20 * log_10 (2500 / mGain) = 90.
    private val mGain = 2500.0 / 10.0.pow(90.0 / 20.0)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent.action) {
            ACTION_PLAYER_INIT -> {
                fileUri = intent.data
                playerInit(fileUri)
            }
            ACTION_PLAYER_START -> {
                fileUri = intent.data
                playerStart(fileUri)
            }
            ACTION_PLAYER_PAUSE -> {
                fileUri = intent.data
                playerPause(fileUri)
            }
            ACTION_PLAYER_STOP -> {
                fileUri = intent.data
                playerRelease(fileUri)
            }
            ACTION_PLAYER_SEEK -> {
                fileUri = intent.data
                val seekPosition = intent.getIntExtra(PLAYBACK_POSITION, 0)
                playerSeek(fileUri, seekPosition)
            }
            ACTION_PLAYBACK_PLAY -> {
                fileUri = intent.data
                playerPlay(fileUri)
            }
            ACTION_PLAYBACK_SPEED -> {
                val speed = intent.type
                if (!TextUtils.isEmpty(speed)) {
                    playbackSpeed = speed!!.toFloat()
                    setPlaybackSpeed()
                }
            }
            ACTION_RECORDING -> {
                mHandlerRecord = Handler()
                recordAudio()
            }
            ACTION_SEND -> {
                stopTimer()
                stopRecording()
                if (audioFile != null) {
                    // sendBroadcast(FileAccess.getUriForFile(this, audioFile));
                    val filePath = audioFile!!.absolutePath
                    sendBroadcast(filePath)
                }
            }
            ACTION_CANCEL -> {
                stopTimer()
                stopRecording()
                if (audioFile != null) {
                    val soundFile = File(audioFile!!.absolutePath)
                    soundFile.delete()
                    audioFile = null
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        stopRecording()
        if (mHandlerPlayback != null) {
            mHandlerPlayback!!.removeCallbacks(playbackStatus)
            mHandlerPlayback = null
        }
        for (uri in uriPlayers.keys) {
            fileUri = uri
            playerRelease(uri)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    /* =============================================================
     * Media player handlers
     * ============================================================= */
    /**
     * Create a new media player instance for the specified uri
     *
     * @param uri Media file uri
     * @return true is creation is successful
     */
    private fun playerCreate(uri: Uri?): Boolean {
        if (uri == null) return false
        if (mHandlerPlayback == null) mHandlerPlayback = Handler()
        mPlayer = MediaPlayer()
        uriPlayers[uri] = mPlayer!!
        mPlayer!!.setAudioAttributes(AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build())
        try {
            mPlayer!!.setOnCompletionListener(this)
            if (uri.toString().startsWith("http")) {
                mPlayer!!.setDataSource(uri.toString())
            } else {
                mPlayer!!.setDataSource(this, uri)
            }
            mPlayer!!.prepare()
        } catch (e: IOException) {
            Timber.e("Media player creation error for: %s", uri.path)
            playerRelease(uri)
            return false
        }
        return true
    }

    /**
     * Return the status of current active player if present; keep the state as it
     * else get the media file info and release player to conserve resource
     *
     * @param uri the media file uri
     */
    private fun playerInit(uri: Uri?) {
        if (uri == null) return
        if (mHandlerPlayback == null) mHandlerPlayback = Handler()

        // Check player status on return to chatSession before start new
        mPlayer = uriPlayers[uri]
        if (mPlayer != null) {
            if (mPlayer!!.isPlaying) {
                playbackState(PlaybackState.PLAY, uri)
                // Cancel and re-sync with only one loop running
                mHandlerPlayback!!.removeCallbacks(playbackStatus)
                mHandlerPlayback!!.postDelayed(playbackStatus, 500)
            } else {
                val position = mPlayer!!.currentPosition
                val duration = mPlayer!!.duration
                if (position in 1..duration) {
                    playbackState(PlaybackState.PAUSE, uri)
                } else {
                    playerReInit(uri)
                }
            }
        } else {
            // Create new to get media info and then release to conserve resource
            if (playerCreate(uri)) {
                playerRelease(uri)
            }
        }
    }

    /**
     * Re-init an existing player and broadcast its state
     *
     * @param uri the media file uri
     */
    private fun playerReInit(uri: Uri) {
        mPlayer = uriPlayers[uri]
        if (mPlayer != null) {
            mPlayer!!.seekTo(0)
            if (mPlayer!!.isPlaying) mPlayer!!.pause()
            playbackState(PlaybackState.INITIATE, uri)
        }
    }

    /**
     * Pause the current player and return the action result
     *
     * @param uri the media file uri
     */
    private fun playerPause(uri: Uri?) {
        if (uri == null) return
        mPlayer = uriPlayers[fileUri]
        if (mPlayer == null) {
            playbackState(PlaybackState.STOP, uri)
        } else if (mPlayer!!.isPlaying) {
            mPlayer!!.pause()
            playbackState(PlaybackState.PAUSE, uri)
        }
    }

    /**
     * Start playing back on existing player or create new if none
     * Broadcast the player status at regular interval
     *
     * @param uri the media file uri
     */
    private fun playerStart(uri: Uri?) {
        if (uri == null) return
        mPlayer = uriPlayers[uri]
        if (mPlayer == null) {
            if (!playerCreate(uri)) return
        } else if (mPlayer!!.isPlaying) {
            return
        }
        try {
            val playPara = mPlayer!!.playbackParams.setSpeed(playbackSpeed)
            mPlayer!!.playbackParams = playPara
            mPlayer!!.start()
            playbackState(PlaybackState.PLAY, uri)
        } catch (e: Exception) {
            Timber.e("Playback failed: %s", e.message)
            playerRelease(uri)
        }
        mHandlerPlayback!!.removeCallbacks(playbackStatus)
        mHandlerPlayback!!.postDelayed(playbackStatus, 500)
    }

    /**
     * Start playing back on existing player or create new if none
     * Broadcast the player satus at regular interval
     *
     * @param uri the media file uri
     */
    private fun playerSeek(uri: Uri?, seekPosition: Int) {
        if (uri == null) return
        mPlayer = uriPlayers[uri]
        if (mPlayer == null && !playerCreate(uri)) return
        try {
            mPlayer!!.seekTo(seekPosition)
            if (!mPlayer!!.isPlaying) playbackState(PlaybackState.PAUSE, uri)
        } catch (e: Exception) {
            Timber.e("Playback failed")
            playerRelease(uri)
        }
    }

    /**
     * Setting of playback speed is only support in Android.M
     */
    private fun setPlaybackSpeed() {
        for ((uri, player) in uriPlayers) {
            if (player == null) continue
            try {
                val playPara = player.playbackParams.setSpeed(playbackSpeed)
                player.playbackParams = playPara

                // Update player state: play will start upon speed change if it was in pause state
                playbackState(PlaybackState.PLAY, uri)
            } catch (e: IllegalStateException) {
                Timber.e("Playback setSpeed failed: %s", e.message)
            }
        }
    }

    /**
     * Release the player resource and remove it from uriPlayers
     *
     * @param uri the media file uri
     */
    private fun playerRelease(uri: Uri?) {
        if (uri == null) return
        mPlayer = uriPlayers[uri]
        if (mPlayer != null) {
            mPlayer!!.seekTo(0)
            playbackState(PlaybackState.STOP, uri)
            uriPlayers.remove(uri)
            if (mPlayer!!.isPlaying) {
                mPlayer!!.stop()
            }
            mPlayer!!.reset()
            mPlayer!!.release()
            mPlayer = null
        }
    }
    // Listener for playback completion
    /**
     * callback from the specific media player when playback of a media source has completed.
     *
     * @param mp Media Player instance
     */
    override fun onCompletion(mp: MediaPlayer) {
        fileUri = getUriByPlayer(mp)
        if (fileUri == null) {
            mp.release()
            stopSelf()
        } else {
            playerRelease(fileUri)
        }
    }

    /**
     * Return the uri of the given mp
     *
     * @param mp the media player
     * @return Uri of the player
     */
    private fun getUriByPlayer(mp: MediaPlayer): Uri? {
        for ((key, value) in uriPlayers) {
            if (value == mp) {
                return key
            }
        }
        return null
    }

    /**
     * Broadcast the relevant info of the media player (uri)
     * a. player state
     * b. player uri file
     * c. playback position
     * d. uri playback duration
     *
     * @param pState player state
     * @param uri media file uri
     */
    private fun playbackState(pState: PlaybackState, uri: Uri?) {
        val xPlayer = uriPlayers[uri]
        if (xPlayer != null) {
            val intent = Intent(PLAYBACK_STATE)
            intent.putExtra(PLAYBACK_URI, uri)
            intent.putExtra(PLAYBACK_STATE, pState)
            intent.putExtra(PLAYBACK_POSITION, xPlayer.currentPosition)
            intent.putExtra(PLAYBACK_DURATION, xPlayer.duration)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            // Timber.d("Audio playback state: %s (%s): %s", pState, xPlayer.getDuration(), uri.getPath());
        }
    }

    /**
     * Broadcast the relevant info of the media playback status (uri); loop@500ms until no active player
     * a. player uri file
     * b. playback position
     * c. uri playback duration
     */
    private val playbackStatus = object : Runnable {
        override fun run() {
            var hasActivePlayer = false
            for ((key, playerX) in uriPlayers) {
                if (playerX == null || !playerX.isPlaying) continue
                hasActivePlayer = true
                // Timber.d("Audio playback state: %s:  %s", playerX.getCurrentPosition(), entry.getKey());
                val intent = Intent(PLAYBACK_STATUS)
                intent.putExtra(PLAYBACK_URI, key)
                intent.putExtra(PLAYBACK_POSITION, playerX.currentPosition)
                intent.putExtra(PLAYBACK_DURATION, playerX.duration)
                LocalBroadcastManager.getInstance(this@AudioBgService).sendBroadcast(intent)
            }
            if (hasActivePlayer) mHandlerPlayback!!.postDelayed(this, 500)
        }
    }

    /**
     * Playback media audio without any UI update
     * hence mHandlerPlayback not required
     *
     * @param uri the audio file
     */
    private fun playerPlay(uri: Uri?) {
        if (playerCreate(uri)) {
            mPlayer!!.start()
            uriPlayers.remove(uri)
        }
        mHandlerPlayback = null
    }

    /* =============================================================
     * Voice recording handlers
     * ============================================================= */
    private fun recordAudio() {
        audioFile = createMediaVoiceFile()
        if (audioFile == null) {
            return
        }
        mRecorder = MediaRecorder()
        mRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mRecorder!!.setOutputFile(audioFile!!.absolutePath)
        try {
            mRecorder!!.prepare()
            mRecorder!!.start()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            Timber.e("io problems while recording [%s]: %s", audioFile!!.absolutePath, e.message)
        }
        startTime = SystemClock.uptimeMillis()
        mHandlerRecord!!.postDelayed(updateSPL, 0)
    }

    private fun stopRecording() {
        if (mRecorder != null) {
            try {
                mRecorder!!.stop()
                mRecorder!!.reset()
                mRecorder!!.release()
                mRecorder = null
            } catch (ex: RuntimeException) {
                /*
                 * Note that a RuntimeException is intentionally thrown to the application, if no
                 * valid audio/video data has been received when stop() is called. This happens
                 * if stop() is called immediately after start().
                 */
                ex.printStackTrace()
            }
        }
    }

    private fun stopTimer() {
        if (mHandlerRecord != null) {
            mHandlerRecord!!.removeCallbacks(updateSPL)
            mHandlerRecord = null
        }
    }

    private fun sendBroadcast(filePath: String) {
        val intent = Intent(ACTION_AUDIO_RECORD)
        // intent.setDataAndType(uri, "video/3gp");
        intent.putExtra(URI, filePath)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private val updateSPL = object : Runnable {
        override fun run() {
            val finalTime = SystemClock.uptimeMillis() - startTime
            var seconds = (finalTime / 1000).toInt()
            val minutes = seconds / 60
            seconds %= 60
            val mDuration = String.format(Locale.US, "%02d:%02d", minutes, seconds)
            val mRmsSmoothed = amplitudeEMA
            val rmsdB = 20.0 * log10(mGain * mRmsSmoothed)

            // The bar has an input range of [0.0 ; 1.0] and 14 segments.
            // Each LED corresponds to 70/14 dB.
            val mSPL = (mOffsetDB + rmsdB) / mDBRange
            // mBarLevel.setLevel(mSPL);
            val intent = Intent(ACTION_SMI)
            intent.putExtra(SPL_LEVEL, mSPL)
            intent.putExtra(RECORD_TIMER, mDuration)
            LocalBroadcastManager.getInstance(this@AudioBgService).sendBroadcast(intent)
            mHandlerRecord!!.postDelayed(this, 100)
        }
    }

    // Compute a smoothed version for less flickering of the display.
    val amplitudeEMA: Double
        get() {
            val amp = amplitude
            // Compute a smoothed version for less flickering of the display.
            mEMA = EMA_FILTER * mEMA + (1.0 - EMA_FILTER) * amp
            return mEMA
        }
    val amplitude: Double
        get() = if (mRecorder != null) mRecorder!!.maxAmplitude.toDouble() else 0.0

    companion object {
        // Media player actions
        const val ACTION_PLAYER_INIT = "player_init"
        const val ACTION_PLAYER_START = "player_start"
        const val ACTION_PLAYER_PAUSE = "player_pause"
        const val ACTION_PLAYER_STOP = "player_stop"
        const val ACTION_PLAYER_SEEK = "player_seek"

        // Playback without any UI update
        const val ACTION_PLAYBACK_PLAY = "playback_play"
        const val ACTION_PLAYBACK_SPEED = "playback_speed"

        // Media player broadcast status parameters
        const val PLAYBACK_STATE = "playback_state"
        const val PLAYBACK_STATUS = "playback_status"
        const val PLAYBACK_DURATION = "playback_duration"
        const val PLAYBACK_POSITION = "playback_position"
        const val PLAYBACK_URI = "playback_uri"

        // ==== Audio recording ====
        const val ACTION_RECORDING = "recording"
        const val ACTION_CANCEL = "cancel"
        const val ACTION_SEND = "send"
        const val ACTION_AUDIO_RECORD = "audio_record"
        const val ACTION_SMI = "sound_meter_info"
        const val URI = "uri"
        const val SPL_LEVEL = "spl_level"
        const val RECORD_TIMER = "record_timer"

        // For displaying error in calibration.
        var mOffsetDB = 0.0 //10 Offset for bar, i.e. 0 lit LEDs at 10 dB.
        @JvmField
        var mDBRange = 70.0 //SPL display range.
        private var mEMA = 1.0 // a temporally filtered version of RMS

        //private double mAlpha =  0.9 Coefficient of IIR smoothing filter for RMS.
        private const val EMA_FILTER = 0.4

        /**
         * Create the audio file if it does not exist
         *
         * @return Voice file for saving audio
         */
        private fun createMediaVoiceFile(): File? {
            var voiceFile: File? = null
            val mediaDir = FileBackend.getaTalkStore(FileBackend.MEDIA_VOICE_SEND, true)
            if (!mediaDir.exists() && !mediaDir.mkdirs()) {
                Timber.w("Fail to create Media voice directory!")
                return null
            }
            try {
                voiceFile = File.createTempFile("voice-", ".3gp", mediaDir)
            } catch (e: IOException) {
                Timber.w("Fail to create Media voice file!")
            }
            return voiceFile
        }
    }
}