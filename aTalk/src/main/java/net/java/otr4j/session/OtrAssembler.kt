/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.session

import java.net.ProtocolException

/**
 * @author Felix Eckhofer
 * @author Eng Chong Meng
 */
class OtrAssembler(
        /**
         * Relevant instance tag. OTRv3 fragments with a different instance tag are discarded.
         */
        private val ownInstance: InstanceTag) {
    /**
     * Accumulated fragment thus far.
     */
    private var fragment: StringBuffer? = null

    /**
     * Number of last fragment received. This variable must be able to store an unsigned short
     * value.
     */
    private var fragmentCur = 0

    /**
     * Total number of fragments in message. This variable must be able to store an unsigned short
     * value.
     */
    private var fragmentMax = 0

    init {
        discard()
    }

    /**
     * Appends a message fragment to the internal buffer and returns the full message if msgText
     * was no fragmented message or all the fragments have been combined. Returns null, if there
     * are fragments pending or an invalid fragment was received.
     *
     *
     * A fragmented OTR message looks like this: (V2) ?OTR,k,n,piece-k,
     * or (V3)?OTR|sender_instance|receiver_instance,k,n,piece-k,
     *
     * @param msgText
     * Message to be processed.
     * @return String with the accumulated message or null if the message was incomplete or
     * malformed
     * @throws ProtocolException
     * MVN_PASS_JAVADOC_INSPECTION
     * @throws UnknownInstanceException
     * MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(ProtocolException::class, UnknownInstanceException::class)
    fun accumulate(msgText: String?): String? {
        // if it's a fragment, remove everything before "k,n,piece-k"
        var msgText = msgText
        if (msgText!!.startsWith(HEAD_FRAGMENT_V2)) {
            // v2
            msgText = msgText.substring(HEAD_FRAGMENT_V2.length)
        } else if (msgText.startsWith(HEAD_FRAGMENT_V3)) {
            // v
            msgText = msgText.substring(HEAD_FRAGMENT_V3.length)

            // break away the v2 part
            val instancePart = msgText.split(",", limit = 2)
            // split the two instance ids
            val instances = instancePart[0].split("\\|".toRegex(), limit = 2)
            if (instancePart.size != 2 || instances.size != 2) {
                discard()
                throw ProtocolException()
            }
            val receiverInstance = try {
                instances[1].toInt(16)
            } catch (e: NumberFormatException) {
                discard()
                throw ProtocolException()
            }
            if (receiverInstance != 0 && receiverInstance != ownInstance.value) {
                // discard message for different instance id
                throw UnknownInstanceException("Message for unknown instance tag "
                        + receiverInstance.toString() + " received: " + msgText)
            }

            // continue with v2 part of fragment
            msgText = instancePart[1]
        } else {
            // not a fragmented message
            discard()
            return msgText
        }
        val params = msgText.split(",", limit = 4)
        val k: Int
        val n: Int
        try {
            k = params[0].toInt()
            n = params[1].toInt()
        } catch (e: NumberFormatException) {
            discard()
            throw ProtocolException()
        } catch (e: ArrayIndexOutOfBoundsException) {
            discard()
            throw ProtocolException()
        }
        if (k == 0 || n == 0 || k > n || params.size != 4 || params[3].length != 0) {
            discard()
            throw ProtocolException()
        }
        msgText = params[2]
        if (k == 1) {
            // first fragment
            discard()
            fragmentCur = k
            fragmentMax = n
            fragment!!.append(msgText)
        } else if (n == fragmentMax && k == fragmentCur + 1) {
            // consecutive fragment
            fragmentCur++
            fragment!!.append(msgText)
        } else {
            // out-of-order fragment
            discard()
            throw ProtocolException()
        }
        return if (n == k && n > 0) {
            val result = fragment.toString()
            discard()
            result
        } else {
            null // incomplete fragment
        }
    }

    /**
     * Discard current fragment buffer and reset the counters.
     */
    fun discard() {
        fragment = StringBuffer()
        fragmentCur = 0
        fragmentMax = 0
    }

    companion object {
        private const val HEAD_FRAGMENT_V2 = "?OTR,"
        private const val HEAD_FRAGMENT_V3 = "?OTR|"
    }
}