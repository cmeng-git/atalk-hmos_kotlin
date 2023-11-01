/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.codec.video.AndroidDecoder
import timber.log.Timber
import java.awt.Dimension
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Layout that aligns remote video `View` by stretching it to max screen width or height.
 * It also controls whether call control buttons group should be auto hidden or stay visible all the time.
 * This layout will work only with `VideoCallActivity`.
 *
 * IMPORTANT: it can't be done from `Activity`, because just after the views are created,
 * we don't know their sizes yet(return 0 or invalid).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class RemoteVideoLayout : LinearLayout {
    /**
     * Last saved preferred video size used to calculate the max screen scaling.
     * Must set to null for sizeChange detection on first layout init; and when the remote view is removed
     */
    var preferredSize: Dimension? = null

    /**
     * Flag indicates any size change on new request. Always forces to requestLayout state if true
     */
    private var preferredSizeChanged = false

    /**
     * Stores last child count.
     */
    private var lastChildCount = -1

    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {}

    /**
     * SizeChange algorithm uses preferredSize and videoSize ratio compare algorithm for full screen video;
     * Otherwise, non-null remoteVideoView will also return false when remote video dimension changes.
     * Note: use ratio compare algorithm to avoid unnecessary doAlignRemoteVideo reDraw unless there is a ratio change
     *
     * @param videoSize received video stream size
     * @param requestLayout true to force relayout request
     * @return `false` if no change is required for remoteVideoViewContainer dimension update
     * to playback the newly received video size:
     * @see AndroidDecoder.configureMediaCodec
     */
    fun setVideoPreferredSize(videoSize: Dimension?, requestLayout: Boolean): Boolean {
        preferredSizeChanged = requestLayout || (preferredSize == null)
                || abs(preferredSize!!.width / preferredSize!!.height - videoSize!!.width / videoSize.height) > 0.01f
        preferredSize = videoSize
        requestLayout()
        return preferredSizeChanged
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val childCount = childCount

        if (childCount == lastChildCount && !preferredSizeChanged) {
            return
        }

        // Store values to prevent from too many calculations
        lastChildCount = childCount
        preferredSizeChanged = false
        if (context !is VideoCallActivity) {
            return
        }

        val videoActivity = context as VideoCallActivity
        if (childCount > 0) {
            /*
             * MeasureSpec.getSize() is determined by previous layout dimension, any may not in full screen size;
             * So force to use the device default display full screen dimension.
             * // int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
             * // int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
             */
            var parentWidth = aTalkApp.mDisplaySize.width
            var parentHeight = aTalkApp.mDisplaySize.height
            if (!aTalkApp.isPortrait) {
                parentWidth = aTalkApp.mDisplaySize.height
                parentHeight = aTalkApp.mDisplaySize.width
            }
            var width: Double
            var height: Double
            if (preferredSize != null) {
                width = preferredSize!!.width.toDouble()
                height = preferredSize!!.height.toDouble()
            } else {
                // NullPointerException from the field? so give it a default
                width = VideoHandlerFragment.DEFAULT_WIDTH.toDouble()
                height = VideoHandlerFragment.DEFAULT_HEIGHT.toDouble()
            }

            // Stretch to match height
            if (parentHeight <= parentWidth) {
                // Timber.i("Stretch to device max height: %s", parentHeight);
                val ratio = width / height
                height = parentHeight.toDouble()
                // width = height * ratio;
                width = ceil(height * ratio / 16.0) * 16
                videoActivity.ensureAutoHideFragmentAttached()
            } else {
                // Timber.i("Stretch to device max width: %s", parentWidth);
                val ratio = height / width
                width = parentWidth.toDouble()
                height = ceil(width * ratio / 16.0) * 16
                videoActivity.ensureAutoHideFragmentDetached()
            }
            Timber.i("Remote video view dimension: [%s x %s]", width, height)
            this.setMeasuredDimension(width.toInt(), height.toInt())
            val params = layoutParams
            params.width = width.toInt()
            params.height = height.toInt()
            this.layoutParams = params
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val chP = child.layoutParams
                chP.width = params.width
                chP.height = params.height
                child.layoutParams = chP
            }
        } else {
            val params = layoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            this.layoutParams = params
            videoActivity.ensureAutoHideFragmentDetached()
        }
    }
}