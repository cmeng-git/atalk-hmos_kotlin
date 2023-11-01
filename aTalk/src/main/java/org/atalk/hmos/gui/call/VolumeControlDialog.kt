/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import org.atalk.hmos.R
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.service.neomedia.VolumeControl
import org.atalk.service.neomedia.event.VolumeChangeEvent
import org.atalk.service.neomedia.event.VolumeChangeListener
import org.atalk.service.osgi.OSGiDialogFragment

/**
 * The dialog allows user to manipulate input or output volume gain level. To specify which one will be manipulated by
 * current instance the [.ARG_DIRECTION] should be specified with one of direction values:
 * [.DIRECTION_INPUT] or [.DIRECTION_OUTPUT]. Static factory methods are convenient for creating
 * parametrized dialogs.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class VolumeControlDialog : OSGiDialogFragment(), VolumeChangeListener, SeekBar.OnSeekBarChangeListener {
    /**
     * Abstract volume control used by this dialog.
     */
    private lateinit var volumeControl: VolumeControl

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaService = NeomediaActivator.getMediaServiceImpl()!!

        // Selects input or output volume control based on the arguments.
        val direction = arguments!!.getInt(ARG_DIRECTION, 0)
        volumeControl = when (direction) {
            DIRECTION_OUTPUT -> {
                mediaService.outputVolumeControl
            }
            DIRECTION_INPUT -> {
                mediaService.inputVolumeControl
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        volumeControl.addVolumeChangeListener(this)
        val bar = getVolumeBar()
        // Initialize volume bar
        val progress = getVolumeBarProgress(bar, volumeControl.volumeLevel)
        bar.progress = progress
    }

    /**
     * {@inheritDoc}
     */
    override fun onPause() {
        super.onPause()
        volumeControl.removeVolumeChangeListener(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val content = inflater.inflate(R.layout.volume_control, container, false)
        val bar = content.findViewById<View>(R.id.seekBar) as SeekBar
        bar.setOnSeekBarChangeListener(this)
        var titleStrId = R.string.service_gui_VOLUME_CONTROL_TITLE
        if (arguments!!.getInt(ARG_DIRECTION) == DIRECTION_INPUT) {
            titleStrId = R.string.service_gui_MIC_CONTROL_TITLE
        }
        dialog!!.setTitle(titleStrId)
        return content
    }

    /**
     * Returns the `SeekBar` used to control the volume.
     *
     * @return the `SeekBar` used to control the volume.
     */
    private fun getVolumeBar(): SeekBar {
        return view!!.findViewById(R.id.seekBar) as SeekBar
    }

    /**
     * {@inheritDoc}
     */
    override fun volumeChange(volumeChangeEvent: VolumeChangeEvent) {
        val seekBar = getVolumeBar()
        val progress = getVolumeBarProgress(seekBar, volumeChangeEvent.level)
        seekBar.progress = progress
    }

    /**
     * Calculates the progress value suitable for given `SeekBar` from the device volume level.
     *
     * @param volumeBar
     * the `SeekBar` for which the progress value will be calculated.
     * @param level
     * actual volume level from `VolumeControl`. Value `-1.0` means the level is invalid and
     * default progress value should be provided.
     * @return the progress value calculated from given volume level that will be suitable for specified
     * `SeekBar`.
     */
    private fun getVolumeBarProgress(volumeBar: SeekBar, level: Float): Int {
        var volLevel = level
        if (volLevel.toDouble() == -1.0) {
            // If the volume is invalid position at the middle
            volLevel = getVolumeCtrlRange() / 2
        }
        val progress = volLevel / getVolumeCtrlRange()
        return (progress * volumeBar.max).toInt()
    }

    /**
     * Returns abstract volume control range calculated for volume control min and max values.
     *
     * @return the volume control range calculated for current volume control min and max values.
     */
    private fun getVolumeCtrlRange(): Float {
        return volumeControl.maxValue - volumeControl.minValue
    }

    /**
     * {@inheritDoc}
     */
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (!fromUser) return
        val position = progress.toFloat() / seekBar.max.toFloat()
        volumeControl.setVolume(getVolumeCtrlRange() * position)
    }

    /**
     * {@inheritDoc}
     */
    override fun onStartTrackingTouch(seekBar: SeekBar) {}

    /**
     * {@inheritDoc}
     */
    override fun onStopTrackingTouch(seekBar: SeekBar) {}

    companion object {
        /**
         * The argument specifies whether output or input volume gain will be manipulated by this dialog.
         */
        const val ARG_DIRECTION = "ARG_DIRECTION"

        /**
         * The direction argument value for output volume gain.
         */
        const val DIRECTION_OUTPUT = 0

        /**
         * The direction argument value for input volume gain.
         */
        const val DIRECTION_INPUT = 1

        /**
         * Creates the `VolumeControlDialog` that can be used to control output volume gain level.
         *
         * @return the `VolumeControlDialog` for output volume gain level.
         */
        fun createOutputVolCtrlDialog(): VolumeControlDialog {
            val dialog = VolumeControlDialog()
            val args = Bundle()
            args.putInt(ARG_DIRECTION, DIRECTION_OUTPUT)
            dialog.arguments = args
            return dialog
        }

        /**
         * Creates the `VolumeControlDialog` for controlling microphone gain level.
         *
         * @return the `VolumeControlDialog` that can be used to set microphone gain level.
         */
        fun createInputVolCtrlDialog(): VolumeControlDialog {
            val dialog = VolumeControlDialog()
            val args = Bundle()
            args.putInt(ARG_DIRECTION, DIRECTION_INPUT)
            dialog.arguments = args
            return dialog
        }
    }
}