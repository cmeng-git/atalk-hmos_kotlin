/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallConference
import org.atalk.service.neomedia.BasicVolumeControl
import org.atalk.service.neomedia.MediaService
import org.atalk.service.neomedia.MediaUseCase
import org.atalk.service.neomedia.RTPTranslator
import org.atalk.service.neomedia.VolumeControl
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.device.MediaDeviceWrapper
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import org.atalk.util.event.PropertyChangeNotifier
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.lang.ref.WeakReference
import java.util.*

/**
 * Extends `CallConference` to represent the media-specific information associated with the
 * telephony conference-related state of a `MediaAwareCall`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class MediaAwareCallConference @JvmOverloads constructor(jitsiVideobridge: Boolean = false, translator: Boolean = false) : CallConference(jitsiVideobridge) {
    /**
     * The `MediaDevice`s indexed by `MediaType` ordinal which are to be used by this
     * telephony conference for media capture and/or playback. If the `MediaDevice` for a
     * specific `MediaType` is `null` ,
     * [MediaService.getDefaultDevice] is called.
     */
    private val devices: Array<MediaDevice?>

    /**
     * The `MediaDevice`s which implement media mixing on the respective
     * `MediaDevice` in [.devices] for the purposes of this telephony conference.
     */
    private val mixers: Array<MediaDevice?>

    /**
     * The `VolumeControl` implementation which is to control the volume (level) of the
     * audio played back the telephony conference represented by this instance.
     */
    val outputVolumeControl = BasicVolumeControl(VolumeControl.PLAYBACK_VOLUME_LEVEL_PROPERTY_NAME)

    /**
     * The `PropertyChangeListener` which listens to sources of
     * `PropertyChangeEvent`s on behalf of this instance.
     */
    private val propertyChangeListener = PropertyChangeListener { ev -> propertyChange(ev) }

    /**
     * Sync around creating/removing audio and video translator.
     */
    private val translatorSyncRoot = Any()

    /**
     * The `RTPTranslator` which forwards video RTP and RTCP traffic between the
     * `CallPeer`s of the `Call`s participating in this telephony conference when the
     * local peer is acting as a conference focus.
     */
    private var videoRTPTranslator: RTPTranslator? = null

    /**
     * The `RTPTranslator` which forwards audio RTP and RTCP traffic
     * between the `CallPeer`s of the `Call`s participating in
     * this telephony conference when the local peer is acting as a conference focus.
     */
    private var audioRTPTranslator: RTPTranslator? = null

    /**
     * The indicator which determines whether the telephony conference
     * represented by this instance is mixing or relaying.
     * By default what can be mixed is mixed (audio) and rest is relayed.
     */
    private var translator = false
    /**
     * Initializes a new `MediaAwareCallConference` instance which is to optionally
     * utilize the Jitsi Videobridge server-side telephony conferencing technology.
     *
     * jitsiVideobridge `true` if the telephony conference
     * represented by the new instance is to utilize the Jitsi Videobridge
     * server-side telephony conferencing technology; otherwise, `false`
     */
    /**
     * Initializes a new `MediaAwareCallConference` instance which is to optionally utilize
     * the Jitsi Videobridge server-side telephony conferencing technology.
     *
     * jitsiVideobridge `true` if the telephony conference represented by the new instance is to
     * utilize the Jitsi Videobridge server-side telephony conferencing technology; otherwise, `false`
     */
    /**
     * Initializes a new `MediaAwareCallConference` instance.
     */
    init {
        this.translator = translator
        val mediaTypeCount = MediaType.values().size
        devices = arrayOfNulls(mediaTypeCount)
        mixers = arrayOfNulls(mediaTypeCount)

        /*
         * Listen to the MediaService in order to reflect changes in the user's selection with
         * respect to the default media device.
         */
        addMediaServicePropertyChangeListener(propertyChangeListener)
    }

    /**
     * {@inheritDoc}
     *
     *
     * If this telephony conference switches from being a conference focus to not being such,
     * disposes of the mixers used by this instance when it was a conference focus
     */
    override fun conferenceFocusChanged(oldValue: Boolean, newValue: Boolean) {
        /*
         * If this telephony conference switches from being a conference focus to not being one,
         * dispose of the mixers used when it was a conference focus.
         */
        if (oldValue && !newValue) {
            Arrays.fill(mixers, null)

            /*
             * Disposing the video translator is not needed when the conference changes as we have
             * video and we will want to continue with the video Removed when chasing a bug where
             * video call becomes conference call and then back again video call and the video from
             * the conference focus side is not transmitted. if (videoRTPTranslator != null) {
             * videoRTPTranslator.dispose(); videoRTPTranslator = null; }
             */
        }
        super.conferenceFocusChanged(oldValue, newValue)
    }

    /**
     * {@inheritDoc}
     *
     *
     * Disposes of `this.videoRTPTranslator` if the removed `Call` was the last
     * `Call` in this `CallConference`.
     *
     * call the `Call` which has been removed from the list of `Call`s participating
     * in this telephony conference.
     */
    override fun callRemoved(call: Call<*>) {
        super.callRemoved(call)
        if (callCount == 0) {
            synchronized(translatorSyncRoot) {
                if (videoRTPTranslator != null) {
                    videoRTPTranslator!!.dispose()
                    videoRTPTranslator = null
                }
                if (audioRTPTranslator != null) {
                    audioRTPTranslator!!.dispose()
                    audioRTPTranslator = null
                }
            }
        }
    }

    /**
     * Gets a `MediaDevice` which is capable of capture and/or playback of media of the
     * specified `MediaType` and is the default choice of the user with respect to such a
     * `MediaDevice`.
     *
     * mediaType the `MediaType` in which the retrieved `MediaDevice` is to capture
     * and/or play back media
     * useCase the `MediaUseCase` associated with the intended utilization of the
     * `MediaDevice` to be retrieved
     * @return a `MediaDevice` which is capable of capture and/or playback of media of the
     * specified `mediaType` and is the default choice of the user with respect to
     * such a `MediaDevice`
     */
    fun getDefaultDevice(mediaType: MediaType, useCase: MediaUseCase): MediaDevice? {
        val mediaTypeIndex = mediaType.ordinal
        var device = devices[mediaTypeIndex]
        val mediaService = ProtocolMediaActivator.mediaService
        if (device == null) device = mediaService!!.getDefaultDevice(mediaType, useCase)

        /*
         * Make sure that the device is capable of mixing in order to support conferencing and call
         * recording.
         */
        if (device != null) {
            var mixer: MediaDevice? = mixers[mediaTypeIndex]
            if (mixer == null) {
                when (mediaType) {
                    MediaType.AUDIO ->                         /*
                         * TODO AudioMixer leads to very poor audio quality on Android so do not
                         * use it unless it is really really necessary.
                         */
                        if ((!OSUtils.IS_ANDROID || isConferenceFocus)
                            && !translator
                            /*
                                 * We can use the AudioMixer only if the device is able to capture
                                 * (because the AudioMixer will push when the capture device pushes).
                                 */
                            && device.direction.allowsSending()) {
                        mixer = mediaService!!.createMixer(device)
                    }
                    MediaType.VIDEO -> if (isConferenceFocus) mixer = mediaService!!.createMixer(device)
                    else -> {}
                }
                mixers[mediaTypeIndex] = mixer
            }
            if (mixer != null) device = mixer
        }
        return device
    }

    /**
     * Gets the `VolumeControl` which controls the volume (level) of the audio played
     * back in the telephony conference represented by this instance.
     *
     * @return the `VolumeControl` which controls the volume (level) of the audio played
     * back in the telephony conference represented by this instance
     */
    fun getOutputVolumeControl(): VolumeControl {
        return outputVolumeControl
    }

    /**
     * Gets the `RTPTranslator` which forwards RTP and RTCP traffic between the
     * `CallPeer`s of the `Call`s participating in this telephony conference when the
     * local peer is acting as a conference focus.
     *
     * mediaType the `MediaType` of the `MediaStream` which RTP and RTCP traffic is to be
     * forwarded between
     * @return the `RTPTranslator` which forwards RTP and RTCP traffic between the
     * `CallPeer`s of the `Call`s participating in this telephony conference
     * when the local peer is acting as a conference focus
     */
    fun getRTPTranslator(mediaType: MediaType?): RTPTranslator? {
        /*
         * XXX A mixer is created for audio even when the local peer is not a conference focus in
         * order to enable additional functionality. Similarly, the videoRTPTranslator is created
         * even when the local peer is not a conference focus in order to enable the local peer to
         * turn into a conference focus at a later time. More specifically, MediaStreamImpl is
         * unable to accommodate an RTPTranslator after it has created its RTPManager. Yet again
         * like the audio mixer, we'd better not try to use it on Android at this time because of
         * performance issues that might arise.
         */

        // cmeng - enable it even for Android - need it for jitsi-videBridge ???
        // if (MediaType.VIDEO.equals(mediaType) && (isConferenceFocus())) {
        if (MediaType.VIDEO == mediaType && (!OSUtils.IS_ANDROID || isConferenceFocus)) {
            synchronized(translatorSyncRoot) {
                if (videoRTPTranslator == null) {
                    videoRTPTranslator = ProtocolMediaActivator.mediaService!!.createRTPTranslator()
                }
                return videoRTPTranslator
            }
        }
        if (translator) {
            synchronized(translatorSyncRoot) {
                if (audioRTPTranslator == null) {
                    audioRTPTranslator = ProtocolMediaActivator.mediaService!!.createRTPTranslator()
                }
                return audioRTPTranslator
            }
        }
        return null
    }

    /**
     * Notifies this `MediaAwareCallConference` about changes in the values of the
     * properties of sources of `PropertyChangeEvent`s. For example, this instance listens
     * to  changes of the value of [MediaService.DEFAULT_DEVICE] which represents the
     * user's choice with respect to the default audio device.
     *
     * ev a `PropertyChangeEvent` which specifies the name of the property which had its
     * value changed and the old and new values of that property
     */
    private fun propertyChange(ev: PropertyChangeEvent) {
        val propertyName = ev.propertyName
        if (MediaService.DEFAULT_DEVICE == propertyName) {
            val source = ev.source
            if (source is MediaService) {
                /*
                 * XXX We only support changing the default audio device at the time of this
                 * writing.
                 */
                val mediaTypeIndex = MediaType.AUDIO.ordinal
                val mixer: MediaDevice? = mixers[mediaTypeIndex]
                val oldValue: MediaDevice? = if (mixer is MediaDeviceWrapper) (mixer as MediaDeviceWrapper?)!!.wrappedDevice else null
                var newValue: MediaDevice? = devices[mediaTypeIndex]
                if (newValue == null) {
                    newValue = ProtocolMediaActivator.mediaService!!
                            .getDefaultDevice(MediaType.AUDIO, MediaUseCase.ANY)
                }

                /*
                 * XXX If MediaService#getDefaultDevice(MediaType, MediaUseCase) above returns null
                 * and its earlier return value was not null, we will not notify of an actual
                 * change in the value of the user's choice with respect to the default audio
                 * device.
                 */
                if (oldValue != newValue) {
                    mixers[mediaTypeIndex] = null
                    firePropertyChange(MediaAwareCall.DEFAULT_DEVICE, oldValue, newValue)
                }
            }
        }
    }

    /**
     * Sets the `MediaDevice` to be used by this telephony conference for capture and/or
     * playback of media of a specific `MediaType`.
     *
     * mediaType the `MediaType` of the media which is to be captured and/or played back by the
     * specified `device`
     * device the `MediaDevice` to be used by this telephony conference for capture and/or
     * playback of media of the specified `mediaType`
     */
    fun setDevice(mediaType: MediaType, device: MediaDevice?) {
        val mediaTypeIndex = mediaType.ordinal
        var oldValue = devices[mediaTypeIndex]

        /*
         * XXX While we know the old and the new master/wrapped devices, we are not sure whether
         * the mixer has been used. Anyway, we have to report different values in order to have
         * PropertyChangeSupport really fire an event.
         */
        val mixer: MediaDevice? = mixers[mediaTypeIndex]
        if (mixer is MediaDeviceWrapper) oldValue = (mixer as MediaDeviceWrapper?)!!.wrappedDevice
        devices[mediaTypeIndex] = device
        val newValue = devices[mediaTypeIndex]
        if (oldValue != newValue) {
            mixers[mediaTypeIndex] = null
            firePropertyChange(MediaAwareCall.DEFAULT_DEVICE, oldValue, newValue)
        }
    }

    /**
     * Implements a `PropertyChangeListener` which weakly references and delegates to
     * specific `PropertyChangeListener`s and automatically adds itself to and removes
     * itself from a specific `PropertyChangeNotifier` depending on whether there are
     * `PropertyChangeListener`s to delegate to. Thus enables listening to a
     * `PropertyChangeNotifier` by invoking
     * [PropertyChangeNotifier.addPropertyChangeListener] without
     * [PropertyChangeNotifier.removePropertyChangeListener].
     */
    private open class WeakPropertyChangeListener(notifier: PropertyChangeNotifier?) : PropertyChangeListener {
        /**
         * The indicator which determines whether this `PropertyChangeListener` has been
         * added to [.notifier].
         */
        private var added = false

        /**
         * The list of `PropertyChangeListener`s which are to be notified about
         * `PropertyChangeEvent`s fired by [.notifier].
         */
        private val listeners: MutableList<WeakReference<PropertyChangeListener>> = LinkedList()

        /**
         * The `PropertyChangeNotifier` this instance is to listen to about
         * `PropertyChangeEvent`s which are to be forwarded to [.listeners].
         */
        private val notifier: PropertyChangeNotifier?

        /**
         * Initializes a new `WeakPropertyChangeListener` instance.
         */
        protected constructor() : this(null)

        /**
         * Initializes a new `WeakPropertyChangeListener` instance which is to listen to a
         * specific `PropertyChangeNotifier`.
         *
         * notifier the `PropertyChangeNotifier` the new instance is to listen to
         */
        init {
            this.notifier = notifier
        }

        /**
         * Adds a specific `PropertyChangeListener` to the list of
         * `PropertyChangeListener`s to be notified about `PropertyChangeEvent`s
         * fired by the `PropertyChangeNotifier` associated with this instance.
         *
         * listener the `PropertyChangeListener` to add
         */
        @Synchronized
        fun addPropertyChangeListener(listener: PropertyChangeListener) {
            val i = listeners.iterator()
            var add = true
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null) i.remove() else if (l == listener) add = false
            }
            if (add && listeners.add(WeakReference(listener))
                    && !added) {
                addThisToNotifier()
                added = true
            }
        }

        /**
         * Adds this as a `PropertyChangeListener` to [.notifier].
         */
        protected open fun addThisToNotifier() {
            notifier?.addPropertyChangeListener(this)
        }

        /**
         * {@inheritDoc}
         *
         *
         * Notifies this instance about a `PropertyChangeEvent` fired by [.notifier].
         */
        override fun propertyChange(ev: PropertyChangeEvent) {
            var ls: Array<PropertyChangeListener?>
            var n: Int
            synchronized(this) {
                val i = listeners.iterator()
                ls = arrayOfNulls(listeners.size)
                n = 0
                while (i.hasNext()) {
                    val l = i.next().get()
                    if (l == null) i.remove() else ls[n++] = l
                }
                if (n == 0 && added) {
                    removeThisFromNotifier()
                    added = false
                }
            }
            if (n != 0) {
                for (l in ls) {
                    if (l == null) break else l.propertyChange(ev)
                }
            }
        }

        /**
         * Removes a specific `PropertyChangeListener` from the list of
         * `PropertyChangeListener`s to be notified about `PropertyChangeEvent`s
         * fired by the `PropertyChangeNotifier` associated with this instance.
         *
         * listener the `PropertyChangeListener` to remove
         */
        @Synchronized
        fun removePropertyChangeListener(listener: PropertyChangeListener) {
            val i = listeners.iterator()
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null || l == listener) i.remove()
            }
            if (added && listeners.size == 0) {
                removeThisFromNotifier()
                added = false
            }
        }

        /**
         * Removes this as a `PropertyChangeListener` from [.notifier].
         */
        protected open fun removeThisFromNotifier() {
            notifier?.removePropertyChangeListener(this)
        }
    }

    companion object {
        /**
         * The `PropertyChangeListener` which will listen to the `MediaService` about
         * `PropertyChangeEvent`s.
         */
        private var mediaServicePropertyChangeListener: WeakPropertyChangeListener? = null

        /**
         * Adds a specific `PropertyChangeListener` to be notified about
         * `PropertyChangeEvent`s fired by the current `MediaService` implementation. The
         * implementation adds a `WeakReference` to the specified `listener` because
         * `MediaAwareCallConference` is unable to determine when the
         * `PropertyChangeListener` is to be removed.
         *
         * listener the `PropertyChangeListener` to add
         */
        @Synchronized
        private fun addMediaServicePropertyChangeListener(
                listener: PropertyChangeListener) {
            if (mediaServicePropertyChangeListener == null) {
                val mediaService: MediaService? = ProtocolMediaActivator.mediaService
                if (mediaService != null) {
                    mediaServicePropertyChangeListener = object : WeakPropertyChangeListener() {
                        override fun addThisToNotifier() {
                            mediaService.addPropertyChangeListener(this)
                        }

                        override fun removeThisFromNotifier() {
                            mediaService.removePropertyChangeListener(this)
                        }
                    }
                }
            }
            if (mediaServicePropertyChangeListener != null) {
                mediaServicePropertyChangeListener!!.addPropertyChangeListener(listener)
            }
        }
    }
}