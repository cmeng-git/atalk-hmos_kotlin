/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.mediarecorder

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraDevice.*
import android.media.MediaRecorder
import android.media.MediaRecorder.VideoSource
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.Process
import android.view.Surface
import okhttp3.internal.notify
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.call.VideoCallActivity
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.h264.H264
import org.atalk.impl.neomedia.device.util.AndroidCamera
import org.atalk.impl.neomedia.device.util.CameraUtils.getOptimalPreviewSize
import org.atalk.impl.neomedia.device.util.CameraUtils.getPreviewOrientation
import org.atalk.impl.neomedia.device.util.PreviewSurfaceProvider
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream
import org.atalk.persistance.FileBackend
import org.atalk.persistance.FileBackend.getaTalkStore
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.awt.Dimension
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.media.Buffer
import javax.media.Format
import javax.media.MediaLocator
import javax.media.control.FormatControl
import javax.media.format.VideoFormat
import javax.media.protocol.PushBufferDataSource
import kotlin.math.roundToLong

/**
 * Implements `PushBufferDataSource` and `CaptureDevice` using Android's `MediaRecorder`.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class DataSource : AbstractPushBufferCaptureDevice {
    /**
     * The path of the file into which [.mediaRecorder] is to write. If the value is not `null`, no
     * bytes will be read from the `mediaRecorder` by the `DataSource` and made available through it.
     */
    private var mOutputFile: String? = null

    /**
     * The system time stamp in nanoseconds of the access unit of [.nal].
     */
    private var accessUnitTimeStamp = 0L
    private val mDataSourceKey: String
    private var lastWrittenParameterSetTime = 0L
    private var mLocalSocket: LocalSocket? = null
    private var mLocalSocketKey: String? = null
    private var maxLocalSocketKeySize = 0

    /**
     * A reference to the opened [CameraDevice] for mCameraId.
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for preview.
     */
    private var mCaptureSession: CameraCaptureSession? = null
    private var mCaptureBuilder: CaptureRequest.Builder? = null
    private var mVideoFormat: VideoFormat? = null

    /**
     * The [android.util.Size] of video recording.
     */
    private var mVideoSize: Dimension? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private var mPreviewSize: Dimension? = null
    private var mPreviewSurface: Surface? = null
    private var mSurfaceProvider: PreviewSurfaceProvider? = null

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundHandler: Handler? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    /**
     * The `MediaRecorder` which implements the actual capturing of media data for this `DataSource`.
     */
    private var mediaRecorder: MediaRecorder? = null
    private var nal = ByteArray(0)

    /**
     * The `Buffer` flags to be applied when [.nal] is read out of the associated `MediaRecorderStream`.
     */
    private var nalFlags = 0

    /**
     * The number of [.nal] elements containing (valid) NAL unit data.
     */
    private var nalLength = 0

    /**
     * The `Object` to synchronize the access to [.nal], [.nalLength], etc.
     */
    private val nalSyncRoot = Any()

    @get:Synchronized
    private var nextLocalSocketKey = 0L
        get() {
            return field++
        }

    /**
     * The `nal_unit_type` of the NAL unit preceding [.nal].
     */
    private var prevNALUnitType = 0

    /**
     * The interval of time in nanoseconds between two consecutive video frames produced by this
     * `DataSource` with which the time stamps of `Buffer`s are to be increased.
     */
    private var videoFrameInterval = 0L

    /**
     * The picture and sequence parameter set for video.
     */
    private var h264Params: H264Parameters? = null
    private lateinit var mParcelFileDescriptors: Array<ParcelFileDescriptor>
    private var mParcelRead: ParcelFileDescriptor? = null
    private var mParcelWrite: ParcelFileDescriptor? = null

    /**
     * Initializes a new `DataSource` instance.
     */
    constructor() {
        mDataSourceKey = nextDataSourceKey.toString()
    }

    /**
     * Initializes a new `DataSource` from a specific `MediaLocator`.
     *
     * @param locator the `MediaLocator` to create the new instance from
     */
    constructor(locator: MediaLocator?) : super(locator) {
        mDataSourceKey = nextDataSourceKey.toString()
    }

    /**
     * Create a new `PushBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PushBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` in the list of streams of this
     * `PushBufferDataSource`
     * @param formatControl the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `PushBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PushBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified `formatControl`
     * @see AbstractPushBufferCaptureDevice.createStream
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl): AbstractPushBufferStream<*> {
        return MediaRecorderStream(this, formatControl)
    }

    @Throws(IOException::class)
    private fun discard(inputStream: InputStream?, byteCount_: Long) {
        var byteCount = byteCount_
        while (byteCount-- > 0) if (-1 == inputStream!!.read()) throw IOException(EOS_IOE_MESSAGE)
    }

    /**
     * Starts the transfer of media data from this `DataSource`.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this `DataSource`
     * @see AbstractPushBufferCaptureDevice.doStart
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    @Throws(IOException::class)
    override fun doStart() {
        if (mediaRecorder == null) {
            mCameraId = AndroidCamera.getCameraId(locator)
            mediaRecorder = MediaRecorder()
            mCameraDevice = null

            // We need a local socket to forward data output by the camera to the packetizer
            createSockets()
            try {
                if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw RuntimeException("Time out waiting to lock camera opening.")
                }
                // Timber.e(new Exception("Media recorder data source tracing only!!!"));
                startBackgroundThread()
                val streamFormats = streamFormats
                // Selects video format
                for (candidate in streamFormats) {
                    if (Constants.H264.equals(candidate!!.encoding, ignoreCase = true)) {
                        mVideoFormat = candidate as VideoFormat?
                        break
                    }
                }
                if (mVideoFormat == null) {
                    throw RuntimeException("H264 not supported")
                }
                val manager = aTalkApp.cameraManager
                val characteristics = manager.getCameraCharacteristics(mCameraId!!)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: throw RuntimeException("Cannot get available preview/video sizes")
                /*
                 * Reflect the size of the VideoFormat of this DataSource on the Camera. It should not be
                 * necessary because it is the responsibility of MediaRecorder to configure the Camera it is
                 * provided with. Anyway, MediaRecorder.setVideoSize(int,int) is not always supported so it may
                 * (or may not) turn out that Camera.Parameters.setPictureSize(int,int) saves the day in some cases.
                 */
                mVideoSize = mVideoFormat!!.size
                mPreviewSize = getOptimalPreviewSize(mVideoSize!!, map.getOutputSizes(SurfaceTexture::class.java))
                Timber.d("Video / preview size: %s %s; %s", mVideoSize, mPreviewSize, mVideoFormat)
                manager.openCamera(mCameraId!!, mStateCallback, mBackgroundHandler)
            } catch (e: CameraAccessException) {
                Timber.e("openCamera: Cannot access the camera.")
            } catch (e: NullPointerException) {
                Timber.e("Camera2API is not supported on the device.")
            } catch (e: InterruptedException) {
                // throw new RuntimeException("Interrupted while trying to lock camera opening.");
                Timber.w("Exception in start camera init: %s", e.message)
            }
        }
    }

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private val mStateCallback = object : StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            mCameraOpenCloseLock.release()
            try {
                /*
                 * set up the target surfaces for local video preview display; Before calling obtainObject(),
                 * must setSurfaceSize() for surfaceHolder.setFixedSize() on surfaceCreated
                 * Then set the local previewSurface size by calling initLocalPreviewContainer()
                 * Note: Do not change the following execution order
                 */
                val videoFragment = VideoCallActivity.getVideoFragment()
                mSurfaceProvider = videoFragment!!.localPreviewSurface
                mSurfaceProvider!!.videoSize = mPreviewSize!!
                // Need to init for AndroidDecoder when hardware decode is enabled
                // AndroidDecoder.renderSurfaceProvider.setSurfaceSize(mPreviewSize);
                val surfaceHolder = mSurfaceProvider!!.obtainObject() // this will create the surfaceView
                videoFragment.initLocalPreviewContainer(mSurfaceProvider)
                mPreviewSurface = surfaceHolder!!.surface

                // Tries to read previously stored parameters; Obtain/save h264 parameters from short sample video if null
                h264Params = H264Parameters.getStoredParameters(mVideoFormat)
                if (h264Params == null) {
                    obtainParameters()
                } else {
                    // startPreview();  // Testing only: not required for aTalk implementation
                    startVideoRecording()
                }
            } catch (ioe: IllegalStateException) {
                Timber.e(ioe, "IllegalStateException (media recorder) in configuring data source: : %s", ioe.message)
                closeMediaRecorder()
            } catch (ioe: IOException) {
                Timber.e(ioe, "IllegalStateException (media recorder) in configuring data source: : %s", ioe.message)
                closeMediaRecorder()
            }
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraDevice!!.close()
            mCameraDevice = null
            mCameraOpenCloseLock.release()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            val errMessage = when (error) {
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                ERROR_CAMERA_DISABLED -> "Device policy"
                ERROR_CAMERA_DEVICE -> "Fatal (device)"
                ERROR_CAMERA_SERVICE -> "Fatal (service)"
                else -> "UnKnown"
            }
            Timber.e("Set camera preview failed: %s", errMessage)
            aTalkApp.showGenericError(R.string.service_gui_DEVICE_VIDEO_FORMAT_NOT_SUPPORTED, "", errMessage)
            mediaRecorder!!.release()
            mediaRecorder = null
            videoFrameInterval = 0
            cameraDevice.close()
            mCameraDevice = null
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Must use H264/RTP for video encoder
     *
     * After API 23, android doesn't allow non seekable file descriptors i.e. mOutputFile = null
     * org.atalk.hmos E/(DataSource.java:294)#startVideoRecording: IllegalStateException (media recorder) in configuring data source: : null
     * java.lang.IllegalStateException
     * at android.media.MediaRecorder._start(Native Method)
     * at android.media.MediaRecorder.start(MediaRecorder.java:1340)
     * at org.atalk.impl.neomedia.jmfext.media.protocol.mediarecorder.DataSource.startVideoRecording(DataSource.java:469)
     */
    private fun startVideoRecording() {
        if (null == mCameraDevice || null == mPreviewSize) {
            return
        }
        try {
            closeCaptureSession()

            // mOutputFile = null;
            mOutputFile = File(getaTalkStore(FileBackend.TMP, true), System.currentTimeMillis().toString() + ".mp4").absolutePath

            // Configure media recorder for video recording
            // mediaRecorder.setVideoSource(VideoSource.DEFAULT);  // has problem with this
            configureMediaRecorder(mVideoFormat, VideoSource.SURFACE)

            // Sets the path of the output file to be produced. Call this after setOutputFormat() but before prepare().
            if (mOutputFile == null) {
                // after API 23, android doesn't allow non seekable file descriptors; both no working
                // mediaRecorder.setOutputFile(createLocalSocket());
                mediaRecorder!!.setOutputFile(mParcelWrite!!.fileDescriptor)
            } else mediaRecorder!!.setOutputFile(mOutputFile)
            mediaRecorder!!.prepare()
            mCaptureBuilder = mCameraDevice!!.createCaptureRequest(TEMPLATE_PREVIEW)

            // Set up Surface for the camera preview
            val surfaces = ArrayList<Surface?>()
            surfaces.add(mPreviewSurface)
            mCaptureBuilder!!.addTarget(mPreviewSurface!!)

            // Set up Surface for the MediaRecorder
            val recorderSurface = mediaRecorder!!.surface
            surfaces.add(recorderSurface)
            mCaptureBuilder!!.addTarget(recorderSurface)

            // Start a capture session; Once the session starts, we can start recording
            mCameraDevice!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    mCaptureSession = session
                    updateCaptureRequest()
                    mediaRecorder!!.start()
                    try {
                        super@DataSource.doStart()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    aTalkApp.showToastMessage("Media recording failed")
                }
            }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Configures the camera and media recorder to work with given `videoFormat`.
     * Note: Do not change the order of the parameters setup before referring to MediaRecorder class
     *
     * @param videoFormat the video format to be used.
     */
    @Throws(IOException::class)
    private fun configureMediaRecorder(videoFormat: VideoFormat?, videoSource: Int) {
        // The sources need to be specified before setting recording-parameters or encoders, e.g. before setOutputFormat().
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        // ==> IllegalStateException in prepare when OUTPUT == null
        mediaRecorder!!.setVideoSource(videoSource)
        mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        // Setup media recorder video size
        if (mVideoSize != null && mVideoSize!!.height > 0 && mVideoSize!!.width > 0) {
            Timber.w("Set video size for '%s' with %sx%s.",
                    locator, mVideoSize!!.width, mVideoSize!!.height)
            mediaRecorder!!.setVideoSize(mVideoSize!!.width, mVideoSize!!.height)
        }

        // Setup media recorder bitrate and frame rate
        mediaRecorder!!.setVideoEncodingBitRate(10000000)
        var frameRate = videoFormat!!.frameRate
        if (frameRate <= 0) frameRate = 15f
        if (frameRate > 0) {
            mediaRecorder!!.setVideoFrameRate(frameRate.toInt())
            videoFrameInterval = (1000 / frameRate * 1000 * 1000).roundToLong()
            videoFrameInterval /= 2 /* ticks_per_frame */
        }

        // Stack Overflow says that setVideoSize should be called before setVideoEncoder.
        mediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

        // Adjust preview display orientation
        val previewOrientation = getPreviewOrientation(mCameraId!!)
        mediaRecorder!!.setOrientationHint(previewOrientation)

        // Reset to recording max duration/size, as it may have been manipulated during parameters retrieval
        mediaRecorder!!.setMaxDuration(-1)
        mediaRecorder!!.setMaxFileSize(-1)
    }

    /**
     * Start the camera preview.
     */
    private fun startPreview() {
        if (null == mCameraDevice || null == mPreviewSize) {
            return
        }
        try {
            closeCaptureSession()
            mCaptureBuilder = mCameraDevice!!.createCaptureRequest(TEMPLATE_PREVIEW)
            mCaptureBuilder!!.addTarget(mPreviewSurface!!)
            mCameraDevice!!.createCaptureSession(listOf(mPreviewSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            mCaptureSession = session
                            updateCaptureRequest()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            aTalkApp.showToastMessage("Camera preview start failed")
                        }
                    }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Update the camera preview. [.startPreview] needs to be called in advance.
     */
    private fun updateCaptureRequest() {
        if (null == mCameraDevice) {
            return
        }
        try {
            mCaptureBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            mCaptureSession!!.setRepeatingRequest(mCaptureBuilder!!.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            Timber.e("Update catpure request exception: %s", e.message)
        }
    }

    private fun closeCaptureSession() {
        if (mCaptureSession != null) {
            mCaptureSession!!.close()
            mCaptureSession = null
        }
    }

    /**
     * Tries to read sequence and picture video parameters by recording sample video and parsing
     * "avcC" part of "stsd" mp4 box. Process takes about 3 seconds
     *
     * Note: Cannot use limitMonitor = new Object() to wait, affect mediaRecorder operations:
     * IllegalStateException (stop called in an invalid state: 8)
     *
     * @throws IOException if we failed to retrieve the parameters.
     */
    @Throws(IOException::class)
    private fun obtainParameters() {
        mOutputFile = aTalkApp.globalContext.cacheDir.path + "/atalk-test.mpeg4"
        val outFile = File(mOutputFile!!)
        Timber.d("Obtaining H264Parameters from short sample video file: %s", mOutputFile)

        // Configure media recorder for obtainParameters
        configureMediaRecorder(mVideoFormat, VideoSource.SURFACE)
        mediaRecorder!!.setOutputFile(mOutputFile)

        // Limit recording time to 1 sec and max file to 1MB
        mediaRecorder!!.setMaxDuration(1000)
        mediaRecorder!!.setMaxFileSize((1024 * 1024).toLong())

        // Wait until one of limits is reached; must not use limitMonitor = new Object() => limitMonitor.notifyAll();;
        mediaRecorder!!.setOnInfoListener { mr: MediaRecorder?, what: Int, extra: Int ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                    || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                Timber.d("Limit monitor notified: %s", what)
                mediaRecorder!!.setOnInfoListener(null)
                closeCaptureSession()
                mediaRecorder!!.stop()
                mediaRecorder!!.reset()
                try {
                    // Retrieve SPS and PPS parameters
                    val h264Params = H264Parameters(mOutputFile)
                    H264Parameters.storeParameters(h264Params, mVideoFormat)
                    h264Params.logParameters()
                } catch (e: IOException) {
                    Timber.e("H264Parameters extraction exception: %s", e.message)
                }

                // Remove sample video
                if (!outFile.delete()) {
                    Timber.e("Sample file could not be removed")
                }

                // Start final mediaRecord after obtainParameters()
                startVideoRecording()
            }
        }

        // Must use createCaptureSession() to start mediaRecorder, else sampleFile is empty
        mediaRecorder!!.prepare()
        try {
            mCaptureBuilder = mCameraDevice!!.createCaptureRequest(TEMPLATE_RECORD)

            // Set up Surface for the MediaRecorder
            val recorderSurface = mediaRecorder!!.surface
            mCaptureBuilder!!.addTarget(recorderSurface)

            // Start a capture session; Once the session starts, we can start recording
            mCameraDevice!!.createCaptureSession(listOf(recorderSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    mCaptureSession = session
                    mediaRecorder!!.start()
                    updateCaptureRequest()
                    Timber.d("Capture session: %s", session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Timber.e("Obtaining h264 parameters failed")
                }
            }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            Timber.e("Media recorder capture session exception: %s", e.message)
        }
    }

    /**
     * Stops the transfer of media data from this `DataSource`.
     *
     * @throws java.io.IOException if anything goes wrong while stopping the transfer of media data from this `DataSource`
     * @see AbstractPushBufferCaptureDevice.doStop
     */
    @Synchronized
    @Throws(IOException::class)
    override fun doStop() {
        closeMediaRecorder()
        stopBackgroundThread()
        super.doStop()

        /*
         * We will schedule stop and release on the mediaRecorder, close the localSocket while stop
         * and release on the mediaRecorder is starting or executing, wait for stop and release on
         * the mediaRecorder to complete, and release the camera.
         */
        var mediaRecorderStopState: IntArray? = null
        try {
            if (mediaRecorder != null) {
                mediaRecorderStopState = try {
                    stop(mediaRecorder!!)
                } finally {
                    mediaRecorder = null
                }
            }
        } finally {
            if (mLocalSocket != null) {
                try {
                    mLocalSocket!!.close()
                } catch (ioe: IOException) {
                    Timber.w(ioe, "Failed to close LocalSocket.")
                } finally {
                    mLocalSocket = null
                    mLocalSocketKey = null
                }
            }
            if (mediaRecorderStopState != null) {
                var stopped = false
                /*
                 * Unfortunately, MediaRecorder may never stop and/or release. So we will not wait forever.
                 */
                var maxWaits = -1
                var interrupted = false
                while (!stopped) {
                    synchronized(mediaRecorderStopState) {
                        when (mediaRecorderStopState[0]) {
                            MEDIA_RECORDER_STOPPED -> stopped = true

                            MEDIA_RECORDER_STOPPING -> {
                                if (maxWaits == -1) maxWaits = 10
                                if (maxWaits == 0) {
                                    stopped = true
                                } else if (maxWaits > 0) {
                                    maxWaits--
                                    try {
                                        (mediaRecorderStopState as Object).wait(500)
                                    } catch (ie: InterruptedException) {
                                        interrupted = true
                                    }
                                }
                            }

                            else -> try {
                                (mediaRecorderStopState as Object).wait(500)
                            } catch (ie: InterruptedException) {
                                interrupted = true
                            }
                        }
                    }
                    if (stopped) break
                }

                if (interrupted) Thread.currentThread().interrupt()
                if (mediaRecorderStopState[0] != MEDIA_RECORDER_STOPPED) {
                    Timber.d("Stopping/releasing MediaRecorder seemed to take a long time - give up.")
                }
            }
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeMediaRecorder() {
        if (mCameraDevice != null) {
            try {
                mCameraOpenCloseLock.acquire()
                closeCaptureSession()
                if (null != mCameraDevice) {
                    mCameraDevice!!.close()
                    mCameraDevice = null
                }
                videoFrameInterval = 0
                if (mediaRecorder != null) {
                    mediaRecorder!!.stop()
                    mediaRecorder!!.release()
                    mediaRecorder = null
                }
            } catch (e: InterruptedException) {
                throw RuntimeException("Interrupted while trying to close camera.", e)
            } finally {
                mCameraOpenCloseLock.release()
                mSurfaceProvider!!.onObjectReleased()
            }
        }
    }

    private val streamFormats: Array<Format?>
        get() {
            val formatControls = formatControls
            val count = formatControls.size
            val streamFormats = arrayOfNulls<Format>(count)
            for (i in 0 until count) {
                val formatControl = formatControls[i]
                var format = formatControl.format
                if (format == null) {
                    val supportedFormats = formatControl.supportedFormats
                    if (supportedFormats != null && supportedFormats.isNotEmpty()) {
                        format = supportedFormats[0]
                    }
                }
                streamFormats[i] = format
            }
            return streamFormats
        }

    @Throws(IOException::class)
    private fun createSockets() {
        mParcelFileDescriptors = ParcelFileDescriptor.createPipe()
        mParcelRead = ParcelFileDescriptor(mParcelFileDescriptors[0])
        mParcelWrite = ParcelFileDescriptor(mParcelFileDescriptors[1])
    }

    // ===================== LocalServerSocket (not further support >= API-21 =========================
    @Throws(IOException::class)
    private fun createLocalSocket(): FileDescriptor {
        var localServerSocket: LocalServerSocket?
        synchronized(DataSource::class.java) {
            if (mLocalServerSocket == null) {
                mLocalServerSocket = LocalServerSocket(LOCAL_SERVER_SOCKET_NAME)
                val localServerSocketThread = object : Thread() {
                    override fun run() {
                        runInLocalServerSocketThread()
                    }
                }
                localServerSocketThread.isDaemon = true
                localServerSocketThread.name = mLocalServerSocket!!.localSocketAddress.name
                localServerSocketThread.start()
            }
            localServerSocket = mLocalServerSocket
        }
        if (mLocalSocket != null) {
            try {
                mLocalSocket!!.close()
            } catch (ioe: IOException) {
                Timber.e("IO Exception: %s", ioe.message)
            }
        }
        if (mLocalSocketKey != null) mLocalSocketKey = null
        mLocalSocket = LocalSocket()
        mLocalSocketKey = nextLocalSocketKey.toString()

        /*
         * Since one LocalServerSocket is being used by multiple DataSource instances, make sure
         * that the LocalServerSocket will be able to determine which DataSource is to receive the
         * media data delivered though a given LocalSocket.
         */
        val dataSourceKey = mDataSourceKey
        try {
            val dataSourceKeyBytes = "$dataSourceKey\n".toByteArray(StandardCharsets.UTF_8)
            val dataSourceKeySize = dataSourceKeyBytes.size
            val localSocketKeyBytes = "$mLocalSocketKey\n".toByteArray(StandardCharsets.UTF_8)
            val localSocketKeySize = localSocketKeyBytes.size
            synchronized(DataSource::class.java) {
                dataSources[dataSourceKey] = this
                if (maxDataSourceKeySize < dataSourceKeySize) maxDataSourceKeySize = dataSourceKeySize
            }
            if (maxLocalSocketKeySize < localSocketKeySize) maxLocalSocketKeySize = localSocketKeySize
            mLocalSocket!!.connect(localServerSocket!!.localSocketAddress)
            val outputStream = mLocalSocket!!.outputStream
            outputStream.write(dataSourceKeyBytes)
            outputStream.write(localSocketKeyBytes)
        } catch (ioe: IOException) {
            synchronized(DataSource::class.java) { dataSources.remove(dataSourceKey) }
            throw ioe
        }
        return mLocalSocket!!.fileDescriptor
    }

    private fun localSocketAccepted(localSocket: LocalSocket, inputStream: InputStream?) {
        var dump: OutputStream? = null
        try {
            /*
             * After this DataSource closes its write LocalSocket, the read LocalSocket will
             * continue to read bytes which have already been written into the write LocalSocket
             * before the closing. In order to prevent the pushing of such invalid data out of the
             * PushBufferStream of this DataSource, the read LocalSocket should identify each of its
             * pushes with the key of the write LocalSocket. Thus this DataSource will recognize and
             * discard the invalid data.
             */
            var maxLocalSocketKeySize: Int
            synchronized(this) { maxLocalSocketKeySize = this.maxLocalSocketKeySize }
            val localSocketKey: String
            if (maxLocalSocketKeySize > 0) {
                localSocketKey = readLine(inputStream, maxLocalSocketKeySize)
                if (localSocketKey == null) throw IOException(UNEXPECTED_IOEXCEPTION_MESSAGE)
            } else throw IOException(UNEXPECTED_IOEXCEPTION_MESSAGE)

            /*
             * The indicator which determines whether the sequence and picture parameter sets are
             * yet to be written. Technically, we could be writing them when we see a
             * FILE_TYPE_BOX_TYPE. Unfortunately, we have experienced a racing after a reINVITE
             * between the RTCP BYE packet and the RTP packets carrying the parameter sets. Since
             * the MEDIA_DATA_BOX_TYPE comes relatively late after the FILE_TYPE_BOX_TYPE, we will
             * just write the parameter sets when we see the first piece of actual media data from
             * the MEDIA_DATA_BOX_TYPE.
             */
            var writeParameterSets = true
            if (DUMP_FILE != null) dump = FileOutputStream("$DUMP_FILE.$localSocketKey")
            while (true) {
                if (dump != null) {
                    dump.write(inputStream!!.read())
                    continue
                }
                var size = readUnsignedInt32(inputStream)
                val type = readUnsignedInt32(inputStream)
                if (type == FILE_TYPE_BOX_TYPE) {
                    /*
                     * Android's MPEG4Writer writes the ftyp box by initially writing a size of zero,
                     * then writing the other fields and finally overwriting the size with the correct value.
                     */
                    size = (4 /* size */
                            + 4 /* type */
                            + 4 /* major_brand */
                            + 4 /* minor_version */
                            + 4 /* compatible_brands[0] == "isom" */
                            + 4).toLong() /* compatible_brands[1] == "3gp4" */
                    discard(inputStream, size - (4 /* size */ + 4 /* type */))
                    if (size != readUnsignedInt32(inputStream)) {
                        throw IOException(UNEXPECTED_IOEXCEPTION_MESSAGE)
                    }
                } else if (type == FREE_SPACE_BOX_TYPE) {
                    /*
                     * Android's MPEG4Writer writes a free box with size equal to the estimated
                     * number of bytes of the moov box. When the MPEG4Writer is stopped, it seeks
                     * back and splits the free box into a moov box and a free box both of which fit
                     * into the initial free box.
                     */
                } else if (type == MEDIA_DATA_BOX_TYPE) {
                    while (true) {
                        var nalLength = readUnsignedInt32(inputStream)

                        // Some devices write ASCII ???? ???? at this point we can retry here
                        if (nalLength == 1061109567L) {
                            Timber.w("Detected ???? ???? NAL length, trying to discard...")
                            // Currently read only 4(????) need 4 more
                            discard(inputStream, 4)
                            // Try to read nal length again
                            nalLength = readUnsignedInt32(inputStream)
                        }
                        if (nalLength in 1..MAX_NAL_LENGTH) {
                            if (writeParameterSets) {
                                writeParameterSets = false
                                val sps = h264Params!!.sps
                                val pps = h264Params!!.pps
                                        ?: throw NullPointerException("Expression 'h264Params' must not be null")
                                /*
                                 * Android's MPEG4Writer will not write the sequence and picture
                                 * parameter set until the associated MediaRecorder is stopped.
                                 */
                                readNAL(localSocketKey, sps, sps!!.size)
                                readNAL(localSocketKey, pps, pps.size)
                            }
                            readNAL(localSocketKey, inputStream, nalLength.toInt())
                        } else {
                            throw IOException(UNEXPECTED_IOEXCEPTION_MESSAGE)
                        }
                    }
                } else {
                    if (size == 1L) {
                        /* largesize */
                        size = readUnsignedInt64(inputStream) - 8
                    }
                    if (size == 0L) {
                        throw IOException(UNSUPPORTED_BOXSIZE_IOE_MESSAGE)
                    } else discard(inputStream, size - (4 /* size */ + 4 /* type */))
                }
            }
        } catch (iae: IllegalArgumentException) {
            Timber.e(iae, "Failed to read from MediaRecorder.")
        } catch (iae: IOException) {
            Timber.e(iae, "Failed to read from MediaRecorder.")
        } finally {
            try {
                localSocket.close()
            } catch (ignore: IOException) {
            }
            if (dump != null) {
                try {
                    dump.close()
                } catch (ignore: IOException) {
                }
            }
        }
    }

    /**
     * Notifies this `DataSource` that a NAL unit has just been read from the associated
     * `MediaRecorder` into [.nal].
     */
    private fun nalRead() {
        val nal_unit_type = nal[0].toInt() and 0x1F
        when (prevNALUnitType) {
            6, 7, 8, 9 -> {}
            0, 1, 5 -> accessUnitTimeStamp += videoFrameInterval
            else -> accessUnitTimeStamp += videoFrameInterval
        }
        when (nal_unit_type) {
            7, 8 -> {
                lastWrittenParameterSetTime = System.currentTimeMillis()
                nalFlags = 0
            }
            6, 9 -> nalFlags = 0
            0, 1, 5 -> nalFlags = Buffer.FLAG_RTP_MARKER
            else -> nalFlags = Buffer.FLAG_RTP_MARKER
        }
        prevNALUnitType = nal_unit_type
    }

    @Throws(IOException::class)
    private fun readNAL(localSocketKey: String, bytes: ByteArray?, nalLength: Int) {
        synchronized(this) {
            if (mLocalSocketKey == null || mLocalSocketKey != localSocketKey) throw IOException(STREAM_CLOSED_IOE_MESSAGE)
            synchronized(nalSyncRoot) {
                if (nal.size < nalLength)
                    nal = ByteArray(nalLength)
                this.nalLength = 0

                if (bytes!!.size < nalLength) throw IOException(EOS_IOE_MESSAGE) else {
                    System.arraycopy(bytes, 0, nal, 0, nalLength)
                    this.nalLength = nalLength

                    // Notify this DataSource that a NAL unit has just been read from the MediaRecorder into #nal.
                    nalRead()
                }
            }
        }
        writeNAL()
    }

    @Throws(IOException::class)
    private fun readNAL(localSocketKey: String, inputStream: InputStream?, nalLength: Int) {
        var delayed: ByteArray? = null
        synchronized(this) {
            if (mLocalSocketKey == null || mLocalSocketKey != localSocketKey) throw IOException(STREAM_CLOSED_IOE_MESSAGE)
            synchronized(nalSyncRoot) {
                if (nal.size < nalLength)
                    nal = ByteArray(nalLength)
                this.nalLength = 0
                var remainingToRead = nalLength
                var totalRead = 0

                while (remainingToRead > 0) {
                    val read = inputStream!!.read(nal, totalRead, remainingToRead)
                    if (-1 == read) throw IOException(EOS_IOE_MESSAGE) else {
                        remainingToRead -= read
                        totalRead += read
                    }
                }

                this.nalLength = nalLength
                if (this.nalLength > 0) {
                    when (nal[0].toInt() and 0x1F) {
                        5, 6 -> {
                            val now = System.currentTimeMillis()
                            if (now - lastWrittenParameterSetTime > PARAMETER_SET_INTERVAL) {
                                delayed = ByteArray(this.nalLength)
                                System.arraycopy(nal, 0, delayed!!, 0, this.nalLength)
                                this.nalLength = 0
                            }
                        }
                    }
                }

                if (delayed == null) {
                    // Notify this DataSource that a NAL unit has just been read from the MediaRecorder into #nal.
                    nalRead()
                }
            }
        }
        if (delayed == null) {
            writeNAL()
        } else {
            readNAL(localSocketKey, h264Params!!.sps, h264Params!!.sps!!.size)
            readNAL(localSocketKey, h264Params!!.pps, h264Params!!.pps!!.size)
            readNAL(localSocketKey, delayed, delayed!!.size)
        }
    }

    /**
     * Attempts to set the `Format` to be reported by the `FormatControl` of a
     * `PushBufferStream` at a specific zero-based index in the list of streams of this
     * `PushBufferDataSource`. The `PushBufferStream` does not exist at the time of
     * the attempt to set its `Format`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` the `Format` of which is to be set
     * @param oldValue the last-known `Format` for the `PushBufferStream` at the specified `streamIndex`
     * @param newValue the `Format` which is to be set
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PushBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PushBufferStream` or `null` if the attempt to set the
     * `Format` did not success and any last-known `Format` is to be left in effect
     * @see AbstractPushBufferCaptureDevice.setFormat
     */
    override fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
        // This DataSource supports setFormat.
        return newValue as? VideoFormat ?: super.setFormat(streamIndex, oldValue, newValue)
    }

    /**
     * Asynchronously calls [MediaRecorder.stop] and [MediaRecorder.release] on a
     * specific `MediaRecorder`. Allows initiating `stop` and `release` on the
     * specified `mediaRecorder` which may be slow and performing additional cleanup in the meantime.
     *
     * @param mediaRecorder the `MediaRecorder` to stop and release
     * @return an array with a single `int` element which represents the state of the stop
     * and release performed by the method. The array is signaled upon changes to its
     * element's value via [Object.notify]. The value is one of
     * [.MEDIA_RECORDER_STOPPING] and [.MEDIA_RECORDER_STOPPED].
     */
    private fun stop(mediaRecorder: MediaRecorder): IntArray {
        val state = IntArray(1)
        val mediaRecorderStop = object : Thread("MediaRecorder.stop") {
            override fun run() {
                try {
                    synchronized(state) {
                        state[0] = MEDIA_RECORDER_STOPPING
                        state.notify()
                    }
                    Timber.d("Stopping MediaRecorder in %s", this)
                    mediaRecorder.stop()
                    Timber.d("Releasing MediaRecorder in %s", this)
                    mediaRecorder.release()
                } catch (t: Throwable) {
                    if (t is ThreadDeath) throw t else {
                        Timber.d("Failed to stop and release MediaRecorder: %s", t.message)
                    }
                } finally {
                    synchronized(state) {
                        state[0] = MEDIA_RECORDER_STOPPED
                        state.notify()
                    }
                }
            }
        }
        mediaRecorderStop.isDaemon = true
        mediaRecorderStop.start()
        return state
    }

    /**
     * Writes the (current) [.nal] into the `MediaRecorderStream` made available by this `DataSource`.
     */
    private fun writeNAL() {
        var stream: MediaRecorderStream?
        synchronized(streamSyncRoot) {
            val streams = streams
            stream = if (streams.isNotEmpty()) streams[0] as MediaRecorderStream else null
        }
        if (stream != null) stream!!.writeNAL()
    }

    private class MediaRecorderStream(dataSource: DataSource?, formatControl: FormatControl?) : AbstractPushBufferStream<PushBufferDataSource?>(dataSource, formatControl) {

        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            val dataSource = dataSource as DataSource
            var byteLength = H264.NAL_PREFIX.size + FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE
            var bytes: ByteArray? = null
            var flags = 0
            var timeStamp = 0L

            synchronized(dataSource.nalSyncRoot) {
                val nalLength = dataSource.nalLength
                if (nalLength > 0) {
                    byteLength += nalLength
                    val data = buffer.data

                    if (data is ByteArray) {
                        bytes = data
                        if (bytes!!.size < byteLength) bytes = null
                    } else bytes = null

                    if (bytes == null) {
                        bytes = ByteArray(byteLength)
                        buffer.data = bytes
                    }

                    System.arraycopy(dataSource.nal, 0, bytes!!, H264.NAL_PREFIX.size, nalLength)
                    flags = dataSource.nalFlags
                    timeStamp = dataSource.accessUnitTimeStamp
                    dataSource.nalLength = 0
                }
            }
            buffer.offset = 0
            if (bytes == null) {
                buffer.length = 0
            }
            else {
                System.arraycopy(H264.NAL_PREFIX, 0, bytes!!, 0, H264.NAL_PREFIX.size)
                Arrays.fill(bytes!!, byteLength - FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE, byteLength, 0.toByte())
                buffer.flags = Buffer.FLAG_RELATIVE_TIME
                buffer.length = byteLength
                buffer.timeStamp = timeStamp
            }
        }

        /**
         * Writes the (current) [DataSource.nal] into this `MediaRecorderStream` i.e.
         * forces this `MediaRecorderStream` to notify its associated
         * `BufferTransferHandler` that data is available for transfer.
         */
        fun writeNAL() {
            val transferHandler = mTransferHandler
            transferHandler?.transferData(this)
        }
    }

    // ===============================================================
    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground")
            backgroundThread!!.start()
            mBackgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    companion object {
        /**
         * The path of the file into which the bytes read from [.mediaRecorder] are to be dumped.
         * If the value is not `null`, the bytes in question will only be dumped to the specified
         * file and will not be made available through the `DataSource`.
         */
        private val DUMP_FILE: String? = null
        private const val EOS_IOE_MESSAGE = "END_OF_STREAM"
        private val FREE_SPACE_BOX_TYPE = stringToBoxType("free")
        private val FILE_TYPE_BOX_TYPE = stringToBoxType("ftyp")
        private const val INTEGER_OVERFLOW_IOE_MESSAGE = "INTEGER_OVERFLOW"

        /**
         * The name of the `LocalServerSocket` created by the `DataSource` class to be
         * utilized by the `MediaRecorder`s which implement the actual capturing of the media
         * data for the purposes of the `DataSource` instances.
         */
        private val LOCAL_SERVER_SOCKET_NAME = DataSource::class.java.name + ".localServerSocket"
        private const val PARAMETER_SET_INTERVAL = 750L

        /**
         * The maximum size of a NAL unit. RFC 6184 &quot;RTP Payload Format for H.264 Video&quot;
         * states: the maximum size of a NAL unit encapsulated in any aggregation packet is 65535 bytes.
         */
        private const val MAX_NAL_LENGTH = 65535L
        private val MEDIA_DATA_BOX_TYPE = stringToBoxType("mdat")
        private const val MEDIA_RECORDER_STOPPING = 1
        private const val MEDIA_RECORDER_STOPPED = 2
        private const val STREAM_CLOSED_IOE_MESSAGE = "STREAM_CLOSED"

        /**
         * The priority to be set to the thread executing the [MediaRecorderStream.read]
         * method of a given `MediaRecorderStream`.
         */
        private const val THREAD_PRIORITY = Process.THREAD_PRIORITY_URGENT_DISPLAY
        private const val UNEXPECTED_IOEXCEPTION_MESSAGE = "UNEXPECTED"
        private const val UNSUPPORTED_BOXSIZE_IOE_MESSAGE = "UNSUPPORTED_BOX_SIZE"
        private val dataSources = HashMap<String, DataSource>()
        private var mLocalServerSocket: LocalServerSocket? = null
        private var maxDataSourceKeySize = 0

        private var nextDataSourceKey = 0L
            get() {
                synchronized(DataSource::class.java) { return field++ }
            }

        private var mCameraId: String? = null
        private fun boxTypeToString(type: Long): String {
            val bytes = ByteArray(4)
            val end = bytes.size - 1
            for (i in end downTo 0) bytes[end - i] = (type shr 8 * i and 0xFFL).toByte()
            return String(bytes, StandardCharsets.US_ASCII)
        }

        @Throws(IOException::class)
        private fun readLine(inputStream: InputStream?, maxSize: Int): String {
            var size = 0
            var b = -1
            val bytes = ByteArray(maxSize)

            while ((size < maxSize) && (inputStream!!.read().also { b = it } != -1) && (b != '\n'.code)) {
                bytes[size] = b.toByte()
                size++
            }

            return String(bytes, 0, size, StandardCharsets.UTF_8)
        }

        // ===================== End of LocalServerSocket (not further support >= API-21 =========================
        @Throws(IOException::class)
        fun readUnsignedInt(inputStream: InputStream?, byteCount: Int): Long {
            var value = 0L
            for (i in byteCount - 1 downTo 0) {
                val b = inputStream!!.read()
                value = if (-1 == b) throw IOException(EOS_IOE_MESSAGE) else {
                    if (i == 7 && b and 0x80 != 0) throw IOException(INTEGER_OVERFLOW_IOE_MESSAGE)
                    value or (b.toLong() and 0xFFL shl 8) * i
                }
            }
            return value
        }

        @Throws(IOException::class)
        private fun readUnsignedInt32(inputStream: InputStream?): Long {
            return readUnsignedInt(inputStream, 4)
        }

        @Throws(IOException::class)
        private fun readUnsignedInt64(inputStream: InputStream?): Long {
            return readUnsignedInt(inputStream, 8)
        }

        private fun runInLocalServerSocketThread() {
            while (true) {
                var localServerSocket: LocalServerSocket?
                synchronized(DataSource::class.java) { localServerSocket = mLocalServerSocket }
                if (localServerSocket == null) break
                var localSocket: LocalSocket? = null
                try {
                    localSocket = localServerSocket!!.accept()
                } catch (ioe: IOException) {
                    /*
                 * At the time of this writing, an IOException during the execution of LocalServerSocket#accept()
                 * will leave localSocket to be equal to null which will in turn break the while loop.
                 */
                }
                if (localSocket == null) break
                var maxDataSourceKeySize: Int
                synchronized(DataSource::class.java) { maxDataSourceKeySize = Companion.maxDataSourceKeySize }
                if (maxDataSourceKeySize < 1) {
                    // We are not currently expecting such a connection so ignore whoever has connected.
                    try {
                        localSocket.close()
                    } catch (ioe: IOException) {
                        Timber.e("IO Exception: %s", ioe.message)
                    }
                } else {
                    val finalLocalSocket = localSocket
                    val finalMaxDataSourceKeySize = maxDataSourceKeySize
                    val localSocketAcceptedThread = object : Thread() {
                        override fun run() {
                            runInLocalSocketAcceptedThread(finalLocalSocket, finalMaxDataSourceKeySize)
                        }
                    }
                    localSocketAcceptedThread.isDaemon = true
                    localSocketAcceptedThread.name = DataSource::class.java.name + ".LocalSocketAcceptedThread"
                    localSocketAcceptedThread.start()
                }
            }
        }

        private fun runInLocalSocketAcceptedThread(localSocket: LocalSocket, maxDataSourceKeySize: Int) {
            var inputStream: InputStream? = null
            var dataSourceKey: String? = null
            var closeLocalSocket = true
            try {
                inputStream = localSocket.inputStream
                // inputStream = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);
                dataSourceKey = readLine(inputStream, maxDataSourceKeySize)
            } catch (ioe: IOException) {
                /*
             * The connection does not seem to be able to identify its associated DataSource so
             * ignore whoever has made that connection.
             */
            }
            if (dataSourceKey != null) {
                var dataSource: DataSource?
                synchronized(DataSource::class.java) {
                    dataSource = dataSources[dataSourceKey]
                    if (dataSource != null) {
                        /*
                     * Once the DataSource instance to receive the media data received though the
                     * LocalSocket has been determined, the association by key is no longer
                     * necessary.
                     */
                        dataSources.remove(dataSourceKey)
                    }
                }
                if (dataSource != null) {
                    dataSource!!.localSocketAccepted(localSocket, inputStream)
                    closeLocalSocket = false
                }
            }
            if (closeLocalSocket) {
                try {
                    localSocket.close()
                } catch (ioe: IOException) {
                    /*
                 * Apart from logging, there do not seem to exist a lot of reasonable alternatives to just ignoring it.
                 */
                }
            }
        }

        /**
         * Sets the priority of the calling thread to [.THREAD_PRIORITY].
         */
        fun setThreadPriority() {
            org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource.setThreadPriority(THREAD_PRIORITY)
        }

        private fun stringToBoxType(str: String): Long {
            val bytes = str.toByteArray(StandardCharsets.US_ASCII)
            val end = bytes.size - 1
            var value = 0L
            for (i in end downTo 0) value = value or (bytes[end - i].toLong() and 0xFFL shl 8) * i
            return value
        }
    }
}