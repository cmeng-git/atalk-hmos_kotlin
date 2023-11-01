/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * An Operation Set defining option to unconditional auto answer incoming calls.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface OperationSetBasicAutoAnswer : OperationSet {
    /**
     * Sets the auto answer option to unconditionally answer all incoming calls.
     */
    fun setAutoAnswerUnconditional()

    /**
     * Is the auto answer option set to unconditionally answer all incoming calls.
     *
     * @return is auto answer set to unconditional.
     */
    fun isAutoAnswerUnconditionalSet(): Boolean

    /**
     * Clear any previous settings.
     */
    fun clear()

    /**
     * Sets the auto answer with video to video calls.
     *
     * @param answerWithVideo A boolean set to true to activate the auto answer with video
     * when receiving a video call. False otherwise.
     */
    fun setAutoAnswerWithVideo(answerWithVideo: Boolean)

    /**
     * Return if the auto answer with video to video calls is enabled.
     *
     * @return A boolean set to true if the auto answer with video when receiving
     * a video call is activated. False otherwise.
     */
    fun isAutoAnswerWithVideoSet(): Boolean

    companion object {
        /**
         * Auto-answer unconditional account property.
         */
        const val AUTO_ANSWER_UNCOND_PROP = "AUTO_ANSWER_UNCONDITIONAL"

        /**
         * Auto answer video calls with video account property.
         */
        const val AUTO_ANSWER_WITH_VIDEO_PROP = "AUTO_ANSWER_WITH_VIDEO"
    }
}