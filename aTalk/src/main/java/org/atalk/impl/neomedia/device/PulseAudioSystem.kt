/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.MediaUtils
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.PulseAudioRenderer
import org.atalk.impl.neomedia.pulseaudio.PA
import org.atalk.impl.neomedia.pulseaudio.PA.sink_info_cb_t
import org.atalk.impl.neomedia.pulseaudio.PA.source_info_cb_t
import org.atalk.service.version.Version
import java.io.IOException
import java.util.*
import javax.media.Format
import javax.media.MediaLocator
import javax.media.Renderer
import javax.media.format.AudioFormat

/**
 * Implements an `AudioSystem` using the native PulseAudio API/library.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class PulseAudioSystem
/**
 * Initializes a new `PulseAudioSystem` instance.
 *
 * @throws Exception if anything goes wrong while initializing the new instance
 */
    : AudioSystem(LOCATOR_PROTOCOL, FEATURE_NOTIFY_AND_PLAYBACK_DEVICES) {
    /**
     * The indicator which specifies whether the method [.createContext] has been executed.
     * Used to enforce a single execution of the method in question.
     */
    private var createContext = false

    /**
     * The connection context for asynchronous communication with the PulseAudio server.
     */
    private var context = 0L

    /**
     * The PulseAudio main loop associated with this `PulseAudioSystem`.
     */
    private var mainloop = 0L

    /**
     * Initializes the connection context for asynchronous communication with the PulseAudio server i.e. creates [.context].
     */
    private fun createContext() {
        check(context == 0L) { "context" }
        startMainloop()
        try {
            var proplist = PA.proplist_new()
            if (proplist == 0L) throw RuntimeException("pa_proplist_new")
            try {
                populateContextProplist(proplist)
                val context = PA.context_new_with_proplist(PA.threaded_mainloop_get_api(mainloop),
                        null /* PA_PROP_APPLICATION_NAME */, proplist)
                if (context == 0L) throw RuntimeException("pa_context_new_with_proplist")
                try {
                    PA.proplist_free(proplist)
                    proplist = 0
                    val stateCallback = Runnable { signalMainloop(false) }
                    lockMainloop()
                    try {
                        PA.context_set_state_callback(context, stateCallback)
                        PA.context_connect(context, null, PA.CONTEXT_NOFLAGS, 0)
                        try {
                            val state = waitForContextState(context, PA.CONTEXT_READY)
                            if (state == PA.CONTEXT_READY) {
                                this.context = context
                            } else {
                                throw IllegalStateException("context.state")
                            }
                        } finally {
                            if (this.context == 0L) PA.context_disconnect(context)
                        }
                    } finally {
                        unlockMainloop()
                    }
                } finally {
                    if (this.context == 0L) PA.context_unref(context)
                }
            } finally {
                if (proplist != 0L) PA.proplist_free(proplist)
            }
        } finally {
            if (context == 0L) stopMainloop()
        }
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the implementation provided by `AudioSystem` because the PulseAudio
     * `Renderer` implementation does not follow the convention of `AudioSystem`.
     */
    override fun createRenderer(playback: Boolean): Renderer? {
        val locator = if (playback) {
            null
        } else {
            val device = getSelectedDevice(DataFlow.NOTIFY)
            if (device == null) {
                // As AudioSystem does, no notification is to be sounded unless
                // there is a device with notify data flow.
                return null
            } else {
                device.locator
            }
        }
        val renderer = PulseAudioRenderer(if (playback) MEDIA_ROLE_PHONE else MEDIA_ROLE_EVENT)
        if (locator != null) renderer.setLocator(locator)
        return renderer
    }

    /**
     * Initializes a new PulseAudio stream which is to input or output audio at a specific sample
     * rate and with a specific number of channels. The new audio stream is to be associated with a
     * specific human-readable name and is to have a specific PulseAudio logic role.
     *
     * @param sampleRate the sample rate at which the new PulseAudio stream is to input or output
     * @param channels the number of channels of the audio to be input or output by the new PulseAudio stream
     * @param mediaName the human-readable name of the new PulseAudio stream
     * @param mediaRole the PulseAudio logic role of the new stream
     * @return a new PulseAudio stream which is to input or output audio at the specified
     * `sampleRate`, with the specified number of `channels`, to be associated
     * with the specified human-readable `mediaName`, and have the specified PulseAudio logic `mediaRole`
     */
    @Throws(RuntimeException::class)
    fun createStream(sampleRate: Int, channels: Int, mediaName: String?, mediaRole: String?): Long {
        val context = getContext()
        check(context != 0L) { "context" }
        val sampleSpec = PA.sample_spec_new(PA.SAMPLE_S16LE, sampleRate, channels)
        if (sampleSpec == 0L) throw RuntimeException("pa_sample_spec_new")
        return try {
            val proplist = PA.proplist_new()
            if (proplist == 0L) throw RuntimeException("pa_proplist_new")
            val l = try {
                PA.proplist_sets(proplist, PA.PROP_MEDIA_NAME, mediaRole)
                val proplistSets = PA.proplist_sets(proplist, PA.PROP_MEDIA_ROLE, mediaRole)
                val stream = PA.stream_new_with_proplist(context, null, sampleSpec, 0, proplist)
                if (stream == 0L) {
                    throw RuntimeException("pa_stream_new_with_proplist")
                }
                stream
            } finally {
                PA.proplist_free(proplist)
            }
            l
        } finally {
            PA.sample_spec_free(sampleSpec)
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun doInitialize() {
        val context = getContext()
        val captureDevices = LinkedList<CaptureDeviceInfo2>()
        val captureDeviceFormats = LinkedList<Format>()

        val sourceInfoListCb = object : source_info_cb_t {
            override fun callback(c: Long, i: Long, eol: Int) {
                try {
                    if (eol == 0 && i != 0L) {
                        sourceInfoListCb(c, i, captureDevices, captureDeviceFormats)
                    }
                } finally {
                    signalMainloop(false)
                }
            }
        }
        val playbackDevices = LinkedList<CaptureDeviceInfo2>()
        val playbackDeviceFormats = LinkedList<Format>()
        val sinkInfoListCb = object : sink_info_cb_t {
            override fun callback(c: Long, i: Long, eol: Int) {
                try {
                    if (eol == 0 && i != 0L) {
                        sinkInfoListCb(c, i, playbackDevices, playbackDeviceFormats)
                    }
                } finally {
                    signalMainloop(false)
                }
            }
        }

        lockMainloop()
        try {
            var o = PA.context_get_source_info_list(context, sourceInfoListCb)
            if (o == 0L) throw RuntimeException("pa_context_get_source_info_list")
            try {
                while (PA.operation_get_state(o) == PA.OPERATION_RUNNING) waitMainloop()
            } finally {
                PA.operation_unref(o)
            }
            o = PA.context_get_sink_info_list(context, sinkInfoListCb)
            if (o == 0L) throw RuntimeException("pa_context_get_sink_info_list")
            try {
                while (PA.operation_get_state(o) == PA.OPERATION_RUNNING) waitMainloop()
            } finally {
                PA.operation_unref(o)
            }
        } finally {
            unlockMainloop()
        }
        if (captureDeviceFormats.isNotEmpty()) {
            captureDevices.add(
                    0,
                    CaptureDeviceInfo2(NULL_DEV_CAPTURE_DEVICE_INFO_NAME,
                            MediaLocator("$LOCATOR_PROTOCOL:"),
                            captureDeviceFormats.toTypedArray(), null, null, null))
        }
        if (playbackDevices.isNotEmpty()) {
            playbackDevices.add(0, CaptureDeviceInfo2(NULL_DEV_CAPTURE_DEVICE_INFO_NAME,
                    MediaLocator("$LOCATOR_PROTOCOL:"), null, null, null, null))
        }
        setCaptureDevices(captureDevices)
        setPlaybackDevices(playbackDevices)
    }

    /**
     * Returns the connection context for asynchronous communication with the PulseAudio server. If
     * such a context does not exist, it is created.
     *
     * @return the connection context for asynchronous communication with the PulseAudio server
     */
    @Synchronized
    fun getContext(): Long {
        if (context == 0L) {
            if (!createContext) {
                createContext = true
                createContext()
            }
            check(context != 0L) { "context" }
        }
        return context
    }

    /**
     * Locks the PulseAudio event loop object associated with this `PulseAudioSystem`,
     * effectively blocking the PulseAudio event loop thread from processing events. May be used to
     * enforce exclusive access to all PulseAudio objects attached to the PulseAudio event loop. The
     * lock is recursive. The method may not be called inside the PulseAudio event loop thread.
     * Events that are dispatched from the PulseAudio event loop thread are executed with the lock held.
     */
    fun lockMainloop() {
        PA.threaded_mainloop_lock(mainloop)
    }

    /**
     * Populates a specific `pa_proplist` which is to be used with a `pa_context` with
     * properties such as the application name and version.
     *
     * @param proplist the `pa_proplist` which is to be populated with `pa_context`-related
     * properties such as the application name and version
     */
    private fun populateContextProplist(proplist: Long) {
        // XXX For the sake of simplicity while working on libjitsi, get the
        // version information in the form of System property values instead of
        // going through the VersionService.
        val name = System.getProperty(Version.PNAME_APPLICATION_NAME)
        val version = System.getProperty(Version.PNAME_APPLICATION_VERSION)
        if (name != null) PA.proplist_sets(proplist, PA.PROP_APPLICATION_NAME, name)
        if (version != null) PA.proplist_sets(proplist, PA.PROP_APPLICATION_VERSION, version)
    }

    /**
     * Signals all threads waiting for a signalling event in [.waitMainloop].
     *
     * @param waitForAccept `true` to not return before the signal is accepted by a
     * `pa_threaded_mainloop_accept()`; otherwise,
     * `false`
    `` */
    fun signalMainloop(waitForAccept: Boolean) {
        PA.threaded_mainloop_signal(mainloop, waitForAccept)
    }

    /**
     * Called back from `pa_context_get_sink_info_list()` to report information about a specific sink.
     *
     * @param context the connection context for asynchronous communication with the PulseAudio server which
     * is reporting the `sinkInfo`
     * @param sinkInfo the information about the sink being reported
     * @param deviceList the list of `CaptureDeviceInfo`s which reperesents existing devices and into
     * which the callback is represent the `sinkInfo`
     * @param formatList the list of `Format`s supported by the various `CaptureDeviceInfo`s in
     * `deviceList` and into which the callback is to represent the `sinkInfo`
     */
    private fun sinkInfoListCb(context: Long, sinkInfo: Long, deviceList: LinkedList<CaptureDeviceInfo2>,
            formatList: List<Format>,
    ) {
        // PulseAudio should supposedly automatically convert between sample
        // formats so we do not have to insist on PA_SAMPLE_S16LE.
        val sampleSpecFormat = PA.sink_info_get_sample_spec_format(sinkInfo)
        if (sampleSpecFormat == PA.SAMPLE_INVALID) return
        val description = PA.sink_info_get_description(sinkInfo)
        val name = PA.sink_info_get_name(sinkInfo)
        deviceList.add(CaptureDeviceInfo2(description ?: name,
                MediaLocator("$LOCATOR_PROTOCOL:$name"), null, null, null, null))
    }

    /**
     * Called back from `pa_context_get_source_info_list()` to report information about a specific source.
     *
     * @param context the connection context for asynchronous communication with the PulseAudio server which
     * is reporting the `sourceInfo`
     * @param sourceInfo the information about the source being reported
     * @param deviceList the list of `CaptureDeviceInfo`s which reperesents existing devices and into
     * which the callback is represent the `sourceInfo`
     * @param formatList the list of `Format`s supported by the various `CaptureDeviceInfo`s in
     * `deviceList` and into which the callback is to represent the sourceInfo`
     */
    private fun sourceInfoListCb(context: Long, sourceInfo: Long,
            deviceList: MutableList<CaptureDeviceInfo2>, formatList: LinkedList<Format>,
    ) {
        val monitorOfSink = PA.source_info_get_monitor_of_sink(sourceInfo)
        if (monitorOfSink != PA.INVALID_INDEX) return

        // PulseAudio should supposedly automatically convert between sample
        // formats so we do not have to insist on PA_SAMPLE_S16LE.
        val sampleSpecFormat = PA.source_info_get_sample_spec_format(sourceInfo)
        if (sampleSpecFormat == PA.SAMPLE_INVALID) return
        var channels = PA.source_info_get_sample_spec_channels(sourceInfo)
        var rate = PA.source_info_get_sample_spec_rate(sourceInfo)
        if (MediaUtils.MAX_AUDIO_CHANNELS != Format.NOT_SPECIFIED && MediaUtils.MAX_AUDIO_CHANNELS < channels) channels = MediaUtils.MAX_AUDIO_CHANNELS
        if (MediaUtils.MAX_AUDIO_SAMPLE_RATE != Format.NOT_SPECIFIED.toDouble() && MediaUtils.MAX_AUDIO_SAMPLE_RATE < rate) rate = MediaUtils.MAX_AUDIO_SAMPLE_RATE.toInt()
        val audioFormat = AudioFormat(AudioFormat.LINEAR, rate.toDouble(), 16, channels,
                AudioFormat.LITTLE_ENDIAN, AudioFormat.SIGNED,
                Format.NOT_SPECIFIED /* frameSizeInBits */,
                Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                Format.byteArray)
        if (!formatList.contains(audioFormat)) formatList.add(audioFormat)
        val description = PA.source_info_get_description(sourceInfo)
        val name = PA.source_info_get_name(sourceInfo)
        deviceList.add(CaptureDeviceInfo2(description ?: name,
                MediaLocator("$LOCATOR_PROTOCOL:$name"), arrayOf(audioFormat), null,
                null, null))
    }

    /**
     * Starts a new PulseAudio event loop thread and associates it with this`PulseAudioSystem`.
     *
     * @throws RuntimeException if a PulseAudio event loop thread exists and is associated with this
     * `PulseAudioSystem` or a new PulseAudio event loop thread initialized for the
     * purposes of association with this `PulseAudioSystem` failed to start
     */
    private fun startMainloop() {
        check(mainloop == 0L) { "mainloop" }
        val mainloop = PA.threaded_mainloop_new()
        if (mainloop == 0L) throw RuntimeException("pa_threaded_mainloop_new")
        try {
            if (PA.threaded_mainloop_start(mainloop) < 0) throw RuntimeException("pa_threaded_mainloop_start")
            this.mainloop = mainloop
        } finally {
            if (this.mainloop == 0L) PA.threaded_mainloop_free(mainloop)
        }
    }

    /**
     * Terminates the PulseAudio event loop thread associated with this `PulseAudioSystem`
     * cleanly. Make sure to unlock the PulseAudio main loop object before calling the method.
     */
    private fun stopMainloop() {
        val mainloop = mainloop
        check(mainloop != 0L) { "mainloop" }
        this.mainloop = 0
        PA.threaded_mainloop_stop(mainloop)
        PA.threaded_mainloop_free(mainloop)
    }

    /**
     * Returns a human-readable `String` representation of this `PulseAudioSystem`.
     * Always returns &quot;PulseAudio&quot;.
     *
     * @return &quot;PulseAudio&quot; as a human-readable `String` representation of this `PulseAudioSystem`
     */
    override fun toString(): String {
        return "PulseAudio"
    }

    /**
     * Unlocks the PulseAudio event look object associated with this `PulseAudioSystem`,
     * inverse of [.lockMainloop].
     */
    fun unlockMainloop() {
        PA.threaded_mainloop_unlock(mainloop)
    }

    /**
     * Waits for a specific PulseAudio context to get into a specific state, `PA_CONTEXT_FAILED`, or `PA_CONTEXT_TERMINATED`.
     *
     * @param context the PulseAudio context to wait for
     * @param stateToWaitFor the PulseAudio state of the specified `context` to wait for
     * @return the state of the specified `context` which caused the method to return
     */
    private fun waitForContextState(context: Long, stateToWaitFor: Int): Int {
        var state: Int
        do {
            state = PA.context_get_state(context)
            if (PA.CONTEXT_FAILED == state || stateToWaitFor == state || PA.CONTEXT_TERMINATED == state) break
            waitMainloop()
        } while (true)
        return state
    }

    /**
     * Waits for a specific PulseAudio stream to get into a specific state, `PA_STREAM_FAILED`, or `PA_STREAM_TERMINATED`.
     *
     * @param stream the PulseAudio stream to wait for
     * @param stateToWaitFor the PulseAudio state of the specified `stream` to wait for
     * @return the state of the specified `stream` which caused the method to return
     */
    fun waitForStreamState(stream: Long, stateToWaitFor: Int): Int {
        var state: Int
        do {
            state = PA.stream_get_state(stream)
            if (stateToWaitFor == state || PA.STREAM_FAILED == state || PA.STREAM_TERMINATED == state) break
            waitMainloop()
        } while (true)
        return state
    }

    /**
     * Wait for an event to be signalled by the PulseAudio event loop thread associated with this `PulseAudioSystem`.
     */
    fun waitMainloop() {
        PA.threaded_mainloop_wait(mainloop)
    }

    companion object {
        /**
         * The protocol of the `MediaLocator`s identifying PulseAudio `CaptureDevice`s.
         */
        private const val LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_PULSEAUDIO

        /**
         * The PulseAudio logic role of media which represents an event, notification.
         */
        const val MEDIA_ROLE_EVENT = "event"

        /**
         * The PulseAudio logic role of media which represents telephony call audio.
         */
        const val MEDIA_ROLE_PHONE = "phone"

        /**
         * The human-readable name of the `CaptureDeviceInfo` which is to represent the
         * automatic, default PulseAudio device to be used in the absence of a specification of a PulseAudio device.
         */
        private const val NULL_DEV_CAPTURE_DEVICE_INFO_NAME = "Default"

        /**
         * Pause or resume the playback of a specific PulseAudio stream temporarily.
         *
         * @param stream the PulseAudio stream to pause or resume the playback of
         * @param b `true` to pause or `false` to resume the playback of the specified `stream`
         */
        @Throws(IOException::class)
        fun corkStream(stream: Long, b: Boolean) {
            if (stream == 0L) throw IOException("stream")
            val o = PA.stream_cork(stream, b, null)
            if (o == 0L) throw IOException("pa_stream_cork")
            PA.operation_unref(o)
        }

        /**
         * Returns the one and only instance of `PulseAudioSystem` known to the `AudioSystem` framework.
         *
         * @return the one and only instance of `PulseAudioSystem` known to the
         * `AudioSystem` framework or `null` if no such system is known to the `AudioSystem` framework
         */
        val pulseAudioSystem: PulseAudioSystem?
            get() {
                val audioSystem = getAudioSystem(LOCATOR_PROTOCOL)
                return if (audioSystem is PulseAudioSystem) audioSystem else null
            }
    }
}