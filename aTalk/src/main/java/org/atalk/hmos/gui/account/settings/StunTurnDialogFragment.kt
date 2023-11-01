/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.DialogFragment
import inet.ipaddr.HostName
import inet.ipaddr.HostNameException
import net.java.sip.communicator.service.protocol.StunServerDescriptor
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.ViewUtil
import timber.log.Timber

/**
 * The dialog fragment that allows user to edit the STUN server descriptor.
 * @link https://github.com/MilanKral/atalk-android/commit/d61d5165dda4d290280ebb3e93075e8846e255ad
 * Enhance TURN with TCP, TLS, DTLS transport
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
class StunTurnDialogFragment : DialogFragment() {
    /**
     * The edited descriptor
     */
    private var descriptor: StunServerDescriptor? = null

    /**
     * Parent adapter that will be notified about any changes to the descriptor
     */
    private var parentAdapter: StunServerAdapter? = null
    private var turnPassword: EditText? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Get the layout inflater
        val inflater = requireActivity().layoutInflater
        val contentView = inflater.inflate(R.layout.stun_turn_dialog, null)
        var builder = AlertDialog.Builder(activity!!)
        builder = builder.setTitle(R.string.service_gui_STUN_TURN_SERVER)
        builder = builder.setView(contentView)
                .setPositiveButton(R.string.service_gui_SAVE, null)
                .setNeutralButton(R.string.service_gui_SERVERS_LIST_CANCEL, null)
        if (descriptor != null) {
            builder = builder.setNegativeButton(R.string.service_gui_SERVERS_LIST_REMOVE, null)
        }

        val ipAddress = contentView.findViewById<EditText>(R.id.ipAddress)
        val ipPort = contentView.findViewById<EditText>(R.id.serverPort)
        val turnUser = contentView.findViewById<EditText>(R.id.usernameField)
        turnPassword = contentView.findViewById(R.id.passwordField)
        val turnProtocolSpinner = contentView.findViewById<Spinner>(R.id.TURNProtocol)
        val adapterType = ArrayAdapter.createFromResource(activity!!,
                R.array.TURN_protocol, R.layout.simple_spinner_item)
        adapterType.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        turnProtocolSpinner.adapter = adapterType
        val turnSetting = contentView.findViewById<View>(R.id.turnSetting)
        val useTurnCbox = contentView.findViewById<CheckBox>(R.id.useTurnCheckbox)
        useTurnCbox.setOnCheckedChangeListener { cButton: CompoundButton?, b: Boolean -> turnSetting.visibility = if (b) View.VISIBLE else View.GONE }
        val showPassword = contentView.findViewById<CheckBox>(R.id.show_password)
        showPassword.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(turnPassword, isChecked) }
        if (descriptor != null) {
            ipAddress.setText(descriptor!!.address)
            ipPort.setText(descriptor!!.port.toString())
            useTurnCbox.isChecked = descriptor!!.isTurnSupported
            turnUser.setText(String(descriptor!!.getUsername()!!))
            turnPassword!!.setText(String(descriptor!!.getPassword()!!))
            val protocolText = convertTURNProtocolTypeToText(descriptor!!.protocol!!)
            val protocolArray = resources.getStringArray(R.array.TURN_protocol)
            for (i in protocolArray.indices) {
                if (protocolText == protocolArray[i]) {
                    turnProtocolSpinner.setSelection(i)
                }
            }
        } else {
            ipPort.setText(JabberAccountID.DEFAULT_STUN_PORT)
        }

        turnSetting.visibility = if (useTurnCbox.isChecked) View.VISIBLE else View.GONE
        val dialog = builder.create()
        dialog.setOnShowListener { dialogInterface: DialogInterface? ->
            val pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            pos.setOnClickListener { view: View? -> if (saveChanges()) dismiss() }
            val neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            neg?.setOnClickListener { view: View? ->
                parentAdapter!!.removeServer(descriptor!!)
                dismiss()
            }
        }
        return dialog
    }

    /**
     * Save the changes to the edited descriptor and notifies parent about the changes.
     * Returns `true` if all fields are correct.
     *
     * @return `true` if all field are correct and changes have been submitted to the parent adapter.
     */
    private fun saveChanges(): Boolean {
        val dialog = dialog
        val useTurn = (dialog!!.findViewById<View>(R.id.useTurnCheckbox) as CheckBox).isChecked
        val ipAddress = ViewUtil.toString(dialog.findViewById(R.id.ipAddress))
        val portStr = ViewUtil.toString(dialog.findViewById(R.id.serverPort))
        val turnUser = ViewUtil.toString(dialog.findViewById(R.id.usernameField))
        val password = ViewUtil.toString(dialog.findViewById(R.id.passwordField))
        val protocolSpinner = dialog.findViewById<Spinner>(R.id.TURNProtocol)
        val protocol = convertTURNProtocolTextToType(protocolSpinner.selectedItem as String)
        if (ipAddress == null || !isValidIpAddress(ipAddress) || portStr == null) {
            aTalkApp.showToastMessage(R.string.service_gui_INVALID_ADDRESS, "$ipAddress:$portStr")
            return false
        }
        val port = portStr.toInt()

        // Create descriptor if new entry
        if (descriptor == null) {
            descriptor = StunServerDescriptor(ipAddress, port, useTurn, turnUser, password, protocol)
            parentAdapter!!.addServer(descriptor!!)
        } else {
            descriptor!!.address = ipAddress
            descriptor!!.port = port
            descriptor!!.isTurnSupported = useTurn
            descriptor!!.setUsername(turnUser)
            descriptor!!.setPassword(password)
            descriptor!!.protocol = protocol
            parentAdapter!!.updateServer(descriptor)
        }
        return true
    }

    companion object {
        /**
         * Creates new instance of [StunTurnDialogFragment]
         *
         * @param parentAdapter the parent adapter
         * @param descriptor the descriptor to edit or `null` if new one shall be created
         */
        fun newInstance(parentAdapter: StunServerAdapter?, descriptor: StunServerDescriptor?): StunTurnDialogFragment {
            if (parentAdapter == null) throw NullPointerException()
            val dialogFragmentST = StunTurnDialogFragment()
            dialogFragmentST.parentAdapter = parentAdapter
            dialogFragmentST.descriptor = descriptor
            return dialogFragmentST
        }

        fun isValidIpAddress(hostStr: String?): Boolean {
            val host = HostName(hostStr)
            try {
                // triggers exception for invalid
                host.validate()
                if (host.isAddress) {
                    val address = host.asAddress()
                    Timber.d("%s address: %s", address.ipVersion, address)
                } else {
                    Timber.d("Host name: %s", host)
                }
            } catch (e: HostNameException) {
                return false
            }
            return true
        }

        private fun convertTURNProtocolTypeToText(type: String): String {
            return when (type) {
                StunServerDescriptor.PROTOCOL_UDP -> "UDP"
                StunServerDescriptor.PROTOCOL_TCP -> "TCP"
                StunServerDescriptor.PROTOCOL_DTLS -> "DTLS"
                StunServerDescriptor.PROTOCOL_TLS -> "TLS"
                else -> throw IllegalArgumentException("unknown TURN protocol")
            }
        }

        private fun convertTURNProtocolTextToType(protocolText: String): String {
            return when (protocolText) {
                "UDP" -> StunServerDescriptor.PROTOCOL_UDP
                "TCP" -> StunServerDescriptor.PROTOCOL_TCP
                "DTLS" -> StunServerDescriptor.PROTOCOL_DTLS
                "TLS" -> StunServerDescriptor.PROTOCOL_TLS
                else -> throw IllegalArgumentException("unknown TURN protocol")
            }
        }
    }
}