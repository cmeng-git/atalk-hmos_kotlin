/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

/**
 * The `ConfigurationForm` interface is meant to be implemented by all
 * bundles that want to add their own specific configuration forms in the UI.
 * Each `ConfigurationForm` implementation could be added to the UI
 * by invoking the `ConfigurationDialog.addConfigurationForm` method.
 *
 *
 * The `ConfigurationDialog` for the current ui implementation could
 * be obtained by invoking `UIService.getConfigurationDialog` method.
 *
 * @author Yana Stamcheva
 */
interface ConfigurationForm {
    /**
     * Returns the title of this configuration form.
     * @return the title of this configuration form
     */
    val title: String?

    /**
     * Returns the icon of this configuration form. It depends on the
     * UI implementation, how this icon will be used and where it will be
     * placed.
     *
     * @return the icon of this configuration form
     */
    val icon: ByteArray?

    /**
     * Returns the containing form. This should be a container with all the
     * fields, buttons, etc.
     *
     *
     * Note that it's very important to return here an object that is compatible
     * with the current UI implementation library.
     * @return the containing form
     */
    val form: Any?

    /**
     * Returns the index of this configuration form in the configuration window.
     * This index is used to put configuration forms in the desired order.
     *
     *
     * 0 is the first position
     * -1 means that the form will be put at the end
     *
     * @return the index of this configuration form in the configuration window.
     */
    val index: Int

    /**
     * Indicates if this is an advanced configuration form.
     * @return `true` if this is an advanced configuration form,
     * otherwise it returns `false`
     */
    val isAdvanced: Boolean

    companion object {
        /**
         * The name of a property representing the type of the configuration form.
         */
        const val FORM_TYPE = "FORM_TYPE"

        /**
         * The security configuration form type.
         */
        const val SECURITY_TYPE = "SECURITY_TYPE"

        /**
         * The general configuration form type.
         */
        const val GENERAL_TYPE = "GENERAL_TYPE"

        /**
         * The advanced configuration form type.
         */
        const val ADVANCED_TYPE = "ADVANCED_TYPE"

        /**
         * The advanced contact source form type.
         */
        const val CONTACT_SOURCE_TYPE = "CONTACT_SOURCE_TYPE"
    }
}