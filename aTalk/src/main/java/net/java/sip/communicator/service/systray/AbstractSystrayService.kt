/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray

import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * Base implementation of [SystrayService]. Manages
 * `PopupMessageHandler`s and `SystrayPopupMessageListener`s.
 *
 * @author Nicolas Chamouard
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Symphorien Wanko
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class AbstractSystrayService
/**
 * Creates new instance of `AbstractSystrayService`.
 *
 * @param bundleContext OSGI bundle context that will be used by this instance
 */
(
        /**
         * OSGI bundle context
         */
        private val bundleContext: BundleContext) : SystrayService {
    /**
     * Get the handler currently used by this implementation to popup message
     *
     * @return the current handler
     */
    /**
     * The popup handler currently used to show popup messages
     */
    override var activePopupMessageHandler: PopupMessageHandler? = null

    /**
     * A set of usable `PopupMessageHandler`
     */
    private val popupHandlerSet = Hashtable<String, PopupMessageHandler>()

    /**
     * List of listeners from early received calls to addPopupMessageListener.
     * Calls to addPopupMessageListener before the UIService is registered.
     */
    private var earlyAddedListeners: MutableList<SystrayPopupMessageListener>? = null

    /**
     * Registers given `PopupMessageHandler`.
     *
     * @param handler the `PopupMessageHandler` to be registered.
     */
    protected fun addPopupHandler(handler: PopupMessageHandler) {
        popupHandlerSet[handler.javaClass.name] = handler
    }

    /**
     * Removes given `PopupMessageHandler`.
     *
     * @param handler the `PopupMessageHandler` to be removed.
     */
    protected fun removePopupHandler(handler: PopupMessageHandler) {
        popupHandlerSet.remove(handler.javaClass.name)
    }

    /**
     * Checks if given `handlerClass` is registered as a handler.
     *
     * @param handlerClass the class name to be checked.
     * @return `true` if given `handlerClass` is already registered as a handler.
     */
    protected fun containsHandler(handlerClass: String?): Boolean {
        return popupHandlerSet.contains(handlerClass)
    }

    /**
     * Implements `SystraService#showPopupMessage()`
     *
     * @param popupMessage the message we will show
     */
    override fun showPopupMessage(popupMessage: PopupMessage) {
        // since popup handler could be loaded and unloaded on the fly,
        // we have to check if we currently have a valid one.
        if (activePopupMessageHandler != null) activePopupMessageHandler!!.showPopupMessage(popupMessage)
    }

    /**
     * Implements the `SystrayService.addPopupMessageListener` method.
     * If `activePopupHandler` is still not available record the listener so we can add him later.
     *
     * @param listener the listener to add
     */
    override fun addPopupMessageListener(listener: SystrayPopupMessageListener) {
        if (activePopupMessageHandler != null) activePopupMessageHandler!!.addPopupMessageListener(listener) else {
            if (earlyAddedListeners == null) earlyAddedListeners = ArrayList()
            earlyAddedListeners!!.add(listener)
        }
    }

    /**
     * Implements the `SystrayService.removePopupMessageListener` method.
     *
     * @param listener the listener to remove
     */
    override fun removePopupMessageListener(listener: SystrayPopupMessageListener) {
        if (activePopupMessageHandler != null) activePopupMessageHandler!!.removePopupMessageListener(listener)
    }

    /**
     * Set the handler which will be used for popup message
     *
     * @param popupHandler the handler to set. providing a null handler is like disabling popup.
     * @return the previously used popup handler
     */
    override fun setActivePopupMessageHandler(popupHandler: PopupMessageHandler?): PopupMessageHandler? {
        val oldHandler = activePopupMessageHandler
        Timber.i("setting the following popup handler as active: %s", popupHandler)

        activePopupMessageHandler = popupHandler
        // if we have received calls to addPopupMessageListener before
        // the UIService is registered we should add those listeners
        if (earlyAddedListeners != null) {
            for (l in earlyAddedListeners!!) {
                activePopupMessageHandler!!.addPopupMessageListener(l)
            }
            earlyAddedListeners!!.clear()
            earlyAddedListeners = null
        }
        return oldHandler
    }

    /**
     * Sets activePopupHandler to be the one with the highest preference index.
     */
    override fun selectBestPopupMessageHandler() {
        var preferredHandler: PopupMessageHandler? = null
        var highestPrefIndex = 0
        if (!popupHandlerSet.isEmpty) {
            val keys = popupHandlerSet.keys()
            while (keys.hasMoreElements()) {
                val handlerName = keys.nextElement()
                val h = popupHandlerSet[handlerName]
                if (h!!.preferenceIndex > highestPrefIndex) {
                    highestPrefIndex = h.preferenceIndex
                    preferredHandler = h
                }
            }
            setActivePopupMessageHandler(preferredHandler!!)
        }
    }

    /**
     * Initializes popup handler by searching registered services for class `PopupMessageHandler`.
     */
    protected fun initHandlers() {
        // Listens for new popup handlers
        try {
            bundleContext.addServiceListener(ServiceListenerImpl(),
                    "(objectclass=" + PopupMessageHandler::class.java.name + ")")
        } catch (e: Exception) {
            Timber.w("%s", e.message)
        }

        // now we look if some handler has been registered before we start to listen
        val handlerRefs = ServiceUtils.getServiceReferences(bundleContext, PopupMessageHandler::class.java) as Array<ServiceReference<PopupMessageHandler>>
        if (handlerRefs.isNotEmpty()) {
            val config = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
            val configuredHandler = config!!.getString("systray.POPUP_HANDLER")
            for (handlerRef in handlerRefs) {
                val handler = bundleContext.getService(handlerRef)
                val handlerName = handler.javaClass.name
                if (!containsHandler(handlerName)) {
                    addPopupHandler(handler)
                    Timber.i("added the following popup handler : %s", handler)
                    if (configuredHandler != null && configuredHandler == handler.javaClass.name) {
                        setActivePopupMessageHandler(handler)
                    }
                }
            }
            if (configuredHandler == null) selectBestPopupMessageHandler()
        }
    }

    /**
     * An implementation of `ServiceListener` we will use
     */
    private inner class ServiceListenerImpl : ServiceListener {
        /**
         * implements `ServiceListener.serviceChanged`
         *
         * @param serviceEvent
         */
        override fun serviceChanged(serviceEvent: ServiceEvent) {
            try {
                if (bundleContext.getService(serviceEvent.serviceReference) !is PopupMessageHandler) return

                // Event filters don't work on Android
                val handler = bundleContext.getService(serviceEvent.serviceReference) as PopupMessageHandler
                if (serviceEvent.type == ServiceEvent.REGISTERED) {
                    if (!containsHandler(handler.javaClass.name)) {
                        Timber.i("adding the following popup handler : %s", handler)
                        addPopupHandler(handler)
                    } else Timber.w("the following popup handler has not been added since it is already known : %s", handler)
                    val cfg = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
                    val configuredHandler = cfg!!.getString("systray.POPUP_HANDLER")
                    if (configuredHandler == null && (activePopupMessageHandler == null || handler.preferenceIndex > activePopupMessageHandler!!.preferenceIndex)) {
                        // The user doesn't have a preferred handler set and new
                        // handler with better preference index has arrived,
                        // thus setting it as active.
                        setActivePopupMessageHandler(handler)
                    }
                    if (configuredHandler != null && configuredHandler == handler.javaClass.name) {
                        // The user has a preferred handler set and it just
                        // became available, thus setting it as active
                        setActivePopupMessageHandler(handler)
                    }
                } else if (serviceEvent.type == ServiceEvent.UNREGISTERING) {
                    Timber.i("removing the following popup handler : %s", handler)
                    removePopupHandler(handler)
                    val activeHandler = activePopupMessageHandler
                    if (activeHandler === handler) {
                        setActivePopupMessageHandler(null)

                        // We just lost our default handler, so we replace it
                        // with the one that has the highest preference index.
                        selectBestPopupMessageHandler()
                    }
                }
            } catch (e: IllegalStateException) {
                Timber.d(e)
            }
        }
    }
}