package org.atalk.hmos.gui.chat

import android.os.Bundle
import org.atalk.service.osgi.OSGiActivity
import timber.log.Timber
import java.net.URI
import java.net.URISyntaxException

/**
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class aTalkProtocolReceiver : OSGiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        Timber.i("aTalk protocol intent received %s", intent)
        val urlStr = intent.dataString
        if (urlStr != null) {
            try {
                val url = URI(urlStr)
                ChatSessionManager.notifyChatLinkClicked(url)
            } catch (e: URISyntaxException) {
                Timber.e(e, "Error parsing clicked URL")
            }
        } else {
            Timber.w("No URL supplied in aTalk link")
        }
        finish()
    }
}