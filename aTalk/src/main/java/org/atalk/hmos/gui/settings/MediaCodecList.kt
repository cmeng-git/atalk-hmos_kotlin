/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.ThemeHelper
import org.atalk.impl.neomedia.codec.video.CodecInfo
import org.atalk.service.osgi.OSGiActivity

/**
 * Activity that lists video `MediaCodec`s available in the system.
 *
 * Meaning of the colors:<br></br>
 * * blue - codec will be used in call<br></br>
 * * white / black - one of the codecs for particular media type, but it won't be used
 * as there is another codec before it on the list<br></br>
 * * grey500 - codec is banned and won't be used<br></br>
 *
 * Click on codec to toggle it's banned state. Changes are not persistent between
 * aTalk restarts so restarting aTalk restores default values.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class MediaCodecList : OSGiActivity(), AdapterView.OnItemClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.list_layout)
        val list = findViewById<ListView>(R.id.list)
        list.adapter = MediaCodecAdapter()
        list.onItemClickListener = this
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val adapter = parent.adapter as MediaCodecAdapter
        val codec = adapter.getItem(position) as CodecInfo

        // Toggle codec banned state
        codec.isBanned = !codec.isBanned
        adapter.notifyDataSetChanged()
    }

    internal inner class MediaCodecAdapter : BaseAdapter() {
        private val codecs = ArrayList(CodecInfo.supportedCodecs)

        override fun getCount(): Int {
            return codecs.size
        }

        override fun getItem(position: Int): Any {
            return codecs[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var row = convertView as TextView?
            if (row == null) {
                row = layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
            }
            row.textSize = 15f
            val codec = codecs[position]
            val codecStr = codec.toString()
            row.text = codecStr
            var color = if (codec.isBanned) R.color.grey500 else R.color.textColorWhite
            if (ThemeHelper.isAppTheme(ThemeHelper.Theme.LIGHT)) {
                color = if (codec.isBanned) R.color.grey500 else R.color.textColorBlack
            }
            if (codec.isNominated) {
                color = R.color.blue
            }
            row.setTextColor(resources.getColor(color, null))
            return row
        }
    }
}