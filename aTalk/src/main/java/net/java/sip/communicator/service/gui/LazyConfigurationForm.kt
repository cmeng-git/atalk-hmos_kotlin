/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

import net.java.sip.communicator.service.gui.internal.GuiServiceActivator
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils.getService
import org.atalk.service.resources.ResourceManagementService
import java.lang.reflect.UndeclaredThrowableException

/**
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class LazyConfigurationForm
/**
 * Creates an instance of `LazyConfigurationForm`.
 *
 * @param formClassName the class name of the configuration form
 * @param formClassLoader the class loader
 * @param iconID the identifier of the form icon
 * @param titleID the identifier of the form title
 * @param index the index of the form in the parent container
 */ @JvmOverloads constructor(
        /**
         * The class name of the form.
         */
        val formClassName: String,
        /**
         * The form class loader.
         */
        protected val formClassLoader: ClassLoader,
        /**
         * The identifier of the icon.
         */
        protected val iconID: String,
        /**
         * The title identifier.
         */
        protected val titleID: String,
        /**
         * The index of the form in the parent container.
         */
        override val index: Int = -1,
        /**
         * Indicates if this form is advanced.
         */
        override val isAdvanced: Boolean = false) : ConfigurationForm {
    /**
     * Returns the form class loader.
     *
     * @return the form class loader
     */
    /**
     * Returns the form class name.
     *
     * @return the form class name
     */
    /**
     * Returns the identifier of the icon.
     *
     * @return the identifier of the icon
     */
    /**
     * Returns the index of the form in its parent container.
     *
     * @return the index of the form in its parent container
     */
    /**
     * Returns the identifier of the title of the form.
     *
     * @return the identifier of the title of the form
     */
    /**
     * Indicates if the form is an advanced form.
     *
     * @return `true` to indicate that this is an advanced form, otherwise returns `false`
     */
    /**
     * Creates an instance of `LazyConfigurationForm`.
     *
     * @param formClassName the class name of the configuration form
     * @param formClassLoader the class loader
     * @param iconID the identifier of the form icon
     * @param titleID the identifier of the form title
     * @param index the index of the form in the parent container
     * @param isAdvanced indicates if the form is advanced configuration form
     */
    /**
     * Creates an instance of `LazyConfigurationForm`.
     *
     * @param formClassName the class name of the configuration form
     * @param formClassLoader the class loader
     * @param iconID the identifier of the form icon
     * @param titleID the identifier of the form title
     */
    /**
     * Returns the form component.
     *
     * @return the form component
     */
    override val form: Any?
        get() {
            val exception: Exception
            exception = try {
                return Class
                        .forName(formClassName, true, formClassLoader)
                        .newInstance()
            } catch (ex: ClassNotFoundException) {
                ex
            } catch (ex: IllegalAccessException) {
                ex
            } catch (ex: InstantiationException) {
                ex
            }
            throw UndeclaredThrowableException(exception)
        }

    /**
     * Returns the icon of the form.
     *
     * @return a byte array containing the icon of the form
     */
    override val icon: ByteArray?
        get() = resources!!.getImageInBytes(iconID)

    /**
     * Returns the title of the form.
     *
     * @return the title of the form
     */
    override val title: String?
        get() = resources!!.getI18NString(titleID)

    companion object {
        /**
         * Returns an instance of the `ResourceManagementService`, which
         * could be used to obtain any resources.
         *
         * @return an instance of the `ResourceManagementService`
         */
        /**
         * The `ResourceManagementService` used to obtain any resources.
         */
        private var resources: ResourceManagementService? = null
            private get() {
                if (field == null) field = getService(GuiServiceActivator.bundleContext)
                return field
            }
    }
}