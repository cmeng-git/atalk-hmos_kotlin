package org.atalk.hmos.gui.account

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import net.java.sip.communicator.service.protocol.PresenceStatus
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.AndroidImageUtil

class StatusListAdapter(context: Context, resource: Int, objects: List<PresenceStatus?>) : ArrayAdapter<PresenceStatus?>(context, resource, objects) {
    private val inflater: LayoutInflater
    private var statusIconView: ImageView? = null

    /**
     * Creates new instance of [StatusListAdapter]
     *
     * @param objects [Iterator] for a set of [PresenceStatus]
     */
    init {
        inflater = LayoutInflater.from(context)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        // Retrieve views
        val statusItemView = convertView ?: inflater.inflate(R.layout.account_presence_status_row, parent, false)
        statusIconView = statusItemView.findViewById(R.id.presenceStatusIconView)
        val statusNameView = statusItemView.findViewById<TextView>(R.id.presenceStatusNameView)

        // Set status name
        val presenceStatus = getItem(position)
        val statusName = presenceStatus!!.statusName
        statusNameView.text = statusName

        // Set status icon
        val presenceIcon = AndroidImageUtil.bitmapFromBytes(presenceStatus.statusIcon)
        statusIconView!!.setImageBitmap(presenceIcon)
        return statusItemView
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }

    fun getStatusIcon(): Drawable {
        return statusIconView!!.drawable
    }
}