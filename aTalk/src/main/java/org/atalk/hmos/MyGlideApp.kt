package org.atalk.hmos

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import org.atalk.persistance.FileBackend.isMediaFile
import java.io.File

@GlideModule
object MyGlideApp : AppGlideModule() {
    /**
     * Display file as thumbnail preview if it is a media file
     *
     * @param viewHolder image preview holder
     * @param file the image file
     * @param isHistory History file image view is only a small preview
     */
    fun loadImage(viewHolder: ImageView, file: File, isHistory: Boolean) {
        if (!file.exists()) {
            viewHolder.setImageDrawable(null)
            return
        }
        val ctx = aTalkApp.globalContext
        if (isMediaFile(file)) {
            // History file image view is only a small preview (192 px max height)
            if (isHistory) {
                Glide.with(ctx)
                        .load(file)
                        .override(640, 192)
                        .placeholder(R.drawable.ic_file_open)
                        .into(viewHolder)
            } else {
                Glide.with(ctx)
                        .load(file)
                        .override(1280, 608)
                        .error(R.drawable.ic_file_open)
                        .into(viewHolder)
            }
        } else {
            viewHolder.setImageResource(R.drawable.ic_file_open)
        }
    }
}