package net.java.sip.communicator.service.globaldisplaydetails.event

import java.util.*

class GlobalAvatarChangeEvent(source: Any?, private val avatar: ByteArray) : EventObject(source) {
    fun getNewAvatar(): ByteArray {
        return avatar
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}