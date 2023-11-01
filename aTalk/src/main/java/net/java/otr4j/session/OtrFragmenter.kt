package net.java.otr4j.session

import net.java.otr4j.OtrEngineHost
import net.java.otr4j.OtrPolicy
import java.io.IOException
import java.util.*
import kotlin.math.min

/**
 * OTR fragmenter.
 * TODO It may be better to separate the v2 and v3 implementations into specialized classes.
 *
 * @author Danny van Heumen
 * @author Eng Chong Meng
 */
class OtrFragmenter(session: Session?, host: OtrEngineHost?) {
    /**
     * Session instance.
     */
    private val session: Session
    /**
     * Get instructions for fragmentation behaviour.
     *
     * @return returns instructions
     */
    /**
     * Instructions on how to fragment the input message.
     */
    val host: OtrEngineHost

    /**
     * Constructor.
     *
     * session session instance (cannot be null)
     * host OTR engine host calling upon OTR session
     */
    init {
        if (session == null) {
            throw NullPointerException("session cannot be null")
        }
        this.session = session
        if (host == null) {
            throw NullPointerException("host cannot be null")
        }
        this.host = host
    }

    /**
     * Calculate the number of fragments that are required for the message to be sent fragmented completely.
     *
     * @param message the original message
     * @return returns the number of fragments required
     * @throws IOException throws an IOException in case fragment size is too small to store any content or when
     * the provided policy does not support fragmentation, for example if only OTRv1 is allowed.
     */
    @Throws(IOException::class)
    fun numberOfFragments(message: String?): Int {
        val sessionID = session.sessionID
        val requested = host.getFragmenterInstructions(sessionID)
        val instructions = FragmenterInstructions.verify(requested)
        return numberOfFragments(message, instructions)
    }

    /**
     * Calculate the number of fragments that are required for the message to be sent fragmented completely.
     *
     * @param message the message to fragment
     * @param instructions the fragmentation instructions
     * @return returns number of fragments required
     * @throws IOException throws an IOException in case fragment size is too small to store any content or when
     * the provided policy does not support fragmentation, for example if only OTRv1 is allowed.
     */
    @Throws(IOException::class)
    private fun numberOfFragments(message: String?, instructions: FragmenterInstructions): Int {
        return if (instructions.maxFragmentSize == FragmenterInstructions.UNLIMITED
                || instructions.maxFragmentSize >= message!!.length) {
            1
        } else computeFragmentNumber(message, instructions)
    }

    /**
     * Compute the number of fragments required.
     *
     * @param message the original message
     * @param instructions fragmentation instructions
     * @return returns number of fragments required.
     * @throws IOException throws an IOException if fragment size is too small.
     */
    @Throws(IOException::class)
    private fun computeFragmentNumber(message: String?, instructions: FragmenterInstructions): Int {
        val overhead = computeHeaderSize()
        val payloadSize = instructions.maxFragmentSize - overhead
        if (payloadSize <= 0) {
            throw IOException("Fragment size too small for storing content.")
        }
        var messages = message!!.length / payloadSize
        if (message.length % payloadSize != 0) {
            messages++
        }
        return messages
    }

    /**
     * Fragment the given message into pieces as specified by the FragmenterInstructions instance.
     *
     * @param message the original message
     * @return returns an array of message fragments. The array will contain at least 1 message
     * fragment, or more if fragmentation is necessary.
     * @throws IOException throws an IOException if the fragment size is too small or if the maximum number of
     * fragments is exceeded.
     */
    @Throws(IOException::class)
    fun fragment(message: String): Array<String> {
        val sessionID = session.sessionID
        val requested = host.getFragmenterInstructions(sessionID)
        val instructions = FragmenterInstructions.verify(requested)
        return fragment(message, instructions)
    }

    /**
     * Fragment a message according to the specified instructions.
     *
     * @param message the message
     * @param instructions the instructions
     * @return returns the fragmented message. The array will contain at least 1 message fragment,
     * or more if fragmentation is necessary.
     * @throws IOException Exception in the case when it is impossible to fragment the message according to the
     * specified instructions.
     */
    @Throws(IOException::class)
    private fun fragment(message: String, instructions: FragmenterInstructions): Array<String> {
        if (instructions.maxFragmentSize == FragmenterInstructions.UNLIMITED
                || instructions.maxFragmentSize >= message.length) {
            return arrayOf(message)
        }
        val num = numberOfFragments(message, instructions)
        if (instructions.maxFragmentsAllowed != FragmenterInstructions.UNLIMITED
                && instructions.maxFragmentsAllowed < num) {
            throw IOException("Need more fragments to store full message.")
        }
        if (num > MAXIMUM_NUMBER_OF_FRAGMENTS) {
            throw IOException("Number of necessary fragments exceeds limit.")
        }
        val payloadSize = instructions.maxFragmentSize - computeHeaderSize()
        var previous = 0
        val fragments = LinkedList<String>()
        while (previous < message.length) {
            // Either get new position or position of exact message end
            val end = min(previous + payloadSize, message.length)
            val partialContent = message.substring(previous, end)
            fragments.add(createMessageFragment(fragments.size, num, partialContent))
            previous = end
        }
        return fragments.toTypedArray()
    }

