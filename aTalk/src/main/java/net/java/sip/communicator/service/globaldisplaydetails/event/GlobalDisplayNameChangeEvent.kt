package net.java.sip.communicator.service.globaldisplaydetails.event

import java.util.*

class GlobalDisplayNameChangeEvent(source: Any?, private val displayName: String) : EventObject(source) {
    fun getNewDisplayName(): String {
        return displayName
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}