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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.os.*
import android.text.Html
import android.text.TextUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.impl.androidtray.NotificationPopupHandler
import org.atalk.persistance.FileBackend.getMimeType
import timber.log.Timber

/**
 * @author Eng Chong Meng
 */
object ShareUtil {
    private const val REQUEST_CODE_SHARE = 500

    /**
     * Total wait time for handling multiple intents (text & images)
     */
    private const val TIME_DELAY = 12000

    /**
     * Share of both text and images with auto start of second intend with a timeDelay in between
     * OS >= VERSION_CODES.LOLLIPOP_MR1 uses pendingIntent call back (broadcast)
     *
     * @param activity a reference of the activity
     * @param msgContent text content for sharing
     * @param imageUris array of image uris for sharing
     */
    @SuppressLint("NewApi")
    fun share(activity: Activity?, msgContent: String, imageUris: ArrayList<Uri>) {
        if (activity != null) {
            var timeDelay = 0
            if (!TextUtils.isEmpty(msgContent)) {
                val shareIntent = share(activity, msgContent)
                try {
                    if (imageUris.isNotEmpty()) {
                        val pi = PendingIntent.getBroadcast(activity, REQUEST_CODE_SHARE,
                                Intent(activity, ShareBroadcastReceiver::class.java),
                                NotificationPopupHandler.getPendingIntentFlag(false, true))
                        activity.startActivity(Intent.createChooser(shareIntent,
                                activity.getString(R.string.service_gui_SHARE_TEXT), pi.intentSender))

                        // setup up media file sending intent
                        ShareBroadcastReceiver.setShareIntent(activity, share(activity, imageUris))
                        return
                    } else {
                        // setting is used only when !imageUris.isEmpty()
                        timeDelay = TIME_DELAY
                        activity.startActivity(Intent.createChooser(shareIntent,
                                activity.getString(R.string.service_gui_SHARE_TEXT)))
                    }
                } catch (e: ActivityNotFoundException) {
                    Timber.w("%s", aTalkApp.getResString(R.string.no_application_found_to_open_file))
                }
            }
            if (imageUris.isNotEmpty()) {
                // must wait for user first before starting file transfer if any
                Handler().postDelayed({
                    val intent = share(activity, imageUris)
                    try {
                        activity.startActivity(Intent.createChooser(intent, activity.getText(R.string.service_gui_SHARE_FILE)))
                    } catch (e: ActivityNotFoundException) {
                        Timber.w("No application found to open file")
                    }
                }, timeDelay.toLong())
            }
        }
    }

    /**
     * Generate a share intent with the given msgContent
     *
     * @param activity a reference of the activity
     * @param content text content for sharing
     * @return share intent of the given msgContent
     */
    fun share(activity: Activity?, content: String): Intent? {
        var msgContent = content
        var shareIntent: Intent? = null
        if (activity != null && !TextUtils.isEmpty(msgContent)) {
            shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND

            // replace all "\n" with <br/> to avoid strip by Html.fromHtml
            msgContent = msgContent.replace("\n".toRegex(), "<br/>")
            msgContent = Html.fromHtml(msgContent).toString()
            msgContent = msgContent.replace("<br/>".toRegex(), "\n")
            shareIntent.putExtra(Intent.EXTRA_TEXT, msgContent)
            shareIntent.type = "text/plain"
        }
        return shareIntent
    }

    /**
     * Generate a share intent with the given imageUris
     *
     * @param context a reference context of the activity
     * @param imageUris array of image uris for sharing
     * @return share intent of the given imageUris
     */
    fun share(context: Context?, imageUris: ArrayList<Uri>): Intent? {
        var shareIntent: Intent? = null
        if (context != null && imageUris.isNotEmpty()) {
            shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND_MULTIPLE
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val mimeType = getMimeType(context, imageUris)
            shareIntent.type = mimeType
        }
        return shareIntent
    }

    /**
     * Share of both text and images in a single intent for local forward only in aTalk;
     * msgContent is saved intent.categories if both types are required; otherwise follow standard share method
     *
     * @param context a reference context of the activity
     * @param shareLocal a reference of the ShareActivity
     * @param msgContent text content for sharing
     * @param imageUris array of image uris for sharing
     */
    fun shareLocal(context: Context?, shareLocal: Intent, msgContent: String?, imageUris: ArrayList<Uri>): Intent {
        if (context != null) {
            if (imageUris.isNotEmpty()) {
                shareLocal.action = Intent.ACTION_SEND_MULTIPLE
                shareLocal.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
                shareLocal.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val mimeType = getMimeType(context, imageUris)
                shareLocal.type = mimeType

                // Pass the extra_text in intent.categories in this case
                if (!TextUtils.isEmpty(msgContent)) {
                    shareLocal.addCategory(msgContent)
                }
            } else if (!TextUtils.isEmpty(msgContent)) {
                shareLocal.action = Intent.ACTION_SEND
                shareLocal.putExtra(Intent.EXTRA_TEXT, msgContent)
                shareLocal.type = "text/plain"
            }
        }
        return shareLocal
    }

    /**
     * Generate a common mime type for the given imageUris; reduce in resolution with more than one image types
     *
     * @param context a reference context of the activity
     * @param imageUris array of image uris for sharing
     * @return th common mime type for the given imageUris
     */
    private fun getMimeType(context: Context, imageUris: ArrayList<Uri>): String {
        var tmp: String?
        var mimeTmp: Array<String>
        var mimeType = arrayOf("*", "*")
        var first = 0
        for (uri in imageUris) {
            tmp = getMimeType(context, uri)
            if (tmp != null) {
                mimeTmp = tmp.split("/").toTypedArray()
                if (first++ == 0) {
                    mimeType = mimeTmp
                } else {
                    if (mimeType[0] != mimeTmp[0]) mimeType[0] = "*"
                    if (mimeType[1] != mimeTmp[1]) mimeType[1] = "*"
                }
            }
        }
        return mimeType[0] + "/" + mimeType[1]
    }

    /**
     * Share BroadcastReceiver call back after user has chosen the share app
     * Some delay is given for user to pick the buddy before starting the next share intent
     */
    class ShareBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val clickedComponent = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
            if (mediaIntent == null) return

            // must wait for user to complete text share before starting file share
            Handler().postDelayed({
                try {
                    context.startActivity(mediaIntent)
                    mediaIntent = null
                } catch (e: ActivityNotFoundException) {
                    Timber.w("No application found to open file")
                }
            }, (TIME_DELAY / 2).toLong())
        }

        companion object {
            private var mediaIntent: Intent? = null
            fun setShareIntent(activity: Activity, intent: Intent?) {
                mediaIntent = Intent.createChooser(intent, activity.getText(R.string.service_gui_SHARE_FILE))
                mediaIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}