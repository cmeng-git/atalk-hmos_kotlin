/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.notify

import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.device.AudioSystem
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.impl.neomedia.device.NoneAudioSystem
import org.atalk.service.audionotifier.AudioNotifierService
import org.atalk.service.audionotifier.SCAudioClip
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.Callable

/**
 * The implementation of `AudioNotifierService`.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
class AudioNotifierServiceImpl : AudioNotifierService, PropertyChangeListener {
    /**
     * The cache of `SCAudioClip` instances which we may reuse. The reuse is complex because
     * a `SCAudioClip` may be used by a single user at a time.
     */
    private var audioClips: MutableMap<AudioKey, SCAudioClip>? = null

    /**
     * The `Object` which synchronizes the access to [.audios].
     */
    private val audiosSyncRoot = Any()
    /**
     * The device configuration.
     *
     * @return the deviceConfiguration
     */
    /**
     * The `DeviceConfiguration` which provides information about the notify and playback
     * devices on which this instance plays `SCAudioClip`s.
     */
    val deviceConfiguration = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration
    /**
     * Returns `true` if the sound is currently disabled; `false`, otherwise.
     *
     * @return `true` if the sound is currently disabled; `false`, otherwise
     */

    /**
     * Enables or disables the sound in the application. If `false`, we try to restore all
     * looping sounds if any.
     *
     * isMute when `true` disables the sound; otherwise, enables the sound.
     */
    override var isMute = false

    /**
     * Initializes a new `AudioNotifierServiceImpl` instance.
     */
    init {
        deviceConfiguration.addPropertyChangeListener(this)
    }

    /**
     * Checks whether the playback and notification configuration share the same device.
     *
     * @return are audio out and notifications using the same device.
     */
    override fun audioOutAndNotificationsShareSameDevice(): Boolean {
        val audioSystem = deviceConfiguration.audioSystem
        val notify = audioSystem!!.getSelectedDevice(AudioSystem.DataFlow.NOTIFY)
        val playback = audioSystem.getSelectedDevice(AudioSystem.DataFlow.PLAYBACK)
        return if (notify == null) playback == null else {
            if (playback == null) false else notify.locator == playback.locator
        }
    }

    /**
     * Creates an SCAudioClip from the given URI and adds it to the list of available audio-s.
     * Uses notification device if any.
     *
     * @param uri the path where the audio file could be found
     * @return a newly created `SCAudioClip` from `uri`
     */
    override fun createAudio(uri: String): SCAudioClip? {
        return createAudio(uri, false)
    }

    /**
     * Creates an SCAudioClip from the given URI and adds it to the list of available audio-s.
     *
     * @param uri the path where the audio file could be found
     * @param playback use or not the playback device.
     * @return a newly created `SCAudioClip` from `uri`
     */
    override fun createAudio(uri: String, playback: Boolean): SCAudioClip? {
        var audio: SCAudioClip?
        synchronized(audiosSyncRoot) {
            val key = AudioKey(uri, playback)

            /*
             * While we want to reuse the SCAudioClip instances, they may be used by a single user
             * at a time. That's why we'll forget about them while they are in use, and we'll
             * reclaim them when they are no longer in use.
             */
            audio = if (audioClips == null) null else audioClips!!.remove(key)
            if (audio == null) {
                audio = try {
                    val audioSystem = deviceConfiguration.audioSystem
                    if (audioSystem == null) {
                        JavaSoundClipImpl(uri, this)
                    } else if (NoneAudioSystem.LOCATOR_PROTOCOL.equals(audioSystem.locatorProtocol, ignoreCase = true)) {
                        null
                    } else {
                        AudioSystemClipImpl(uri, this, audioSystem, playback)
                    }
                } catch (t: Throwable) {
                    return if (t is ThreadDeath) throw t else {
                        /*
                         * Could not initialize a new SCAudioClip instance to be played.
                         */
                        null
                    }
                }
            }

            /*
             * Make sure the SCAudioClip will be reclaimed for reuse when it is no longer in use.
             */
            if (audio != null) {
                if (audioClips == null) audioClips = HashMap()
                /*
                     * We have to return in the Map which was active at the time the SCAudioClip was
                     * initialized because it may have become invalid if the playback or notify audio
                     * device changed.
                     */
                val finalAudios = audioClips!!
                val finalAudio = audio!!
                audio = object : SCAudioClip {
                    /**
                     * Evaluates a specific `loopCondition` as defined by
                     * [SCAudioClip.play].
                     *
                     * @param loopCondition the `Callable<Boolean>` which represents the 'loopCondition` to be evaluated
                     * @return Boolean.FALSE if `loopCondition` is `null`; otherwise,
                     * the value returned by invoking [Callable.call] on the specified `loopCondition`
                     * @throws Exception if the specified `loopCondition` throws an `Exception`
                     */
                    @Throws(Exception::class)
                    private fun evaluateLoopCondition(loopCondition: Callable<Boolean>?): Boolean {
                        /*
                             * SCAudioClip.play(int,Callable<Boolean>) is documented to play the
                             * SCAudioClip once only if the loopCondition is null. The same will be
                             * accomplished by returning Boolean.FALSE.
                             */
                        return if (loopCondition == null) java.lang.Boolean.FALSE else loopCondition.call()
                    }

                    /**
                     * {@inheritDoc} :
                     *
                     * Returns the wrapped `SCAudioClip` into the cache from it has earlier
                     * been retrieved in order to allow its reuse.
                     */
                    @Throws(Throwable::class)
                    protected fun finalize() {  // java.lang.Object.finalize()
                        try {
                            synchronized(audioClips!!) { finalAudios.put(key, finalAudio) }
                        } finally {
                            // super.finalize()
                        }
                    }

                    override fun play() {
                        /*
                         * SCAudioClip.play() is documented to behave as if loopInterval is
                         * negative and/or loopCondition is null. We have to take care that this
                         * instance does not get garbage collected until the finalAudio finishes
                         * playing so we will delegate to this instance's implementation of
                         * SCAudioClip.play(int,Callable<Boolean>) instead of to the finalAudio's.
                         */
                        play(-1, null)
                    }

                    override fun play(loopInterval: Int, loopCondition: Callable<Boolean>?) {
                        /*
                             * We have to make sure that this instance does not get garbage collected
                             * before the finalAudio finishes playing. The argument loopCondition of
                             * the method SCAudioClip.play(int,Callable<Boolean>) will live/be
                             * referenced during that time so we will use it to hold on to this
                             * instance.
                             */
                        val fLoopCondition = Callable { evaluateLoopCondition(loopCondition) }
                        finalAudio.play(loopInterval, fLoopCondition)
                    }

                    override fun stop() {
                        finalAudio.stop()
                    }

                    /**
                     * Determines whether this audio is started i.e. a `play` method was
                     * invoked and no subsequent `stop` has been invoked yet.
                     *
                     * @return `true` if this audio is started; otherwise, `false`
                     */
                    override val isStarted: Boolean
                        get() = finalAudio.isStarted
                }
            }
        }
        return audio
    }

    /**
     * Listens for changes in notify device.
     *
     * @param ev the event that notify device has changed.
     */
    override fun propertyChange(ev: PropertyChangeEvent) {
        val propertyName = ev.propertyName
        if (DeviceConfiguration.AUDIO_NOTIFY_DEVICE == propertyName || DeviceConfiguration.AUDIO_PLAYBACK_DEVICE == propertyName) {
            synchronized(audiosSyncRoot) {
                /*
                 * Make sure that the currently referenced SCAudioClips will not be reclaimed.
                 */
                audioClips = null
            }
        }
    }

    /**
     * Implements the key of [AudioNotifierServiceImpl.audioClips]. Combines the `uri` of
     * the `SCAudioClip` with the indicator which determines whether the
     * `SCAudioClip` in question uses the playback or the notify audio device.
     */
    private class AudioKey
    /**
     * Initializes a new `AudioKey` instance.
     *
     * @param uri
     * @param playback
     */
    (
            /**
             * The uri.
             */
            val uri: String?,
            /**
             * Is it playback?
             */
            private val playback: Boolean,
    ) {
        /**
         * {@inheritDoc}
         */
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is AudioKey) return false
            return (playback == other.playback
                    && if (uri == null) other.uri == null else uri == other.uri)
        }

        /**
         * {@inheritDoc}
         */
        override fun hashCode(): Int {
            return (uri?.hashCode() ?: 0) + if (playback) 1 else 0
        }
    }
}