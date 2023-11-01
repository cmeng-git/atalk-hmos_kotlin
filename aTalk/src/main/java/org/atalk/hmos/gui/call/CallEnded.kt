/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.AndroidImageUtil
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiFragment
import org.jivesoftware.smackx.avatar.AvatarManager
import org.jxmpp.jid.BareJid
import org.osgi.framework.Bundle
import timber.log.Timber

/**
 * Fragment displayed in `VideoCallActivity` when the call has ended.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CallEnded : OSGiFragment(), View.OnClickListener {
    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.call_ended, container, false)

        // Display callPeer avatar; take care NPE from field
        var avatar: ByteArray? = null
        try {
            val bareJid = VideoCallActivity.callState.callPeer!!.asBareJid()
            avatar = AvatarManager.getAvatarImageByJid(bareJid)
        } catch (e: Exception) {
            Timber.w("Failed to find callPeer Jid")
        }
        if (avatar != null) (v.findViewById<View>(R.id.calleeAvatar) as ImageView).setImageBitmap(AndroidImageUtil.bitmapFromBytes(avatar))
        ViewUtil.setTextViewValue(v, R.id.callTime, VideoCallActivity.callState.callDuration)
        val errorReason = VideoCallActivity.callState.errorReason
        if (errorReason.isNotEmpty()) {
            ViewUtil.setTextViewValue(v, R.id.callErrorReason, errorReason)
        } else {
            ViewUtil.ensureVisible(v, R.id.callErrorReason, false)
        }
        v.findViewById<View>(R.id.button_call_hangup).setOnClickListener(this)
        v.findViewById<View>(R.id.button_call_back_to_chat).setOnClickListener(this)
        return v
    }

    /**
     * Handles buttons action events. the `ActionEvent` that notified us
     */
    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_call_hangup, R.id.button_call_back_to_chat -> {
                val ctx = activity!!
                ctx.finish()
                ctx.startActivity(aTalkApp.homeIntent)
            }
        }
    }
}