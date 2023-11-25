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
package org.atalk.crypto

import android.content.Intent
import android.text.TextUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.gui.ChatLinkClickedListener
import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceListener
import org.atalk.crypto.listener.CryptoModeChangeListener
import org.atalk.crypto.omemo.AndroidOmemoService
import org.atalk.crypto.omemo.OmemoAuthenticateDialog
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.chat.MetaContactChatSession
import org.atalk.hmos.gui.settings.SettingsActivity
import org.atalk.service.osgi.OSGiFragment
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoService
import org.jivesoftware.smackx.omemo.OmemoStore
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException
import org.jivesoftware.smackx.omemo.exceptions.NoOmemoSupportException
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.pubsub.PubSubException
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.DomainBareJid
import timber.log.Timber
import java.io.IOException
import java.net.URI
import java.util.*

/**
 * Fragment when added to `Activity` will display the padlock allowing user to select
 * various type of encryption options. Only currently active chat is handled by this fragment.
 *
 * @author Eng Chong Meng
 */
class CryptoFragment : OSGiFragment(), ChatSessionManager.CurrentChatListener, ChatRoomMemberPresenceListener, OmemoAuthenticateDialog.AuthenticateListener {
    /**
     * Menu instances used to select and control the crypto choices.
     */
    private lateinit var mCryptoChoice: MenuItem
    private lateinit var mNone: MenuItem
    private lateinit var mOmemo: MenuItem

    private var mConnection: XMPPConnection? = null

    /**
     * Can either be Contact or ChatRoom
     */
    private var mDescriptor: Any? = null
    private var mOmemoManager: OmemoManager? = null
    private var mOmemoStore: OmemoStore<*, *, *, *, *, *, *, *, *>? = null
    private var mChatType = ChatFragment.MSGTYPE_NORMAL

    /**
     * Current active instance of chatSession & user.
     */
    private var activeChat: ChatPanel? = null
    private var mMultiUserChat: MultiUserChat? = null
    private var mEntity: String? = null
    private var mCurrentChatSessionId: String? = null

    private val mMHS: MessageHistoryService

    /**
     * Creates a new instance of `OtrFragment`.
     */
    init {
        setHasOptionsMenu(true)
        mMHS = MessageHistoryActivator.messageHistoryService
    }

    /**
     * {@inheritDoc}
     */
    override fun onStop() {
        ChatSessionManager.removeCurrentChatListener(this)
        super.onStop()
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        // This happens when Activity is recreated by the system after OSGi service has been killed (and the whole process)
        if (AndroidGUIActivator.bundleContext == null) {
            Timber.e("OSGi service probably not initialized")
            return
        }
        mOmemoStore = OmemoService.getInstance().omemoStoreBackend

        /*
         * Menu instances used to select and control the crypto choices.
         * Add chat encryption choices if not found
         */
        if (menu.findItem(R.id.encryption_none) == null)
            inflater.inflate(R.menu.crypto_choices, menu)

        mCryptoChoice = menu.findItem(R.id.crypto_choice)
        mNone = menu.findItem(R.id.encryption_none)
        mOmemo = menu.findItem(R.id.encryption_omemo)

        // Initialize the padlock icon only after the Crypto menu is created
        doInit()
    }

