/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.event.VolumeChangeEvent
import org.atalk.service.neomedia.event.VolumeChangeListener
import timber.log.Timber
import java.awt.Component
import java.lang.ref.WeakReference
import java.util.*
import javax.media.GainChangeEvent
import javax.media.GainChangeListener
import javax.media.GainControl
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Provides a basic implementation of `VolumeControl` which stores the volume level/value set
 * on it in the `ConfigurationService`.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class BasicVolumeControl(volumeLevelConfigurationPropertyName: String) : VolumeControl, GainControl {
    /**
     * Current level in db.
     */
    private var db = 0f

    /**
     * Listeners interested in volume change inside FMJ/JMF.
     */
    private var gainChangeListeners: MutableList<GainChangeListener>? = null

    /**
     * The power level reference used to compute equivalents between the volume power level and the
     * gain in decibels.
     */
    private val gainReferenceLevel: Float

    /**
     * Current mute state, by default we start unmuted.
     */
    private var mute = false

    /**
     * The `VolumeChangeListener`s interested in volume change events through the
     * `VolumeControl` interface.
     *
     *
     * Because the instances of `AbstractVolumeControl` are global at the time of this
     * writing and, consequently, they cause the `VolumeChangeListener`s to be leaked, the
     * listeners are referenced using `WeakReference`s.
     *
     */
    private val volumeChangeListeners = ArrayList<WeakReference<VolumeChangeListener>>()

    final override var volumeLevel: Float

    /**
     * The name of the configuration property which specifies the value of the volume level of this
     * `AbstractVolumeControl`.
     */
    private val volumeLevelConfigurationPropertyName: String

    /**
     * Creates volume control instance and initializes initial level value if stored in the
     * configuration service.
     *
     * volumeLevelConfigurationPropertyName the name of the configuration property
     * which specifies the value of the volume level of the new instance
     */
    init {
        // Initializes default values.
        volumeLevel = defaultVolumeLevel
        gainReferenceLevel = getGainReferenceLevel()
        this.volumeLevelConfigurationPropertyName = volumeLevelConfigurationPropertyName

        // Read the initial volume level from the ConfigurationService.
        loadVolume()
    }

    /**
     * Register for gain change update events. A `GainChangeEvent` is posted when the
     * state of the `GainControl` changes.
     *
     * @param listener The object to deliver events to.
     */
    override fun addGainChangeListener(listener: GainChangeListener) {
        if (gainChangeListeners == null) gainChangeListeners = ArrayList()
        gainChangeListeners!!.add(listener)
    }

    /**
     * Adds a `VolumeChangeListener` to be informed for any change in the volume levels.
     *
     * @param listener volume change listener.
     */
    override fun addVolumeChangeListener(listener: VolumeChangeListener) {
        synchronized(volumeChangeListeners) {
            val i = volumeChangeListeners.iterator()
            var contains = false
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null) i.remove() else if (l == listener) contains = true
            }
            if (!contains) volumeChangeListeners.add(WeakReference(listener))
        }
    }

    override val minValue: Float
        get() = MIN_VOLUME_LEVEL

    override val maxValue: Float
        get() = MAX_VOLUME_LEVEL

    /**
     * Fires a new `GainChangeEvent` to the `GainChangeListener`s added to this
     * instance to notify about a change in the level of this `GainControl`.
     */
    private fun fireGainChange() {
        if (gainChangeListeners != null) {
            val ev = GainChangeEvent(this, mute, db, volumeLevel)
            for (l in gainChangeListeners!!) l.gainChange(ev)
        }
    }

    /**
     * Fires a new `VolumeChangeEvent` to the `VolumeChangeListener`s added to this
     * instance to notify about a change in the volume (level) of this `VolumeControl`.
     */
    private fun fireVolumeChange() {
        var ls: MutableList<VolumeChangeListener>
        synchronized(volumeChangeListeners) {
            val i = volumeChangeListeners.iterator()
            ls = ArrayList(volumeChangeListeners.size)
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null) i.remove() else ls.add(l)
            }
        }
        val ev = VolumeChangeEvent(this, volumeLevel, mute)
        for (l in ls) l.volumeChange(ev)
    }

    /**
     * Not used.
     *
     * @return null
     */
    override fun getControlComponent(): Component? {
        return null
    }

    /**
     * Get the current gain set for this object in dB.
     *
     * @return The gain in dB.
     */
    override fun getDB(): Float {
        return db
    }

    /**
     * Get the current gain set for this object as a value between 0.0 and 1.0
     *
     * @return The gain in the level scale (0.0-1.0).
     * @see javax.media.GainControl
     */
    override fun getLevel(): Float {
        return volumeLevel
    }

    override fun getMute(): Boolean {
        return mute
    }

    /**
     * Mutes current sound.
     *
     * @param mute mutes/unmutes.
     */
    override fun setMute(mute: Boolean) {
        if (this.mute != mute) {
            this.mute = mute
            fireVolumeChange()
            fireGainChange()
        }
    }

    /**
     * Reads the initial volume level from the system.
     */
    private fun loadVolume() {
        try {
            val cfg = LibJitsi.configurationService
            if (cfg != null) {
                val volumeLevelString = cfg.getString(volumeLevelConfigurationPropertyName)
                if (volumeLevelString != null) {
                    volumeLevel = volumeLevelString.toFloat()
                    Timber.d("Restored volume: %s", volumeLevel)
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "Failed to restore volume")
        }
    }

    /**
     * Remove interest in gain change update events.
     *
     * @param listener The object that has been receiving events.
     */
    override fun removeGainChangeListener(listener: GainChangeListener) {
        if (gainChangeListeners != null) gainChangeListeners!!.remove(listener)
    }

    /**
     * Removes a `VolumeChangeListener`.
     *
     * @param listener the volume change listener to be removed.
     */
    override fun removeVolumeChangeListener(listener: VolumeChangeListener) {
        synchronized(volumeChangeListeners) {
            val i = volumeChangeListeners.iterator()
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null || l == listener) i.remove()
            }
        }
    }

    /**
     * Set the gain in decibels. Setting the gain to 0.0 (the default) implies that the audio signal
     * is neither amplified nor attenuated. Positive values amplify the audio signal and negative
     * values attenuate the signal.
     *
     * @param gain The new gain in dB.
     * @return The gain that was actually set.
     * @see javax.media.GainControl
     */
    override fun setDB(gain: Float): Float {
        if (db != gain) {
            db = gain
            val volumeLevel = getPowerRatioFromDb(gain, gainReferenceLevel)
            setVolumeLevel(volumeLevel)
        }
        return db
    }

    /**
     * Set the gain using a floating point scale with values between 0.0 and 1.0. 0.0 is silence;
     * 1.0 is the loudest useful level that this `GainControl` supports.
     *
     * @param level The new gain value specified in the level scale.
     * @return The level that was actually set.
     * @see javax.media.GainControl
     */
    override fun setLevel(level: Float): Float {
        return setVolumeLevel(level)
    }

    /**
     * Changes volume level.
     *
     * @param value the new level to set.
     * @return the actual level which was set.
     * @see VolumeControl
     */
    override fun setVolume(value: Float): Float {
        return setVolumeLevel(value)
    }

    /**
     * Internal implementation combining setting level from JMF and from outside Media Service.
     *
     * @param sValue the new value, changed if different from current volume settings.
     * @return the value that was changed or just the current one if the same.
     */
    private fun setVolumeLevel(sValue: Float): Float {
        var value = sValue
        if (value < MIN_VOLUME_LEVEL) value = MIN_VOLUME_LEVEL
        else if (value > MAX_VOLUME_LEVEL) value = MAX_VOLUME_LEVEL

        if (volumeLevel == value) return value
        volumeLevel = value
        updateHardwareVolume()
        fireVolumeChange()

        /*
         * Save the current volume level in the ConfigurationService so that we can restore it on
         * the next application run.
         */
        LibJitsi.configurationService.setProperty(volumeLevelConfigurationPropertyName, volumeLevel.toString())

        db = getDbFromPowerRatio(value, gainReferenceLevel)
        fireGainChange()
        return volumeLevel
    }

    /**
     * Modifies the hardware microphone sensibility (hardware amplification). This is a void
     * function for AbstractVolumeControl since it does not have any connection to hardware volume.
     * But, this function must be redefined by any extending class.
     */
    private fun updateHardwareVolume() {
        /*
         * AbstractVolumeControl does not implement such functionality, the method is defined and
         * invoked in order to allow extenders to provide such functionality.
         */
    }

    companion object {
        /**
         * Returns the maximum allowed volume value.
         *
         * @return the maximum allowed volume value.
         * @see VolumeControl
         */
        /**
         * The maximum volume level accepted by `AbstractVolumeControl`.
         */
        const val MAX_VOLUME_LEVEL = 1.0f

        /**
         * The maximum volume level expressed in percent accepted by `AbstractVolumeControl`.
         */
        const val MAX_VOLUME_PERCENT = 200
        /**
         * Returns the minimum allowed volume value.
         *
         * @return the minimum allowed volume value.
         * @see VolumeControl
         */
        /**
         * The minimum volume level accepted by `AbstractVolumeControl`.
         */
        const val MIN_VOLUME_LEVEL = 0.0f

        /**
         * The minimum volume level expressed in percent accepted by `AbstractVolumeControl`.
         */
        const val MIN_VOLUME_PERCENT = 0

        /**
         * Applies the gain specified by `gainControl` to the signal defined by the
         * `length` number of samples given in `buffer` starting at `offset`.
         *
         * @param gainControl the `GainControl` which specifies the gain to apply
         * @param buffer the samples of the signal to apply the gain to
         * @param offset the start of the samples of the signal in `buffer`
         * @param length the number of samples of the signal given in `buffer`
         */
        fun applyGain(gainControl: GainControl, buffer: ByteArray, offset: Int, length: Int) {
            if (gainControl.mute) Arrays.fill(buffer, offset, offset + length, 0.toByte())
            else {
                // Assign a maximum of MAX_VOLUME_PERCENT to the volume scale.
                val level = gainControl.level * (MAX_VOLUME_PERCENT / 100)
                if (level != 1f) {
                    var i = offset
                    val toIndex = offset + length
                    while (i < toIndex) {
                        val i1 = i + 1
                        var s = (buffer[i].toInt() and 0xff or (buffer[i1].toInt() shl 8)).toShort()

                        /* Clip, don't wrap. */
                        var si = s.toInt()
                        si = (si * level).toInt()
                        s = if (si > Short.MAX_VALUE) Short.MAX_VALUE else if (si < Short.MIN_VALUE) Short.MIN_VALUE else si.toShort()
                        buffer[i] = s.toByte()
                        buffer[i1] = (s.toInt() shr 8).toByte()
                        i += 2
                    }
                }
            }
        }

        /**
         * Returns the decibel value for a ratio between a power level required and the reference power
         * level.
         *
         * @param powerLevelRequired The power level wished for the signal (corresponds to the measured power level).
         * @param referencePowerLevel The reference power level.
         * @return The gain in Db.
         */
        private fun getDbFromPowerRatio(powerLevelRequired: Float, referencePowerLevel: Float): Float {
            val powerRatio = powerLevelRequired / referencePowerLevel

            // Limits the lowest power ratio to be 0.0001.
            val minPowerRatio = 0.0001f
            val flooredPowerRatio = max(powerRatio, minPowerRatio)
            return (20.0 * log10(flooredPowerRatio.toDouble())).toFloat()
        }

        /**
         * Returns the default volume level.
         *
         * @return The default volume level.
         */
        protected val defaultVolumeLevel: Float
            get() {
                return MIN_VOLUME_LEVEL + (MAX_VOLUME_LEVEL - MIN_VOLUME_LEVEL) / (MAX_VOLUME_PERCENT - MIN_VOLUME_PERCENT) / 100
            }

        /**
         * Returns the reference volume level for computing the gain.
         *
         * @return The reference volume level for computing the gain.
         */
        protected fun getGainReferenceLevel(): Float {
            return defaultVolumeLevel
        }

        /**
         * Returns the measured power level corresponding to a gain in decibel and compared to the
         * reference power level.
         *
         * @param gainInDb The gain in Db.
         * @param referencePowerLevel The reference power level.
         * @return The power level the signal, which corresponds to the measured power level.
         */
        private fun getPowerRatioFromDb(gainInDb: Float, referencePowerLevel: Float): Float {
            return 10.0.pow(gainInDb / 20.0).toFloat() * referencePowerLevel
        }
    }
}