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
package org.atalk.crypto.otr

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import net.java.otr4j.session.Session
import net.java.otr4j.session.SessionStatus
import net.java.sip.communicator.plugin.otr.OtrActivator
import net.java.sip.communicator.plugin.otr.OtrContactManager
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiDialogFragment

/**
 * A special [] that controls the switching of OTRv3 outgoing
 * sessions in case the remote party is logged in multiple times.
 *
 * @author Eng Chong Meng
 */
class OTRv3OutgoingSessionSwitcher : OSGiDialogFragment(), AdapterView.OnItemClickListener, View.OnClickListener {
    private val otrSessionLabels = ArrayList<String?>()

    // session label => session
    private val outgoingSessions = HashMap<String, Session>()

    // session label => icon
    private val sessionIcons = HashMap<String, Int?>()
    private var mSelectedSession: Session? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dialog!!.setTitle("OTR Sessions")
        val contentView = inflater.inflate(R.layout.otr_session_list, container, false)
        val buttonOk = contentView.findViewById<Button>(R.id.btn_OK)
        buttonOk.setOnClickListener(this)
        val buttonCancel = contentView.findViewById<Button>(R.id.btn_Cancel)
        buttonCancel.setOnClickListener(this)
        val listview = contentView.findViewById<ListView>(R.id.otrSessionListView)
        val list = buildSessionList(mOtrContact)
        val adapter = SessionArrayAdapter(context!!, list)
        listview.adapter = adapter
        listview.onItemClickListener = this
        isCancelable = false
        return contentView
    }

    private inner class SessionArrayAdapter(private val context: Context, private val values: ArrayList<String?>) : ArrayAdapter<String?>(context, -1, values) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var iConvertView = convertView
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            if (iConvertView == null) {
                iConvertView = inflater.inflate(R.layout.otr_session_list_row, parent, false)
            }
            val textView = iConvertView!!.findViewById<TextView>(R.id.otr_session)
            val imageView = iConvertView.findViewById<ImageView>(R.id.otr_icon)
            val label = values[position]
            textView.text = label
            val icon = sessionIcons[label]
            if (icon != null) imageView.setImageResource(icon)
            return iConvertView
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }

    /**
     * Builds the view list used for switching between outgoing OTRv3 Sessions in
     * case the remote party is logged in multiple locations
     *
     * @param otrContact the contact which is logged in multiple locations
     */
    private fun buildSessionList(otrContact: OtrContactManager.OtrContact?): ArrayList<String?> {
        var label: String
        val multipleInstances = OtrActivator.scOtrEngine.getSessionInstances(otrContact)
        var index = 0
        for (session in multipleInstances) {
            index++
            val imageIcon = when (session.getSessionStatus(session.receiverInstanceTag)) {
                SessionStatus.ENCRYPTED -> {
                    val pubKey = session.getRemotePublicKey(session.receiverInstanceTag)
                    val fingerprint = OtrActivator.scOtrKeyManager.getFingerprintFromPublicKey(pubKey)
                    if (OtrActivator.scOtrKeyManager.isVerified(otrContact!!.contact, fingerprint)) R.drawable.crypto_otr_verified_grey else R.drawable.crypto_otr_unverified_grey
                }
                SessionStatus.FINISHED -> R.drawable.crypto_otr_finished_grey
                SessionStatus.PLAINTEXT -> R.drawable.crypto_otr_unsecure_grey
            }
            label = "Session $index"
            otrSessionLabels.add(label)
            outgoingSessions[label] = session
            sessionIcons[label] = imageIcon
        }
        return otrSessionLabels
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        if (view.isSelected) {
            view.setBackgroundResource(R.drawable.color_blue_gradient)
        } else {
            view.setBackgroundResource(R.drawable.list_selector_state)
        }
        if (position < otrSessionLabels.size) {
            mSelectedSession = outgoingSessions[otrSessionLabels[position]]
        }
    }

    /**
     * Method fired when the ok button is clicked.
     *
     * @param v ok button's `View`.
     */
    override fun onClick(v: View) {
        if (v.id == R.id.btn_OK) {
            if (mSelectedSession != null) OtrActivator.scOtrEngine.setOutgoingSession(mOtrContact, mSelectedSession!!.receiverInstanceTag)
        }
        dismiss()
    }

    companion object {
        /**
         * The `Contact` that belongs to OTR session handled by this instance.
         */
        private var mOtrContact: OtrContactManager.OtrContact? = null
        fun newInstance(contact: OtrContactManager.OtrContact?): OTRv3OutgoingSessionSwitcher {
            mOtrContact = contact
            val args = Bundle()
            val dialog = OTRv3OutgoingSessionSwitcher()
            dialog.setArguments(args)
            return dialog
        }
    }
}