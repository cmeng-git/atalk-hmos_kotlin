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
package net.java.sip.communicator.impl.history

import android.database.sqlite.SQLiteDatabase
import net.java.sip.communicator.service.history.History
import net.java.sip.communicator.service.history.HistoryID
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.history.records.HistoryRecordStructure
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.persistance.DatabaseBackend
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.fileaccess.FileCategory
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.w3c.dom.Document
import org.xml.sax.SAXException
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.collections.Map.*

/**
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
open class HistoryServiceImpl(bundleContext: BundleContext) : HistoryService {
    // Note: Hashtable is SYNCHRONIZED
    private val histories = Hashtable<HistoryID, History>()
    private val fileAccessService: FileAccessService?
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()!!

    /**
     * Returns whether caching of read documents is enabled or disabled.
     *
     * @return boolean
     */
    val isCacheEnabled: Boolean
    private val mDB: SQLiteDatabase

    /**
     * Constructor.
     *
     * bundleContext OSGi bundle context
     * Exception if something went wrong during initialization
     */
    init {
        isCacheEnabled = getConfigurationService(bundleContext)!!.getBoolean(HistoryService.CACHE_ENABLED_PROPERTY, false)
        fileAccessService = getFileAccessService(bundleContext)
        mDB = DatabaseBackend.writableDB
    }

    override fun isHistoryExisting(id: HistoryID?): Boolean {
        return histories.containsKey(id)
    }

    override fun getExistingIDs(): Iterator<HistoryID> {
        val vect = Vector<File>()
        val histDir: File
        try {
            val userSetDataDirectory = System.getProperty("HistoryServiceDirectory")
            histDir = getFileAccessService()!!.getPrivatePersistentDirectory(userSetDataDirectory
                    ?: DATA_DIRECTORY, FileCategory.PROFILE)!!
            findDatFiles(vect, histDir)
        } catch (e: Exception) {
            Timber.e(e, "Error opening directory")
        }
        val structParse = DBStructSerializer(this)
        for (f in vect) {
            synchronized(histories) {
                try {
                    val hist = structParse.loadHistory(f)
                    if (!histories.containsKey(hist.getID())) {
                        histories[hist.getID()] = hist
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Could not load history from file: %s", f.absolutePath)
                }
            }
        }
        synchronized(histories) { return histories.keys.iterator() }
    }

    @Throws(IllegalArgumentException::class)
    override fun getHistory(id: HistoryID): History? {
        var retVal: History? = null
        synchronized(histories) {
            retVal = if (histories.containsKey(id)) {
                histories[id]
            } else {
                throw IllegalArgumentException("No history corresponds to the specified ID.")
            }
        }
        return retVal
    }

    @Throws(IllegalArgumentException::class, IOException::class)
    override fun createHistory(id: HistoryID, recordStructure: HistoryRecordStructure): History {
        var retVal: History
        synchronized(histories) {
            if (histories.containsKey(id)) {
                retVal = histories[id]!!
                retVal.setHistoryRecordsStructure(recordStructure)
            } else {
                val dir = createHistoryDirectories(id)
                val history = HistoryImpl(id, dir, recordStructure, this)
                val dbDatFile = File(dir, DATA_FILE)
                val dbss = DBStructSerializer(this)
                dbss.writeHistory(dbDatFile, history)
                histories[id] = history
                retVal = history
            }
        }
        return retVal
    }

    private fun getFileAccessService(): FileAccessService? {
        return fileAccessService
    }

    /**
     * Parse documents. Synchronized to avoid exception when concurrently parsing with same
     * DocumentBuilder
     *
     * @param file File the file to parse
     * @return Document the result document
     * @throws SAXException exception
     * @throws IOException exception
     */
    @Synchronized
    @Throws(SAXException::class, IOException::class)
    fun parse(file: File?): Document {
        val fis = FileInputStream(file)
        val doc = documentBuilder.parse(fis)
        fis.close()
        return doc
    }

    /**
     * Parse documents. Synchronized to avoid exception when concurrently parsing with same
     * DocumentBuilder
     *
     * @param in ByteArrayInputStream the stream to parse
     * @return Document the result document
     * @throws SAXException exception
     * @throws IOException exception
     */
    @Synchronized
    @Throws(SAXException::class, IOException::class)
    fun parse(`in`: ByteArrayInputStream?): Document {
        return documentBuilder.parse(`in`)
    }

    private fun findDatFiles(vect: MutableList<File>, directory: File) {
        val files = directory.listFiles()!!
        for (i in files.indices) {
            if (files[i].isDirectory) {
                findDatFiles(vect, files[i])
            } else if (DATA_FILE.equals(files[i].name, ignoreCase = true)) {
                vect.add(files[i])
            }
        }
    }

    @Throws(IOException::class)
    private fun createHistoryDirectories(id: HistoryID?): File {
        val idComponents = id!!.getID()

        // escape chars in directory names
        escapeCharacters(idComponents)
        val userSetDataDirectory = System.getProperty("HistoryServiceDirectory")
        var dir = File(userSetDataDirectory ?: DATA_DIRECTORY)
        for (s in idComponents) {
            dir = File(dir, s!!)
        }
        val directory = try {
            getFileAccessService()!!.getPrivatePersistentDirectory(dir.toString(), FileCategory.PROFILE)
        } catch (e: Exception) {
            val ioe = IOException("Could not create history due to file system error")
            ioe.initCause(e)
            throw ioe
        }
        if (!directory!!.exists() && !directory.mkdirs()) {
            throw IOException("Could not create requested history service files:" + directory.absolutePath)
        }
        return directory
    }

    /**
     * Permanently removes local stored History
     *
     * @param id HistoryID
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun purgeLocallyStoredHistory(id: HistoryID?) {
        // get the history directory corresponding the given id
        val dir = createHistoryDirectories(id)
        Timber.log(TimberLog.FINER, "Removing history directory %s", dir)
        deleteDirAndContent(dir)
        val history = histories.remove(id)
        if (history == null) {
            // well this can be global delete, so lets remove all matching sub-histories
            val ids = id!!.getID()
            val iter = histories.entries.iterator()
            while (iter.hasNext()) {
                val (key) = iter.next()
                if (isSubHistory(ids, key)) {
                    iter.remove()
                }
            }
        }
    }

    /**
     * Permanently removes locally stored message history for the sessionUuid.
     * - Remove only chatMessages for metaContacts
     * - Remove both chatSessions and chatMessages for muc
     */
    fun purgeLocallyStoredHistory(contact: Contact?, sessionUuid: String) {
        val args = arrayOf(sessionUuid)
        if (contact != null) {
            mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.SESSION_UUID + "=?", args)
        } else {
            mDB.delete(ChatSession.TABLE_NAME, ChatSession.SESSION_UUID + "=?", args)
        }
    }

    /**
     * Clears locally(in memory) cached histories.
     */
    override fun purgeLocallyCachedHistories() {
        histories.clear()
    }

    /**
     * Checks the ids of the parent, do they exist in the supplied history ids. If it exist the
     * history is sub history
     * of the on with the supplied ids.
     *
     * @param parentIDs the parent ids
     * @param hid the history to check
     * @return whether history is sub one (contained) of the parent.
     */
    private fun isSubHistory(parentIDs: Array<String?>, hid: HistoryID?): Boolean {
        val hids = hid!!.getID()
        if (hids.size < parentIDs.size) return false
        for (i in parentIDs.indices) {
            if (parentIDs[i] != hids[i]) return false
        }
        // everything matches, return true
        return true
    }

    /**
     * Deletes given directory and its content
     *
     * @param dir File
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun deleteDirAndContent(dir: File?) {
        if (!dir!!.isDirectory) return
        val content = dir.listFiles()
        var tmp: File
        for (i in content!!.indices) {
            tmp = content[i]
            if (tmp.isDirectory) deleteDirAndContent(tmp) else tmp.delete()
        }
        dir.delete()
    }

    /**
     * Replacing the characters that we must escape used for the created filename.
     *
     * @param ids Ids - folder names as we are using FileSystem for storing files.
     */
    private fun escapeCharacters(ids: Array<String?>) {
        for (i in ids.indices) {
            var currId = ids[i]
            for (j in ESCAPE_SEQUENCES.indices) {
                currId = currId!!.replace(ESCAPE_SEQUENCES[j][0].toRegex(), ESCAPE_SEQUENCES[j][1])
            }
            ids[i] = currId
        }
    }

    /**
     * Moves the content of oldId history to the content of the newId. Moves the content from the
     * oldId folder to the
     * newId folder. Old folder must exist.
     *
     * @param oldId old and existing history
     * @param newId the place where content of oldId will be moved
     * @throws java.io.IOException problem moving to newId
     */
    @Throws(IOException::class)
    override fun moveHistory(oldId: HistoryID?, newId: HistoryID?) {
        if (!isHistoryCreated(oldId)) // || !isHistoryExisting(newId))
            return
        val oldDir = createHistoryDirectories(oldId)
        val newDir = getDirForHistory(newId)

        // make sure parent path is existing
        newDir.parentFile!!.mkdirs()
        if (!oldDir.renameTo(newDir)) {
            Timber.w("Cannot move history!")
            throw IOException("Cannot move history!")
        }
        histories.remove(oldId)
    }

    /**
     * Returns the folder for the given history without creating it.
     *
     * @param id the history
     * @return the folder for the history
     */
    private fun getDirForHistory(id: HistoryID?): File {
        // put together subfolder names.
        val dirNames = id!!.getID()
        val dirName = StringBuilder()
        for (i in dirNames.indices) {
            if (i > 0) dirName.append(File.separatorChar)
            dirName.append(dirNames[i])
        }

        // get the parent directory
        var histDir: File? = null
        try {
            val userSetDataDirectory = System.getProperty("HistoryServiceDirectory")
            histDir = getFileAccessService()!!.getPrivatePersistentDirectory(
                    userSetDataDirectory ?: DATA_DIRECTORY,
                    FileCategory.PROFILE)
        } catch (e: Exception) {
            Timber.e(e, "Error opening directory")
        }
        return File(histDir, dirName.toString())
    }

    /**
     * Checks whether a history is created and stored. Exists in the file system.
     *
     * @param id the history to check
     * @return whether a history is created and stored.
     */
    override fun isHistoryCreated(id: HistoryID?): Boolean {
        return getDirForHistory(id).exists()
    }

    /**
     * Enumerates existing histories.
     *
     * @param rawId the start of the HistoryID of all the histories that will be returned.
     * @return list of histories which HistoryID starts with `rawId`.
     * @throws IllegalArgumentException if the `rawId` contains ids which are missing in current history.
     */
    @Throws(IllegalArgumentException::class)
    override fun getExistingHistories(rawId: Array<String>?): List<HistoryID?> {
        var histDir: File? = null
        try {
            histDir = getFileAccessService()!!.getPrivatePersistentDirectory(DATA_DIRECTORY,
                    FileCategory.PROFILE)
        } catch (e: Exception) {
            Timber.e(e, "Error opening directory")
        }
        if (histDir == null || !histDir.exists()) return ArrayList<HistoryID?>()

        val folderPath = StringBuilder()
        // history_ver1.0/messages/default
        for (id in rawId!!) folderPath.append(id).append(File.separator)
        val srcFolder = File(histDir, folderPath.toString())
        if (!srcFolder.exists()) return ArrayList<HistoryID?>()
        val recentFiles = TreeMap<File, HistoryID> { o1, o2 -> o1.name.compareTo(o2.name) }

        getExistingFiles(srcFolder, rawId.asList(), recentFiles)
        // return non duplicate
        val result = ArrayList<HistoryID?>()
        for ((_, hid) in recentFiles) {
            if (result.contains(hid)) continue
            result.add(hid)
        }
        return result
    }

    /**
     * Get existing files in `res` and their corresponding historyIDs.
     *
     * @param sourceFolder the folder to search into.
     * @param rawID the rawID.
     * @param res the result map.
     */
    private fun getExistingFiles(sourceFolder: File, rawID: List<String>, res: MutableMap<File, HistoryID>) {
        for (f in sourceFolder.listFiles()!!) {
            if (f.isDirectory) {
                val newRawID = ArrayList(rawID)
                newRawID.add(f.name)
                getExistingFiles(f, newRawID, res)
            } else {
                if (f.name == DATA_FILE) continue
                res[f] = HistoryID.createFromRawStrings(rawID.toTypedArray())
            }
        }
    }

    companion object {
        /**
         * The data directory.
         */
        const val DATA_DIRECTORY = "history_ver1.0"

        /**
         * The data file.
         */
        const val DATA_FILE = "dbstruct.dat"

        /**
         * Characters and their replacement in created folder names
         */
        private val ESCAPE_SEQUENCES = arrayOf(arrayOf("&", "&_amp"), arrayOf("/", "&_sl"), arrayOf("\\\\", "&_bs"), arrayOf(":", "&_co"), arrayOf("\\*", "&_as"), arrayOf("\\?", "&_qm"), arrayOf("\"", "&_pa"), arrayOf("<", "&_lt"), arrayOf(">", "&_gt"), arrayOf("\\|", "&_pp"))
        private fun getConfigurationService(bundleContext: BundleContext): ConfigurationService? {
            val serviceReference = bundleContext.getServiceReference(ConfigurationService::class.java.name) as ServiceReference<ConfigurationService>?
            return if (serviceReference == null) null else bundleContext.getService(serviceReference) as ConfigurationService
        }

        private fun getFileAccessService(bundleContext: BundleContext): FileAccessService? {
            return ServiceUtils.getService<FileAccessService>(bundleContext, FileAccessService::class.java)
        }
    }
}