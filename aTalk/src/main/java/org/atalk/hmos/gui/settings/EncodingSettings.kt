/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.os.Bundle
import android.view.KeyEvent
import org.atalk.hmos.R
import org.atalk.hmos.gui.account.settings.MediaEncodingActivity
import org.atalk.hmos.gui.account.settings.MediaEncodingsFragment
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.service.osgi.OSGiActivity
import org.atalk.util.MediaType

/**
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class EncodingSettings : OSGiActivity() {
    private lateinit var mMediaEncodings: MediaEncodingsFragment
    private lateinit var mediaType: MediaType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaTypeStr = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        if (MEDIA_TYPE_AUDIO == mediaTypeStr) {
            mediaType = MediaType.AUDIO
            setMainTitle(R.string.service_gui_settings_AUDIO_CODECS_TITLE)
        } else if (MEDIA_TYPE_VIDEO == mediaTypeStr) {
            mediaType = MediaType.VIDEO
            setMainTitle(R.string.service_gui_settings_VIDEO_CODECS_TITLE)
        }

        if (savedInstanceState == null) {
            val mediaSrvc = NeomediaActivator.getMediaServiceImpl()
            if (mediaSrvc != null) {
                val encConfig = mediaSrvc.currentEncodingConfiguration
                val formats = MediaEncodingActivity.getEncodings(encConfig, mediaType)
                val encodings = MediaEncodingActivity.getEncodingsStr(formats.iterator())
                val priorities = MediaEncodingActivity.getPriorities(formats, encConfig)
                mMediaEncodings = MediaEncodingsFragment.newInstance(encodings, priorities)
                supportFragmentManager.beginTransaction().add(android.R.id.content, mMediaEncodings).commit()
            }
        } else {
            mMediaEncodings = supportFragmentManager.findFragmentById(android.R.id.content) as MediaEncodingsFragment
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val mediaSrvc = NeomediaActivator.getMediaServiceImpl()
        if (keyCode == KeyEvent.KEYCODE_BACK && mediaSrvc != null) {
            MediaEncodingActivity.commitPriorities(
                    NeomediaActivator.getMediaServiceImpl()!!.currentEncodingConfiguration,
                    mediaType, mMediaEncodings)
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        const val EXTRA_MEDIA_TYPE = "media_type"
        const val MEDIA_TYPE_AUDIO = "media_type.AUDIO"
        const val MEDIA_TYPE_VIDEO = "media_type.VIDEO"
    }
}