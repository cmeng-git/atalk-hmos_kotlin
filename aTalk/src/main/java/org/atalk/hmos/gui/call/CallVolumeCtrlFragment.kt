/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.widget.Toast
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.service.neomedia.BasicVolumeControl
import org.atalk.service.neomedia.VolumeControl
import org.atalk.service.neomedia.event.VolumeChangeEvent
import org.atalk.service.neomedia.event.VolumeChangeListener
import org.atalk.service.osgi.OSGiFragment

/**
 * Fragment used to control call volume. Key events for volume up and down have to be captured by the parent
 * `Activity` and passed here, before they get to system audio service. The volume is increased using
 * `AudioManager` until it reaches maximum level, then we increase the Libjitsi volume gain.
 * The opposite happens when volume is being decreased.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CallVolumeCtrlFragment : OSGiFragment(), VolumeChangeListener {
    /**
     * Current volume gain "position" in range from 0 to 10.
     */
    private var position = 0

    /**
     * Output volume control.
     */
    private var volumeControl: VolumeControl? = null

    /**
     * The `AudioManager` used to control voice call stream volume.
     */
    private lateinit var audioManager: AudioManager

    /**
     * The toast instance used to update currently displayed toast if any.
     */
    private var toast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = aTalkApp.globalContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val mediaService = NeomediaActivator.getMediaServiceImpl()
        if (mediaService != null) volumeControl = mediaService.outputVolumeControl
    }

    override fun onResume() {
        super.onResume()
        if (volumeControl == null) return

        val currentVol = (volumeControl as BasicVolumeControl).level
        // Default
        position = if (currentVol < 0) {
            5
        } else {
            calcPosition(currentVol)
        }

        volumeControl!!.addVolumeChangeListener(this)
    }

    override fun onPause() {
        if (volumeControl != null) {
            volumeControl!!.removeVolumeChangeListener(this)
        }
        if (toast != null && toast!!.view != null) {
            toast!!.cancel()
            toast = null
        }
        super.onPause()
    }

    /**
     * Returns current volume index for `AudioManager.STREAM_VOICE_CALL`.
     *
     * @return current volume index for `AudioManager.STREAM_VOICE_CALL`.
     */
    private fun getAudioStreamVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
    }

    /**
     * Method should be called by the parent `Activity` when volume up key is pressed.
     */
    fun onKeyVolUp() {
        var controlMode = AudioManager.ADJUST_RAISE
        if (position < 5) {
            controlMode = AudioManager.ADJUST_SAME
        }
        val current = getAudioStreamVolume()
        audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, controlMode, AudioManager.FLAG_SHOW_UI)
        val newStreamVol = getAudioStreamVolume()
        if (current == newStreamVol) {
            setVolumeGain(position + 1)
        } else {
            setVolumeGain(5)
        }
    }

    /**
     * Method should be called by the parent `Activity` when volume down key is pressed.
     */
    fun onKeyVolDown() {
        var controlMode = AudioManager.ADJUST_LOWER
        if (position > 5) {
            // We adjust the same just to show the gui
            controlMode = AudioManager.ADJUST_SAME
        }
        val current = getAudioStreamVolume()
        audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, controlMode, AudioManager.FLAG_SHOW_UI)
        val newStreamVol = getAudioStreamVolume()
        if (current == newStreamVol) {
            setVolumeGain(position - 1)
        } else {
            setVolumeGain(5)
        }
    }

    private fun calcPosition(volumeGain: Float): Int {
        return (volumeGain / getVolumeCtrlRange() * 10f).toInt()
    }

    private fun setVolumeGain(newPosition: Int) {
        val newVolume = getVolumeCtrlRange() * (newPosition.toFloat() / 10f)
        position = calcPosition(volumeControl!!.setVolume(newVolume))
    }

    override fun volumeChange(volumeChangeEvent: VolumeChangeEvent) {
        position = calcPosition(volumeChangeEvent.level / getVolumeCtrlRange())
        runOnUiThread {
            val parent = activity ?: return@runOnUiThread
            val txt = aTalkApp.getResString(R.string.service_gui_VOLUME_GAIN_LEVEL, position * 10)
            if (toast == null) {
                toast = Toast.makeText(parent, txt, Toast.LENGTH_SHORT)
            } else {
                toast!!.setText(txt)
            }
            toast!!.show()
        }
    }

    /**
     * Returns abstract volume control range calculated for volume control min and max values.
     *
     * @return the volume control range calculated for current volume control min and max values.
     */
    private fun getVolumeCtrlRange(): Float {
        return volumeControl!!.maxValue - volumeControl!!.minValue
    }
}