/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.account

import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.SecurityAuthority
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.osgi.framework.Bundle
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import timber.log.Timber

/**
 * The `LoginManager` manages the login operation. Here we obtain the
 * `ProtocolProviderFactory`, we make the account installation and we handle all events
 * related to the registration state.
 *
 *
 * The `LoginManager` is the one that opens one or more `LoginWindow`s for each
 * `ProtocolProviderFactory`. The `LoginWindow` is where user could enter an
 * identifier and password.
 *
 *
 * Note that the behavior of this class will be changed when the Configuration Service is ready.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class LoginManager(private val loginRenderer: LoginRenderer) : ServiceListener, RegistrationStateChangeListener //,AccountManagerListener
{
    /**
     * Returns `true` to indicate the atalk has been manually disconnected, `false` - otherwise.
     *
     * @return `true` to indicate the atalk has been manually disconnected, `false` - otherwise
     */
    /**
     * Sets the manually disconnected property.
     *
     * manuallyDisconnected `true` to indicate the atalk has been manually disconnected, `false` - otherwise
     */
    var isManuallyDisconnected = false

    /**
     * Creates an instance of the `LoginManager`, by specifying the main application window.
     *
     * loginRenderer the main application window
     */
    init {
        UtilActivator.bundleContext!!.addServiceListener(this)
    }

    /**
     * Registers the given protocol provider.
     *
     * @param protocolProvider the ProtocolProviderService to register.
     */
    fun login(protocolProvider: ProtocolProviderService) {
        // Timber.log(TimberLog.FINER, "SMACK stack access: %s", Log.getStackTraceString(new Exception()));
        loginRenderer.startConnectingUI(protocolProvider)
        RegisterProvider(protocolProvider, loginRenderer.getSecurityAuthorityImpl(protocolProvider)).start()
    }

    /**
     * Shows login window for each registered account.
     */
    fun runLogin() {
        for (providerFactory in UtilActivator.protocolProviderFactories.values) {
            addAccountsForProtocolProviderFactory(providerFactory)
        }
    }

    /**
     * Handles stored accounts for a protocol provider factory and add them to the UI and register
     * them if needed.
     *
     * @param providerFactory the factory to handle.
     */
    private fun addAccountsForProtocolProviderFactory(providerFactory: ProtocolProviderFactory) {
        for (accountID in providerFactory.getRegisteredAccounts()) {
            val serRef = providerFactory.getProviderForAccount(accountID)
            val protocolProvider = UtilActivator.bundleContext!!.getService(serRef)
            handleProviderAdded(protocolProvider)
        }
    }

    /**
     * The method is called by a ProtocolProvider implementation whenever a change in the
     * registration state of the corresponding provider has occurred.
     *
     * @param evt ProviderStatusChangeEvent the event describing the status change.
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        val newState = evt.getNewState()
        val protocolProvider = evt.getProvider()
        val accountID = protocolProvider.accountID
        if (TimberLog.isTraceEnable) Timber.log(TimberLog.FINER, "Protocol provider: %s changes state to: %s Reason: %s",
                protocolProvider, evt.getNewState().getStateName(), evt.getReason())
        if (newState == RegistrationState.REGISTERED || newState == RegistrationState.UNREGISTERED || newState == RegistrationState.EXPIRED || newState == RegistrationState.AUTHENTICATION_FAILED || newState == RegistrationState.CONNECTION_FAILED || newState == RegistrationState.CHALLENGED_FOR_AUTHENTICATION) {
            loginRenderer.stopConnectingUI(protocolProvider)
        }
        if (newState == RegistrationState.REGISTERED) {
            loginRenderer.protocolProviderConnected(protocolProvider, System.currentTimeMillis())
        }
        //		else {
        //			ResourceManagementService mRMS = UtilActivator.getResources();
        //			String msgText = null;
        //
        //			if (newState.equals(RegistrationState.AUTHENTICATION_FAILED)) {
        //				switch (evt.getReasonCode()) {
        //					case RegistrationStateChangeEvent.REASON_RECONNECTION_RATE_LIMIT_EXCEEDED:
        //						msgText = mRMS.getI18NString("service.gui.RECONNECTION_LIMIT_EXCEEDED",
        //								new String[]{accountID.userID, accountID.getService()});
        //						break;
        //
        //					case RegistrationStateChangeEvent.REASON_NON_EXISTING_USER_ID:
        //						msgText = mRMS.getI18NString("service.gui.NON_EXISTING_USER_ID",
        //								new String[]{protocolProvider.getProtocolDisplayName()});
        //						break;
        //					case RegistrationStateChangeEvent.REASON_TLS_REQUIRED:
        //						msgText = mRMS.getI18NString("service.gui.NON_SECURE_CONNECTION",
        //								new String[]{accountID.getAccountJid()});
        //						break;
        //					default:
        //						break;
        //				}
        //
        //  		Timber.log(TimberLog.FINER, "%s", evt.getReason());
        //			}
        //			// CONNECTION_FAILED events are now dispatched in reconnect plugin
        ////			else if (newState.equals(RegistrationState.CONNECTION_FAILED)) {
        ////				loginRenderer.protocolProviderConnectionFailed(
        ////						protocolProvider, this);
        ////				Timber.log(TimberLog.FINER, evt.getReason());
        ////			}
        //			else if (newState.equals(RegistrationState.EXPIRED)) {
        //				msgText = mRMS.getI18NString("service.gui.CONNECTION_EXPIRED_MSG",
        //						new String[]{protocolProvider.getProtocolDisplayName()});
        //				Timber.e(evt.getReason());
        //			}
        //			else if (newState.equals(RegistrationState.UNREGISTERED)) {
        //				if (!manuallyDisconnected) {
        //					switch (evt.getReasonCode()) {
        //						case RegistrationStateChangeEvent.REASON_MULTIPLE_LOGIN:
        //							msgText = aTalkApp.getResString(R.string.service_gui_MULTIPLE_LOGIN,
        //									accountID.userID, accountID.getService());
        //							break;
        //						case RegistrationStateChangeEvent.REASON_CLIENT_LIMIT_REACHED_FOR_IP:
        //							msgText = mRMS.getI18NString("service.gui.LIMIT_REACHED_FOR_IP",
        //									new String[]{protocolProvider.getProtocolDisplayName()});
        //							break;
        //						case RegistrationStateChangeEvent.REASON_USER_REQUEST:
        //							// do nothing
        //							break;
        //						default:
        //							msgText = aTalkApp.getResString(R.string.service_gui_UNREGISTERED_MESSAGE,
        //									accountID.userID, accountID.getServerAddress());
        //					}
        //						Timber.log(TimberLog.FINER, evt.getReason());
        //				}
        //			}
        //			if (msgText != null)
        //				UtilActivator.getAlertUIService()
        //						.showAlertDialog(mRMS.getI18NString("service.gui.ERROR"), msgText);
        //		}
    }

    /**
     * Implements the `ServiceListener` method. Verifies whether the passed event
     * concerns a `ProtocolProviderService` and adds the corresponding UI controls.
     *
     * @param event The `ServiceEvent` object.
     */
    override fun serviceChanged(event: ServiceEvent) {
        val serviceRef = event.serviceReference

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.bundle.state == Bundle.STOPPING) return
        val service = UtilActivator.bundleContext!!.getService(serviceRef) as? ProtocolProviderService
                ?: return

        // we don't care if the source service is not a protocol provider
        when (event.type) {
            ServiceEvent.REGISTERED -> handleProviderAdded(service)
            ServiceEvent.UNREGISTERING -> handleProviderRemoved(service)
        }
    }

    /**
     * Adds all UI components (status selector box, etc) related to the given protocol provider.
     *
     * @param protocolProvider the `ProtocolProviderService`
     */
    private fun handleProviderAdded(protocolProvider: ProtocolProviderService) {
        Timber.log(TimberLog.FINER, "The following protocol provider was just added: "
                + protocolProvider.accountID.accountJid)

        synchronized(loginRenderer) {
            if (!loginRenderer.containsProtocolProviderUI(protocolProvider)) {
                protocolProvider.addRegistrationStateChangeListener(this)
                loginRenderer.addProtocolProviderUI(protocolProvider)
            } else return
        }
        val status = AccountStatusUtils.getProtocolProviderLastStatus(protocolProvider)
        if (status == null || status == GlobalStatusEnum.ONLINE_STATUS || status is PresenceStatus && status.status >= PresenceStatus.ONLINE_THRESHOLD) {
            login(protocolProvider)
        }
    }

    /**
     * Removes all UI components related to the given protocol provider.
     *
     * @param protocolProvider the `ProtocolProviderService`
     */
    private fun handleProviderRemoved(protocolProvider: ProtocolProviderService) {
        loginRenderer.removeProtocolProviderUI(protocolProvider)
    }

    /**
     * Registers a protocol provider in a separate thread.
     */
    private inner class RegisterProvider
    //Timber.log(TimberLog.FINER, new Exception("Not an error! Just tracing for provider registering."),
    //  "Registering provider: %s", protocolProvider.accountID.getAccountJid());
    (private val protocolProvider: ProtocolProviderService, private val secAuth: SecurityAuthority?) : Thread() {
        /**
         * Registers the contained protocol provider.
         * # Process all possible errors that may occur during the registration process.
         * # This is now handled with pps registration process
         */
        override fun run() {
            try {
                protocolProvider.register(secAuth)
            } catch (ex: OperationFailedException) {
                handleOperationFailedException(ex)
            } catch (ex: Throwable) {
                // cmeng: all exceptions will be handled within pps
                Timber.e(ex, "Failed to register protocol provider. ")
            }
        }

        private fun handleOperationFailedException(ex: OperationFailedException) {
            Timber.e(ex, "Provider failed to register with: ")
            if (OperationFailedException.NETWORK_FAILURE == ex.getErrorCode()) {
                loginRenderer.protocolProviderConnectionFailed(protocolProvider, this@LoginManager)
            }
        }
    }

    /**
     * Unregisters a protocol provider in a separate thread.
     */
    private class UnregisterProvider(var protocolProvider: ProtocolProviderService) : Thread() {
        /**
         * Unregisters the contained protocol provider and process all possible errors that may
         * occur during the un-registration process.
         */
        override fun run() {
            try {
                protocolProvider.unregister(true)
            } catch (ex: OperationFailedException) {
                Timber.e("Provider failed unRegistration with error: %s", ex.message)

//				String alertMsg = "Provider could not be unregistered due to ";
//				int errorCode = ex.getErrorCode();
//				if (errorCode == OperationFailedException.GENERAL_ERROR) {
//					Timber.e(alertMsg + "general error: " + ex);
//				}
//				else if (errorCode == OperationFailedException.INTERNAL_ERROR) {
//					Timber.e(alertMsg + "internal error: " + ex);
//				}
//				else if (errorCode == OperationFailedException.NETWORK_FAILURE) {
//					Timber.e(alertMsg + "network failure: " + ex);
//				}
                DialogActivity.showDialog(aTalkApp.globalContext,
                        R.string.service_gui_ERROR, R.string.service_gui_LOGOFF_NOT_SUCCEEDED,
                        protocolProvider.accountID.mUserID,
                        protocolProvider.accountID.service)
            }
        }
    }

    companion object {
        /**
         * Unregisters the given protocol provider.
         *
         * @param protocolProvider the ProtocolProviderService to unregister
         */
        fun logoff(protocolProvider: ProtocolProviderService) {
            UnregisterProvider(protocolProvider).start()
        }
    }
}