package org.atalk.hmos.gui.call.telephony

import android.content.Context
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import org.atalk.hmos.R
import java.util.regex.Pattern

class RecipientAdapter(private val context: Context) : BaseAdapter(), Filterable {
    private var recipients: List<RecipientSelectView.Recipient?>? = null
    private var highlight: String? = null
    private var showAdvancedInfo = false
    fun setRecipients(recipients: List<RecipientSelectView.Recipient?>?) {
        this.recipients = recipients
        notifyDataSetChanged()
    }

    fun setHighlight(highlight: String?) {
        this.highlight = highlight
    }

    override fun getCount(): Int {
        return if (recipients == null) 0 else recipients!!.size
    }

    override fun getItem(position: Int): RecipientSelectView.Recipient? {
        return if (recipients == null) null else recipients!![position]!!
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var iView = view
        if (iView == null) {
            iView = newView(parent)
        }
        val recipient = getItem(position)
        bindView(iView, recipient!!)
        return iView
    }

    private fun newView(parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.recipient_dropdown_item, parent, false)
        val holder = RecipientTokenHolder(view)
        view.tag = holder
        return view
    }

    private fun bindView(view: View, recipient: RecipientSelectView.Recipient) {
        val holder = view.tag as RecipientTokenHolder
        holder.name.text = highlightText(recipient.getDisplayNameOrUnknown(context))
        val address = recipient.address.getAddress()
        holder.phone.text = highlightText(address!!)
        setContactPhotoOrPlaceholder(context, holder.photo, recipient)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults? {
                if (recipients == null) {
                    return null
                }
                val result = FilterResults()
                result.values = recipients
                result.count = recipients!!.size
                return result
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                notifyDataSetChanged()
            }
        }
    }

    fun setShowAdvancedInfo(showAdvancedInfo: Boolean) {
        this.showAdvancedInfo = showAdvancedInfo
    }

    private class RecipientTokenHolder internal constructor(view: View) {
        val name: TextView
        val phone: TextView
        val photo: ImageView

        init {
            name = view.findViewById(R.id.text1)
            phone = view.findViewById(R.id.text2)
            photo = view.findViewById(R.id.contact_photo)
        }
    }

    private fun highlightText(text: String): Spannable {
        val highlightedSpannable = Spannable.Factory.getInstance().newSpannable(text)
        if (highlight == null) {
            return highlightedSpannable
        }
        val pattern = Pattern.compile(highlight!!, Pattern.LITERAL or Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            highlightedSpannable.setSpan(
                    ForegroundColorSpan(context.resources.getColor(android.R.color.holo_blue_light, null)),
                    matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return highlightedSpannable
    }

    companion object {
        fun setContactPhotoOrPlaceholder(context: Context?, imageView: ImageView?, recipient: RecipientSelectView.Recipient?) {
//        ContactPicture.getContactPictureLoader(context).loadContactPicture(address, imageView);
        }
    }
}