/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification

import net.java.sip.communicator.service.notification.NotificationAction
import net.java.sip.communicator.service.notification.NotificationData
import net.java.sip.communicator.service.notification.SoundNotificationAction
import net.java.sip.communicator.service.notification.SoundNotificationHandler
import org.apache.commons.lang3.StringUtils
import org.atalk.service.audionotifier.AbstractSCAudioClip
import org.atalk.service.audionotifier.SCAudioClip
import org.atalk.util.OSUtils
import timber.log.Timber
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.Callable

/**
 * An implementation of the `SoundNotificationHandler` interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class SoundNotificationHandlerImpl : SoundNotificationHandler {
    /**
     * The indicator which determines whether this `SoundNotificationHandler` is currently muted
     * i.e. the sounds are off. TRUE if currently the sound is off, FALSE otherwise
     */
    override var isMute = false
        set(mute) {
            field = mute
            if (mute) {
                val ans = NotificationActivator.audioNotifier
                if (ans != null && ans.isMute != field)
                    ans.isMute = field
            }
        }

    private val playedClips = WeakHashMap<SCAudioClip, NotificationData?>()

    /**
     * Plays the sound given by the containing `soundFileDescriptor`. The sound is played in loop if the
     * loopInterval is defined.
     *
     * @param action The action to act upon.
     * @param data Additional data for the event.
     * @param device Audio clip playback device
     */
    private fun play(action: SoundNotificationAction, data: NotificationData?, device: SCAudioClipDevice) {
        val audioNotifService = NotificationActivator.audioNotifier
        if (audioNotifService == null || StringUtils.isBlank(action.descriptor))
            return

        // this is hack, seen on some os (particularly seen on macosx with external devices).
        // when playing notification in the call, can break the call and
        // no further communicating can be done after the notification.
        // So we skip playing notification if we have a call running
        val cfg = NotificationActivator.configurationService
        if (cfg != null
                && cfg.getBoolean(PROP_DISABLE_NOTIFICATION_DURING_CALL, false)
                && SCAudioClipDevice.PLAYBACK == device) {
            val uiService = NotificationActivator.uIService
            if (!uiService!!.inProgressCalls!!.isEmpty())
                return
        }

        var audio: SCAudioClip? = null
        when (device) {
            SCAudioClipDevice.NOTIFICATION,
            SCAudioClipDevice.PLAYBACK ->
                audio = audioNotifService.createAudio(action.descriptor!!, SCAudioClipDevice.PLAYBACK == device)

            SCAudioClipDevice.PC_SPEAKER ->
                if (!OSUtils.IS_ANDROID)
                    audio = PCSpeakerClip()
        }

        // it is possible that audio cannot be created
        if (audio == null)
            return

        synchronized(playedClips) {
            playedClips.put(audio, data)
        }

        var played = false
        try {
            val loopCondition = data!!.getExtra(NotificationData.SOUND_NOTIFICATION_HANDLER_LOOP_CONDITION_EXTRA) as Callable<Boolean>?
            audio.play(action.loopInterval, loopCondition)
            played = true
        } finally {
            synchronized(playedClips) {
                if (!played)
                    playedClips.remove(audio)
            }
        }
    }

    /**
     * Plays the sound given by the containing `soundFileDescriptor`. The sound is played in loop if the
     * loopInterval is defined.
     *
     * @param action The action to act upon.
     * @param data Additional data for the event.
     */
    override fun start(action: SoundNotificationAction, data: NotificationData?) {
        if (isMute)
            return

        var playOnlyOnPlayback = true
        val audioNotifService = NotificationActivator.audioNotifier
        if (audioNotifService != null) {
            playOnlyOnPlayback = audioNotifService.audioOutAndNotificationsShareSameDevice()
        }

        if (playOnlyOnPlayback) {
            if (action.isSoundNotificationEnabled || action.isSoundPlaybackEnabled) {
                play(action, data, SCAudioClipDevice.PLAYBACK)
            }
        } else {
            if (action.isSoundNotificationEnabled)
                play(action, data, SCAudioClipDevice.NOTIFICATION)
            if (action.isSoundPlaybackEnabled)
                play(action, data, SCAudioClipDevice.PLAYBACK)
        }

        if (action.isSoundPCSpeakerEnabled)
            play(action, data, SCAudioClipDevice.PC_SPEAKER)
    }

    /**
     * Stops the sound.
     *
     * @param data Additional data for the event.
     */
    override fun stop(data: NotificationData?) {
        val audioNotifService = NotificationActivator.audioNotifier
        if (audioNotifService != null) {
            val clipsToStop = ArrayList<SCAudioClip>()

            synchronized(playedClips) {
                val i = playedClips.entries.iterator()
                while (i.hasNext()) {
                    val (key, value) = i.next()
                    if (value == data) {
                        clipsToStop.add(key)
                        i.remove()
                    }
                }
            }

            for (clip in clipsToStop) {
                try {
                    clip.stop()
                } catch (t: Throwable) {
                    Timber.e(t, "Error stopping audio clip")
                }
            }
        }
    }

    /**
     * Tells if the given notification sound is currently played.
     *
     * @param data Additional data for the event.
     */
    override fun isPlaying(data: NotificationData?): Boolean {
        val audioNotifService = NotificationActivator.audioNotifier

        if (audioNotifService != null) {
            synchronized(playedClips) {
                for ((key, value) in playedClips) {
                    if (value == data) {
                        return key.isStarted
                    }
                }
            }
        }
        return false
    }

    override val actionType: String
        get() = NotificationAction.ACTION_SOUND

    /**
     * Beeps the PC speaker.
     */
    private class PCSpeakerClip : AbstractSCAudioClip("null", NotificationActivator.audioNotifier!!) {
        /**
         * The beep method.
         */
        private var beepMethod: Method? = null

        /**
         * The toolkit.
         */
        private var toolkit: Any? = null

        /**
         * Initializes a new `PCSpeakerClip` instance.
         */
        init {

            // load the method java.awt.Toolkit.getDefaultToolkit().beep();
            // use reflection to be sure it will not throw exception in Android
            try {
                val defaultToolkit = Class.forName("java.awt.Toolkit").getMethod("getDefaultToolkit")
                toolkit = defaultToolkit.invoke(null)
                beepMethod = toolkit?.javaClass?.getMethod("beep")
            } catch (t: Throwable) {
                Timber.e(t, "Cannot load awt.Toolkit")
            }
        }

        /**
         * Beeps the PC speaker.
         *
         * @return `true` if the playback was successful; otherwise, `false`
         */
        override fun runOnceInPlayThread(): Boolean {
            return try {
                if (beepMethod != null)
                    beepMethod!!.invoke(toolkit)
                true
            } catch (t: Throwable) {
                if (t is ThreadDeath) throw t
                else false
            }
        }
    }

    /**
     * Enumerates the types of devices on which `SCAudioClip`s may be played back.
     */
    private enum class SCAudioClipDevice {
        NOTIFICATION, PC_SPEAKER, PLAYBACK
    }

    companion object {
        /**
         * Property to disable sound notification during an on-going call.
         */
        private const val PROP_DISABLE_NOTIFICATION_DURING_CALL = "notification.disableNotificationDuringCall"
    }
}