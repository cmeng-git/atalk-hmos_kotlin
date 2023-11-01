package org.atalk.hmos.plugin.mediaplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerFullScreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.loadOrCueVideo
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.ui.menu.MenuItem
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.R
import org.atalk.util.FullScreenHelper
import timber.log.Timber
import java.util.*

class YoutubePlayerFragment : Fragment() {
    private lateinit var youTubePlayerView: YouTubePlayerView

    var fullScreenHelper: FullScreenHelper? = null
        private set

    private var mediaUrl: String? = null
    private var mVideoId: String? = null
    private val usePlayList = false

    // Will attempt to playback the given videoId as playlist if onError first encountered
    private var onErrorOnce = true
    private var mSpeed = 1.0f

    // private static final String[] mpSpeedValues = HymnsApp.getAppResources().getStringArray(R.array.mp_speed_value);
    private lateinit var mContext: FragmentActivity

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context as FragmentActivity
        fullScreenHelper = FullScreenHelper(mContext)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.youtube_player_fragment, container, false)
        youTubePlayerView = view.findViewById(R.id.youtube_player_view)
        val args = arguments
        if (args != null) {
            mediaUrl = args.getString(MediaExoPlayerFragment.ATTR_MEDIA_URL)

            // Comment out the following to test loadPlaylist_videoIds()
            mediaUrls = args.getStringArrayList(MediaExoPlayerFragment.ATTR_MEDIA_URLS)
            if (mediaUrls != null && mediaUrls!!.isNotEmpty()) {
                mVideoIds.clear()
                for (i in mediaUrls!!.indices) {
                    val videoId = getVideoId(mediaUrls!![i])
                    mVideoIds.add(videoId)
                }
            }
        }
        initYouTubePlayerView()
        return view
    }

    private fun initYouTubePlayerView() {
        // hymnchtv crashes when enabled
        // initPlayerMenu();

        // The player will automatically release itself when the fragment is destroyed.
        // The player will automatically pause when the fragment is stopped
        // If you don't add YouTubePlayerView as a lifecycle observer, you will have to release it manually.
        lifecycle.addObserver(youTubePlayerView)
        youTubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                if (usePlayList) {
                    startPlaylist(youTubePlayer, playLists[0])
                } else if (mediaUrls != null) {
                    // comma separated strings for playlist array conversation: i.e ['vCKCkc8llaM','LvetJ9U_tVY','S0Q4gqBUs7c','9HPiBJBCOq8'];
                    startPlaylist(youTubePlayer, TextUtils.join(SEPARATOR, mVideoIds))
                } else if (mediaUrl != null) {
                    mVideoId = getVideoId(mediaUrl!!)
                    if (mVideoId!!.uppercase(Locale.getDefault()).startsWith(PLAYLIST)) {
                        startPlaylist(youTubePlayer, mVideoId)
                    } else {
                        onErrorOnce = true
                        youTubePlayer.loadOrCueVideo(lifecycle, mVideoId!!, 0f)
                    }
                }
                addActionsToPlayer(youTubePlayer)
                initPlaybackSpeed(youTubePlayer)
                addFullScreenListenerToPlayer()
            }

            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerError) {
                // Error message will be shown in player view by API
                Timber.w("Youtube url: %s, playback failed: %s", mediaUrl, error)

                // Try to load as playlist if onError
                if (onErrorOnce && error == PlayerError.VIDEO_CONTENT_RESTRICTION_OR_UNAVAILABLE) {
                    onErrorOnce = false
                    startPlaylist(youTubePlayer, mVideoId)
                } else {
                    // Use external player if playlist playback failed
                    playVideoUrlExt(mediaUrl)
                }
            }

            override fun onVideoUrl(youTubePlayer: YouTubePlayer, videoUrl: String) {
                Timber.w("Youtube videoUrl: %s (%s)", mediaUrl, videoUrl)
            }

            override fun onPlaybackRateChange(youTubePlayer: YouTubePlayer, playbackRate: String) {
                mSpeed = playbackRate.toFloat()
                Toast.makeText(mContext, mContext.getString(R.string.gui_playback_rate, playbackRate), Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Enable the Previous & Next button and start playlist playback
     *
     * @param youTubePlayer instance of youtube player
     * @param videoId video PL id or ids for playback
     */
    private fun startPlaylist(youTubePlayer: YouTubePlayer, videoId: String?) {
        val nextActionIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_next)!!

        // Set a click listener on the "Play next video" button
        youTubePlayerView.getPlayerUiController().setPreviousAction(nextActionIcon) { youTubePlayer.previousVideo() }
        youTubePlayerView.getPlayerUiController().setNextAction(nextActionIcon) { youTubePlayer.nextVideo() }
        if (videoId!!.contains(SEPARATOR)) {
            youTubePlayer.loadPlaylist_videoIds(videoId)
        } else {
            youTubePlayer.loadPlaylist(videoId, 0)
        }
    }

    /**
     * Extract the youtube videoId from the following string formats:
     * a. vCKCkc8llaM
     * b. https://youtu.be/vCKCkc8llaM
     * c. https://youtube.com/watch?v=14VrDQSnfzI&feature=share
     * d. https://www.youtube.com/playlist?list=PL0KROm2A3S8HaMLBxYPF5kuEEtTYvUJox\
     *
     * @param url Any of the above url string
     * @return the youtube videoId
     */
    private fun getVideoId(url: String): String {
        var mVideoId = url.substring(mediaUrl!!.lastIndexOf('/') + 1)
        if (mVideoId.contains("=")) {
            mVideoId = mVideoId.substring(mVideoId.indexOf("=") + 1).split("&")[0]
        }
        return mVideoId
    }

    /**
     * This method adds a new custom action to the player.
     * Custom actions are shown next to the Play/Pause button in the middle of the player.
     */
    private fun addActionsToPlayer(youTubePlayer: YouTubePlayer) {
        val rewindActionIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_rewind)
        val forwardActionIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_forward)
        val rateIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_speed)
        assert(rewindActionIcon != null)
        assert(forwardActionIcon != null)
        assert(rateIcon != null)
        youTubePlayerView.getPlayerUiController().setRewindAction(rewindActionIcon!!)
        { youTubePlayer.advanceTo(-5.0f) }
        youTubePlayerView.getPlayerUiController().setForwardAction(forwardActionIcon!!)
        { youTubePlayer.advanceTo(15.0f) }

        youTubePlayerView.getPlayerUiController().setRateIncAction(rateIcon!!)
        {
            val tmp = mSpeed + rateStep
            val rate = if (tmp <= rateMax) tmp else mSpeed
            youTubePlayer.setPlaybackRate(rate)
        }

        youTubePlayerView.getPlayerUiController().setRateDecAction(rateIcon)
        {
            val tmp = mSpeed - rateStep
            val rate = if (tmp >= rateMin) tmp else mSpeed
            youTubePlayer.setPlaybackRate(rate)
        }
    }

    /**
     * Shows the menu button in the player and adds an item to it.
     */
    private fun initPlayerMenu() {
        val youTubePlayerMenu = Objects.requireNonNull(youTubePlayerView.getPlayerUiController()
                .showMenuButton(true)
                .getMenu()!!)
                .addItem(MenuItem("menu item1", R.drawable.ic_speed)
                { Toast.makeText(mContext, "item1 clicked", Toast.LENGTH_SHORT).show() })
                .addItem(MenuItem("menu item2", R.drawable.ic_mood_black_24dp)
                { Toast.makeText(mContext, "item2 clicked", Toast.LENGTH_SHORT).show() })
                .addItem(MenuItem("menu item no icon", null)
                { Toast.makeText(mContext, "item no icon clicked", Toast.LENGTH_SHORT).show() })
    }

    private fun addFullScreenListenerToPlayer() {
        youTubePlayerView.addFullScreenListener(object : YouTubePlayerFullScreenListener {
            override fun onYouTubePlayerEnterFullScreen() {
                fullScreenHelper!!.enterFullScreen()
            }

            override fun onYouTubePlayerExitFullScreen() {
                fullScreenHelper!!.exitFullScreen()
            }
        })
    }

    /**
     * Initialize the Media Player playback speed to the user defined setting
     */
    fun initPlaybackSpeed(youTubePlayer: YouTubePlayer) {
        mSpeed = configService!!.getDouble(MediaExoPlayerFragment.PREF_PLAYBACK_SPEED, 1.0).toFloat()
        youTubePlayer.setPlaybackRate(mSpeed)
    }

    /**
     * Play the specified videoUrl using android Intent.ACTION_VIEW
     *
     * @param videoUrl videoUrl not playable by ExoPlayer
     */
    private fun playVideoUrlExt(videoUrl: String?) {
        // remove the youtube player fragment
        mContext.supportFragmentManager.beginTransaction().remove(this).commit()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * Manually release the youtube player when user pressing backKey
     */
    fun release() {
        // Audio media player speed is (0.5 > mSpeed < 1.5)
        if (mSpeed in rateMin..rateMax) {
            configService!!.setProperty(MediaExoPlayerFragment.PREF_PLAYBACK_SPEED, mSpeed)
        }
        youTubePlayerView.release()
    }

    companion object {
        // regression to check for valid youtube link
        const val URL_YOUTUBE = "http[s]*://[w.]*youtu[.]*be.*"

        // Array may contain the youtube url links or just videoId's
        private var mediaUrls: MutableList<String>? = ArrayList()

        init {
            mediaUrls!!.add("https://youtu.be/vCKCkc8llaM")
            mediaUrls!!.add("https://youtu.be/LvetJ9U_tVY")
            mediaUrls!!.add("https://youtu.be/S0Q4gqBUs7c")
            mediaUrls!!.add("https://youtu.be/9HPiBJBCOq8")
        }

        // Array contains only the youtube videoId's
        private val mVideoIds = ArrayList<String?>()

        // Youtube playlist ids for testing; set usePlayList to true
        private val playLists = arrayOf("RDMQTvg5EUgWU", "PLUh4W61bt_K5jmi1qbVACvLAPkudEmLKO")
        private const val PLAYLIST = "PL"
        private const val SEPARATOR = ","

        // Playback ratio of normal speed constants.
        const val rateMin = 0.25f
        const val rateMax = 2.0f
        private const val rateStep = 0.25f
        private val configService = UtilActivator.configurationService

        /**
         * Create a new instance of MediaExoPlayerFragment, providing "bundle" as an argument.
         */
        @JvmStatic
        fun getInstance(args: Bundle?): YoutubePlayerFragment {
            val youtubePlayer = YoutubePlayerFragment()
            youtubePlayer.arguments = args
            return youtubePlayer
        }
    }
}