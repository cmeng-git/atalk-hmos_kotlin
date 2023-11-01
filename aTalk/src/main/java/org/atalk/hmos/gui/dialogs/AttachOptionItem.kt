package org.atalk.hmos.gui.dialogs

import org.atalk.hmos.R

/**
 * The `AttachOptionItem` gives list items for optional attachments.
 *
 * @author Eng Chong Meng
 */
enum class AttachOptionItem(val textId: Int, val iconId: Int) {
    PICTURE(R.string.attachOptionDialog_picture, R.drawable.ic_attach_photo),
    VIDEO(R.string.attachOptionDialog_Videos, R.drawable.ic_attach_video),
    CAMERA(R.string.attachOptionDialog_camera, R.drawable.ic_attach_camera),
    VIDEO_RECORD(R.string.attachOptionDialog_videoRecord, R.drawable.ic_attach_video_record),
    //	audio_record(R.string.attachOptionDialog_audioRecord, R.drawable.ic_action_audio_record),
    //	share_contact(R.string.attachOptionDialog_shareContact, R.drawable.ic_attach_contact),
    SHARE_FILE(R.string.attachOptionDialog_shareFile, R.drawable.ic_attach_file);

}