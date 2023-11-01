/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import net.java.sip.communicator.service.gui.ConfigurationForm
import net.java.sip.communicator.service.notification.NotificationAction
import net.java.sip.communicator.service.notification.NotificationData
import net.java.sip.communicator.service.notification.NotificationHandler
import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.service.notification.PopupMessageNotificationHandler
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils
import net.java.sip.communicator.service.systray.SystrayService
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.service.audionotifier.AudioNotifierService
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*

/**
 * Implements `BundleActivator` for the neomedia bundle.
 *
 * @author Martin Andre
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class NeomediaActivator : BundleActivator {
    /**
     * A listener to the click on the popup message concerning device configuration changes.
     */
    private var deviceConfigurationPropertyChangeListener: AudioDeviceConfigurationListener? = null

    /**
     * Starts the execution of the neomedia bundle in the specified context.
     *
     * @param bundleContext the context in which the neomedia bundle is to start executing
     * @throws Exception if an error occurs while starting the execution of the neomedia bundle in the specified context
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        if (MainMenuActivity.disableMediaServiceOnFault) return


        // MediaService
        Companion.bundleContext = bundleContext
        mediaServiceImpl = LibJitsi.mediaService as MediaServiceImpl?
        if (mediaServiceImpl == null) {
            Timber.w("Media Service startup failed - jnlibffmpeg failed to load?")
            return
        }
        bundleContext.registerService(MediaService::class.java.name, mediaServiceImpl, null)
        Timber.d("Media Service ... [REGISTERED]")

        // mediaConfiguration = new MediaConfigurationImpl();
        // bundleContext.registerService(MediaConfigurationService.class.getName(), getMediaConfiguration(), null);
        // Timber.d("Media Configuration ... [REGISTERED]");
        val cfg = getConfigurationService()
        val mediaProps: Dictionary<String, String> = Hashtable()
        mediaProps.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.GENERAL_TYPE)

        // If the audio configuration form is disabled don't register it.
        //        if ((cfg == null) || !cfg.getBoolean(AUDIO_CONFIG_DISABLED_PROP, false)) {
        //            audioConfigurationForm = new LazyConfigurationForm(AudioConfigurationPanel.class.getName(),
        //                    getClass().getClassLoader(), "plugin.mediaconfig.AUDIO_ICON", "impl.neomedia.configform.AUDIO", 3);
        //
        //            bundleContext.registerService(ConfigurationForm.class.getName(), audioConfigurationForm, mediaProps);
        //
        //            if (deviceConfigurationPropertyChangeListener == null) {
        //                // Initializes and registers the changed device configuration event for the notification service.
        //                getNotificationService();
        //
        //                deviceConfigurationPropertyChangeListener = new AudioDeviceConfigurationListener();
        //                mediaServiceImpl.getDeviceConfiguration().addPropertyChangeListener(deviceConfigurationPropertyChangeListener);
        //            }
        //        }

        //        // If the video configuration form is disabled don't register it.
        //        if ((cfg == null) || !cfg.getBoolean(VIDEO_CONFIG_DISABLED_PROP, false)) {
        //            bundleContext.registerService(ConfigurationForm.class.getName(),
        //                    new LazyConfigurationForm(VideoConfigurationPanel.class.getName(), etClass().getClassLoader(),
        //                            "plugin.mediaconfig.VIDEO_ICON", "impl.neomedia.configform.VIDEO", 4), mediaProps);
        //        }

        // H.264
        // If the H.264 configuration form is disabled don't register it.
        //        if ((cfg == null) || !cfg.getBoolean(H264_CONFIG_DISABLED_PROP, false)) {
        //            Dictionary<String, String> h264Props
        //                    = new Hashtable<String, String>();
        //
        //            h264Props.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.ADVANCED_TYPE);
        //            bundleContext.registerService(
        //                    ConfigurationForm.class.getName(),
        //                    new LazyConfigurationForm(ConfigurationPanel.class.getName(), getClass().getClassLoader(),
        //                            "plugin.mediaconfig.VIDEO_ICON", "impl.neomedia.configform.H264", -1, true), h264Props);
        //        }

        // ZRTP
        // If the ZRTP configuration form is disabled don't register it.
        //        if ((cfg == null) || !cfg.getBoolean(ZRTP_CONFIG_DISABLED_PROP, false)) {
        //            Dictionary<String, String> securityProps = new Hashtable<String, String>();
        //
        //            securityProps.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.SECURITY_TYPE);
        //            bundleContext.registerService(ConfigurationForm.class.getName(),
        //                    new LazyConfigurationForm(SecurityConfigForm.class.getName(), getClass().getClassLoader(),
        //                            "impl.media.security.zrtp.CONF_ICON", "impl.media.security.zrtp.TITLE", 0), securityProps);
        //        }

        // we use the nist-sdp stack to make parse sdp and we need to set the following property to make
        // sure that it would accept java generated IPv6 addresses that contain address scope zones.
        System.setProperty("gov.nist.core.STRIP_ADDR_SCOPES", "true")

        // AudioNotifierService
        val audioNotifierService = LibJitsi.audioNotifierService
        audioNotifierService.isMute = (cfg == null
                || !cfg.getBoolean("media.sound.isSoundEnabled", true))
        bundleContext.registerService(AudioNotifierService::class.java.name, audioNotifierService, null)
        Timber.i("Audio Notifier Service ...[REGISTERED]")

        //        Call Recording
        //        If the call recording configuration form is disabled don 't continue.
        //        if ((cfg == null) || !cfg.getBoolean(CALL_RECORDING_CONFIG_DISABLED_PROP, false)) {
        //            Dictionary<String, String> callRecordingProps = new Hashtable<String, String>();
        //
        //            callRecordingProps.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.ADVANCED_TYPE);
        //            bundleContext.registerService(ConfigurationForm.class.getName(),
        //                    new LazyConfigurationForm(CallRecordingConfigForm.class.getName(), getClass().getClassLoader(),
        //                            null, "plugin.callrecordingconfig.CALL_RECORDING_CONFIG", 1100, true), callRecordingProps);
        //        }
    }

    /**
     * Stops the execution of the neomedia bundle in the specified context.
     *
     * @param bundleContext the context in which the neomedia bundle is to stop executing
     * @throws Exception if an error occurs while stopping the execution of the neomedia bundle in the specified context
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        try {
            if (deviceConfigurationPropertyChangeListener != null) {
                mediaServiceImpl!!.deviceConfiguration
                        .removePropertyChangeListener(deviceConfigurationPropertyChangeListener!!)
                if (deviceConfigurationPropertyChangeListener != null) {
                    deviceConfigurationPropertyChangeListener!!.managePopupMessageListenerRegistration(false)
                    deviceConfigurationPropertyChangeListener = null
                }
            }
        } finally {
            configurationService = null
            fileAccessService = null
            mediaServiceImpl = null
            resources = null
        }
    }

    /**
     * A listener to the click on the popup message concerning device configuration changes.
     */
    private inner class AudioDeviceConfigurationListener : PropertyChangeListener /*
     * ,
     * SystrayPopupMessageListener
     */ {
        /**
         * A boolean used to verify that this listener registers only once to the popup message notification handler.
         */
        private var isRegisteredToPopupMessageListener = false

        /**
         * Registers or unregister as a popup message listener to detect when a user click on
         * notification saying that the device configuration has changed.
         *
         * @param enable True to register to the popup message notification handler. False to unregister.
         */
        fun managePopupMessageListenerRegistration(enable: Boolean) {
            val notificationHandlers = notificationService!!.getActionHandlers(NotificationAction.ACTION_POPUP_MESSAGE).iterator()
            var notificationHandler: NotificationHandler
            while (notificationHandlers.hasNext()) {
                notificationHandler = notificationHandlers.next()
                if (notificationHandler is PopupMessageNotificationHandler) {
                    // Register.
                    if (enable) {
                        // ((PopupMessageNotificationHandler) notificationHandler).addPopupMessageListener(this);
                    } else {
                        // ((PopupMessageNotificationHandler) notificationHandler).removePopupMessageListener(this);
                    }
                }
            }
        }

        /**
         * Function called when an audio device is plugged or unplugged.
         *
         * @param event The property change event which may concern the audio device.
         */
        override fun propertyChange(event: PropertyChangeEvent) {
            if (DeviceConfiguration.PROP_AUDIO_SYSTEM_DEVICES == event.propertyName) {
                val notificationService = getNotificationService()
                if (notificationService != null) {
                    // Registers only once to the popup message notification handler.
                    if (!isRegisteredToPopupMessageListener) {
                        isRegisteredToPopupMessageListener = true
                        managePopupMessageListenerRegistration(true)
                    }

                    // Fires the popup notification.
                    val resources = getResources()
                    val extras = HashMap<String, Any>()
                    extras[NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA] = this
                    notificationService.fireNotification(DEVICE_CONFIGURATION_HAS_CHANGED, SystrayService.NONE_MESSAGE_TYPE,
                            aTalkApp.getResString(R.string.impl_media_configform_AUDIO_DEVICE_CONFIG_CHANGED),
                            aTalkApp.getResString(R.string.impl_media_configform_AUDIO_DEVICE_CONFIG_MANAGMENT_CLICK),
                            null, extras)
                }
            }
        } /*
         * Indicates that user has clicked on the systray popup message.
         *
         * @param evt the event triggered when user clicks on the systray popup message
         */
        //        public void popupMessageClicked(SystrayPopupMessageEvent evt)
        //        {
        //            // Checks if this event is fired from one click on one of our popup  message.
        //            if (evt.getTag() == deviceConfigurationPropertyChangeListener) {
        //                // Get the UI service
        //                ServiceReference uiReference = bundleContext.getServiceReference(UIService.class.getName());
        //
        //                UIService uiService = (UIService) bundleContext.getService(uiReference);
        //
        //                if (uiService != null) {
        //                    // Shows the audio configuration window.
        //                    ConfigurationContainer configurationContainer = uiService.getConfigurationContainer();
        //                    configurationContainer.setSelected(audioConfigurationForm);
        //                    configurationContainer.setVisible(true);
        //                }
        //            }
        //        }
    }

    companion object {
        /**
         * Indicates if the audio configuration form should be disabled, i.e. not visible to the user.
         */
        private const val AUDIO_CONFIG_DISABLED_PROP = "neomedia.AUDIO_CONFIG_DISABLED"

        /**
         * Indicates if the video configuration form should be disabled, i.e. not visible to the user.
         */
        private const val VIDEO_CONFIG_DISABLED_PROP = "neomedia.VIDEO_CONFIG_DISABLED"

        /**
         * Indicates if the H.264 configuration form should be disabled, i.e. not visible to the user.
         */
        private const val H264_CONFIG_DISABLED_PROP = "neomedia.h264config.DISABLED"

        /**
         * Indicates if the ZRTP configuration form should be disabled, i.e. not visible to the user.
         */
        private const val ZRTP_CONFIG_DISABLED_PROP = "neomedia.zrtpconfig.DISABLED"

        /**
         * Indicates if the call recording config form should be disabled, i.e. not visible to the user.
         */
        private const val CALL_RECORDING_CONFIG_DISABLED_PROP = "neomedia.callrecordingconfig.DISABLED"

        /**
         * The name of the notification pop-up event displayed when the device configuration has changed.
         */
        private const val DEVICE_CONFIGURATION_HAS_CHANGED = "DeviceConfigurationChanged"

        /**
         * The context in which the one and only `NeomediaActivator` instance has started executing.
         */
        private var bundleContext: BundleContext? = null

        /**
         * The `ConfigurationService` registered in [.bundleContext] and used by the
         * `NeomediaActivator` instance to read and write configuration properties.
         */
        private var configurationService: ConfigurationService? = null

        /**
         * The `FileAccessService` registered in [.bundleContext] and used by the
         * `NeomediaActivator` instance to safely access files.
         */
        private var fileAccessService: FileAccessService? = null

        /**
         * The notification service to pop-up messages.
         */
        private var notificationService: NotificationService? = null

        /**
         * The one and only `MediaServiceImpl` instance registered in [.bundleContext] by
         * the `NeomediaActivator` instance.
         */
        private var mediaServiceImpl: MediaServiceImpl? = null

        /**
         * The `ResourceManagementService` registered in [.bundleContext] and representing
         * the resources such as internationalized and localized text and images used by the neomedia bundle.
         */
        private var resources: ResourceManagementService? = null
        /*
     * A {@link MediaConfigurationService} instance.
     */
        // private static MediaConfigurationImpl mediaConfiguration;
        /**
         * The audio configuration form used to define the capture/notify/playback audio devices.
         */
        private val audioConfigurationForm: ConfigurationForm? = null

        /**
         * Returns a reference to a ConfigurationService implementation currently registered in the
         * bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the ConfigurationService.
         */
        fun getConfigurationService(): ConfigurationService? {
            if (configurationService == null) {
                configurationService = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
            }
            return configurationService
        }

        /**
         * Returns a reference to a FileAccessService implementation currently registered in the bundle
         * context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the FileAccessService .
         */
        fun getFileAccessService(): FileAccessService? {
            if (fileAccessService == null) {
                fileAccessService = ServiceUtils.getService(bundleContext, FileAccessService::class.java)
            }
            return fileAccessService
        }

        /**
         * Gets the `MediaService` implementation instance registered by the neomedia bundle.
         *
         * @return the `MediaService` implementation instance registered by the neomedia bundle
         */
        fun getMediaServiceImpl(): MediaServiceImpl? {
            return mediaServiceImpl
        }
        // public static MediaConfigurationService getMediaConfiguration()
        // {
        // return mediaConfiguration;
        // }
        /**
         * Gets the `ResourceManagementService` instance which represents the resources such as
         * internationalized and localized text and images used by the neomedia bundle.
         *
         * @return the `ResourceManagementService` instance which represents the resources such
         * as internationalized and localized text and images used by the neomedia bundle
         */
        fun getResources(): ResourceManagementService? {
            if (resources == null) {
                resources = ResourceManagementServiceUtils.getService(bundleContext)
            }
            return resources
        }

        /**
         * Returns the `NotificationService` obtained from the bundle context.
         *
         * @return The `NotificationService` obtained from the bundle context.
         */
        fun getNotificationService(): NotificationService? {
            if (notificationService == null) {
                // Get the notification service implementation
                val notifReference = bundleContext!!.getServiceReference(NotificationService::class.java.name)
                notificationService = bundleContext!!.getService<Any>(notifReference as ServiceReference<Any>) as NotificationService
                if (notificationService != null) {
                    // Register a popup message for a device configuration changed notification.
                    notificationService!!.registerDefaultNotificationForEvent(DEVICE_CONFIGURATION_HAS_CHANGED,
                            NotificationAction.ACTION_POPUP_MESSAGE,
                            "Device configuration has changed", null)
                }
            }
            return notificationService
        }

        fun getBundleContext(): BundleContext? {
            return bundleContext
        }
    }
}