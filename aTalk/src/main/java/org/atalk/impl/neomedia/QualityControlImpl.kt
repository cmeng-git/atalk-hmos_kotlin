/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.service.neomedia.QualityControl
import org.atalk.service.neomedia.QualityPreset
import timber.log.Timber
import java.awt.Dimension

/**
 * Implements [QualityControl] for the purposes of [VideoMediaStreamImpl].
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class QualityControlImpl : QualityControl {
    /**
     * This is the local settings from the configuration panel.
     */
    private var localSettingsPreset: QualityPreset? = null

    /**
     * The maximum values for resolution, and framerate etc.
     */
    private var maxPreset: QualityPreset? = null

    /**
     * The current used preset.
     */
    private var preset: QualityPreset? = null

    /**
     * The current used preset.
     * Changes remote send preset, the one we will receive.
     */
    private fun setRemoteReceivePreset(preset: QualityPreset) {
        val preferredSendPreset = getPreferredSendPreset()

        if (preset > preferredSendPreset)
            this.preset = preferredSendPreset
        else {
            this.preset = preset

            var resolution: Dimension?
            if (preset.resolution.also { resolution = it } != null) {
                Timber.i("video send resolution: %dx%d", resolution!!.width, resolution!!.height)
            }
        }
    }

    /**
     * The minimum resolution values for remote part.
     * We do not support such a value at the time of this writing
     */
    override fun getRemoteReceivePreset(): QualityPreset? {
        return preset
    }

    /**
     * The max resolution values for remote part.
     */
    override fun getRemoteSendMinPreset(): QualityPreset? {
        /* We do not support such a value at the time of this writing. */
        return null
    }

    /**
     * The max resolution values for remote part.
     *
     * @return max resolution values for remote part.
     */
    override fun getRemoteSendMaxPreset(): QualityPreset? {
        return maxPreset
    }

    /**
     * Changes remote send preset, the one we will receive.
     *
     * @param preset
     */
    override fun setRemoteSendMaxPreset(preset: QualityPreset) {
        maxPreset = preset
    }

    /**
     * Does nothing specific locally.
     */
    override fun setPreferredRemoteSendMaxPreset(preset: QualityPreset) {
        setRemoteSendMaxPreset(preset)
    }

    /**
     * Gets the local setting of capture.
     *
     * @return the local setting of capture
     */
    private fun getPreferredSendPreset(): QualityPreset {
        if (localSettingsPreset == null) {
            val devCfg = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration
            localSettingsPreset = QualityPreset(devCfg.getVideoSize(), devCfg.getFrameRate().toFloat())
        }
        return localSettingsPreset!!
    }

    /**
     * Sets maximum resolution.
     */
    fun setRemoteReceiveResolution(res: Dimension?) {
        setRemoteReceivePreset(QualityPreset(res))
    }
}