    /**
     * Create a message fragment.
     *
     * @param count the current fragment number
     * @param total the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     * @throws UnsupportedOperationException in case v1 is only allowed in policy
     */
    private fun createMessageFragment(count: Int, total: Int, partialContent: String): String {
        return if (policy.allowV3) {
            createV3MessageFragment(count, total, partialContent)
        } else {
            createV2MessageFragment(count, total, partialContent)
        }
    }

    /**
     * Create a message fragment according to the v3 message format.
     *
     * @param count the current fragment number
     * @param total the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     */
    private fun createV3MessageFragment(count: Int, total: Int, partialContent: String): String {
        return String.format(Locale.US, OTR_V3_MESSAGE_FRAGMENT_FORMAT,
                senderInstance, receiverInstance, count + 1, total, partialContent)
    }

    /**
     * Create a message fragment according to the v2 message format.
     *
     * @param count the current fragment number
     * @param total the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     */
    private fun createV2MessageFragment(count: Int, total: Int,
                                        partialContent: String): String {
        return String.format(Locale.US, OTR_V2_MESSAGE_FRAGMENT_FORMAT, count + 1, total, partialContent)
    }

    /**
     * Compute size of fragmentation header size.
     *
     * @return returns size of fragment header
     * @throws UnsupportedOperationException in case v1 is only allowed in policy
     */
    private fun computeHeaderSize(): Int {
        return if (policy.allowV3) {
            computeHeaderV3Size()
        } else if (policy.allowV2) {
            computeHeaderV2Size()
        } else {
            throw UnsupportedOperationException(OTR_V1_NOT_SUPPORTED)
        }
    }

    /**
     * Get the OTR policy.
     *
     * @return returns the policy
     */
    private val policy: OtrPolicy
        get() = session.sessionPolicy

    /**
     * Get the sender instance.
     *
     * @return returns the sender instance
     */
    private val senderInstance: Int
        get() = session.senderInstanceTag.value

    /**
     * Get the receiver instance.
     *
     * @return returns the receiver instance
     */
    private val receiverInstance: Int
        get() = session.receiverInstanceTag.value

    companion object {
        /**
         * Exception message in cases where only OTRv1 is allowed.
         */
        private const val OTR_V1_NOT_SUPPORTED = "Fragmentation is not supported in OTRv1."

        /**
         * The maximum number of fragments supported by the OTR (v3) protocol.
         */
        private const val MAXIMUM_NUMBER_OF_FRAGMENTS = 65535

        /**
         * The message format of an OTRv3 message fragment.
         */
        private const val OTR_V3_MESSAGE_FRAGMENT_FORMAT = "?OTR|%08x|%08x,%05d,%05d,%s,"

        /**
         * The message format of an OTRv2 message fragment.
         */
        private const val OTR_V2_MESSAGE_FRAGMENT_FORMAT = "?OTR,%d,%d,%s,"

        /**
         * Compute the overhead size for a v3 header.
         *
         * @return returns size of v3 header
         */
        fun computeHeaderV3Size(): Int {
            // For a OTRv3 header this seems to be a constant number, since the
            // specs seem to suggest that smaller numbers have leading zeros.
            return 36
        }

        /**
         * Compute the overhead size for a v2 header.
         *
         *
         * Current implementation returns an upper bound size for the size of the header. As I
         * understand it, the protocol does not require leading zeros to fill a 5-space number are so
         * in theory it is possible to gain a few extra characters per message if an exact
         * calculation of the number of required chars is used.
         *
         *
         * TODO I think this is dependent on the number of chars in a decimal representation of the
         * current and total number of fragments.
         *
         * @return returns size of v2 header
         */
        fun computeHeaderV2Size(): Int {
            // currently returns an upper bound (for the case of 10000+ fragments)
            return 18
        }
    }
}