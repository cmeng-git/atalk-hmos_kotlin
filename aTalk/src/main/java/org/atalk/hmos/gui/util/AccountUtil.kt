/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.core.content.res.ResourcesCompat
import net.java.sip.communicator.service.protocol.ProtocolNames
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp

/**
 * Class containing utility methods that may concern accounts. Provide default values for some fields.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object AccountUtil {
    /**
     * Returns [Drawable] representing default presence status for specified `protocolName`
     *
     * @param context [Context] of current [android.app.Activity]
     * @param protocolName the name of the protocol
     * @return [Drawable] for default presence status or `null` otherwise
     */
    fun getDefaultPresenceIcon(context: Context, protocolName: String): Drawable? {
        if (protocolName == ProtocolNames.SIP) {
            return BitmapDrawable(aTalkApp.appResources,
                    BitmapFactory.decodeResource(context.resources, R.drawable.default_sip_status))
        } else if (protocolName == ProtocolNames.JABBER) {
            return BitmapDrawable(aTalkApp.appResources,
                    BitmapFactory.decodeResource(context.resources, R.drawable.default_jabber_status))
        }
        return null
    }

    /**
     * Returns the default avatar [Drawable]
     *
     * @param context current application [Context]
     * @return the default avatar [Drawable]
     */
    fun getDefaultAvatarIcon(context: Context): LayerDrawable {
        return ResourcesCompat.getDrawable(context.resources, R.drawable.avatar_layer_drawable, null) as LayerDrawable
    }
}