    /**
     * {@inheritDoc}
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var hasChange = false
        item.isChecked = true

        when (item.itemId) {
            R.id.crypto_choice -> {
                var isOmemoSupported = omemoCapable[mDescriptor]
                if (isOmemoSupported == null)
                    isOmemoSupported = false
                mOmemo.isEnabled = isOmemoSupported
                mOmemo.icon!!.alpha = if (isOmemoSupported) 255 else 80

                // sync button check to current chatType
                if (activeChat != null) {
                    val mItem = checkCryptoButton(activeChat!!.chatType)
                    mItem.isChecked = true
                }
                return true
            }

            R.id.encryption_none -> {
                // if ((mChatType != MSGTYPE_NORMAL) && (mChatType != MSGTYPE_MUC_NORMAL)) {
                mChatType = if (mDescriptor is Contact) ChatFragment.MSGTYPE_NORMAL
                else ChatFragment.MSGTYPE_MUC_NORMAL
                hasChange = true
                doHandleOmemoPressed(false)
            }

            R.id.encryption_omemo -> {
                if (!activeChat!!.isOmemoChat) mChatType = ChatFragment.MSGTYPE_OMEMO
                hasChange = true
                doHandleOmemoPressed(true)
            }

            else -> {}
        }

        if (hasChange) {
            val chatId = ChatSessionManager.getCurrentChatId()
            encryptionChoice[chatId] = mChatType
            setStatusOmemo(mChatType)
            // Timber.w("update persistent ChatType to: %s", mChatType);

            mMHS.setSessionChatType(activeChat!!.chatSession!!, mChatType)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Synchronise the cryptoChoice button checkMark with the current chatType
     *
     * @param chatType Sync cryptoChoice button check to the given chatType
     * @return the button menuItem corresponding to the given chatType
     */
    private fun checkCryptoButton(chatType: Int): MenuItem {
        val mItem = when (chatType) {
            ChatFragment.MSGTYPE_NORMAL,
            ChatFragment.MSGTYPE_MUC_NORMAL,
            -> mNone

            ChatFragment.MSGTYPE_OMEMO,
            ChatFragment.MSGTYPE_OMEMO_UA,
            ChatFragment.MSGTYPE_OMEMO_UT,
            -> mOmemo

            else -> mNone
        }
        return mItem
    }

