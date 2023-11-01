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
package org.atalk.hmos.gui.chatroomslist

import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiDialogFragment
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.muc.RoomInfo
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber

/**
 * This fragment dialog shows the chatRoom information retrieve from the server
 *
 * @author Eng Chong Meng
 */
class ChatRoomInfoDialog : OSGiDialogFragment() {
    private lateinit var contentView: View
    private var mChatRoomWrapper: ChatRoomWrapper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (dialog != null) dialog!!.setTitle(R.string.service_gui_CHATROOM_INFO)
        contentView = inflater.inflate(R.layout.chatroom_info, container, false)
        val buttonOk = contentView.findViewById<Button>(R.id.button_ok)
        buttonOk.setOnClickListener { v: View? -> dismiss() }
        getRoomInfo().execute()

        // setCancelable(false);
        return contentView
    }

    /**
     * Retrieve the chatRoom info from server and populate the fragment with the available information
     */
    private open inner class getRoomInfo : AsyncTask<Void?, Void?, RoomInfo?>() {
        var errMsg: String? = null
        override fun onPreExecute() {}
        override fun doInBackground(vararg params: Void?): RoomInfo? {
            val crpWrapper = mChatRoomWrapper!!.parentProvider
            if (crpWrapper != null) {
                val pps = crpWrapper.protocolProvider
                val entityBareJid = mChatRoomWrapper!!.chatRoom!!.getIdentifier()
                val mucManager = MultiUserChatManager.getInstanceFor(pps.connection)
                errMsg = try {
                    return mucManager.getRoomInfo(entityBareJid)
                } catch (e: SmackException.NoResponseException) {
                    e.message
                } catch (e: SmackException.NotConnectedException) {
                    e.message
                } catch (e: InterruptedException) {
                    e.message
                } catch (e: XMPPException.XMPPErrorException) {
                    val descriptiveText = e.stanzaError.descriptiveText + "\n"
                    descriptiveText + e.message
                }
            }
            return null
        }

        override fun onPostExecute(chatRoomInfo: RoomInfo?) {
            val emptyString = ""
            super.onPostExecute(chatRoomInfo)
            if (chatRoomInfo != null) {
                var textView = contentView.findViewById<TextView>(R.id.roominfo_name)
                textView.text = chatRoomInfo.name
                textView = contentView.findViewById(R.id.roominfo_subject)
                textView.text = toString(chatRoomInfo.subject, emptyString)
                textView = contentView.findViewById(R.id.roominfo_description)
                textView.text = toString(chatRoomInfo.description, mChatRoomWrapper!!.bookmarkName!!)
                textView = contentView.findViewById(R.id.roominfo_occupants)
                var count = chatRoomInfo.occupantsCount
                if (count == -1) {
                    val occupants = mChatRoomWrapper!!.chatRoom!!.getMembers()
                    count = occupants.size
                }
                textView.text = toValue(count, emptyString)
                textView = contentView.findViewById(R.id.maxhistoryfetch)
                textView.text = toValue(chatRoomInfo.maxHistoryFetch,
                        getString(R.string.service_gui_INFO_NOT_SPECIFIED))
                textView = contentView.findViewById(R.id.roominfo_contactjid)
                try {
                    val contactJids = chatRoomInfo.contactJids
                    if (contactJids.isNotEmpty()) textView.text = contactJids[0]
                } catch (e: NullPointerException) {
                    Timber.e("Contact Jids exception: %s", e.message)
                }
                textView = contentView.findViewById(R.id.roominfo_lang)
                textView.text = toString(chatRoomInfo.lang, emptyString)
                textView = contentView.findViewById(R.id.roominfo_ldapgroup)
                textView.text = toString(chatRoomInfo.ldapGroup, emptyString)
                var cbox = contentView.findViewById<CheckBox>(R.id.muc_membersonly)
                cbox.isChecked = chatRoomInfo.isMembersOnly
                cbox = contentView.findViewById(R.id.muc_nonanonymous)
                cbox.isChecked = chatRoomInfo.isNonanonymous
                cbox = contentView.findViewById(R.id.muc_persistent)
                cbox.isChecked = chatRoomInfo.isPersistent
                cbox = contentView.findViewById(R.id.muc_passwordprotected)
                cbox.isChecked = chatRoomInfo.isPasswordProtected
                cbox = contentView.findViewById(R.id.muc_moderated)
                cbox.isChecked = chatRoomInfo.isModerated
                cbox = contentView.findViewById(R.id.room_subject_modifiable)
                cbox.isChecked = toBoolean(chatRoomInfo.isSubjectModifiable)
            } else {
                var textView = contentView.findViewById<TextView>(R.id.roominfo_name)
                textView.text = XmppStringUtils.parseLocalpart(mChatRoomWrapper!!.chatRoomID)
                textView = contentView.findViewById(R.id.roominfo_subject)
                // Must not use getResources.getColor()
                textView.setTextColor(Color.RED)
                textView.text = errMsg
            }
        }

        /**
         * Return String value of the integer value
         *
         * @param value Integer
         * @param defaultValue return default string if int == -1
         * @return String value of the specified Integer value
         */
        private fun toValue(value: Int, defaultValue: String): String {
            return if (value != -1) value.toString() else defaultValue
        }

        /**
         * Return string if not null or default
         *
         * @param text test String
         * @param defaultValue return default string
         * @return text if not null else defaultValue
         */
        private fun toString(text: String?, defaultValue: String): String {
            return text ?: defaultValue
        }

        /**
         * Return Boolean state if not null else false
         *
         * @param state Boolean state
         * @return Boolean value if not null else false
         */
        private fun toBoolean(state: Boolean?): Boolean {
            return state ?: false
        }
    }

    companion object {
        fun newInstance(chatRoomWrapper: ChatRoomWrapper?): ChatRoomInfoDialog {
            val dialog = ChatRoomInfoDialog()
            dialog.mChatRoomWrapper = chatRoomWrapper
            return dialog
        }
    }
}