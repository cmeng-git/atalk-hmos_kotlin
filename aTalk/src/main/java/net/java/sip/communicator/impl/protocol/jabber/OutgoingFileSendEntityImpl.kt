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
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import java.io.File

/**
 * The Jabber protocol OutgoingFileSendEntityImpl extension of the `AbstractFileTransfer`.
 * This class is used when sending file to a single recipient or multiple as in conference room
 *
 * @author Eng Chong Meng
 */
class OutgoingFileSendEntityImpl(sendTo: Any, id: String?, file: String) : AbstractFileTransfer() {
    private val msgUuid: String
    /**
     * Get the recipient of the file
     *
     * @return Contact or ChatRoom
     */
    /**
     * The file recipient i.e. Contact or ChatRoom
     */
    val entityJid: Any
    private val mFile: File

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    val fileName: String

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    val fileSize: Long

    /**
     * Creates an `OutgoingFileSendEntityImpl`.
     *
     * @param sendTo the recipient of the file
     * @param id the message Uuid uniquely identify  record in DB
     * @param file the download link may contains other options e.g. file.length()
     */
    init {
        // Create a new msg Uuid if none provided
        msgUuid = id ?: System.currentTimeMillis().toString() + hashCode()
        entityJid = sendTo
        fileName = file
        mFile = File(file)
        fileSize = mFile.length()
    }

    /**
     * Cancels the file transfer.
     */
    override fun cancel() {
        // jabberTransfer.cancel();
    }

    /**
     * The direction is outgoing.
     *
     * @return OUT
     */
    override fun getDirection(): Int {
        return FileTransfer.OUT
    }

    /**
     * Returns the sender of the file.
     *
     * @return the sender of the file
     */
    override fun getContact(): Contact? {
        return if (entityJid is Contact) entityJid else null
    }

    /**
     * Returns the identifier of this file transfer.
     *
     * @return the identifier of this file transfer
     */
    override fun getID(): String? {
        return msgUuid
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    override fun getLocalFile(): File? {
        return mFile
    }
}