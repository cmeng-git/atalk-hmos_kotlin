package org.atalk.hmos.gui.util

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputConnectionCompat.OnCommitContentListener
import androidx.core.view.inputmethod.InputContentInfoCompat

class ContentEditText : AppCompatEditText {
    private var commitListener: CommitListener? = null

    constructor(context: Context?) : super(context!!) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {}

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val inputConnection = super.onCreateInputConnection(editorInfo)
        EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf(MIME_TYPE_GIF, MIME_TYPE_PNG, MIME_TYPE_WEBP))

        // read and display inputContentInfo asynchronously
        val callback = OnCommitContentListener { inputContentInfo, flags, opts ->
            if (Build.VERSION.SDK_INT >= 25
                    && flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                try {
                    inputContentInfo.requestPermission()
                } catch (e: java.lang.Exception) {
                    // return false if failed
                    return@OnCommitContentListener false
                }
            }
            // read and display inputContentInfo asynchronously.
            // call inputContentInfo.releasePermission() as needed.
            if (commitListener != null) {
                commitListener!!.onCommitContent(inputContentInfo)
            }
            // return true if succeeded
            true
        }
        return InputConnectionCompat.createWrapper(inputConnection!!, editorInfo, callback)
    }

    fun setCommitListener(listener: CommitListener?) {
        commitListener = listener
    }

    interface CommitListener {
        fun onCommitContent(info: InputContentInfoCompat?)
    }

    companion object {
        private const val MIME_TYPE_GIF = "image/gif"
        private const val MIME_TYPE_PNG = "image/png"
        private const val MIME_TYPE_WEBP = "image/webp"
    }
}