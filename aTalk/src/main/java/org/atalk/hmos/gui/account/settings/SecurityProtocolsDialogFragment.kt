/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import net.java.sip.communicator.service.protocol.SecurityAccountRegistration
import org.atalk.hmos.R
import org.atalk.hmos.gui.widgets.TouchInterceptor
import java.io.Serializable

/**
 * The dialog that displays a list of security protocols in [SecurityActivity].
 * It allows user to enable/disable each protocol and set their priority.
 */
class SecurityProtocolsDialogFragment : DialogFragment() {
    /**
     * The list model for the protocols
     */
    private lateinit var mProtocolsAdapter: ProtocolsAdapter

    /**
     * The listener that will be notified when this dialog is closed
     */
    private lateinit var mListener: DialogClosedListener
    private lateinit var mActivity: AppCompatActivity

    /**
     * Flag indicating if there have been any changes made
     */
    private var hasChanges = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mActivity = context as AppCompatActivity
        mListener = context as DialogClosedListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mProtocolsAdapter = if (savedInstanceState == null) {
            ProtocolsAdapter(arguments!![ARG_ENCRYPTION] as Map<String, Int>,
                    arguments!![ARG_ENCRYPTION_STATUS] as MutableMap<String, Boolean>)
        } else {
            ProtocolsAdapter(savedInstanceState.getStringArray(STATE_ENCRYPTION) as Array<String>,
                    savedInstanceState[STATE_ENCRYPTION_STATUS] as MutableMap<String, Boolean>)
        }
        // Get the layout inflater
        val inflater = requireActivity().layoutInflater
        val contentView = inflater.inflate(R.layout.sec_protocols_dialog, null)

        // Builds the dialog
        var builder = AlertDialog.Builder(mActivity)
        builder = builder.setTitle(R.string.service_gui_SEC_PROTOCOLS_TITLE)
        builder.setView(contentView).setPositiveButton(R.string.service_gui_SEC_PROTOCOLS_OK) { dialog: DialogInterface?, i: Int ->
            hasChanges = true
            dismiss()
        }.setNegativeButton(R.string.service_gui_SEC_PROTOCOLS_CANCEL) { dialog: DialogInterface?, i: Int ->
            hasChanges = false
            dismiss()
        }
        val lv = contentView.findViewById<TouchInterceptor>(android.R.id.list)
        lv.adapter = mProtocolsAdapter
        lv.setDropListener(mProtocolsAdapter)
        return builder.create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(STATE_ENCRYPTION, mProtocolsAdapter.mEncryption)
        outState.putSerializable(STATE_ENCRYPTION_STATUS, mProtocolsAdapter.mEncryptionStatus as Serializable?)
    }

    /**
     * Commits the changes into given [SecurityAccountRegistration]
     *
     * @param securityReg the registration object that will hold new security preferences
     */
    fun commit(securityReg: SecurityAccountRegistration) {
        val protocol = HashMap<String, Int>()
        for (i in mProtocolsAdapter.mEncryption.indices) {
            protocol[mProtocolsAdapter.mEncryption[i]] = i
        }

        securityReg.setEncryptionProtocol(protocol)
        securityReg.setEncryptionProtocolStatus(mProtocolsAdapter.mEncryptionStatus)
    }

    /**
     * The interface that will be notified when this dialog is closed
     */
    interface DialogClosedListener {
        fun onDialogClosed(dialog: SecurityProtocolsDialogFragment)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mListener.onDialogClosed(this)
    }

    /**
     * Flag indicating whether any changes have been done to security config
     *
     * @return `true` if any changes have been made
     */
    fun hasChanges(): Boolean {
        return hasChanges
    }

    /**
     * List model for security protocols and their priorities
     */
    internal inner class ProtocolsAdapter : BaseAdapter, TouchInterceptor.DropListener {
        /**
         * The array of encryption protocol names and their on/off status in mEncryptionStatus
         */
        var mEncryption: Array<String>
        var mEncryptionStatus: MutableMap<String, Boolean>

        /**
         * Creates a new instance of [ProtocolsAdapter]
         *
         * @param encryption reference copy
         * @param encryptionStatus reference copy
         */
        constructor(encryption: Map<String, Int>, encryptionStatus: MutableMap<String, Boolean>) {
            mEncryption = SecurityAccountRegistration.loadEncryptionProtocol(encryption, encryptionStatus)[0] as Array<String>
            // Fill missing entries
            for (enc in encryption.keys) {
                if (!encryptionStatus.containsKey(enc))
                    encryptionStatus[enc] = false
            }
            mEncryptionStatus = encryptionStatus
        }

        /**
         * Creates new instance of [ProtocolsAdapter]
         *
         * @param encryption reference copy
         * @param encryptionStatus reference copy
         */
        constructor(encryption: Array<String>, encryptionStatus: MutableMap<String, Boolean>) {
            mEncryption = encryption
            mEncryptionStatus = encryptionStatus
        }

        override fun getCount(): Int {
            return mEncryption.size
        }

        override fun getItem(i: Int): Any {
            return mEncryption[i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
            val encryption = getItem(i) as String
            val li = requireActivity().layoutInflater
            val v = li.inflate(R.layout.encoding_item, viewGroup, false)
            val tv = v.findViewById<TextView>(android.R.id.text1)
            tv.text = encryption
            val cb = v.findViewById<CheckBox>(android.R.id.checkbox)
            cb.isChecked = mEncryptionStatus.containsKey(encryption) && mEncryptionStatus[encryption]!!

            cb.setOnCheckedChangeListener { cb1: CompoundButton?, state: Boolean ->
                mEncryptionStatus[encryption] = state
                hasChanges = true
            }
            return v
        }

        /**
         * Implements [TouchInterceptor.DropListener]. Method swaps protocols priorities.
         *
         * @param from source item index
         * @param to destination item index
         */
        override fun drop(from: Int, to: Int) {
            hasChanges = true
            val swap = mEncryption[to]
            mEncryption[to] = mEncryption[from]
            mEncryption[from] = swap

            mActivity.runOnUiThread {
                notifyDataSetChanged()
            }
        }
    }

    companion object {
        /**
         * The encryption protocols managed by this dialog.
         */
        // public static final String[] encryptionProtocols = {"ZRTP", "SDES"};
        const val ARG_ENCRYPTION = "arg_encryption"
        const val ARG_ENCRYPTION_STATUS = "arg_encryption_status"
        const val STATE_ENCRYPTION = "state_encryption"
        const val STATE_ENCRYPTION_STATUS = "state_encryption_status"
    }
}