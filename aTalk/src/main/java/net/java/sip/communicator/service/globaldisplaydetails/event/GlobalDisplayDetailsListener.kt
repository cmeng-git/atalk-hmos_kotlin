package net.java.sip.communicator.service.globaldisplaydetails.event

import java.util.*

interface GlobalDisplayDetailsListener : EventListener {
    fun globalDisplayNameChanged(evt: GlobalDisplayNameChangeEvent)
    fun globalDisplayAvatarChanged(evt: GlobalAvatarChangeEvent)
}