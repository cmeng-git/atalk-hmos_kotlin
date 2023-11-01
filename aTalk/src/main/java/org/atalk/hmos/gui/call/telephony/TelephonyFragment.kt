/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.call.telephony

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicTelephonyJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicTelephonyJabberImpl.Companion.GOOGLE_VOICE_DOMAIN
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.account.Account
import org.atalk.hmos.gui.account.AccountsListAdapter
import org.atalk.hmos.gui.call.AndroidCallUtil
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiFragment
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException

/**
 * This activity allows user to make pbx phone call via the selected service gateway.
 * The server must support pbx phone call via gateway i.e.
 * <feature var='urn:xmpp:jingle:apps:rtp:audio'></feature>
 * <feature var='urn:xmpp:jingle:apps:rtp:video'></feature>
 *
 * @author Eng Chong Meng
 */
class TelephonyFragment : OSGiFragment() {
    private var mContext: Context? = null
    private var fragmentActivity: FragmentActivity? = null
    private var accountsSpinner: Spinner? = null
    private lateinit var vRecipient: RecipientSelectView
    private var vTelephonyDomain: TextView? = null
    private var mPPS: ProtocolProviderService? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        fragmentActivity = activity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreate(savedInstanceState)
        val content = inflater.inflate(R.layout.telephony, container, false)
        vRecipient = content.findViewById(R.id.address)
        vRecipient.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                mLastJid = if (!vRecipient.isEmpty) {
                    vRecipient.addresses[0]!!.getAddress()
                } else {
                    ViewUtil.toString(vRecipient)
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
        if (mLastJid != null) vRecipient.setText(mLastJid)
        vTelephonyDomain = content.findViewById(R.id.telephonyDomain)
        accountsSpinner = content.findViewById(R.id.selectAccountSpinner)
        accountsSpinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                val selectedAcc = accountsSpinner!!.selectedItem as Account
                mPPS = selectedAcc.protocolProvider
                val accountJID = mPPS!!.accountID
                var telephonyDomain = accountJID.getOverridePhoneSuffix()
                if (TextUtils.isEmpty(telephonyDomain)) {
                    // StrictMode$AndroidBlockGuardPolicy.onNetwork(StrictMode.java:1448); so a simple check only instead of SRV
                    // boolean isGoogle =  mPPS.isGmailOrGoogleAppsAccount;
                    val isGoogle = accountJID.toString().contains("google.com")
                    telephonyDomain = if (isGoogle) {
                        val bypassDomain = accountJID.getTelephonyDomainBypassCaps()
                        if (!TextUtils.isEmpty(bypassDomain)) bypassDomain else GOOGLE_VOICE_DOMAIN
                    } else accountJID.service
                }
                mDomainJid = telephonyDomain
                vTelephonyDomain!!.text = telephonyDomain
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {
                // your code here
            }
        }
        initAccountSpinner()
        initButton(content)
        return content
    }

    /**
     * Initializes accountIDs spinner selector with existing registered accounts.
     */
    private fun initAccountSpinner() {
        var idx = 0
        var selectedIdx = -1
        val accounts = ArrayList<AccountID>()
        val providers = AccountUtils.registeredProviders
        for (provider in providers) {
            val opSet = provider.getOperationSet(OperationSetPresence::class.java)
            if (opSet != null) {
                val accountID = provider.accountID
                accounts.add(accountID)
                if (selectedIdx == -1 && mDomainJid != null) {
                    if (mDomainJid!!.contains(accountID.service!!)) selectedIdx = idx
                }
                idx++
            }
        }
        val accountsAdapter = AccountsListAdapter(activity!!,
                R.layout.select_account_row, R.layout.select_account_dropdown, accounts, true)
        accountsSpinner!!.adapter = accountsAdapter

        // if we have only select account option and only one account select the available account
        if (accounts.size == 1) accountsSpinner!!.setSelection(0) else accountsSpinner!!.setSelection(selectedIdx)
    }

    /**
     * Initializes the button click actions.
     */
    private fun initButton(content: View) {
        val buttonAudio = content.findViewById<Button>(R.id.button_audio)
        buttonAudio.setOnClickListener { v: View? -> onCallClicked(false) }
        val buttonVideo = content.findViewById<Button>(R.id.button_video)
        buttonVideo.setOnClickListener { v: View? -> onCallClicked(true) }
        val buttonCancel = content.findViewById<Button>(R.id.button_cancel)
        buttonCancel.setOnClickListener { v: View? -> closeFragment() }
    }

    /**
     * Method fired when one of the call buttons is clicked.
     *
     * @param videoCall vide call is true else audio call
     */
    private fun onCallClicked(videoCall: Boolean) {
        var recipient = if (!vRecipient.isEmpty) {
            vRecipient.addresses[0]!!.getAddress()
        } else {
            ViewUtil.toString(vRecipient)
        }
        if (recipient == null) {
            // aTalkApp.showToastMessage(R.string.service_gui_NO_ONLINE_TELEPHONY_ACCOUNT);
            aTalkApp.showToastMessage(R.string.service_gui_NO_CONTACT_PHONE)
            return
        }
        recipient = recipient.replace(" ", "")
        mLastJid = recipient
        if (!recipient.contains("@")) {
            val telephonyDomain = ViewUtil.toString(vTelephonyDomain)
            recipient += "@$telephonyDomain"
        }
        val phoneJid = try {
            JidCreate.from(recipient)
        } catch (e: XmppStringprepException) {
            aTalkApp.showToastMessage(R.string.unknown_recipient)
            return
        } catch (e: IllegalArgumentException) {
            aTalkApp.showToastMessage(R.string.unknown_recipient)
            return
        }

        // Must init the Sid if call not via JingleMessage
        val basicTelephony = mPPS!!.getOperationSet(OperationSetBasicTelephony::class.java) as OperationSetBasicTelephonyJabberImpl
        basicTelephony.initSid()
        AndroidCallUtil.createCall(mContext!!, mPPS, phoneJid, videoCall)
        closeFragment()
    }

    fun closeFragment(): Boolean {
        val phoneFragment = fragmentActivity!!.supportFragmentManager.findFragmentByTag(TELEPHONY_TAG)
        if (phoneFragment != null) {
            fragmentActivity!!.supportFragmentManager.beginTransaction().remove(phoneFragment).commit()
            return true
        }
        return false
    }

    companion object {
        const val TELEPHONY_TAG = "telephonyFragment"
        private var mLastJid: String? = null
        private var mDomainJid: String? = null
        fun newInstance(domainJid: String?): TelephonyFragment {
            val telephonyFragment = TelephonyFragment()
            mDomainJid = domainJid
            return telephonyFragment
        }
    }
}