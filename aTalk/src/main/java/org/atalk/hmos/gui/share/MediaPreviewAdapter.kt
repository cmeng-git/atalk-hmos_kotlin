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
package org.atalk.hmos.gui.share

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import org.atalk.hmos.MyGlideApp
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.databinding.MediaPreviewBinding
import org.atalk.hmos.gui.chat.ChatActivity
import org.atalk.hmos.gui.share.MediaPreviewAdapter.MediaPreviewViewHolder
import org.atalk.persistance.FilePathHelper
import java.io.File

class MediaPreviewAdapter(private val mChatActivity: ChatActivity, private val viewHolder: ImageView) : RecyclerView.Adapter<MediaPreviewViewHolder>() {
    val attachments = ArrayList<Attachment>()

    private val layoutParams: LinearLayout.LayoutParams

    init {
        val width = aTalkApp.mDisplaySize.width
        layoutParams = LinearLayout.LayoutParams(width, width)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaPreviewViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = DataBindingUtil.inflate<MediaPreviewBinding>(layoutInflater, R.layout.media_preview, parent, false)
        return MediaPreviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaPreviewViewHolder, position: Int) {
        val attachment = attachments[position]
        val file = File(FilePathHelper.getFilePath(mChatActivity, attachment))
        MyGlideApp.loadImage(holder.binding.mediaPreviewItem, file, true)
        holder.binding.deleteButton.setOnClickListener { v: View? ->
            val pos = attachments.indexOf(attachment)
            attachments.removeAt(pos)
            notifyItemRemoved(pos)

            // update send button mode
            if (attachments.isEmpty()) mChatActivity.toggleInputMethod()
        }
        holder.binding.mediaPreviewItem.setOnClickListener { v: View? ->
            viewHolder.layoutParams = layoutParams
            MyGlideApp.loadImage(viewHolder, file, true)
        }
    }

    fun addMediaPreviews(attachments: Collection<Attachment>) {
        // mediaPreviews.clear(); // Do not remove any existing attachments in the mediaPreviews
        this.attachments.addAll(attachments)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return attachments.size
    }

    fun hasAttachments(): Boolean {
        return attachments.size > 0
    }

    fun clearPreviews() {
        attachments.clear()
    }

    class MediaPreviewViewHolder(val binding: MediaPreviewBinding) : RecyclerView.ViewHolder(binding.root)
}