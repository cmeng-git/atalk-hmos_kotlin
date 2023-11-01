/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.Manifest
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.ImageView
import androidx.appcompat.widget.PopupMenu
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiFragment
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.roster.Roster
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * Tha `CallContactFragment` encapsulated GUI used to make a call.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CallContactFragment : OSGiFragment() {
    /**
     * The bundle context.
     */
    private var bundleContext: BundleContext? = null

    /**
     * {@inheritDoc}
     */
    @Synchronized
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext?) {
        super.start(bundleContext)
        /*
         * If there are unit tests to be run, do not run anything else and just perform
         * the unit tests.
         */
        if (System.getProperty("net.java.sip.communicator.slick.runner.TEST_LIST") != null) return
        this.bundleContext = bundleContext
        initAndroidAccounts()
    }

    /**
     * Shows "call via" menu allowing user to selected from multiple providers if available.
     *
     * @param v the View that will contain the popup menu.
     * @param calleeAddress target callee name.
     */
    private fun showCallViaMenu(v: View, calleeAddress: String) {
        val popup = PopupMenu(activity!!, v)
        val menu = popup.menu
        var mProvider: ProtocolProviderService? = null
        val onlineProviders = AccountUtils.onlineProviders
        for (provider in onlineProviders) {
            val connection = provider.connection!!
            try {
                if (Roster.getInstanceFor(connection).contains(JidCreate.bareFrom(calleeAddress))) {
                    val accountAddress = provider.accountID.accountJid
                    val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, accountAddress)
                    menuItem.setOnMenuItemClickListener { item: MenuItem? ->
                        createCall(provider, calleeAddress)
                        false
                    }
                    mProvider = provider
                }
            } catch (e: XmppStringprepException) {
                e.printStackTrace()
            }
        }
        if (menu.size() > 1) popup.show() else createCall(mProvider, calleeAddress)
    }

    /**
     * Creates new call to given `destination` using selected `provider`.
     *
     * @param destination target callee name.
     * @param provider the provider that will be used to make a call.
     */
    private fun createCall(provider: ProtocolProviderService?, destination: String) {
        object : Thread() {
            override fun run() {
                try {
                    CallManager.createCall(provider!!, destination, false)
                } catch (t: Throwable) {
                    Timber.e(t, "Error creating the call: %s", t.message)
                    DialogActivity.showDialog(activity!!, getString(R.string.service_gui_ERROR), t.message)
                }
            }
        }.start()
    }

    /**
     * Loads Android accounts.
     */
    fun initAndroidAccounts() {
        if (aTalk.hasPermission(activity, true,
                        aTalk.PRC_GET_CONTACTS, Manifest.permission.GET_ACCOUNTS)) {
            val androidAccManager = android.accounts.AccountManager.get(activity)
            val androidAccounts = androidAccManager.getAccountsByType(getString(R.string.ACCOUNT_TYPE))
            for (account in androidAccounts) {
                System.err.println("ACCOUNT======$account")
            }
        }
    }

    companion object {
        /**
         * Optional phone number argument.
         */
        var ARG_PHONE_NUMBER = "arg.phone_number"

        /**
         * Creates new parametrized instance of `CallContactFragment`.
         *
         * @param phoneNumber optional phone number that will be filled.
         *
         * @return new parameterized instance of `CallContactFragment`.
         */
        fun newInstance(phoneNumber: String?): CallContactFragment {
            val ccFragment = CallContactFragment()
            val args = Bundle()
            args.putString(ARG_PHONE_NUMBER, phoneNumber)
            ccFragment.arguments = args
            return ccFragment
        }

        /**
         * {@inheritDoc}
         */
        fun onCreateView(callContactFragment: CallContactFragment, inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val content = inflater.inflate(R.layout.call_contact, container, false)
            val callButton = content.findViewById<ImageView>(R.id.callButtonFull)
            callButton.setOnClickListener { v: View? ->
                val contact = ViewUtil.toString(content.findViewById(R.id.callField))
                if (contact == null) {
                    System.err.println("Contact is empty")
                } else {
                    callContactFragment.showCallViaMenu(callButton, contact)
                }
            }

            // Call intent handling
            val arguments = callContactFragment.arguments!!
            val phoneNumber = arguments.getString(ARG_PHONE_NUMBER)
            if (!TextUtils.isEmpty(phoneNumber)) {
                ViewUtil.setTextViewValue(content, R.id.callField, phoneNumber)
            }
            return content
        }
    }
}