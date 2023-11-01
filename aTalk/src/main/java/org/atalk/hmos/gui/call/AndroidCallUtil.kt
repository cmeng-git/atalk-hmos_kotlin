/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.app.Activity
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicTelephonyJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smackx.jinglemessage.element.JingleMessage
import org.jxmpp.jid.Jid
import timber.log.Timber

/**
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object AndroidCallUtil {
    /**
     * Field used to track the thread used to create outgoing calls.
     */
    private var createCallThread: Thread? = null

    /**
     * Creates an android call.
     *
     * @param context the android context
     * @param contact the contact address to call
     * @param callButtonView the button view that generated the call
     * @param isVideoCall true to setup video call
     */
    fun createAndroidCall(context: Context, contact: Jid, callButtonView: View?, isVideoCall: Boolean) {
        showCallViaMenu(context, contact, callButtonView, isVideoCall)
    }

    /**
     * Shows "call via" menu allowing user to select from multiple providers.
     *
     * @param context the android context
     * @param calleeJid the target callee name that will be used.
     * @param v the View that will contain the popup menu.
     * @param isVideoCall true for video call setup
     */
    private fun showCallViaMenu(context: Context, calleeJid: Jid, v: View?, isVideoCall: Boolean) {
        val popup = PopupMenu(context, v)
        val menu = popup.menu
        var mProvider: ProtocolProviderService? = null

        // loop through all registered providers to find the callee own provider
        for (provider in AccountUtils.onlineProviders) {
            val connection = provider.connection
            if (Roster.getInstanceFor(connection).contains(calleeJid.asBareJid())) {
                val accountAddress = provider.accountID.accountJid
                val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, accountAddress)
                menuItem.setOnMenuItemClickListener { item: MenuItem? ->
                    createCall(context, provider, calleeJid, isVideoCall)
                    false
                }
                mProvider = provider
            }
            // Pre-assigned current provider in case the calleeAddress is not listed in roaster
            // e.g call contact from phone book - user the first available
            if (mProvider == null) mProvider = provider
        }

        // Show contact selection menu if more than one choice
        if (menu.size() > 1) {
            popup.show()
        } else if (mProvider != null) createCall(context, mProvider, calleeJid, isVideoCall)
    }

    /**
     * Creates new call to given `destination` using selected `provider`.
     *
     * @param context the android context
     * @param metaContact target callee metaContact.
     * @param isVideoCall true to setup video call
     * @param callButtonView not null if call via contact list fragment.
     */
    fun createCall(context: Context, metaContact: MetaContact, isVideoCall: Boolean, callButtonView: View?) {
        // Check for resource permission before continue
        if (!aTalk.isMediaCallAllowed(isVideoCall)) {
            Timber.w("createCall permission denied #1: %s", isVideoCall)
            return
        }
        val contact = metaContact.getDefaultContact()!!
        val callee = contact.contactJid!!
        val pps = contact.protocolProvider
        val isJmSupported = metaContact.isFeatureSupported(JingleMessage.NAMESPACE)
        if (isJmSupported) {
            JingleMessageSessionImpl.sendJingleMessagePropose(pps.connection!!, callee, isVideoCall)
        } else {
            // Must init the Sid if call not via JingleMessage
            val basicTelephony = pps.getOperationSet(OperationSetBasicTelephony::class.java) as OperationSetBasicTelephonyJabberImpl
            basicTelephony.initSid()
            if (callButtonView != null) {
                showCallViaMenu(context, callee, callButtonView, isVideoCall)
            } else {
                createCall(context, pps, callee, isVideoCall)
            }
        }
    }

    /**
     * Creates new call to given `destination` using selected `provider`.
     *
     * @param context the android context
     * @param provider the provider that will be used to make a call.
     * @param callee target callee Jid.
     * @param isVideoCall true for video call setup
     */
    fun createCall(context: Context, provider: ProtocolProviderService?,
                   callee: Jid?, isVideoCall: Boolean) {
        if (!aTalk.isMediaCallAllowed(isVideoCall)) {
            Timber.w("createCall permission denied #2: %s", isVideoCall)
            return
        }

        // Force to null assuming user is making a call seeing no call in progress, otherwise cannot make call at all
        if (createCallThread != null) {
            Timber.w("Another call is being created restarting call thread!")
            createCallThread = null
        } else if (CallManager.getActiveCallsCount() > 1) {
            aTalkApp.showToastMessage(R.string.gui_call_max_transfer)
            return
        }
        // cmeng (20210319: Seems to have no chance to show and it causes waitForDialogOpened() (10s) error often, so remove it
//        final long dialogId = ProgressDialogFragment.showProgressDialog(
//                aTalkApp.getResString(R.string.service_gui_CALL_OUTGOING),
//                aTalkApp.getResString(R.string.service_gui_CALL_OUTGOING_MSG, callee))
        createCallThread = object : Thread("Create call thread") {
            override fun run() {
                try {
                    CallManager.createCall(provider!!, callee.toString(), isVideoCall)
                } catch (t: Throwable) {
                    Timber.e(t, "Error creating the call: %s", t.message)
                    DialogActivity.showDialog(context, context.getString(R.string.service_gui_ERROR), t.message)
                } finally {
                    createCallThread = null
                }
            }
        }
        createCallThread!!.start()
    }

    /**
     * Checks if there is a call in progress. If true then shows a warning toast and finishes the activity.
     *
     * @param activity activity doing a check.
     *
     * @return `true` if there is call in progress and `Activity` was finished.
     */
    fun checkCallInProgress(activity: Activity): Boolean {
        return if (CallManager.getActiveCallsCount() > 0) {
            Timber.w("Call is in progress")
            Toast.makeText(activity, R.string.service_gui_WARN_CALL_IN_PROGRESS, Toast.LENGTH_SHORT).show()
            activity.finish()
            true
        } else {
            false
        }
    }
}