    /**
     * Handle OMEMO state when the option is selected/unSelected.
     */
    private fun doHandleOmemoPressed(enable: Boolean) {
        // return: nothing to do if not enable
        val pps = activeChat!!.protocolProvider
        if (!enable || mOmemoManager == null || mDescriptor == null || !pps.isRegistered) return

        // Linked map between OmemoDevice and its fingerprint.
        var fingerPrints = HashMap<OmemoDevice?, OmemoFingerprint?>()
        var omemoDevice: OmemoDevice
        var fingerPrint: OmemoFingerprint
        var allTrusted = true
        if (mDescriptor is Contact) {
            val bareJid = (mDescriptor as Contact).contactJid!!.asBareJid()
            mEntity = bareJid.toString()
            try {
                fingerPrints = mOmemoManager!!.getActiveFingerprints(bareJid)
            } catch (e: CorruptedOmemoKeyException) {
                // IllegalArgumentException is throw when IdentityKeyPair is null
                Timber.w("Fetching active fingerPrints has failed: %s", e.message)
            } catch (e: CannotEstablishOmemoSessionException) {
                Timber.w("Fetching active fingerPrints has failed: %s", e.message)
            } catch (e: SmackException.NotConnectedException) {
                Timber.w("Fetching active fingerPrints has failed: %s", e.message)
            } catch (e: SmackException.NotLoggedInException) {
                Timber.w("Fetching active fingerPrints has failed: %s", e.message)
            } catch (e: InterruptedException) {
                Timber.w("Fetching active fingerPrints has failed: %s", e.message)
            } catch (e: SmackException.NoResponseException) {
                Timber.w("Fetching active fingerPrints has failed: %s", e.message)
            } catch (e: IllegalArgumentException) {
                Timber.w("Fetching active fingerPrints has failed: %s", e.message)
            } catch (e: IOException) {
                Timber.w("Fetching active fingerPrints has failed: %s", e.message)
            }
            try {
                mOmemoManager!!.encrypt(bareJid, "Hi buddy!")
            } catch (e: UndecidedOmemoIdentityException) {
                val omemoDevices = e.undecidedDevices
                Timber.w("There are undecided Omemo devices: %s", omemoDevices)
                startActivity(OmemoAuthenticateDialog.createIntent(context, mOmemoManager, omemoDevices, this))
                allTrusted = false
            } catch (e: InterruptedException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                Timber.i("OMEMO changes mChatType to: %s", mChatType)
                return
            } catch (e: SmackException.NoResponseException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                Timber.i("OMEMO changes mChatType to: %s", mChatType)
                return
            } catch (e: CryptoFailedException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                Timber.i("OMEMO changes mChatType to: %s", mChatType)
                return
            } catch (e: SmackException.NotConnectedException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                Timber.i("OMEMO changes mChatType to: %s", mChatType)
                return
            } catch (e: SmackException.NotLoggedInException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                Timber.i("OMEMO changes mChatType to: %s", mChatType)
                return
            } catch (e: Exception) { // catch any non-advertised exception
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                Timber.w("Revert OMEMO mChatType to: %s", mChatType)
                return
            }
            var numUntrusted = 0
            for ((key, value) in fingerPrints) {
                omemoDevice = key!!
                fingerPrint = value!!
                if (!mOmemoManager!!.isTrustedOmemoIdentity(omemoDevice, fingerPrint)) {
                    numUntrusted++
                }
            }
            /*
             * Found no trusted device for OMEMO session, so set to MSGTYPE_OMEMO_UT
             * Encrypted message without the buddy <rid/> key
             */
            if (numUntrusted > 0 && numUntrusted == fingerPrints.size) {
                mChatType = ChatFragment.MSGTYPE_OMEMO_UT
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_UNTRUSTED))
            }
            else if (allTrusted) {
                mChatType = ChatFragment.MSGTYPE_OMEMO
            }
            else {
                mChatType = ChatFragment.MSGTYPE_OMEMO_UA
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_UNVERIFIED))
            }
        }
        else if (mDescriptor is ChatRoom) {
            (mDescriptor as ChatRoom).addMemberPresenceListener(this)
            val entityBareJid = (mDescriptor as ChatRoom).getIdentifier()
            mEntity = entityBareJid.toString()
            val mucMgr = MultiUserChatManager.getInstanceFor(mConnection)
            mMultiUserChat = mucMgr.getMultiUserChat(entityBareJid)

            try {
                mOmemoManager!!.encrypt(mMultiUserChat, "Hi everybody!")
            } catch (e: UndecidedOmemoIdentityException) {
                val omemoDevices = e.undecidedDevices
                Timber.w("There are undecided Omemo devices: %s", omemoDevices)
                startActivity(OmemoAuthenticateDialog.createIntent(context, mOmemoManager, omemoDevices, this))
                allTrusted = false
            } catch (e: NoOmemoSupportException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                return
            } catch (e: InterruptedException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                return
            } catch (e: SmackException.NoResponseException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                return
            } catch (e: XMPPException.XMPPErrorException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                return
            } catch (e: CryptoFailedException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                return
            } catch (e: SmackException.NotConnectedException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                return
            } catch (e: SmackException.NotLoggedInException) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
                return
            } catch (e: Exception) { // catch any non-advertised exception
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message))
            }
            allTrusted = allTrusted && isAllTrusted(mMultiUserChat)
            if (allTrusted) {
                mChatType = ChatFragment.MSGTYPE_OMEMO
            }
            else {
                mChatType = ChatFragment.MSGTYPE_OMEMO_UA
                activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_UNVERIFIED_UNTRUSTED))
            }
        }
        // Timber.d("OMEMO changes mChatType to: %s", mChatType);
    }

    /**
     * Check to see if all muc recipients are verified or trusted
     *
     * @param multiUserChat MultiUserChat
     * @return return `true` if all muc recipients are verified or trusted. Otherwise `false`
     */
    private fun isAllTrusted(multiUserChat: MultiUserChat?): Boolean {
        var allTrusted = true
        var fingerPrint: OmemoFingerprint
        var recipient: BareJid
        for (e in multiUserChat!!.occupants) {
            recipient = multiUserChat.getOccupant(e).jid.asBareJid()
            try {
                val theirDevices = mOmemoStore!!.loadCachedDeviceList(mOmemoManager!!.ownDevice, recipient)
                for (id in theirDevices.activeDevices) {
                    val recipientDevice = OmemoDevice(recipient, id)
                    try {
                        fingerPrint = mOmemoManager!!.getFingerprint(recipientDevice)
                        allTrusted = (mOmemoManager!!.isTrustedOmemoIdentity(recipientDevice, fingerPrint)
                                && allTrusted)
                    } catch (e1: CorruptedOmemoKeyException) {
                        Timber.w("AllTrusted check exception: %s", e1.message)
                    } catch (e1: CannotEstablishOmemoSessionException) {
                        Timber.w("AllTrusted check exception: %s", e1.message)
                    } catch (e1: SmackException.NotLoggedInException) {
                        e1.printStackTrace()
                    } catch (e1: SmackException.NotConnectedException) {
                        e1.printStackTrace()
                    } catch (e1: SmackException.NoResponseException) {
                        e1.printStackTrace()
                    } catch (e1: InterruptedException) {
                        e1.printStackTrace()
                    } catch (e1: IOException) {
                        e1.printStackTrace()
                    }
                }
            } catch (ex: IOException) {
                Timber.w("IOException: %s", ex.message)
            }
        }
        return allTrusted
    }

    /**
     * Trigger when invited participant join the conference. This will fill the partial identities table for the
     * participant and request fingerPrint verification is undecided. Hence ensuring that the next sent message
     * is properly received by the new member.
     *
     * @param evt the `ChatRoomMemberPresenceChangeEvent` instance containing the source chat
     */
    override fun memberPresenceChanged(evt: ChatRoomMemberPresenceChangeEvent) {
        if (mOmemoManager != null && activeChat!!.isOmemoChat && ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED == evt.getEventType()) {
            try {
                mOmemoManager!!.encrypt(mMultiUserChat, "Hi everybody!")
            } catch (e: UndecidedOmemoIdentityException) {
                val omemoDevices = e.undecidedDevices
                Timber.w("There are undecided Omemo devices: %s", omemoDevices)
                startActivity(OmemoAuthenticateDialog.createIntent(context, mOmemoManager, omemoDevices, this))
            } catch (e: NoOmemoSupportException) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
            } catch (e: InterruptedException) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
            } catch (e: SmackException.NoResponseException) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
            } catch (e: XMPPException.XMPPErrorException) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
            } catch (e: CryptoFailedException) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
            } catch (e: IOException) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
            } catch (e: SmackException.NotConnectedException) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
            } catch (e: SmackException.NotLoggedInException) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.message)
            }
        }
    }

    /**
     * Register listeners, initializes the padlock icons and encryption options menu enable state.
     * Must only be performed after the completion of onCreateOptionsMenu().
     *
     * @see .onCreateOptionsMenu
     */
    private fun doInit() {
        ChatSessionManager.addCurrentChatListener(this)

        // Setup currentChatSession options for the Jid if login in
        val currentChatId = ChatSessionManager.getCurrentChatId()
        if (currentChatId != null)
            setCurrentChatSession(currentChatId)
    }

    /**
     * Event trigger when user slide the chatFragment
     *
     * @param chatId id of current chat session or `null` if there is no chat currently
     */
    override fun onCurrentChatChanged(chatId: String) {
        setCurrentChatSession(chatId)
    }

    /**
     * Triggered from onCreateOption() or onCurrentChatChanged()
     *
     *
     * Sets current `ChatPanel` identified by given `chatSessionKey`.
     * Init Crypto choice to last selected or encryption_none.
     *
     * @param chatSessionId chat session key managed by `ChatSessionManager`
     */
    private fun setCurrentChatSession(chatSessionId: String) {
        mCurrentChatSessionId = chatSessionId
        var metaContact: MetaContact? = null
        activeChat = ChatSessionManager.getActiveChat(chatSessionId)
        if (activeChat != null
                && activeChat!!.chatSession is MetaContactChatSession) {
            metaContact = activeChat!!.metaContact
        }
        val contact = metaContact?.getDefaultContact()

        // activeChat must not be null; proceed if it is conference call (contact == null).
        // Do not proceed if chat session is triggered from system server (domainBareJid) i.e. welcome message
        runOnUiThread {
            if (activeChat != null
                    && (contact == null || contact.contactJid !is DomainBareJid))
                initOmemo(chatSessionId)
            else {
                mOmemo.isEnabled = false
                mOmemo.icon!!.alpha = 80
            }
        }
    }

    /**
     * This method needs to be called to update crypto status after:
     * - the crypto menu is first created (after launch of chatFragment).
     * - when user slides chatSession pages i.e. onCurrentChatChanged().
     *
     * @param chatSessionId Current ChatSession Id to update crypto Status and button check
     */
    private fun initOmemo(chatSessionId: String) {
        // Get from the persistent storage chatType for new instance
        var chatType: Int
        if (!encryptionChoice.containsKey(chatSessionId)) {
            chatType = mMHS.getSessionChatType(activeChat!!.chatSession!!)
            encryptionChoice[chatSessionId] = chatType
        }
        chatType = encryptionChoice[chatSessionId]!!
        activeChat!!.chatType = chatType

        // mItem may be null if it was accessed prior to Crypto menu init is completed.
        val mItem = checkCryptoButton(chatType)
        mItem.isChecked = true
        updateOmemoSupport()
        setStatusOmemo(chatType)

//		Timber.w("ChatSession ID: %s\nEncryption choice: %s\nmItem: %s\nChatType: %s", chatSessionId,
//				encryptionChoice, mItem, activeChat.getChatType());
    }

    /**
     * Sets the padlock icon according to the passed in OMEMO mChatType.
     *
     * @param chatType OMEMO ChatType.
     */
    private fun setStatusOmemo(chatType: Int) {
        val iconId: Int
        val tipKey: Int
        mChatType = chatType
        when (chatType) {
            ChatFragment.MSGTYPE_OMEMO -> {
                iconId = R.drawable.crypto_omemo_verified
                tipKey = R.string.omemo_menu_authenticated
            }
            ChatFragment.MSGTYPE_OMEMO_UA -> {
                iconId = R.drawable.crypto_omemo_unverified
                tipKey = R.string.omemo_menu_unauthenticated
            }
            ChatFragment.MSGTYPE_OMEMO_UT -> {
                iconId = R.drawable.crypto_omemo_untrusted
                tipKey = R.string.omemo_menu_untrusted
            }
            ChatFragment.MSGTYPE_NORMAL,
            ChatFragment.MSGTYPE_MUC_NORMAL,
            -> {
                iconId = R.drawable.crypto_unsecure
                tipKey = R.string.menu_crypto_plain_text
            }
            // return if it is in none of above
            else ->
                return
        }
        runOnUiThread {
            mCryptoChoice.setIcon(iconId)
            mCryptoChoice.setTitle(tipKey)
        }
        // Timber.w("Omemo CryptMode change to: %s for %s", chatType, mDescriptor);
        notifyCryptoModeChanged(mChatType)
    }

    /**
     * Check and cache result of OMEMO is supported by current chatTransport;
     */
    fun updateOmemoSupport() {
        // Following few parameters must get initialized while in updateOmemoSupport()
        // Do not proceed if account is not log in, otherwise system crash
        val mChatTransport = activeChat!!.chatSession!!.currentChatTransport
                ?: return
        mDescriptor = mChatTransport.descriptor
        mConnection = mChatTransport.protocolProvider.connection
        if (mConnection == null || !mConnection!!.isAuthenticated) {
            omemoCapable[mDescriptor] = false
            return
        }

        // Seems like from FFR; OmemoManager can still be null after user is authenticated
        mOmemoManager = OmemoManager.getInstanceFor(mConnection)
        if (mOmemoManager == null) {
            omemoCapable[mDescriptor] = false
            return
        }

        // Execute in a new thread to avoid ANR with black screen when chat window is opened.
        object : Thread() {
            override fun run() {
                var serverCan = false
                var entityCan = false
                try {
                    val serverJid = mConnection!!.xmppServiceDomain
                    serverCan = (AndroidOmemoService.isOmemoInitSuccessful
                            || OmemoManager.serverSupportsOmemo(mConnection, serverJid))
                    entityCan = if (mDescriptor is ChatRoom) {
                        val muc = (mDescriptor as ChatRoom).getMultiUserChat()
                        mOmemoManager!!.multiUserChatSupportsOmemo(muc)
                    }
                    else {
                        // buddy online check may sometimes experience reply timeout; OMEMO obsoleted feature
                        // not a good idea to include PEP_NODE_DEVICE_LIST_NOTIFY as some siblings may
                        // support omemo encryption.
                        // boolean support = ServiceDiscoveryManager.getInstanceFor(connection)
                        //      .discoverInfo(contactJId).containsFeature(PEP_NODE_DEVICE_LIST_NOTIFY);

                        // Check based on present of keys on server - may have problem if buddy has old axolotf data
                        val contactJId = (mDescriptor as Contact).contactJid
                        mOmemoManager!!.contactSupportsOmemo(contactJId!!.asBareJid())

                        // cmeng - what about check from backend database entities table instead
                        // String usrID = ((Contact) mDescriptor).getAddress();
                        // entityCan = ((SQLiteOmemoStore) mOmemoStore).getContactNumTrustedKeys(usrID) > 0;
                    }
                } catch (e: XMPPException.XMPPErrorException) {
                    Timber.w("Exception in omemo support checking: %s", e.message)
                } catch (e: SmackException.NoResponseException) {
                    Timber.w("Exception in omemo support checking: %s", e.message)
                } catch (e: InterruptedException) {
                    Timber.w("Exception in omemo support checking: %s", e.message)
                } catch (e: SmackException.NotConnectedException) {
                    Timber.w("Exception in omemo support checking: %s", e.message)
                } catch (e: IOException) {
                    Timber.w("Exception in omemo support checking: %s", e.message)
                } catch (e: PubSubException.NotALeafNodeException) {
                    Timber.w("Exception in checking entity omemo support: %s", e.message)
                }

                // update omemoSupported in cache; revert to MSGTYPE_NORMAL if Default OMEMO not supported by session
                val omemoSupported = serverCan && entityCan
                omemoCapable[mDescriptor] = omemoSupported
                if (!omemoSupported && ChatFragment.MSGTYPE_OMEMO == mChatType) setChatType(ChatFragment.MSGTYPE_NORMAL)
            }
        }.start()
    }

    /**
     * Listens for show history popup link
     */
    class ShowHistoryLinkListener : ChatLinkClickedListener {
        /**
         * {@inheritDoc}
         */
        override fun chatLinkClicked(url: URI) {
            if (url.path == "/showHistoryPopupMenu") {
                // Display settings
                val ctx = aTalkApp.globalContext
                val settings = Intent(ctx, SettingsActivity::class.java)
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(settings)
            }
        }
    }

    /**
     * Callback when user clicks the omemo Authentication dialog's confirm/cancel button.
     *
     * @param allTrusted allTrusted state.
     * @param omemoDevices set of unTrusted devices
     */
    override fun onAuthenticate(allTrusted: Boolean, omemoDevices: Set<OmemoDevice>?) {
        if (allTrusted) {
            onOmemoAuthenticate(ChatFragment.MSGTYPE_OMEMO)
            activeChat!!.addMessage(mEntity!!, Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                getString(R.string.crypto_msg_OMEMO_SESSION_VERIFIED))
        }
        else {
            onOmemoAuthenticate(ChatFragment.MSGTYPE_OMEMO_UA)
            // activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
            //         "Undecided Omemo Identity: " + omemoDevices.toString());
        }
    }

    /**
     * @param chatType New chatType pending user verification action from omemo Authentication dialog
     * @see OmemoAuthenticateDialog
     */
    private fun onOmemoAuthenticate(chatType: Int) {
        // must update encryptionChoice and set ChatPanel to the new chatType
        encryptionChoice[mCurrentChatSessionId] = chatType
        activeChat!!.chatType = chatType

        // ChatActivity onResume (on OmemoAuthenticateDialog closed) will trigger this. but do it just in case
        setStatusOmemo(chatType)
        updateOmemoSupport()
    }

    /**
     * Chat Type change notification to all registered cryptoModeChangeListeners mainly to change
     * chatFragment background color for the new chatType; which is triggered from user cryptoMode selection.
     * When a listener for the mDescriptor key is not found, then the map is updated linking
     * current mDescriptor with the recent added listener with null key. null key is added when a
     * chatFragment is opened or became the primary selected.
     *
     * @param chatType The new chatType to broadcast to registered listener
     */
    private fun notifyCryptoModeChanged(chatType: Int) {
        // Timber.w(new Exception(), "notifyCryptoModeChanged %s", chatType);
        val listener: CryptoModeChangeListener?
        if (!cryptoModeChangeListeners.containsKey(mDescriptor)) {
            listener = cryptoModeChangeListeners[null]
            addCryptoModeListener(mDescriptor, listener)
        }
        else {
            listener = cryptoModeChangeListeners[mDescriptor]
        }
        listener?.onCryptoModeChange(chatType)
    }

    /**
     * Note: mDescriptor is always null when first triggers by chatFragment. It gets updated in notifyCryptoModeChanged()
     *
     * @param listener CryptoModeChangeListener added by chatFragment.
     * @see .notifyCryptoModeChanged
     */
    fun addCryptoModeListener(descriptor: Any?, listener: CryptoModeChangeListener?) {
        // Timber.w("CryptMode Listener added: %s <= %s", listener, mDescriptor);
        cryptoModeChangeListeners[descriptor] = listener
    }

    /**
     * @param chatType chatType see case
     */
    fun setChatType(chatType: Int) {
        // Return if the crypto menu option items are not initialized yet.
        if (mCryptoChoice == null) {
            return
        }
        runOnUiThread {
            when (chatType) {
                ChatFragment.MSGTYPE_NORMAL,
                ChatFragment.MSGTYPE_MUC_NORMAL,
                ->
                    onOptionsItemSelected(mNone)

                ChatFragment.MSGTYPE_OMEMO ->
                    // Do not emulate Omemo button press if mOmemoManager is null
                    if (mOmemoManager != null) {
                        onOptionsItemSelected(mOmemo)
                    }
            }
        }
    }

    companion object {
        /**
         * A map of the user selected chatType. The stored key is the chatSessionId. The information
         * is used to restore the last user selected encryption choice when a chat window is page slided in view.
         */
        private val encryptionChoice = LinkedHashMap<String?, Int>()

        /**
         * A cache map of the Descriptor and its OmemoSupport capability. The Descriptor can be ChatRoom or Contact
         */
        private val omemoCapable = LinkedHashMap<Any?, Boolean>()

        /**
         * A cache map of the Descriptor and its CryptoModeChangeListener. Need this as listener is added only
         * when the chatFragment is launched. Slide pages does not get updated.
         * ChatType change event is sent to CryptoModeChangeListener to update chatFragment background colour:
         */
        private val cryptoModeChangeListeners = LinkedHashMap<Any?, CryptoModeChangeListener?>()

        /**
         * Reset the encryption choice for the specified chatSessionId.
         * Mainly use by ChatSessionFragement; to re-enable the chat Session for UI when it is selected again
         *
         * @param chatSessionId chat session Uuid
         */
        fun resetEncryptionChoice(chatSessionId: String?) {
            if (TextUtils.isEmpty(chatSessionId)) {
                encryptionChoice.clear()
            }
            else {
                encryptionChoice.remove(chatSessionId)
            }
        }
    }
}