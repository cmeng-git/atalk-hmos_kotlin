/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording

import org.atalk.impl.neomedia.device.AudioMixerMediaDevice
import org.atalk.impl.neomedia.device.MediaDeviceSession
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.MediaException
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.recording.Recorder
import org.atalk.service.neomedia.recording.RecorderEvent
import org.atalk.service.neomedia.recording.RecorderEventHandler
import org.atalk.service.neomedia.recording.Synchronizer
import org.atalk.util.MediaType
import org.atalk.util.SoundFileUtils
import java.io.IOException
import java.util.*
import javax.media.*
import javax.media.protocol.*

/**
 * The call recording implementation. Provides the capability to start and stop call recording.
 *
 * @author Dmitri Melnikov
 * @author Lubomir Marinov
 * @author Boris Grozev
 */
class RecorderImpl(device: AudioMixerMediaDevice?) : Recorder {
    /**
     * The `AudioMixerMediaDevice` which is to be or which is already being recorded by this `Recorder`.
     */
    private val device: AudioMixerMediaDevice

    /**
     * The `RecorderEventHandler` which this `Recorder` should notify when events
     * related to recording (such as start/end of a recording) occur.
     */
    private var eventHandler: RecorderEventHandler? = null

    /**
     * The `MediaDeviceSession` is used to create an output data source.
     */
    private var deviceSession: MediaDeviceSession? = null

    /**
     * The `List` of `Recorder.Listener`s interested in notifications from this `Recorder`.
     */
    private val listeners = ArrayList<Recorder.Listener>()

    /**
     * `DataSink` used to save the output data.
     */
    private var sink: DataSink? = null

    /**
     * The indicator which determines whether this `Recorder` is set to skip media from mic.
     */
    private var mute = false
    /**
     * Returns the filename we are last started or stopped recording to, null if not started.
     *
     * @return the filename we are last started or stopped recording to, null if not started.
     */
    /**
     * The filename we will use to record data, supplied when Recorder is started.
     */
    override var filename: String? = null
        private set

    /**
     * Constructs the `RecorderImpl` with the provided session.
     *
     * device device that can create a session that provides the output data source
     */
    init {
        if (device == null) throw NullPointerException("device")
        this.device = device
    }

