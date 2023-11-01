/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * The `ChatRoomConfigurationFormField` is contained in the
 * `ChatRoomConfigurationForm` and represents a configuration property of a chat room. It's
 * meant to be used by GUI-s to provide access to the user to chat room configuration. <br></br>
 * The `ChatRoomConfigurationFormField` defines 8 different types of fields:
 *
 *  * TYPE_TEXT_FIXED - information text, that could not be changed
 *  * TYPE_BOOLEAN - boolean values
 *  * TYPE_TEXT_PRIVATE - text, that should not be shown to the user as clear text
 *  * TYPE_TEXT_MULTI - multilines text
 *  * TYPE_TEXT_SINGLE - single line text
 *  * TYPE_LIST_MULTI - multi choice list
 *  * TYPE_LIST_SINGLE - single choice list
 *  * TYPE_UNDEFINED - undefined type
 *
 * The type of the field will help the GUI to determine the component to use to represent the given
 * field.
 *
 * @author Yana Stamcheva
 */
interface ChatRoomConfigurationFormField {
    /**
     * Returns the name of the field to be filled out. This serves as an identifier of the field.
     *
     * @return the name of the field
     */
    fun getName(): String?

    /**
     * Returns a description that provides extra clarification about the field. This information
     * could be presented to the user either in tool-tip,help button, or as a section of text before
     * the question.
     *
     *
     *
     * @return description that provides extra clarification about the question.
     */
    fun getDescription(): String?

    /**
     * Returns the label of the field which should give enough information to the user to fill out
     * the form.
     *
     * @return label of the question.
     */
    fun getLabel(): String?

    /**
     * Returns an Iterator for the available options that the user has in order to answer the
     * question.
     *
     * @return Iterator for the available options.
     */
    fun getOptions(): Iterator<String?>?

    /**
     * Returns true if the question must be answered in order to complete the questionnaire
     *
     * @return true if the question must be answered in order to complete the questionnaire.
     */
    fun isRequired(): Boolean

    /**
     * Returns an indicative of the format for the data to answer. The valid types are all TYPE_XXX
     * constants defined in this class.
     *
     * @return format for the data to answer.
     */
    fun getType(): String?

    /**
     * Returns an Iterator for the default values of the field if the field is part of a form to
     * fill out. Otherwise, returns an Iterator for the answered values of the field.
     *
     * @return an Iterator for the default values or answered values of the field
     */
    fun getValues(): Iterator<*>?

    /**
     * Adds the given value to the values of this field.
     *
     * @param value
     * the value to add
     */
    fun addValue(value: Any?)

    /**
     * Sets the list of values for this field.
     *
     * @param newValues
     * the values of this field
     */
    fun setValues(newValues: Array<Any?>?)

    companion object {
        /**
         * The undefined type is meant to be used by the implementation if they don't know the type of
         * the configuration property.
         */
        const val TYPE_UNDEFINED = "Undefined"

        /**
         * The fixed text type means that the value of this field is a text, that could not be changed.
         * This type is meant to be used for adding additional information helping the user to complete
         * the form.
         */
        const val TYPE_TEXT_FIXED = "FixedText"

        /**
         * The private text type indicates that the text, contained in this field should not be shown to
         * the user in clear text, instead if should be protected by showing '*'. This type is used for
         * passwords.
         */
        const val TYPE_TEXT_PRIVATE = "PrivateText"

        /**
         * The boolean type means that the value of this field is of type Boolean.
         */
        const val TYPE_BOOLEAN = "Boolean"

        /**
         * The multi lines text type means that the value of this field is a text represented on
         * multiple lines.
         */
        const val TYPE_TEXT_MULTI = "MultipleLinesText"

        /**
         * The single line text type means that the value of this field is a text represented on one
         * line.
         */
        const val TYPE_TEXT_SINGLE = "SingleLineText"

        /**
         * The list multi type means that the value of this field is a list that allows multiple choice
         * (i.e. multiple lines could be selected at the same time).
         */
        const val TYPE_LIST_MULTI = "ListMultiChoice"

        /**
         * The list single type means that the value of this field is a list that allows only one line
         * to be selected at a time.
         */
        const val TYPE_LIST_SINGLE = "ListSingleChoice"

        /**
         * The multi id type means that the value of this field is a list of ids.
         */
        const val TYPE_ID_MULTI = "MultiIDChoice"

        /**
         * The id single type means that the value of this field is only one id that can be selected. As
         * TYPE_TEXT_SINGLE but contains id, most probably in form of user@service.com.
         */
        const val TYPE_ID_SINGLE = "SingleIDChoice"
    }
}