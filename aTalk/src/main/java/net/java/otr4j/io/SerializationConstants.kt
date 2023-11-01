/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.io

/**
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
interface SerializationConstants {
    companion object {
        const val HEAD = "?OTR"
        const val HEAD_ENCODED = ':'
        const val HEAD_ERROR = ' '
        const val HEAD_QUERY_Q = '?'
        const val HEAD_QUERY_V = 'v'
        const val ERROR_PREFIX = "Error:"
        const val TYPE_LEN_BYTE = 1
        const val TYPE_LEN_SHORT = 2
        const val TYPE_LEN_INT = 4
        const val TYPE_LEN_MAC = 20
        const val TYPE_LEN_CTR = 8
        const val DATA_LEN = TYPE_LEN_INT
        const val TLV_LEN = TYPE_LEN_SHORT
    }
}