/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.service.libjitsi.LibJitsi

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object NeomediaServiceUtils {
    @JvmStatic
    val mediaServiceImpl: MediaServiceImpl?
        get() = LibJitsi.mediaService as MediaServiceImpl?
}