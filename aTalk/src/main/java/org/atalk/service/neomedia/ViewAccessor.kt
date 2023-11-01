/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import android.content.Context
import android.view.View

/**
 * Declares the interface to be supported by providers of access to [View]s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface ViewAccessor {
    /**
     * Gets the [View] provided by this instance which is to be used in a specific [Context].
     *
     * @param context the `Context` in which the provided `View` will be used
     * @return the `View` provided by this instance which is to be used in a specific `Context`
     */
    fun getView(context: Context?): View?
}