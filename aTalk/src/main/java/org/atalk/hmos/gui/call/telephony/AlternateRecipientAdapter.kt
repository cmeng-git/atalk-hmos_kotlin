package org.atalk.hmos.gui.call.telephony

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.atalk.hmos.R

class AlternateRecipientAdapter(private val context: Context, private val listener: AlternateRecipientListener) : BaseAdapter() {
    private var recipients: List<RecipientSelectView.Recipient?>? = null
    private var currentRecipient: RecipientSelectView.Recipient? = null
    private var showAdvancedInfo = false
    fun setCurrentRecipient(currentRecipient: RecipientSelectView.Recipient?) {
        this.currentRecipient = currentRecipient
    }

    fun setAlternateRecipientInfo(recipients: MutableList<RecipientSelectView.Recipient?>) {
        this.recipients = recipients
        val indexOfCurrentRecipient = recipients.indexOf(currentRecipient)
        if (indexOfCurrentRecipient >= 0) {
            currentRecipient = recipients[indexOfCurrentRecipient]
        }
        recipients.remove(currentRecipient)
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return if (recipients == null) {
            NUMBER_OF_FIXED_LIST_ITEMS
        } else recipients!!.size + NUMBER_OF_FIXED_LIST_ITEMS
    }

    override fun getItem(position: Int): RecipientSelectView.Recipient? {
        if (position == POSITION_HEADER_VIEW || position == POSITION_CURRENT_ADDRESS) {
            return currentRecipient!!
        }
        return if (recipients == null) null else getRecipientFromPosition(position)!!
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun getRecipientFromPosition(position: Int): RecipientSelectView.Recipient? {
        return recipients!![position - NUMBER_OF_FIXED_LIST_ITEMS]
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var iView = view
        if (iView == null) {
            iView = newView(parent)
        }
        val recipient = getItem(position)
        if (position == POSITION_HEADER_VIEW) {
            bindHeaderView(iView, recipient!!)
        } else {
            bindItemView(iView, recipient!!)
        }
        return iView
    }

    fun newView(parent: ViewGroup?): View {
        val view = LayoutInflater.from(context).inflate(R.layout.recipient_alternate_item, parent, false)
        val holder = RecipientTokenHolder(view)
        view.tag = holder
        return view
    }

    override fun isEnabled(position: Int): Boolean {
        return position != POSITION_HEADER_VIEW
    }

    fun bindHeaderView(view: View, recipient: RecipientSelectView.Recipient) {
        val holder = view.tag as RecipientTokenHolder
        holder.setShowAsHeader(true)
        holder.headerName.text = recipient.getNameOrUnknown(context)
        if (!TextUtils.isEmpty(recipient.addressLabel)) {
            holder.headerAddressLabel.text = recipient.addressLabel
            holder.headerAddressLabel.visibility = View.VISIBLE
        } else {
            holder.headerAddressLabel.visibility = View.GONE
        }
        holder.headerRemove.setOnClickListener { v: View? -> listener.onRecipientRemove(currentRecipient) }
    }

    fun bindItemView(view: View, recipient: RecipientSelectView.Recipient) {
        val holder = view.tag as RecipientTokenHolder
        holder.setShowAsHeader(false)
        val address = recipient.address.getAddress()
        holder.itemAddress.text = address
        if (!TextUtils.isEmpty(recipient.addressLabel)) {
            holder.itemAddressLabel.text = recipient.addressLabel
            holder.itemAddressLabel.visibility = View.VISIBLE
        } else {
            holder.itemAddressLabel.visibility = View.GONE
        }
        val isCurrent = currentRecipient === recipient
        holder.itemAddress.setTypeface(null, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
        holder.itemAddressLabel.setTypeface(null, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
        holder.layoutItem.setOnClickListener { v: View? -> listener.onRecipientChange(currentRecipient, recipient) }
    }

    fun setShowAdvancedInfo(showAdvancedInfo: Boolean) {
        this.showAdvancedInfo = showAdvancedInfo
    }

    private class RecipientTokenHolder(view: View) {
        val layoutHeader: View
        val layoutItem: View
        val headerName: TextView
        val headerAddressLabel: TextView
        val headerRemove: View
        val itemAddress: TextView
        val itemAddressLabel: TextView

        init {
            layoutHeader = view.findViewById(R.id.alternate_container_header)
            layoutItem = view.findViewById(R.id.alternate_container_item)
            headerName = view.findViewById(R.id.alternate_header_name)
            headerAddressLabel = view.findViewById(R.id.alternate_header_label)
            headerRemove = view.findViewById(R.id.alternate_remove)
            itemAddress = view.findViewById(R.id.alternate_address)
            itemAddressLabel = view.findViewById(R.id.alternate_address_label)
        }

        fun setShowAsHeader(isHeader: Boolean) {
            layoutHeader.visibility = if (isHeader) View.VISIBLE else View.GONE
            layoutItem.visibility = if (isHeader) View.GONE else View.VISIBLE
        }
    }

    interface AlternateRecipientListener {
        fun onRecipientRemove(currentRecipient: RecipientSelectView.Recipient?)
        fun onRecipientChange(currentRecipient: RecipientSelectView.Recipient?, alternateRecipient: RecipientSelectView.Recipient)
    }

    companion object {
        private const val NUMBER_OF_FIXED_LIST_ITEMS = 2
        private const val POSITION_HEADER_VIEW = 0
        private const val POSITION_CURRENT_ADDRESS = 1
    }
}