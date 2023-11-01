/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.plugin.desktoputil

import net.java.sip.communicator.service.browserlauncher.BrowserLauncherService
import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService
import net.java.sip.communicator.service.credentialsstorage.MasterPasswordInputService
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService
import net.java.sip.communicator.service.gui.AuthenticationWindowService
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.audionotifier.AudioNotifierService
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.neomedia.MediaService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import java.awt.image.BufferedImage
import java.net.URL
import java.security.cert.Certificate
import javax.swing.ImageIcon

class DesktopUtilActivator : BundleActivator, VerifyCertificateDialogService {
    /**
     * Calls `Thread.setUncaughtExceptionHandler()`
     *
     * @param context The execution context of the bundle being started
     * (unused).
     * @throws Exception If this method throws an exception, this bundle is
     * marked as stopped and the Framework will remove this bundle's
     * listeners, unregister all services registered by this bundle, and
     * release all services used by this bundle.
     */
    @Throws(Exception::class)
    override fun start(context: BundleContext) {
        bundleContext = context

        // register the VerifyCertificateDialogService
        bundleContext!!.registerService(VerifyCertificateDialogService::class.java.name, this, null)
        bundleContext!!.registerService(
                MasterPasswordInputService::class.java.name,
                object : MasterPasswordInputService {
                    override fun showInputDialog(prevSuccess: Boolean): String? {
                        return null //MasterPasswordInputDialog.showInput(prevSuccess);
                    }
                } as MasterPasswordInputService?, null)
        bundleContext!!.registerService(
                AuthenticationWindowService::class.java.name,
                object : AuthenticationWindowService {
                    override fun create(userName: String?, password: CharArray?, server: String?, isUserNameEditable: Boolean, isRememberPassword: Boolean, icon: Any?, windowTitle: String?, windowText: String?, usernameLabelText: String?, passwordLabelText: String?, errorMessage: String?, signupLink: String?): AuthenticationWindowService.AuthenticationWindow? {
                        var imageIcon: ImageIcon? = null
                        if (icon is ImageIcon) imageIcon = icon
                        val creator = AuthenticationWindowCreator(
                                userName,
                                password,
                                server,
                                isUserNameEditable,
                                isRememberPassword,
                                imageIcon,
                                windowTitle,
                                windowText,
                                usernameLabelText,
                                passwordLabelText,
                                errorMessage,
                                signupLink)
                        return creator.authenticationWindow
                    }
                } as AuthenticationWindowService?, null)
    }

    /**
     * Doesn't do anything.
     *
     * @param context The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is
     * still marked as stopped, and the Framework will remove the bundle's
     * listeners, unregister all services registered by the bundle, and
     * release all services used by the bundle.
     */
    @Throws(Exception::class)
    override fun stop(context: BundleContext) {
    }

    /**
     * Creates the dialog.
     *
     * @param certs the certificates list
     * @param title The title of the dialog; when null the resource
     * `service.gui.CERT_DIALOG_TITLE` is loaded and used.
     * @param message A text that describes why the verification failed.
     */
    override fun createDialog(certs: Array<Certificate>?, title: String?, message: String?): VerifyCertificateDialogService.VerifyCertificateDialog? {

        val creator = VerifyCertificateDialogCreator(certs, title, message)
//        try
//        {
//            SwingUtilities.invokeAndWait(creator);
//        }
//        catch(InterruptedException e)
//        {
//            Timber.e("Error creating dialog", e);
//        }
//        catch(InvocationTargetException e)
//        {
//            Timber.e("Error creating dialog", e);
//        }
        return null //creator.dialog;
    }

    /**
     * Runnable to create verify dialog.
     */
    private inner class VerifyCertificateDialogCreator
    /**
     * Constructs.
     *
     * @param certs the certificates list
     * @param title The title of the dialog; when null the resource
     * `service.gui.CERT_DIALOG_TITLE` is loaded and used.
     * @param message A text that describes why the verification failed.
     */
    (
            /**
             * Certs.
             */
            private val certs: Array<Certificate>?,
            /**
             * Dialog title.
             */
            private val title: String?,
            /**
             * Dialog message.
             */
            private val message: String?) : Runnable {
        /*
         * The result dialog.
         */
        // VerifyCertificateDialogImpl dialog = null;
        override fun run() {
            //  dialog = new VerifyCertificateDialogImpl(certs, title, message);
        }
    }

