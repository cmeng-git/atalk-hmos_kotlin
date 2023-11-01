/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object TablesTypeOffset {
    val SKP_Silk_type_offset_CDF = intArrayOf(0, 37522, 41030, 44212, 65535)
    const val SKP_Silk_type_offset_CDF_offset = 2
    val SKP_Silk_type_offset_joint_CDF = arrayOf<IntArray?>(intArrayOf(0, 57686, 61230, 62358, 65535), intArrayOf(0, 18346, 40067, 43659, 65535), intArrayOf(0, 22694, 24279, 35507, 65535), intArrayOf(0, 6067, 7215, 13010, 65535))
}