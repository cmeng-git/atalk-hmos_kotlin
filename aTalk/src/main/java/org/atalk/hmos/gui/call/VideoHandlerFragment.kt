/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.Manifest
import android.graphics.Color
import android.hardware.camera2.CameraMetadata
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.RelativeLayout.LayoutParams
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.CallState
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.controller.SimpleDragController
import org.atalk.hmos.gui.util.AndroidUtils
import org.atalk.impl.neomedia.codec.video.AndroidDecoder
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.impl.neomedia.device.util.AndroidCamera
import org.atalk.impl.neomedia.device.util.CameraUtils
import org.atalk.impl.neomedia.device.util.OpenGlCtxProvider
import org.atalk.impl.neomedia.device.util.PreviewSurfaceProvider
import org.atalk.impl.neomedia.device.util.ViewDependentProvider
import org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera.CameraStreamBase
import org.atalk.service.neomedia.ViewAccessor
import org.atalk.service.osgi.OSGiFragment
import org.atalk.util.event.SizeChangeVideoEvent
import org.atalk.util.event.VideoEvent
import org.atalk.util.event.VideoListener
import timber.log.Timber
import java.awt.Component
import java.awt.Dimension

/**
 * Fragment takes care of handling call UI parts related to the video - both local and remote.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class VideoHandlerFragment : OSGiFragment(), View.OnLongClickListener {
    /**
     * The callee avatar.
     */
    private var calleeAvatar: ImageView? = null

    /**
     * The remote video container.
     */
    private var remoteVideoContainer: RemoteVideoLayout? = null

    /**
     * The remote video view
     */
    private var remoteVideoAccessor: ViewAccessor? = null

    /**
     * Container used for local preview
     */
    private var localPreviewContainer: ViewGroup? = null

    /**
     * The preview surface state handler
     */
    var localPreviewSurface: PreviewSurfaceProvider? = null

    /**
     * `OpenGlCtxProvider` that provides Open GL context for local preview rendering. It is
     * used in direct surface encoding mode.
     */
    var mLocalPreviewGlCtxProvider: OpenGlCtxProvider? = null
    private var currentPreviewProvider: ViewDependentProvider<*>? = null

    /**
     * Instance of video listener that should be unregistered once this Activity is destroyed
     */
    private var callPeerVideoListener: VideoListener? = null

    /**
     * Indicate phone orientation change and need to init RemoteVideoContainer
     */
    private var initOnPhoneOrientationChange = false

    /**
     * The call for which this fragment is handling video events.
     */
    private var mCall: Call<*>? = null

    /**
     * The thread that switches the camera.
     */
    private var cameraSwitchThread: Thread? = null

    /**
     * Call info group
     */
    private var callInfoGroup: ViewGroup? = null

    /**
     * Call control buttons group.
     */
    private var ctrlButtonsGroup: View? = null

    /**
     * Local video call button.
     */
    private var mCallVideoButton: ImageView? = null

    /**
     * For long press to toggle between front and back (full screen display - not shown option in Android 8.0)
     */
    private var mCameraToggle: MenuItem? = null

    /**
     * VideoHandlerFragment parent activity for the callback i.e. VideoCallActivity
     */
    private var mCallActivity: VideoCallActivity? = null

    /**
     * Create a new instance of `VideoHandlerFragment`.
     */
    init {
        setHasOptionsMenu(true)
    }

    /**
     * Must be called by parent activity on fragment attached
     *
     * @param activity VideoCall Activity
     */
    fun setRemoteVideoChangeListener(activity: VideoCallActivity?) {
        mCallActivity = activity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        remoteVideoContainer = mCallActivity!!.findViewById(R.id.remoteVideoContainer)
        localPreviewContainer = mCallActivity!!.findViewById(R.id.localPreviewContainer)
        callInfoGroup = mCallActivity!!.findViewById(R.id.callInfoGroup)
        ctrlButtonsGroup = mCallActivity!!.findViewById(R.id.button_Container)

        // (must be done after layout or 0 sizes will be returned)
        ctrlButtonsGroup!!.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // We know the size of all components at this point, so we can init layout
                // dependent stuff. Initial call info margin adjustment
                updateCallInfoMargin()

                // Remove the listener, as it has to be called only once
                ctrlButtonsGroup!!.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        isCameraEnable = aTalk.hasPermission(aTalk.instance, false,
                aTalk.PRC_CAMERA, Manifest.permission.CAMERA)
        calleeAvatar = mCallActivity!!.findViewById(R.id.calleeAvatar)
        mCallVideoButton = mCallActivity!!.findViewById(R.id.button_call_video)
        mCallVideoButton!!.setOnClickListener { callVideoButton: View -> onLocalVideoButtonClicked(callVideoButton) }
        if (isCameraEnable) {
            mCallVideoButton!!.setOnLongClickListener(this)
        }
        mCall = mCallActivity!!.getCall()

        // Creates and registers surface handler for events
        localPreviewSurface = PreviewSurfaceProvider(mCallActivity!!, localPreviewContainer!!, true)
        CameraUtils.setPreviewSurfaceProvider(localPreviewSurface)
        // Makes the local preview window draggable on the screen
        localPreviewContainer!!.setOnTouchListener(SimpleDragController())

        /*
         * Initialize android hardware encoder and decoder Preview and surfaceProvider;
         * use only if hardware encoder/decoder are enabled.
         *
         * See Constants#ANDROID_SURFACE, DataSource#AbstractPushBufferStream and AndroidEncoder#isDirectSurfaceEnabled
         * on conditions for the selection and use of this stream in SurfaceStream setup
         * i.e. AndroidEncoder#DIRECT_SURFACE_ENCODE_PROPERTY must be true
         */
        mLocalPreviewGlCtxProvider = OpenGlCtxProvider(mCallActivity!!, localPreviewContainer!!)
        AndroidDecoder.renderSurfaceProvider = PreviewSurfaceProvider(mCallActivity!!, remoteVideoContainer!!, false)

        // Make the remote preview display draggable on the screen - not applicable in aTalk default full screen mode
        // remoteVideoContainer.setOnTouchListener(new SimpleDragController());
    }

    override fun onResume() {
        super.onResume()
        if (mCall == null) {
            Timber.e("Call is null")
            return
        }
        if (java.lang.Boolean.TRUE == mVideoLocalLastState[mCall!!.callId]) setLocalVideoEnabled(true)

        // Checks if call peer has video component
        val peers = mCall!!.getCallPeers()
        if (peers.hasNext()) {
            val callPeer = peers.next()
            addVideoListener(callPeer)
            initRemoteVideo(callPeer)
        }
        else {
            Timber.e("There aren't any peers in the call")
        }
    }

    /**
     * Restores local video state if it was enabled or on first video call entry; The local preview size is
     * configured to be proportional to the actually camera captured video dimension with the default width.
     * The localPreview can either be localPreviewSurface or mLocalPreviewGlCtxProvider
     *
     * Note: runOnUiThread(0 for view as this may be call from non-main thread
     */
    fun initLocalPreviewContainer(provider: ViewDependentProvider<*>?) {
        Timber.d("init Local Preview Container %s (%s)", mVideoLocalLastState[mCall!!.callId], provider)
        if (provider != null) {
            currentPreviewProvider = provider
            val params: LayoutParams = localPreviewContainer!!.layoutParams as LayoutParams
            val videoSize = provider.videoSize
            // Local preview size has default fixed width of 160 (landscape mode)
            val scale = resources.displayMetrics.density * DEFAULT_PREVIEW_WIDTH / videoSize.width
            val previewSize = if (aTalkApp.isPortrait) {
                Dimension(videoSize.height, videoSize.width)
            }
            else {
                videoSize
            }
            params.width = (previewSize.width * scale + 0.5).toInt()
            params.height = (previewSize.height * scale + 0.5).toInt()
            runOnUiThread {
                localPreviewContainer!!.layoutParams = params
                provider.setAspectRatio(params.width, params.height)
            }
            Timber.d("SurfaceView instance Size: [%s x %s]; %s", params.width, params.height, provider.view)
        }
        // Set proper videoCallButtonState and restore local video
        initLocalVideoState(true)
    }

    override fun onPause() {
        super.onPause()

        // Make sure to join the switch camera thread
        if (cameraSwitchThread != null) {
            try {
                cameraSwitchThread!!.join()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        if (mCall == null) {
            Timber.e("Call is null")
            return
        }
        removeVideoListener()
        if (mCall!!.callState != CallState.CALL_ENDED) {
            // save a copy of the local video state for use when the call is resumed
            mVideoLocalLastState[mCall!!.callId] = isLocalVideoEnabled()

            /*
             * Disables local video to stop the camera and release the surface.
             * Otherwise, media recorder will crash on invalid preview surface.
             * 20210306 - must setLocalVideoEnabled(false) to restart camera after screen orientation changed
             */
            setLocalVideoEnabled(false)
            localPreviewSurface!!.waitForObjectRelease()
            // TODO: release object on rotation, but the data source have to be paused
            // remoteSurfaceHandler.waitForObjectRelease();
            //}
        }
        else {
            mVideoLocalLastState.remove(mCall!!.callId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release shared video component
        remoteVideoContainer!!.removeAllViews()
        mLocalPreviewGlCtxProvider = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        val selectedCamera = AndroidCamera.selectedCameraDevInfo
        if (!isCameraEnable || selectedCamera == null) {
            return
        }

        // Check and set camera option with other facing from current system if available
        val isFrontCamera = selectedCamera.cameraFacing == AndroidCamera.FACING_FRONT
        val otherFacing = if (isFrontCamera) AndroidCamera.FACING_BACK else AndroidCamera.FACING_FRONT
        if (AndroidCamera.getCameraFromCurrentDeviceSystem(otherFacing) != null) {
            inflater.inflate(R.menu.camera_menu, menu)
            val displayName = if (isFrontCamera) getString(R.string.service_gui_settings_USE_BACK_CAMERA) else getString(R.string.service_gui_settings_USE_FRONT_CAMERA)
            mCameraToggle = menu.findItem(R.id.switch_camera).setTitle(displayName)
        }
    }

    /**
     * Switch to alternate camera on the device when user toggles the camera
     *
     * @param item the user clicked menu item
     * @return return true is activation is from menu item R.id.switch_camera
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.switch_camera) {
            startCameraSwitchThread(item)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Long press camera icon changes to alternate camera available on the device.
     *
     * @param v the clicked view
     * @return return true is activation is from R.id.button_call_video
     */
    override fun onLongClick(v: View): Boolean {
        if (v.id == R.id.button_call_video) {
            // Do not proceed if no alternate camera (i.e. mCameraToggle == null) is available on the device
            if (mCameraToggle != null) {
                aTalkApp.showToastMessage(mCameraToggle!!.title.toString())
                startCameraSwitchThread(mCameraToggle!!)
            }
            return true
        }
        return false
    }

    /**
     * Toggle the camera device in separate thread and update the menu title text
     *
     * @param item Menu Item
     */
    private fun startCameraSwitchThread(item: MenuItem) {
        // Ignore action if camera switching is in progress
        if (cameraSwitchThread != null) return
        val newDevice: AndroidCamera?
        val back = getString(R.string.service_gui_settings_USE_BACK_CAMERA)
        if (item.title == back) {
            // Switch to back camera and toggle item name
            newDevice = AndroidCamera.getCameraFromCurrentDeviceSystem(CameraMetadata.LENS_FACING_BACK)
            item.setTitle(R.string.service_gui_settings_USE_FRONT_CAMERA)
        }
        else {
            // Switch to front camera and toggle item name
            newDevice = AndroidCamera.getCameraFromCurrentDeviceSystem(CameraMetadata.LENS_FACING_FRONT)
            item.title = back
        }

        // Timber.w("New Camera selected: %s", newDevice.getName());
        // Switch the camera in separate thread
        cameraSwitchThread = object : Thread() {
            override fun run() {
                if (newDevice != null) {
                    val instance = CameraStreamBase.getInstance()
                    instance?.switchCamera(newDevice.locator, isLocalVideoEnabled())

                    // Keep track of created threads
                    cameraSwitchThread = null
                }
            }
        }
        cameraSwitchThread!!.start()
    }

    /**
     * Called when local video button is pressed. Give user feedback if camera not enabled
     *
     * @param callVideoButton local video button `View`.
     */
    private fun onLocalVideoButtonClicked(callVideoButton: View) {
        if (aTalk.isMediaCallAllowed(true)) {
            initLocalVideoState(!isLocalVideoEnabled())
        }
    }

    /**
     * Initialize the Call Video Button to its proper state
     */
    private fun initLocalVideoState(isVideoEnable: Boolean) {
        setLocalVideoEnabled(isVideoEnable)
        when {
            !isCameraEnable -> {
                mCallVideoButton!!.setImageResource(R.drawable.call_video_no_dark)
                mCallVideoButton!!.setBackgroundColor(Color.TRANSPARENT)
            }
            isVideoEnable -> {
                mCallVideoButton!!.setImageResource(R.drawable.call_video_record_dark)
                mCallVideoButton!!.setBackgroundColor(0x50000000)
            }
            else -> {
                mCallVideoButton!!.setImageResource(R.drawable.call_video_dark)
                mCallVideoButton!!.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    /**
     * Checks local video status.
     *
     * @return `true` if local video is enabled.
     */
    fun isLocalVideoEnabled(): Boolean {
        return CallManager.isLocalVideoEnabled(mCall)
    }

    /**
     * Sets local video status.
     *
     * @param enable flag indicating local video status to be set.
     */
    private fun setLocalVideoEnabled(enable: Boolean) {
        if (mCall == null) {
            Timber.e("Call instance is null (the call has ended already?)")
            return
        }
        CallManager.enableLocalVideo(mCall!!, enable)
    }
    // ============ Remote video view handler ============
    /**
     * Adds a video listener for the given call peer.
     *
     * @param callPeer the `CallPeer` to which we add a video listener
     */
    private fun addVideoListener(callPeer: CallPeer) {
        val pps = callPeer.getProtocolProvider()
        val osvt = pps.getOperationSet(OperationSetVideoTelephony::class.java)
                ?: return
        if (callPeerVideoListener == null) {
            callPeerVideoListener = object : VideoListener {
                override fun videoAdded(event: VideoEvent) {
                    handleVideoEvent(callPeer, event)
                }

                override fun videoRemoved(event: VideoEvent) {
                    // Timber.w(new Exception(), "Call Peer: %s; event: %s", callPeer, event);
                    handleVideoEvent(callPeer, event)
                }

                override fun videoUpdate(event: VideoEvent) {
                    handleVideoEvent(callPeer, event)
                }
            }
        }
        osvt.addVideoListener(callPeer, callPeerVideoListener!!)
    }

    /**
     * Handles a video event.
     *
     * @param callPeer the corresponding call peer
     * @param event the `VideoEvent` that notified us
     */
    fun handleVideoEvent(callPeer: CallPeer?, event: VideoEvent) {
        if (event.isConsumed) return
        event.consume()

        /*
         * VideoEvent.LOCAL: local video events are not handled here because the preview is required for the
         * camera to start, and it must not be removed until is stopped, so it's handled by directly
         */
        if (event.origin == VideoEvent.REMOTE) {
            val eventType = event.type
            val visualComponent = if (eventType == VideoEvent.VIDEO_ADDED || eventType == VideoEvent.VIDEO_SIZE_CHANGE) event.visualComponent else null
            val scve = if (eventType == VideoEvent.VIDEO_SIZE_CHANGE) event as SizeChangeVideoEvent else null
            Timber.d("handleVideoEvent %s; %s", eventType, visualComponent)
            handleRemoteVideoEvent(visualComponent, scve)
        }
    }

    /**
     * Removes remote video listener.
     */
    private fun removeVideoListener() {
        val calPeers = mCall!!.getCallPeers()
        if (calPeers.hasNext()) {
            val callPeer = calPeers.next()
            val pps = mCall!!.pps ?: return
            val osvt = pps.getOperationSet(OperationSetVideoTelephony::class.java)
                    ?: return
            if (callPeerVideoListener != null) {
                osvt.removeVideoListener(callPeer, callPeerVideoListener)
            }
        }
    }

    /**
     * Initializes remote video for the call. Visual component is always null on initial setup;
     * but non-null on phone rotate: Need to re-init remote video on screen rotation. However device
     * rotation is currently handled by onConfigurationChanged, so handleRemoteVideoEvent will not be called
     *
     * Let remote handleVideoEvent triggers the initial setup.
     * Multiple quick access to GLSurfaceView can cause problem.
     *
     * @param callPeer owner of video object.
     */
    private fun initRemoteVideo(callPeer: CallPeer) {
        val pps = callPeer.getProtocolProvider()
        var visualComponent: Component? = null
        if (pps != null) {
            val osvt = pps.getOperationSet(OperationSetVideoTelephony::class.java)
            if (osvt != null) visualComponent = osvt.getVisualComponent(callPeer)
        }
        if (visualComponent != null) {
            initOnPhoneOrientationChange = true
            handleRemoteVideoEvent(visualComponent, null)
        }
    }

    /**
     * Handles the remote video event.
     *
     * @param visualComponent the remote video `Component` if available or `null` otherwise.
     * visualComponent is null on video call initial setup and on remote video removed.
     * No null on the remote device rotated; need to re-init remote video on screen rotation
     * @param scvEvent the `SizeChangeVideoEvent` event if was supplied.
     */
    private fun handleRemoteVideoEvent(visualComponent: Component?, scvEvent: SizeChangeVideoEvent?) {
        if (visualComponent is ViewAccessor) {
            remoteVideoAccessor = visualComponent
        }
        else {
            remoteVideoAccessor = null
            // null visualComponent evaluates to false, so need to check here before warn
            // Report component is not compatible
            if (visualComponent != null) {
                Timber.e("Remote video component is not Android compatible.")
            }
        }

        // Update window full screen visibility only in UI
        runOnUiThread {
            mCallActivity!!.onRemoteVideoChange(remoteVideoAccessor != null)
            if (remoteVideoAccessor != null) {
                val view = remoteVideoAccessor!!.getView(mCallActivity)
                val preferredSize = selectRemotePreferredSize(visualComponent, view, scvEvent)
                doAlignRemoteVideo(view, preferredSize)
            }
            else {
                remoteVideoContainer!!.preferredSize = null
                doAlignRemoteVideo(null, null)
            }
        }
    }

    /**
     * Selected remote video preferred size based on current visual components and event status.
     * In android: the remote video view container size is fixed by aTalk to full screen; and user is
     * not allow to change. Hence remoteVideoView has higher priority over visualComponent
     *
     * @param visualComponent remote video `Component`, `null` if not available
     * @param remoteVideoView the remote video `View` if already created, or `null` otherwise
     * @param scvEvent the `SizeChangeVideoEvent` if was supplied during event handling or `null` otherwise.
     * @return selected preferred remote video size.
     */
    private fun selectRemotePreferredSize(visualComponent: Component?, remoteVideoView: View?,
            scvEvent: SizeChangeVideoEvent?): Dimension {
        // There is no remote video View, so returns the default video dimension.
        if (remoteVideoView == null || visualComponent == null) {
            return Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        }
        var width = DEFAULT_WIDTH
        var height = DEFAULT_HEIGHT
        /*
         * The SizeChangeVideoEvent may have been delivered with a delay and thus may not
         * represent the up-to-date size of the remote video. The visualComponent is taken
         * as fallback in case SizeChangeVideoEvent is null
         */
        if (scvEvent != null && scvEvent.height > 0 && scvEvent.width > 0) {
            width = scvEvent.width
            height = scvEvent.height
        }
        else {
            val preferredSize = visualComponent.preferredSize
            if (preferredSize != null && preferredSize.width > 0 && preferredSize.height > 0) {
                width = preferredSize.width
                height = preferredSize.height
            }
        }
        return Dimension(width, height)
    }

    /**
     * Align remote `Video` component if available.
     *
     * @param remoteVideoView the remote video `View` if available or `null` otherwise.
     * @param preferredSize preferred size of remote video `View`.
     */
    private fun doAlignRemoteVideo(remoteVideoView: View?, preferredSize: Dimension?) {
        if (remoteVideoView != null) {
            // GLSurfaceView frequent changes can cause error, so change only if necessary
            val sizeChange = remoteVideoContainer!!.setVideoPreferredSize(preferredSize, initOnPhoneOrientationChange)
            Timber.w("Remote video view alignment @ size: %s; sizeChange: %s; initOnPhoneOrientationChange: %s",
                    preferredSize, sizeChange, initOnPhoneOrientationChange)
            if (!sizeChange && !initOnPhoneOrientationChange) {
                return
            }

            // reset the flag after use
            initOnPhoneOrientationChange = false

            // Hack only for GLSurfaceView. Remote video view will match parents width and height,
            // but renderer object is properly updated only when removed and added back again.
            if (remoteVideoView is GLSurfaceView) {
                remoteVideoContainer!!.removeAllViews()

                // remoteVideoView must be an orphan before assigned to another ViewGroup parent
                val viewGroup = remoteVideoView.parent as ViewGroup?
                if (viewGroup != null) {
                    viewGroup.removeView(remoteVideoView)
                    Timber.d("Make remoteVideo view '%s' as orphan", remoteVideoView)
                }
                remoteVideoContainer!!.addView(remoteVideoView)
            }

            // When remote video is visible then the call info is positioned in the bottom part of the screen
            val params = callInfoGroup!!.layoutParams as LayoutParams
            params.addRule(RelativeLayout.CENTER_VERTICAL, 0)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)

            // Realign call group info start from left if system is in landscape mode
            // int rotation = mCallActivity.getWindowManager().getDefaultDisplay().getRotation();
            // if ((rotation == Surface.ROTATION_90) || (rotation == Surface.ROTATION_270))
            //     params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            callInfoGroup!!.layoutParams = params
            calleeAvatar!!.visibility = View.GONE
            remoteVideoContainer!!.visibility = View.VISIBLE
        }
        else { // if (!initOnPhoneOrientationChange) {
            Timber.d("Remote video view removed: %s", preferredSize)
            remoteVideoContainer!!.removeAllViews()

            // When remote video is hidden then the call info is centered below the avatar
            val params = callInfoGroup!!.layoutParams as LayoutParams
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0)
            params.addRule(RelativeLayout.CENTER_VERTICAL)
            callInfoGroup!!.layoutParams = params
            calleeAvatar!!.visibility = View.VISIBLE
            remoteVideoContainer!!.visibility = View.GONE
        }

        // Update call info group margin based on control buttons group visibility state
        updateCallInfoMargin()
    }

    /**
     * Returns `true` if local video is currently visible.
     *
     * @return `true` if local video is currently visible.
     */
    fun isLocalVideoVisible(): Boolean {
        return localPreviewContainer!!.childCount > 0
    }

    fun isRemoteVideoVisible(): Boolean {
        return remoteVideoContainer!!.childCount > 0
    }

    /**
     * Block the program until camera is stopped to prevent from crashing on not existing preview surface.
     */
    fun ensureCameraClosed() {
        localPreviewSurface!!.waitForObjectRelease()
        // TODO: remote display must be released too (but the DataSource must be paused)
        // remoteVideoSurfaceHandler.waitForObjectRelease();
    }

    /**
     * Positions call info group buttons.
     */
    fun updateCallInfoMargin() {
        val params = callInfoGroup!!.layoutParams as LayoutParams
        var marginBottom = 0
        // If we have remote video
        if (remoteVideoContainer!!.childCount > 0) {
            val displaymetrics = DisplayMetrics()
            mCallActivity!!.windowManager.defaultDisplay.getMetrics(displaymetrics)
            val ctrlButtonsHeight = ctrlButtonsGroup!!.height
            marginBottom = (0.10 * displaymetrics.heightPixels).toInt()
            if (marginBottom < ctrlButtonsHeight
                    && ctrlButtonsGroup!!.visibility == View.VISIBLE) {
                marginBottom = ctrlButtonsHeight + AndroidUtils.pxToDp(10)
            }

            // This can be used if we want to keep it on the same height
            if (ctrlButtonsGroup!!.visibility == View.VISIBLE) {
                marginBottom -= ctrlButtonsHeight
            }
        }
        params.setMargins(0, 0, 0, marginBottom)
        callInfoGroup!!.layoutParams = params
    }

    // Parent container activity must implement this interface for callback from this fragment
    interface OnRemoteVideoChangeListener {
        fun onRemoteVideoChange(isRemoteVideoVisible: Boolean)
    }

    /**
     * Init both the local and remote video container on device rotation.
     */
    fun initVideoViewOnRotation() {
        if (isLocalVideoEnabled()) {
            initLocalPreviewContainer(currentPreviewProvider)
        }
        if (remoteVideoAccessor != null) {
            initOnPhoneOrientationChange = true
            handleRemoteVideoEvent(remoteVideoAccessor as Component?, null)
        }
    }

    companion object {
        /**
         * Default remote video view dimension (aTalk default) - must also be valid for OpenGL else crash
         * Note: Other dimension ratio e.g. (1x1) will cause Invalid Operation in OpenGL
         * Static variable must only be init in constructor for android Fragment
         *
         * Assuming the received video is in portrait and using aTalk default
         */
        var DEFAULT_WIDTH = DeviceConfiguration.DEFAULT_VIDEO_HEIGHT
        var DEFAULT_HEIGHT = DeviceConfiguration.DEFAULT_VIDEO_WIDTH

        // Default local preview width
        private const val DEFAULT_PREVIEW_WIDTH = 160

        /**
         * Stores local video state when `CallActivity` is paused i.e. back-to-chat, and is used to start content-modify
         * for new local video streaming when the call is resumed. Screen rotation uses isLocalVideoEnabled() to re-init local video
         */
        private val mVideoLocalLastState = HashMap<String, Boolean>()
        private var isCameraEnable = false
    }
}