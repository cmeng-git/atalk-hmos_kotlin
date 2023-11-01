/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * An Advanced Operation Set defining options to auto answer/forward incoming calls.
 *
 * @author Damian Minkov
 */
interface OperationSetAdvancedAutoAnswer : OperationSet {
    /**
     * Sets a specified header and its value if they exist in the incoming call packet this will
     * activate auto answer. If value is empty or null it will be considered as any (will search
     * only for a header with that name and ignore the value)
     *
     * @param headerName
     * the name of the header to search
     * @param value
     * the value for the header, can be null.
     */
    fun setAutoAnswerCondition(headerName: String?, value: String?)

    /**
     * Is the auto answer option set to conditionally answer all incoming calls.
     *
     * @return is auto answer set to conditional.
     */
    fun isAutoAnswerConditionSet(): Boolean

    /**
     * Returns the name of the header if conditional auto answer is set.
     *
     * @return the name of the header if conditional auto answer is set.
     */
    fun getAutoAnswerHeaderName(): String?

    /**
     * Returns the value of the header for the conditional auto answer.
     *
     * @return the value of the header for the conditional auto answer.
     */
    fun getAutoAnswerHeaderValue(): String?

    /**
     * Set to automatically forward all calls to the specified number using the same provider.
     *
     * @param numberTo
     * number to use for forwarding
     */
    fun setCallForward(numberTo: String?)

    /**
     * Get the value for automatically forward all calls to the specified number using the same
     * provider..
     *
     * @return numberTo number to use for forwarding
     */
    fun getCallForward(): String?

    /**
     * Clear any previous settings.
     */
    fun clear()

    companion object {
        /**
         * Auto answer conditional account property - field name.
         */
        const val AUTO_ANSWER_COND_NAME_PROP = "AUTO_ANSWER_CONDITIONAL_NAME"

        /**
         * Auto answer conditional account property - field value.
         */
        const val AUTO_ANSWER_COND_VALUE_PROP = "AUTO_ANSWER_CONDITIONAL_VALUE"

        /**
         * Auto forward all calls account property.
         */
        const val AUTO_ANSWER_FWD_NUM_PROP = "AUTO_ANSWER_FWD_NUM"
    }
}