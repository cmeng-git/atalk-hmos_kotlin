/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.audionotifier

import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import org.atalk.hmos.aTalkApp
import org.atalk.impl.androidresources.AndroidResourceServiceImpl
import timber.log.Timber
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An abstract base implementation of [SCAudioClip] which is provided in order to aid
 * implementers by allowing them to extend `AbstractSCAudioClip` and focus on the task of
 * playing actual audio once.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractSCAudioClip  // private int currentVolume;
protected constructor(
        /**
         * The `String` uri of the audio to be played by this instance.
         * `AbstractSCAudioClip` does not use it and just remembers it in order to make it available to extenders.
         */
        protected val uri: String,

        /**
         * The `AudioNotifierService` which has initialized this instance.
         * `AbstractSCAudioClip` monitors its `mute` property/state in order to silence
         * the played audio as appropriate/necessary.
         */
        private val audioNotifier: AudioNotifierService,
) : SCAudioClip {

    private var command: Runnable? = null

    /**
     * Determines whether this instance is marked as invalid. `AbstractSCAudioClip` does not use the
     * `invalid` property/state of this instance and merely remembers the value which was set
     * on it by [.setInvalid]. The default value is `false` i.e. this instance is valid by default.
     */
    var isInvalid = false

    /**
     * The indicator which determines whether this audio is to play in a loop. Generally,
     * public invocation of the method is not necessary because the looping is controlled by the
     * `loopInterval` property of this instance and the `loopInterval` and
     * `loopCondition` parameters of [.play] anyway.
     */
    private var isLooping = false
        set(looping) {
            synchronized(sync) {
                if (field != looping) {
                    field = looping
                    sync.notifyAll()
                }
            }
        }

    /**
     * The interval of time in milliseconds between consecutive plays of this audio in a loop. If
     * negative, this audio is played once only. If non-negative, this audio may still be played
     * once only if the `loopCondition` specified to [.play] is
     * `null` or its invocation fails.
     */
    private var loopInterval = -1
        private set(loopInterval) {
            synchronized(sync) {
                if (field != loopInterval) {
                    field = loopInterval
                    sync.notifyAll()
                }
            }
        }

    /**
     * The indicator which determines whether the playback of this audio is started.
     */
    private var started = false

    /**
     * The `Object` used for internal synchronization purposes which arise because this
     * instance does the actual playback of audio in a separate thread.
     *
     * The synchronization root is exposed to extenders in case they would like to, for example,
     * get notified as soon as possible when this instance gets stopped.
     */
    protected val sync = Object()

    /**
     * An instance for playback of android OS ringTone
     */
    private var ringtone: Ringtone? = null

    /**
     * Notifies this instance that its execution in its background/separate thread dedicated to
     * the playback of this audio is about to start playing this audio for the first time.
     * Regardless of whether this instance is to be played once or multiple times in a loop, the
     * method is called once in order to allow extenders/implementers to perform one-time
     * initialization before this audio starts playing. The `AbstractSCAudioClip`
     * implementation does nothing.
     */
    protected open fun enterRunInPlayThread() {}

    /**
     * Notifies this instance that its execution in its background/separate thread dedicated to
     * the playback of this audio is about to stop playing this audio once. Regardless of whether
     * this instance is to be played once or multiple times in a loop, the method is called once
     * in order to allow extenders/implementers to perform one-time cleanup after this audio
     * stops playing. The `AbstractSCAudioClip` implementation does nothing.
     */
    protected open fun exitRunInPlayThread() {}

    /**
     * Notifies this instance that its execution in its background/separate thread dedicated to
     * the playback of this audio is about the start playing this audio once. If this audio is to
     * be played in a loop, the method is invoked at the beginning of each iteration of the loop.
     * Allows extenders/implementers to perform per-loop iteration initialization. The
     * `AbstractSCAudioClip` implementation does nothing.
     */
    private fun enterRunOnceInPlayThread() {}

    /**
     * Notifies this instance that its execution in its background/separate thread dedicated to
     * the playback of this audio is about to stop playing this audio. If this audio is to be
     * played in a loop, the method is called at the end of each iteration of the loop. Allows
     * extenders/implementers to perform per-loop iteration cleanup. The
     * `AbstractSCAudioClip` implementation does nothing.
     */
    protected open fun exitRunOnceInPlayThread() {}

    /**
     * Plays this audio once.
     *
     * @return `true` if subsequent plays of this audio and, respectively, the method are
     * to be invoked if this audio is to be played in a loop; otherwise, `false`. The
     * value reflects an implementation-specific loop condition, is not dependent on
     * `loopInterval` and `loopCondition` and is combined with the latter in order
     * to determine whether there will be a subsequent iteration of the playback loop.
     */
    protected abstract fun runOnceInPlayThread(): Boolean

    /**
     * Delegates to [.play] with `loopInterval` `-1` and
     * `loopCondition` `null` in order to conform with the contract for the
     * behavior of this method specified by the interface `SCAudioClip`.
     */
    override fun play() {
        play(-1, null)
    }

    /**
     * {@inheritDoc}
     */
    override fun play(loopInterval: Int, loopCondition: Callable<Boolean>?) {
        // Timber.w(new Exception("Ring tone playing start"));
        var loopInterval = loopInterval
        if (loopInterval >= 0 && loopCondition == null)
            loopInterval = -1

        synchronized(sync) {
            if (command != null)
                return

            this.loopInterval = loopInterval
            this.isLooping = (loopInterval >= 0)

            /*
             * We use a thread pool shared among all AbstractSCAudioClip instances in order to
             * reduce the impact of thread creation/initialization.
             */
            var executorService: ExecutorService
            synchronized(AbstractSCAudioClip::class.java) {
                if (Companion.executorService == null) {
                    Companion.executorService = Executors.newCachedThreadPool()
                }
                executorService = Companion.executorService!!
            }

            try {
                started = false
                command = object : Runnable {
                    override fun run() {
                        try {
                            synchronized(sync) {
                                /*
                                 * We have to wait for play(int,Callable<Boolean>) to let go of sync
                                 * i.e. be ready with setting up the whole AbstractSCAudioClip state;
                                 * otherwise, this Runnable will most likely prematurely seize to exist.
                                 */
                                if (!equals(command))
                                    return
                            }
                            if (uri.startsWith(AndroidResourceServiceImpl.PROTOCOL)) {
                                // setNotificationVolume();
                                runInPlayThread(loopCondition)
                            }
                            // use runInPlayRingtoneThread if it is for android RingTone playing
                            else {
                                runInPlayRingtoneThread(loopCondition)
                            }
                        } finally {
                            synchronized(sync) {
                                if (equals(command)) {
                                    command = null
                                    started = false
                                    sync.notifyAll()
                                }
                            }
                        }
                    }
                }
                executorService.execute(command)
                started = true
            } finally {
                if (!started)
                    command = null
                sync.notifyAll()
            }
        }
    }

    /**
     * Determines whether this audio is started i.e. a `play` method was invoked and no
     * subsequent `stop` has been invoked yet.
     *
     * @return `true` if this audio is started; otherwise, `false`
     */
    override val isStarted: Boolean
        get() {
            synchronized(sync) {
                return started
            }
        }

    /**
     * Runs in a background/separate thread dedicated to the actual playback of this audio and
     * plays this audio once or in a loop.
     *
     * @param loopCondition a `Callback<Boolean>` which represents the condition on which this
     * audio will play more than once. If `null`, this audio will play once only. If an invocation of
     * `loopCondition` throws a `Throwable`, this audio will discontinue playing.
     */
    private fun runInPlayThread(loopCondition: Callable<Boolean>?) {
        enterRunInPlayThread()
        try {
            var interrupted = false

            while (isStarted) {
                if (audioNotifier.isMute) {
                    /*
                     * If the AudioNotifierService has muted the sounds, we will have to really
                     * wait a bit in order to not fall into a busy wait.
                     */
                    synchronized(sync) {
                        try {
                            sync.wait(500)
                        } catch (ie: InterruptedException) {
                            interrupted = true
                        }
                    }
                }
                else {
                    enterRunOnceInPlayThread()
                    try {
                        if (!runOnceInPlayThread())
                            break
                    } finally {
                        exitRunOnceInPlayThread()
                    }
                }

                if (!isLooping)
                    break

                synchronized(sync) {
                    /*
                     * We may have waited to acquire sync. Before beginning the wait for
                     * loopInterval, make sure we should continue.
                     */
                    if (isStarted) {
                        try {
                            /*
                             * XXX The value 0 means that this instance should loop playing without
                             * waiting, but it means infinity to Object.wait(long).
                             */
                            val loopInterval = loopInterval
                            if (loopInterval > 0)
                                sync.wait(loopInterval.toLong())
                        } catch (ie: InterruptedException) {
                            interrupted = true
                        }
                    }
                }

                /*
                 * After this audio has been played once, loopCondition should be consulted to
                 * approve each subsequent iteration of the loop. Before invoking loopCondition
                 * which may take noticeable time to execute, make sure that this instance has
                 * not been stopped while it waited for loopInterval.
                 */
                if (!isStarted)
                    break

                if (loopCondition == null) {
                    /*
                     * The interface contract is that this audio plays once only if the loopCondition is null.
                     */
                    break
                }

                /*
                 * The contract of the SCAudioClip interface with respect to loopCondition is that
                 * the loop will continue only if loopCondition successfully and explicitly evaluates to true.
                 */
                var loop = false
                try {
                    loop = loopCondition.call()
                } catch (t: Throwable) {
                    if (t is ThreadDeath) throw t
                    /*
                     * If loopCondition fails to successfully and explicitly evaluate to true,
                     * this audio should seize to play in a loop. Otherwise, there is a risk that
                     * whoever requested this audio to be played in a loop and provided the
                     * loopCondition will continue to play it forever.
                     */
                }

                if (!loop) {
                    /*
                     * The loopCondition failed to successfully and explicitly evaluate to true so
                     * the loop will not continue.
                     */
                    break
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt()
        } finally {
            exitRunInPlayThread()
        }
    }

    // The notification volume for aTalk - no good to implement as it affect all notifications
    //    private void setNotificationVolume() {
    //        AudioManager audioManager = (AudioManager)  aTalkApp.globalContext.getSystemService(Context.AUDIO_SERVICE);
    //        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
    //        Timber.d("Current volume: %s", currentVolume);
    //        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), 0);
    //    }
    //
    //    private void restoreNotificationVolume() {
    //        AudioManager audioManager = (AudioManager)  aTalkApp.globalContext.getSystemService(Context.AUDIO_SERVICE);
    //        Timber.d("Current volume restore: %s", currentVolume);
    //        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, currentVolume, 0);
    //    }

    /**
     * Runs in a background/separate thread dedicated to the actual playback of the android ringtone
     * Plays this audio once or in a loop is controlled by RingTone player. So setup to check external
     * condition changes to stop playing if triggered
     * There is always a wait of 3 seconds for non-loop tone to complete playing at least once
     *
     * @param loopCondition a `Callback<Boolean>` which represents the condition on which this
     * audio will play more than once. If `null`, this audio will play once only. If an invocation of
     * `loopCondition` throws a `Throwable`, this audio will discontinue playing.
     */
    private fun runInPlayRingtoneThread(loopCondition: Callable<Boolean>?) {
        try {
            var interrupted = false

            while (isStarted) {
                if (audioNotifier.isMute) {
                    /*
                     * If the AudioNotifierService has muted the sounds, we will have to really
                     * wait a bit in order to not fall into a busy wait.
                     */
                    synchronized(sync) {
                        try {
                            sync.wait(500)
                        } catch (ie: InterruptedException) {
                            interrupted = true
                        }
                    }
                }
                else {
                    if (ringtone == null || !ringtone!!.isPlaying) {
                        try {
                            if (!ringTonePlayBack(loopCondition))
                                break
                        } catch (ex: Exception) {
                            break
                        }
                    }
                }

                // Do nothing for ringtone playback assuming it is already setup to play only once
                // if (!isLooping()) break;
                synchronized(sync) {
                    /*
                     * We may have waited to acquire sync. Before beginning the wait for loopInterval (3000ms);
                     * to monitor the progress of ringtone playback after playing back for 3s.
                     */
                    if (isStarted) {
                        /*
                         * Playback by ringTone is auto-looping; but need to wait for some time before proceed;
                         * This is to allow notification non-loop alert to play for at least 3 seconds
                         */
                        try {
                            sync.wait(3000)
                        } catch (ie: InterruptedException) {
                            interrupted = true
                        }
                    }
                }

                /*
                 * After this audio has started playing, loopCondition should be consulted at regular interval to
                 * approve continue ringtone playing. Before invoking loopCondition which may take noticeable time
                 * to execute, make sure that this instance has not been stopped while it waited for loopInterval.
                 */
                if (!isStarted) {
                    break
                }

                /*
                 * The interface contract is that this audio plays once only if the loopCondition is null.
                 */
                if (loopCondition == null) {
                    break
                }

                /*
                 * The contract of the SCAudioClip interface with respect to loopCondition is that
                 * the loop will continue only if loopCondition successfully and explicitly evaluates to true.
                 */
                try {
                    if (!loopCondition.call())
                        break
                } catch (t: Exception) {
                    /*
                     * If loopCondition fails to successfully and explicitly evaluate to true,
                     * this audio should cease to play in a loop. Otherwise, there is a risk that
                     * whoever requested this audio to be played in a loop and provided the
                     * loopCondition will continue to play it forever.
                     */
                    break
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt()
        } finally {
            ringToneStop()
        }
    }

    /**
     * Use RingTone to play android OS ringtone; AudioSystemClipImpl support only Wav media
     * Warn: looping and setVolume support only for android-P and above
     *
     * @param loopCondition check for loop
     */
    @Throws(Exception::class)
    private fun ringTonePlayBack(loopCondition: Callable<Boolean>?): Boolean {
        // stop previously play ringTone if any and create new ringTone
        if (ringtone != null) {
            try {
                ringtone!!.stop()
                ringtone = null
            } catch (ex: IllegalStateException) {
                // just ignore any ringtone stop exception
                Timber.w("End existing ringtone error: %s", ex.message)
            }
        }

        val ctx = aTalkApp.globalContext
        ringtone = RingtoneManager.getRingtone(ctx, Uri.parse(uri))
        if (ringtone == null)
            return false

        val loop = (loopCondition != null) && loopCondition.call() && (loopInterval > 0)

        // cmeng: seem android ring tone already follow the system ring tone setting or mute state.
        // AudioManager am = aTalkApp.getAudioManager();
        // int currentVolume = am.getStreamVolume(AudioManager.STREAM_RING);
        // am.setStreamVolume(AudioManager.STREAM_RING, currentVolume, AudioManager.FLAG_SHOW_UI);
        // Timber.d(new Exception(), "RingTone playing loop = %s; volume = %s; %s",
        //        loop, currentVolume, ringtone.getTitle(ctx));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone!!.isLooping = loop // may not be necessary as this is taken care by RingTone playing
            /*
             * Set the ringTone playback volume according to system RingTone setting
             * Seem above am.setStreamVolume() is also working for all android devices
             */
            // int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_RING);
            // float ringVolume = (float) (1.0 - Math.log(maxVolume - currentVolume) / Math.log(maxVolume));
            // Timber.d("RingTone playing volume %s/%s, %s", currentVolume, maxVolume, ringVolume);
            // ringtone.setVolume(ringVolume);
        }
        ringtone!!.play()
        return true
    }

    private fun ringToneStop() {
        if (!uri.startsWith(AndroidResourceServiceImpl.PROTOCOL)) {
            if (ringtone != null) {
                // Timber.d("Ring tone playback stopping: %s = %s", ringtone.getTitle(aTalkApp.getGlobalContext()), uri);
                try {
                    ringtone!!.stop()
                    ringtone = null
                } catch (ex: IllegalStateException) {
                    Timber.w("Ringtone stopping exception %s", ex.message)
                }
            }
        }
//        else {
//            restoreNotificationVolume();
//        }
    }

    /**
     * {@inheritDoc}
     */
    override fun stop() {
        ringToneStop()
        internalStop()
        isLooping = false
    }

    /**
     * Stops this audio without setting the isLooping property in the case of a looping audio. The
     * AudioNotifier uses this method to stop the audio when setMute(true) is invoked. This
     * allows us to restore all looping audios when the sound is restored by calling setMute(false).
     */
    protected open fun internalStop() {
        var interrupted = false

        synchronized(sync) {
            started = false
            sync.notifyAll()

            while (command != null) {
                try {
                    /*
                     * Technically, we do not need a timeout. If a notifyAll() is not called to wake us up,
                     * then we will likely already be in trouble. Anyway, use a timeout just in case.
                     */
                    sync.wait(500)
                } catch (ie: InterruptedException) {
                    interrupted = true
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt()
    }

    companion object {
        /**
         * The thread pool used by the `AbstractSCAudioClip` instances in order to reduce the
         * impact of thread creation/initialization.
         */
        private var executorService: ExecutorService? = null
    }
}