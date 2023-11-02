/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.app.Dialog
import android.content.DialogInterface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import timber.log.Timber

/**
 * This fragment when added to parent `VideoCallActivity` will listen for proximity sensor
 * updates and turn the screen on and off when NEAR/FAR distance is detected.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ProximitySensorFragment : Fragment(), SensorEventListener {
    /**
     * Proximity sensor managed used by this fragment.
     */
    private var proximitySensor: Sensor? = null

    /**
     * Unreliable sensor status flag.
     */
    private var sensorDisabled = true

    /**
     * Instant of fragmentManager for screen off Dialog creation
     */
    private var fm: FragmentManager? = null

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        val manager = aTalkApp.sensorManager
        fm = activity!!.supportFragmentManager

        // Skips if the sensor has been already attached
        if (proximitySensor != null) {
            // Re-registers the listener as it might have been unregistered in onPause()
            manager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            return
        }

//		List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_ALL);
//		Timber.d("Device has %s  sensors", sensors.size());
//		for (Sensor s : sensors) {
//			Timber.d("Sensor %s; type: %s", s.getName(), s.getType());
//		}
        proximitySensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor == null) {
            return
        }
        Timber.i("Using proximity sensor: %s", proximitySensor!!.name)
        manager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
        sensorDisabled = false
    }

    /**
     * {@inheritDoc}
     */
    override fun onPause() {
        super.onPause()
        if (proximitySensor != null) {
            screenOn()
            aTalkApp.sensorManager.unregisterListener(this)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroy() {
        super.onDestroy()
        if (proximitySensor != null) {
            screenOn()
            aTalkApp.sensorManager.unregisterListener(this)
            proximitySensor = null
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        if (sensorDisabled) return
        val proximity = event.values[0]
        val max = event.sensor.maximumRange
        // Timber.i("Proximity updated: $proximity max range: $max")
        if (proximity > 0) {
            screenOn()
        } else {
            screenOff()
        }
    }

    /**
     * Turns the screen off.
     */
    private fun screenOff() {
        // ScreenOff exist - proximity detection screen on is out of sync; so just reuse the existing one
//		if (screenOffDialog != null) {
//			Timber.w("screenOffDialog exist when trying to perform screenOff");
//		}
        screenOffDialog = ScreenOffDialog()
        screenOffDialog!!.show(fm!!, "screen_off_dialog")
    }

    /**
     * Turns the screen on.
     */
    private fun screenOn() {
        if (screenOffDialog != null) {
            screenOffDialog!!.dismiss()
            screenOffDialog = null
        }
        //        else {
//			Timber.w("screenOffDialog was null when trying to perform screenOn");
//        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            sensorDisabled = true
            screenOn()
        } else {
            sensorDisabled = false
        }
    }

    /**
     * Blank full screen dialog that captures all keys (BACK is what interest us the most).
     */
    class ScreenOffDialog : DialogFragment() {
        private var volControl: CallVolumeCtrlFragment? = null
        override fun onResume() {
            super.onResume()
            volControl = (activity as VideoCallActivity).getVolCtrlFragment()
        }

        override fun onPause() {
            super.onPause()
            volControl = null
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val d = super.onCreateDialog(savedInstanceState)
            d.setContentView(R.layout.screen_off)
            d.window!!.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            d.setOnKeyListener { dialog: DialogInterface?, keyCode: Int, event: KeyEvent ->
                // Capture all events, but dispatch volume keys to volume control fragment
                if (volControl != null && event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            volControl!!.onKeyVolUp()
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            volControl!!.onKeyVolDown()
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            screenOffDialog!!.dismiss()
                            screenOffDialog = null
                        }
                    }
                }
                true
            }
            return d
        }
    }

    companion object {
        /**
         * Instant of screen off Dialog - dismiss in screenOn()
         */
        private var screenOffDialog: ScreenOffDialog? = null
    }
}