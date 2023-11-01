/*
 * aTalk, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.hmos.gui.chat.conference

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.protocol.AdHocChatRoomInvitation
import net.java.sip.communicator.service.protocol.ChatRoomInvitation
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.ViewUtil.toString
import org.jxmpp.jid.EntityJid

/**
 * The dialog that pops up when a chat room invitation is received.
 *
 * @author Eng Chong Meng
 */
class InvitationReceivedDialog : Dialog {
    /**
     * The `MultiUserChatManager` is the one that deals with invitation events.
     */
    private var mMultiUserChatManager: ConferenceChatManager

    /**
     * The operation set that would handle the rejection if the user choose to reject the
     * invitation.
     */
    private lateinit var mMultiUserChatOpSet: OperationSetMultiUserChat

    /**
     * The operation set that would handle the rejection if the user choose to reject the
     * invitation, in case of an `AdHocChatRoom`.
     */
    private var mMultiUserChatAdHocOpSet: OperationSetAdHocMultiUserChat? = null

    /**
     * The `ChatRoomInvitation` for which this dialog is.
     */
    private lateinit var mInvitation: ChatRoomInvitation

    /**
     * The `AdHocChatRoomInvitation` for which this dialog is, in case of an
     * `AdHocChatRoom`.
     */
    private var mInvitationAdHoc: AdHocChatRoomInvitation? = null
    private lateinit var mContext: Context
    private var reasonTextArea: EditText? = null
    private var mInviter: EntityJid
    private var mChatRoomName: String
    private var mReason: String?

    /**
     * Constructs the `ChatInviteDialog`.
     *
     * // @param multiUserChatManager
     * the `MultiUserChatManager` is the one that deals with invitation events
     *
     * @param multiUserChatOpSet the operation set that would handle the rejection if the user choose to reject the
     * invitation
     * @param invitation the invitation that this dialog represents
     */
    constructor(context: Context, multiUserChatManager: ConferenceChatManager,
                multiUserChatOpSet: OperationSetMultiUserChat, invitation: ChatRoomInvitation) : super(context) {
        mContext = context
        mMultiUserChatManager = multiUserChatManager
        mMultiUserChatOpSet = multiUserChatOpSet
        mInvitation = invitation
        mInviter = invitation.getInviter()
        mChatRoomName = invitation.getTargetChatRoom().getName()
        mReason = mInvitation.getReason()
    }

    /**
     * Constructs the `ChatInviteDialog`, in case of an `AdHocChatRoom`.
     *
     * // @param multiUserChatManager
     * the `MultiUserChatManager` is the one that deals with invitation events
     *
     * @param multiUserChatAdHocOpSet the operation set that would handle the rejection if the user choose to reject the
     * invitation
     * @param invitationAdHoc the invitation that this dialog represents
     */
    constructor(mContext: Context?, multiUserChatManager: ConferenceChatManager,
                multiUserChatAdHocOpSet: OperationSetAdHocMultiUserChat?, invitationAdHoc: AdHocChatRoomInvitation) : super(mContext!!) {
        mMultiUserChatManager = multiUserChatManager
        mMultiUserChatAdHocOpSet = multiUserChatAdHocOpSet
        mInvitationAdHoc = invitationAdHoc
        mInviter = invitationAdHoc.getInviter()
        mChatRoomName = invitationAdHoc.getTargetAdHocChatRoom().getName()
        mReason = invitationAdHoc.getReason()
    }

    /**
     * {@inheritDoc}
     */
    public override fun onCreate(savedInstanceState: Bundle) {
        setContentView(R.layout.muc_invitation_received_dialog)
        setTitle(mContext.getString(R.string.service_gui_INVITATION_RECEIVED))
        val infoTextArea = findViewById<TextView>(R.id.textMsgView)
        infoTextArea.text = mContext.getString(R.string.service_gui_INVITATION_RECEIVED_MSG,
                mInviter, mChatRoomName)
        val textInvitation = findViewById<EditText>(R.id.textInvitation)
        if (!TextUtils.isEmpty(mReason)) {
            textInvitation.setText(mReason)
        } else {
            textInvitation.setText("")
        }
        reasonTextArea = findViewById(R.id.rejectReasonTextArea)
        val mAcceptButton = findViewById<Button>(R.id.button_Accept)
        mAcceptButton.setOnClickListener {
            dismiss()
            onAcceptClicked()
        }
        val mRejectButton = findViewById<Button>(R.id.button_Reject)
        mRejectButton.setOnClickListener {
            dismiss()
            onRejectClicked()
        }
        val mIgnoreButton = findViewById<Button>(R.id.button_Ignore)
        mIgnoreButton.setOnClickListener { dismiss() }
    }

    /**
     * Handles the `ActionEvent` triggered when one user clicks on one of the buttons.
     */
    private fun onAcceptClicked(): Boolean {
        if (mInvitationAdHoc == null) {
            MUCActivator.mucService.acceptInvitation(mInvitation)
        } else {
            try {
                mMultiUserChatManager.acceptInvitation(mInvitationAdHoc!!, mMultiUserChatAdHocOpSet)
            } catch (e1: OperationFailedException) {
                e1.printStackTrace()
            }
        }
        return true
    }

    private fun onRejectClicked() {
        val reasonField = toString(reasonTextArea)
        if (mMultiUserChatAdHocOpSet == null && mInvitationAdHoc == null) {
            try {
                MUCActivator.mucService.rejectInvitation(mMultiUserChatOpSet, mInvitation, reasonField)
            } catch (e: OperationFailedException) {
                e.printStackTrace()
            }
        }
        if (mMultiUserChatAdHocOpSet != null) mMultiUserChatManager.rejectInvitation(mMultiUserChatAdHocOpSet!!, mInvitationAdHoc, reasonField)
    }

    /**
     * Shows given error message as an alert.
     *
     * @param errorMessage the error message to show.
     */
    private fun showErrorMessage(errorMessage: String) {
        val ctx = aTalkApp.globalContext
        DialogActivity.showDialog(ctx, ctx.getString(R.string.service_gui_ERROR), errorMessage)
    }
}