    /**
     * Runnable to create auth window.
     */
    private inner class AuthenticationWindowCreator
    /**
     * Creates an instance of the `AuthenticationWindow` implementation.
     *
     * @param server the server name
     * @param isUserNameEditable indicates if the user name is editable
     * @param imageIcon the icon to display on the left of
     * the authentication window
     * @param windowTitle customized window title
     * @param windowText customized window text
     * @param usernameLabelText customized username field label text
     * @param passwordLabelText customized password field label text
     * @param errorMessage an error message if this dialog is shown
     * to indicate the user that something went wrong
     * @param signupLink an URL that allows the user to sign up
     */
    (var userName: String?,
        var password: CharArray?,
        var server: String?,
        var isUserNameEditable: Boolean,
        var isRememberPassword: Boolean,
        var imageIcon: ImageIcon?,
        var windowTitle: String?,
        var windowText: String?,
        var usernameLabelText: String?,
        var passwordLabelText: String?,
        var errorMessage: String?,
        var signupLink: String?) : Runnable {
        var authenticationWindow: AuthenticationWindowService.AuthenticationWindow? = null
        override fun run() {
//            authenticationWindow = new net.java.sip.communicator.plugin.desktoputil
//                    .AuthenticationWindow(
//                    userName, password,
//                    server,
//                    isUserNameEditable, isRememberPassword,
//                    imageIcon,
//                    windowTitle, windowText,
//                    usernameLabelText, passwordLabelText,
//                    errorMessage,
//                    signupLink);
        }
    }

    companion object {
        var bundleContext: BundleContext? = null

        /**
         * Returns the `ConfigurationService` currently registered.
         *
         * @return the `ConfigurationService`
         */
        var configurationService: ConfigurationService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
                }
                return field
            }
            private set

        // private static KeybindingsService keybindingsService;
        private var resourceService: ResourceManagementService? = null
        private var browserLauncherService: BrowserLauncherService? = null
        private var uiService: UIService? = null

        /**
         * Returns the `AccountManager` obtained from the bundle context.
         *
         * @return the `AccountManager` obtained from the bundle context
         */
        var accountManager: AccountManager? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, AccountManager::class.java)
                }
                return field
            }
            private set

        /**
         * Returns the `FileAccessService` obtained from the bundle context.
         *
         * @return the `FileAccessService` obtained from the bundle context
         */
        var fileAccessService: FileAccessService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, FileAccessService::class.java)
                }
                return field
            }
            private set

        /**
         * Returns an instance of the `MediaService` obtained from the
         * bundle context.
         *
         * @return an instance of the `MediaService` obtained from the
         * bundle context
         */
        var mediaService: MediaService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, MediaService::class.java)
                }
                return field
            }
            private set
        private var audioNotifierService: AudioNotifierService? = null

        /**
         * Returns the `GlobalDisplayDetailsService` obtained from the bundle context.
         *
         * @return the `GlobalDisplayDetailsService` obtained from the bundle context
         */
        var globalDisplayDetailsService: GlobalDisplayDetailsService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(
                            bundleContext, GlobalDisplayDetailsService::class.java)
                }
                return field
            }
            private set

        /**
         * Returns the service giving access to all application resources.
         *
         * @return the service giving access to all application resources.
         */
        val resources: ResourceManagementService?
            get() {
                if (resourceService == null) {
                    resourceService = ResourceManagementServiceUtils.getService(bundleContext)
                }
                return resourceService
            }

        /**
         * Returns the image corresponding to the given `imageID`.
         *
         * @param imageID the identifier of the image
         * @return the image corresponding to the given `imageID`
         */
        fun getImage(imageID: String): BufferedImage? {
            val image: BufferedImage? = null
            val path = resources!!.getImageURL(imageID)
                    ?: return null

//        try
//        {
//            image = ImageIO.read(path);
//        }
//        catch (Exception exc)
//        {
//            Timber.e(exc, "Failed to load image: %s", path);
//        }
            return image
        }

        /**
         * Returns the `BrowserLauncherService` obtained from the bundle context.
         *
         * @return the `BrowserLauncherService` obtained from the bundle context
         */
        val browserLauncher: BrowserLauncherService?
            get() {
                if (browserLauncherService == null) {
                    browserLauncherService = ServiceUtils.getService(bundleContext, BrowserLauncherService::class.java)
                }
                return browserLauncherService
            }

        /**
         * Gets the `UIService` instance registered in the
         * `BundleContext` of the `UtilActivator`.
         *
         * @return the `UIService` instance registered in the
         * `BundleContext` of the `UtilActivator`
         */
        val uIService: UIService?
            get() {
                if (uiService == null) uiService = ServiceUtils.getService(bundleContext, UIService::class.java)
                return uiService
            }
        //    /**
        //     * Returns the <code>KeybindingsService</code> currently registered.
        //     *
        //     * @return the <code>KeybindingsService</code>
        //     */
        //    public static KeybindingsService getKeybindingsService()
        //    {
        //        if (keybindingsService == null) {
        //            keybindingsService
        //                = ServiceUtils.getService(bundleContext, KeybindingsService.class);
        //        }
        //        return keybindingsService;
        //    }
        /**
         * Returns the `AudioNotifierService` obtained from the bundle
         * context.
         *
         * @return the `AudioNotifierService` obtained from the bundle context
         */
        val audioNotifier: AudioNotifierService?
            get() {
                if (audioNotifierService == null) {
                    audioNotifierService = ServiceUtils.getService(bundleContext, AudioNotifierService::class.java)
                }
                return audioNotifierService
            }
    }
}