    /**
     * Adds a new `Recorder.Listener` to the list of listeners interested in notifications
     * from this `Recorder`.
     *
     * @param listener the new `Recorder.Listener` to be added to the list of listeners interested in
     * notifications from this `Recorder`
     * @see Recorder.addListener
     */
    override fun addListener(listener: Recorder.Listener) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
    }

    /**
     * Returns a content descriptor to create a recording session with.
     *
     * @param format the format that corresponding to the content descriptor
     * @return content descriptor
     * @throws IllegalArgumentException if the specified `format` is not a supported recording format
     */
    @Throws(IllegalArgumentException::class)
    private fun getContentDescriptor(format: String): ContentDescriptor {
        val type = when {
            SoundFileUtils.wav.equals(format, ignoreCase = true) -> FileTypeDescriptor.WAVE
            SoundFileUtils.mp3.equals(format, ignoreCase = true) -> FileTypeDescriptor.MPEG_AUDIO
            SoundFileUtils.gsm.equals(format, ignoreCase = true) -> FileTypeDescriptor.GSM
            SoundFileUtils.au.equals(format, ignoreCase = true) -> FileTypeDescriptor.BASIC_AUDIO
            SoundFileUtils.aif.equals(format, ignoreCase = true) -> FileTypeDescriptor.AIFF
            else -> {
                throw IllegalArgumentException("$format is not a supported recording format.")
            }
        }
        return ContentDescriptor(type)
    }

    /**
     * Gets a list of the formats in which this `Recorder` supports recording media.
     *
     * @return a `List` of the formats in which this `Recorder` supports recording
     * media
     * @see Recorder.supportedFormats
     */
    override val supportedFormats: List<String>
        get() = Arrays.asList(*SUPPORTED_FORMATS)

    /**
     * Removes a existing `Recorder.Listener` from the list of listeners interested in
     * notifications from this `Recorder`.
     *
     * @param listener the existing `Recorder.Listener` to be removed from the list of listeners
     * interested in notifications from this `Recorder`
     * @see Recorder.removeListener
     */
    override fun removeListener(listener: Recorder.Listener) {
        if (listener != null) {
            synchronized(listeners) { listeners.remove(listener) }
        }
    }

    /**
     * Starts the recording of the media associated with this `Recorder` (e.g. the media
     * being sent and received in a `Call`) into a file with a specific name.
     *
     * @param format the format into which the media associated with this `Recorder` is to be
     * recorded into the specified file
     * @param filename the name of the file into which the media associated with this `Recorder` is to
     * be recorded
     * @throws IOException if anything goes wrong with the input and/or output performed by this
     * `Recorder`
     * @throws MediaException if anything else goes wrong while starting the recording of media performed by this
     * `Recorder`
     * @see Recorder.start
     */
    @Throws(IOException::class, MediaException::class)
    override fun start(format: String?, filename: String?) {
        var fName = filename
        if (sink == null) {
            if (format == null) throw NullPointerException("format")
            if (fName == null) throw NullPointerException("filename")
            this.filename = fName

            /*
             * A file without an extension may not only turn out to be a touch more difficult to
             * play but is suspected to also cause an exception inside of JMF.
             */
            val extensionBeginIndex = fName.lastIndexOf('.')
            if (extensionBeginIndex < 0) fName += ".$format" else if (extensionBeginIndex == fName.length - 1) fName += format
            val deviceSession = device.createSession()
            try {
                deviceSession.setContentDescriptor(getContentDescriptor(format))

                // set initial mute state, if mute was set before starting the
                // recorder
                deviceSession.isMute = mute

                /*
                 * This RecorderImpl will use deviceSession to get a hold of the media being set to
                 * the remote peers associated with the same AudioMixerMediaDevice i.e. this
                 * RecorderImpl needs deviceSession to only capture and not play back.
                 */
                deviceSession.start(MediaDirection.SENDONLY)
                this.deviceSession = deviceSession
            } finally {
                if (this.deviceSession == null) {
                    throw MediaException("Failed to create MediaDeviceSession from"
                            + " AudioMixerMediaDevice for the purposes of recording")
                }
            }
            var exception: Throwable? = null
            try {
                val outputDataSource = deviceSession.outputDataSource
                val sink = Manager.createDataSink(outputDataSource,
                        MediaLocator("file: $fName"))
                sink.open()
                sink.start()
                this.sink = sink
            } catch (ndsex: NoDataSinkException) {
                exception = ndsex
            } finally {
                if (sink == null || exception != null) {
                    stop()
                    throw MediaException("Failed to start recording into file "
                            + fName, exception)
                }
            }
            if (eventHandler != null) {
                val event = RecorderEvent()
                event.type = RecorderEvent.Type.RECORDING_STARTED
                event.instant = System.currentTimeMillis()
                event.mediaType = MediaType.AUDIO
                event.filename = fName
                eventHandler!!.handleEvent(event)
            }
        }
    }

    /**
     * Stops the recording of the media associated with this `Recorder` (e.g. the media
     * being
     * sent and received in a `Call`) if it has been started and prepares this
     * `Recorder` for garbage collection.
     *
     * @see Recorder.stop
     */
    override fun stop() {
        if (deviceSession != null) {
            deviceSession!!.close(MediaDirection.SENDRECV)
            deviceSession = null
        }
        if (sink != null) {
            sink!!.close()
            sink = null

            /*
             * RecorderImpl creates the sink upon start() and it does it only if it is null so this
             * RecorderImpl has really stopped only if it has managed to close() the (existing)
             * sink. Notify the registered listeners.
             */
            var listeners: Array<Recorder.Listener>
            synchronized(this.listeners) { listeners = this.listeners.toTypedArray() }
            for (listener in listeners) listener.recorderStopped(this)
            if (eventHandler != null) {
                val event = RecorderEvent()
                event.type = RecorderEvent.Type.RECORDING_ENDED
                event.instant = System.currentTimeMillis()
                event.mediaType = MediaType.AUDIO
                event.filename = filename
                eventHandler!!.handleEvent(event)
            }
        }
    }

    /**
     * Put the recorder in mute state. It won't record the local input. This is used when the local
     * call is muted and we don't won't to record the local input.
     *
     * @param mute the new value of the mute property
     */
    override fun setMute(mute: Boolean) {
        this.mute = mute
        if (deviceSession != null) deviceSession!!.isMute = mute
    }

    /**
     * {@inheritDoc}
     */
    override fun setEventHandler(eventHandler: RecorderEventHandler?) {
        this.eventHandler = eventHandler
    }
    /**
     * {@inheritDoc}
     *
     *
     * This `Recorder` implementation does not use a `Synchronizer`.
     */
    /**
     * {@inheritDoc}
     *
     *
     * This `Recorder` implementation does not use a `Synchronizer`.
     */
    override var synchronizer: Synchronizer?
        get() = null
        set(synchronizer) {}

    /**
     * {@inheritDoc}
     */
    override val mediaStream: MediaStream?
        get() = null

    companion object {
        /**
         * The list of formats in which `RecorderImpl` instances support recording media.
         */
        val SUPPORTED_FORMATS = arrayOf( /* Disables formats currently not working
            SoundFileUtils.aif,
            SoundFileUtils.au,
            SoundFileUtils.gsm, */
                SoundFileUtils.wav,
                SoundFileUtils.mp3
        )
    }
}