/*
 * hymnchtv: COG hymns' lyrics viewer and player client
 * Copyright 2020 Eng Chong Meng
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
package org.atalk.hmos.plugin.mediaplayer

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.video.VideoSize
import net.java.sip.communicator.util.UtilActivator
import org.apache.http.util.TextUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.persistance.FileBackend

/**
 * The class handles the actual content source address decoding for the user selected hymn
 * see https://developer.android.com/codelabs/exoplayer-intro#0
 *
 * This MediaExoPlayerFragment requires its parent FragmentActivity to handle onConfigurationChanged()
 * It does not consider onSaveInstanceState(); it uses the speed in the user configuration setting.
 *
 * @author Eng Chong Meng
 */
class MediaExoPlayerFragment : Fragment() {
    // Default playback video url
    private var mediaUrl: String? = sampleUrl
    private var mediaUrls: ArrayList<String>? = null

    // Playback ratio of normal speed.
    private var mSpeed = 1.0f
    private lateinit var mContext: FragmentActivity
    private var mExoPlayer: ExoPlayer? = null
    private lateinit var mPlayerView: StyledPlayerView
    private var playbackStateListener: PlaybackStateListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context as FragmentActivity
    }

    @SuppressLint("CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        if (args != null) {
            // mediaUrl = "https://youtu.be/vCKCkc8llaM";  for testing only
            mediaUrl = args.getString(ATTR_MEDIA_URL)
            mediaUrls = args.getStringArrayList(ATTR_MEDIA_URLS)
        }
        playbackStateListener = PlaybackStateListener()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mConvertView = inflater.inflate(R.layout.media_player_exo_ui, container, false)
        mPlayerView = mConvertView.findViewById(R.id.exoplayerView)
        if (container != null) container.visibility = View.VISIBLE

        // Need to set text color in Hymnchtv; although ExoStyledControls.ButtonText specifies while
        val rewindButtonTextView = mConvertView.findViewById<TextView>(com.google.android.exoplayer2.ui.R.id.exo_rew_with_amount)
        rewindButtonTextView.setTextColor(Color.WHITE)
        val fastForwardButtonTextView = mConvertView.findViewById<TextView>(com.google.android.exoplayer2.ui.R.id.exo_ffwd_with_amount)
        fastForwardButtonTextView.setTextColor(Color.WHITE)
        return mConvertView
    }

    override fun onResume() {
        super.onResume()
        // Load the media and start playback each time onResume() is called.
        initializePlayer()
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    private fun initializePlayer() {
        if (mExoPlayer == null) {
            mExoPlayer = ExoPlayer.Builder(mContext).build()
            mExoPlayer!!.addListener(playbackStateListener!!)
            mPlayerView.player = mExoPlayer
        }
        if (mediaUrls == null || mediaUrls!!.isEmpty()) {
            val mediaItem = buildMediaItem(mediaUrl!!)
            mediaItem?.let { playMedia(it) }
        } else {
            playVideoUrls()
        }
    }

    /**
     * Media play-back takes a lot of resources, so everything should be stopped and released at this time.
     * Release all media-related resources. In a more complicated app this
     * might involve unregistering listeners or releasing audio focus.
     *
     * Save the user defined playback speed
     */
    fun releasePlayer() {
        if (mExoPlayer != null) {
            mSpeed = mExoPlayer!!.playbackParameters.speed

            // Audio media player speed is (0.25 >= mSpeed <= 2.0)
            if (mSpeed >= YoutubePlayerFragment.rateMin && mSpeed <= YoutubePlayerFragment.rateMax) {
                configService.setProperty(PREF_PLAYBACK_SPEED, mSpeed)
            }
            mExoPlayer!!.playWhenReady = false
            mExoPlayer!!.removeListener(playbackStateListener!!)
            mExoPlayer!!.release()
            mExoPlayer = null
        }
    }

    /**
     * Prepare and play the specified mediaItem
     *
     * @param mediaItem for playback
     */
    private fun playMedia(mediaItem: MediaItem?) {
        if (mediaItem != null) {
            mSpeed = configService.getDouble(PREF_PLAYBACK_SPEED, 1.0).toFloat()
            setPlaybackSpeed(mSpeed)
            mExoPlayer!!.setMediaItem(mediaItem, 0)
            mExoPlayer!!.playWhenReady = true
            mExoPlayer!!.prepare()
        }
    }

    /**
     * Prepare and playback a list of given video URLs if not empty
     */
    private fun playVideoUrls() {
        if (mediaUrls != null && mediaUrls!!.isNotEmpty()) {
            val mediaItems = ArrayList<MediaItem>()
            for (tmpUrl in mediaUrls!!) {
                mediaItems.add(buildMediaItem(tmpUrl)!!)
            }
            mSpeed = configService.getDouble(PREF_PLAYBACK_SPEED, 1.0).toFloat()
            setPlaybackSpeed(mSpeed)
            mExoPlayer!!.setMediaItems(mediaItems)
            mExoPlayer!!.playWhenReady = true
            mExoPlayer!!.prepare()
        }
    }

    /**
     * Play the specified videoUrl using android Intent.ACTION_VIEW
     *
     * @param videoUrl videoUrl not playable by ExoPlayer
     */
    private fun playVideoUrlExt(videoUrl: String?) {
        // remove the exoPlayer fragment
        mContext.supportFragmentManager.beginTransaction().remove(this).commit()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * set SimpleExoPlayer playback speed
     *
     * @param speed playback speed: default 1.0f
     */
    private fun setPlaybackSpeed(speed: Float) {
        val playbackParameters = PlaybackParameters(speed, 1.0f)
        if (mExoPlayer != null) {
            mExoPlayer!!.playbackParameters = playbackParameters
        }
    }

    /**
     * Build and return the mediaItem or
     * Proceed to play if it is a youtube link; return null;
     *
     * @param mediaUrl for building the mediaItem
     * @return built mediaItem
     */
    private fun buildMediaItem(mediaUrl: String): MediaItem? {
        if (TextUtils.isEmpty(mediaUrl)) return null
        val mediaItem: MediaItem
        val uri = Uri.parse(mediaUrl)
        val mimeType = FileBackend.getMimeType(mContext, uri)!!
        mediaItem = if (!TextUtils.isEmpty(mimeType) && (mimeType.contains("video") || mimeType.contains("audio"))) {
            MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMimeType(mimeType)
                    .build()
        } else {
            MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
        }
        return mediaItem
    }

    /**
     * ExoPlayer playback state listener
     */
    private inner class PlaybackStateListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                ExoPlayer.STATE_IDLE -> {
                    aTalkApp.showToastMessage(R.string.gui_playback_error)
                    // Attempt to use android player if exoplayer failed to play
                    playVideoUrlExt(mediaUrl)
                }
                ExoPlayer.STATE_ENDED -> {}
                ExoPlayer.STATE_READY -> if (VideoSize.UNKNOWN == mExoPlayer!!.videoSize) {
                    val vHeight = 0.62f * aTalkApp.mDisplaySize.width
                    mPlayerView.layoutParams = LinearLayout.LayoutParams(aTalkApp.mDisplaySize.width, vHeight.toInt())
                }
                ExoPlayer.STATE_BUFFERING -> {}
                else -> {}
            }
        }
    }

    companion object {
        // Tag for the instance state bundle.
        const val ATTR_MEDIA_URL = "mediaUrl"
        const val ATTR_MEDIA_URLS = "mediaUrls"
        const val PREF_PLAYBACK_SPEED = "playBack_speed"
        private const val sampleUrl = "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4"
        private val configService = UtilActivator.configurationService!!

        /**
         * Create a new instance of MediaExoPlayerFragment, providing "bundle" as an argument.
         */
        @JvmStatic
        fun getInstance(args: Bundle?): MediaExoPlayerFragment {
            val exoPlayerFragment = MediaExoPlayerFragment()
            exoPlayerFragment.arguments = args
            return exoPlayerFragment
        }
    }
}