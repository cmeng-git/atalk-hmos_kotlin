package org.atalk.hmos.gui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import org.atalk.hmos.R
import org.atalk.hmos.gui.chat.ChatActivity

/**
 * The `AttachOptionDialog` provides user with optional attachments.
 *
 * @author Eng Chong Meng
 */
class AttachOptionDialog(context: Context) : Dialog(context) {
    init {
        setTitle(R.string.service_gui_FILE_ATTACHMENT)
    }

    public override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attach_option_dialog)
        val mListView = findViewById<ListView>(R.id.attach_optionlist)
        val items = ArrayList(listOf(*AttachOptionItem.values()))
        val mAttachOptionAdapter = AttachOptionModeAdapter(R.layout.attach_option_child_row, items)
        mListView.adapter = mAttachOptionAdapter

        mListView.onItemClickListener = OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            val mSelectedItem = mAttachOptionAdapter.getItem(id.toInt())
            (context as ChatActivity).sendAttachment(mSelectedItem)
            closeDialog()
        }
    }

    fun closeDialog() {
        cancel()
    }

   inner class AttachOptionModeAdapter(var layoutResourceId: Int, var data: List<AttachOptionItem>)
        : ArrayAdapter<AttachOptionItem>(context, layoutResourceId, data) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var cView = convertView
            val holder: AttachOptionHolder

            if (cView == null) {
                cView = layoutInflater.inflate(layoutResourceId, parent, false)
                holder = AttachOptionHolder()
                holder.imgIcon = cView.findViewById(R.id.attachOption_icon)
                holder.txtTitle = cView.findViewById(R.id.attachOption_screenname)
                cView.tag = holder
            } else {
                holder = cView.tag as AttachOptionHolder
            }

            // AttachOptionItem item = data.get(position);
            holder.txtTitle!!.setText(getItem(position)!!.textId)
            holder.imgIcon!!.setImageResource(getItem(position)!!.iconId)
            return cView!!
        }
    }

    internal class AttachOptionHolder {
        var imgIcon: ImageView? = null
        var txtTitle: TextView? = null
    }
}