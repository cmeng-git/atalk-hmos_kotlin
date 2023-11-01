/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidnotification

import android.content.Context
import android.os.Vibrator
import net.java.sip.communicator.service.notification.NotificationAction
import net.java.sip.communicator.service.notification.VibrateNotificationAction
import net.java.sip.communicator.service.notification.VibrateNotificationHandler
import org.atalk.hmos.aTalkApp

/**
 * Android implementation of [VibrateNotificationHandler].
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class VibrateHandlerImpl : VibrateNotificationHandler {
    /**
     * The `Vibrator` if present on this device.
     */
    private val vibratorService: Vibrator?

    /**
     * Creates new instance of `VibrateHandlerImpl`.
     */
    init {
        vibratorService = aTalkApp.globalContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Returns `true` if the `Vibrator` service is present on this device.
     *
     * @return `true` if the `Vibrator` service is present on this device.
     */
    private fun hasVibrator(): Boolean {
        return vibratorService != null && vibratorService.hasVibrator()
    }

    /**
     * {@inheritDoc}
     */
    override fun vibrate(vibrateAction: VibrateNotificationAction?) {
        if (hasVibrator())
            vibratorService!!.vibrate(vibrateAction!!.pattern, vibrateAction.repeat)
    }

    /**
     * {@inheritDoc}
     */
    override fun cancel() {
        if (!hasVibrator()) return
        vibratorService!!.cancel()
    }

    override val actionType: String
        get() = NotificationAction.ACTION_VIBRATE
}

        
        