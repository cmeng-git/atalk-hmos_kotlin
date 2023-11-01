/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.ChatRoomConfigurationFormField
import org.jivesoftware.smackx.xdata.FormField
import org.jivesoftware.smackx.xdata.FormFieldWithOptions
import org.jivesoftware.smackx.xdata.ListMultiFormField
import org.jivesoftware.smackx.xdata.TextSingleFormField
import org.jivesoftware.smackx.xdata.form.FillableForm
import java.util.*

/**
 * The Jabber protocol implementation of the `ChatRoomConfigurationFormField`. This
 * implementation is based on the smack Form and FormField types.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ChatRoomConfigurationFormFieldJabberImpl(
        /**
         * The smack library FormField.
         */
        private val smackFormField: FormField, submitForm: FillableForm) : ChatRoomConfigurationFormField {
    /**
     * The smack library submit form field. It's the one that will care all values set by user,
     * before submitting the form.
     */
    private var smackSubmitFormField: FormField? = null

    /**
     * Creates an instance of `ChatRoomConfigurationFormFieldJabberImpl` by passing to it the
     * smack form field and the smack submit form, which are the base of this implementation.
     *
     * @param formField the smack form field
     * @param submitForm the smack submit form.
     */
    init {
        if (smackFormField.type != FormField.Type.fixed) smackSubmitFormField = submitForm.getField(smackFormField.fieldName) else smackSubmitFormField = null
    }

    /**
     * Returns the variable name of the corresponding smack property.
     *
     * @return the variable name of the corresponding smack property.
     */
    override fun getName(): String? {
        return smackFormField.fieldName
    }

    /**
     * Returns the description of the corresponding smack property.
     *
     * @return the description of the corresponding smack property.
     */
    override fun getDescription(): String? {
        return smackFormField.description
    }

    /**
     * Returns the label of the corresponding smack property.
     *
     * @return the label of the corresponding smack property.
     */
    override fun getLabel(): String? {
        return smackFormField.label
    }

    /**
     * Returns the options of the corresponding smack property.
     *
     * @return the options of the corresponding smack property.
     */
    override fun getOptions(): Iterator<String?>? {
        val options: MutableList<String> = ArrayList()
        val ffOptions = (smackFormField as FormFieldWithOptions).options
        for (smackOption in ffOptions) {
            options.add(smackOption.valueString)
        }
        return Collections.unmodifiableList(options).iterator()
    }

    /**
     * Returns the isRequired property of the corresponding smack property.
     *
     * @return the isRequired property of the corresponding smack property.
     */
    override fun isRequired(): Boolean {
        return smackFormField.isRequired
    }

    /**
     * For each of the smack form field types returns the corresponding
     * `ChatRoomConfigurationFormField` type.
     *
     * @return the type of the property
     */
    override fun getType(): String? {
        val smackType = smackFormField.type
        return when (smackType) {
            FormField.Type.bool -> ChatRoomConfigurationFormField.TYPE_BOOLEAN
            FormField.Type.fixed -> ChatRoomConfigurationFormField.TYPE_TEXT_FIXED
            FormField.Type.text_private -> ChatRoomConfigurationFormField.TYPE_TEXT_PRIVATE
            FormField.Type.text_single -> ChatRoomConfigurationFormField.TYPE_TEXT_SINGLE
            FormField.Type.text_multi -> ChatRoomConfigurationFormField.TYPE_TEXT_MULTI
            FormField.Type.list_single -> ChatRoomConfigurationFormField.TYPE_LIST_SINGLE
            FormField.Type.list_multi -> ChatRoomConfigurationFormField.TYPE_LIST_MULTI
            FormField.Type.jid_single -> ChatRoomConfigurationFormField.TYPE_ID_SINGLE
            FormField.Type.jid_multi -> ChatRoomConfigurationFormField.TYPE_ID_MULTI
            else -> ChatRoomConfigurationFormField.TYPE_UNDEFINED
        }
    }

    /**
     * Returns an Iterator over the list of values of this field.
     *
     * @return an Iterator over the list of values of this field.
     */
    override fun getValues(): Iterator<*> {
        val smackValues = smackFormField.valuesAsString
        val valuesIter: Iterator<*>
        valuesIter = if (smackFormField.type == FormField.Type.bool) {
            val values: MutableList<Boolean> = ArrayList()
            for (smackValue in smackValues) {
                values.add(if (smackValue == "1" || smackValue == "true") java.lang.Boolean.TRUE else java.lang.Boolean.FALSE)
            }
            values.iterator()
        } else smackValues.iterator()
        return valuesIter
    }

    /**
     * Adds the given value to the list of values of this field.
     *
     * @param value the value to add
     */
    override fun addValue(value: Any?) {
        var value = value
        if (value is Boolean) value = if (value) "1" else "0"
        val fieldBuilder = (smackSubmitFormField as TextSingleFormField?)!!.asBuilder()
        fieldBuilder.setValue(value as String?)
        smackSubmitFormField = fieldBuilder.build()
    }

    /**
     * Sets the given list of values to this field.
     *
     * @param newValues the list of values to set.
     */
    override fun setValues(newValues: Array<Any?>?) {
        val list: MutableList<String?> = ArrayList()
        for (value in newValues!!) {
            var stringValue: String?
            stringValue = if (value is Boolean) if (value) "1" else "0" else value?.toString()
            list.add(stringValue)
        }
        // smackSubmitFormField.addValues(list);
        val fieldBuilder = (smackSubmitFormField as ListMultiFormField?)!!.asBuilder()
        fieldBuilder.addValues(list)
        smackSubmitFormField = fieldBuilder.build()
    }
}