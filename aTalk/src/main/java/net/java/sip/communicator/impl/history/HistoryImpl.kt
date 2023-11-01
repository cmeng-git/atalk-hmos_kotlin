/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history

import net.java.sip.communicator.service.history.History
import net.java.sip.communicator.service.history.HistoryID
import net.java.sip.communicator.service.history.HistoryReader
import net.java.sip.communicator.service.history.HistoryWriter
import net.java.sip.communicator.service.history.InteractiveHistoryReader
import net.java.sip.communicator.service.history.records.HistoryRecordStructure
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.util.xml.XMLUtils
import org.w3c.dom.Document
import timber.log.Timber
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.security.InvalidParameterException
import java.util.*

/**
 * @author Alexander Pelov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class HistoryImpl(private val id: HistoryID, private val directory: File, private var historyRecordStructure: HistoryRecordStructure, val historyServiceImpl: HistoryServiceImpl) : History {
    private var reader: HistoryReader? = null

    /**
     * The `InteractiveHistoryReader`.
     */
    private var interactiveReader: InteractiveHistoryReader? = null
    private var writer: HistoryWriter? = null
    private val historyDocuments = TreeMap<String?, Any?>()

    /**
     * Creates an instance of `HistoryImpl` by specifying the history identifier, the directory, the
     * `HistoryRecordStructure` to use and the parent `HistoryServiceImpl`.
     *
     * id the identifier
     * directory the directory
     * historyRecordStructure the structure
     * historyServiceImpl the parent history service
     */
    init {
        // TODO: Assert: Assert.assertNonNull(historyServiceImpl, "The
        // historyServiceImpl should be non-null.");
        // TODO: Assert: Assert.assertNonNull(id, "The ID should be
        // non-null.");
        // TODO: Assert: Assert.assertNonNull(historyRecordStructure, "The
        // structure should be non-null.");
        reloadDocumentList()
    }

    /**
     * Returns the identifier of this history.
     *
     * @return the identifier of this history
     */
    override fun getID(): HistoryID {
        return id
    }

    /**
     * Returns the current `HistoryRecordStructure`.
     *
     * @return the current `HistoryRecordStructure`
     */
    override fun getHistoryRecordsStructure(): HistoryRecordStructure {
        return historyRecordStructure
    }

    /**
     * Sets the given `structure` to be the new history records structure used in this history implementation.
     *
     * @param structure the new `HistoryRecordStructure` to use
     */
    override fun setHistoryRecordsStructure(structure: HistoryRecordStructure) {
        historyRecordStructure = structure
        try {
            val dbDatFile = File(directory, HistoryServiceImpl.DATA_FILE)
            val dbss = DBStructSerializer(historyServiceImpl)
            dbss.writeHistory(dbDatFile, this)
        } catch (e: IOException) {
            Timber.d("Could not create new history structure")
        }
    }

    override fun getReader(): HistoryReader? {
        if (reader == null) {
            reader = HistoryReaderImpl(this)
        }
        return reader
    }

    /**
     * Returns an object that can be used to read and query this history. The `InteractiveHistoryReader` differs
     * from the `HistoryReader` in the way it manages query results. It allows to cancel a search at any time and
     * to track history results through a `HistoryQueryListener`.
     *
     * @return an object that can be used to read and query this history
     */
    override fun getInteractiveReader(): InteractiveHistoryReader? {
        if (interactiveReader == null) interactiveReader = InteractiveHistoryReaderImpl(this)
        return interactiveReader
    }

    override fun getWriter(): HistoryWriter? {
        if (writer == null) writer = HistoryWriterImpl(this)
        return writer
    }

    private fun reloadDocumentList() {
        synchronized(historyDocuments) {
            historyDocuments.clear()
            val files = directory.listFiles()
            // TODO: Assert: Assert.assertNonNull(files, "The list of files
            // should be non-null.");
            for (file in files!!) {
                if (!file.isDirectory) {
                    val filename = file.name
                    if (filename.endsWith(SUPPORTED_FILETYPE)) {
                        historyDocuments[filename] = file
                    }
                }
            }
        }
    }

    fun createDocument(filename: String?): Document? {
        var retVal: Document?
        synchronized(historyDocuments) {
            if (historyDocuments.containsKey(filename)) {
                retVal = getDocumentForFile(filename)
            } else {
                retVal = historyServiceImpl.documentBuilder.newDocument()
                retVal!!.appendChild(retVal!!.createElement("history"))
                historyDocuments.put(filename, retVal)
            }
        }
        return retVal
    }

    @Throws(InvalidParameterException::class, IOException::class)
    fun writeFile(filename: String) {
        val file = File(directory, filename)
        synchronized(historyDocuments) {
            if (!historyDocuments.containsKey(filename)) {
                throw InvalidParameterException("The requested filename does not exist in the document list.")
            }
            val obj = historyDocuments[filename]
            if (obj is Document) {
                val doc = obj
                synchronized(doc) { XMLUtils.writeXML(doc, file) }
            }
        }
    }

    @Throws(InvalidParameterException::class, IOException::class)
    fun writeFile(filename: String, doc: Document?) {
        val file = File(directory, filename)
        synchronized(historyDocuments) {
            if (!historyDocuments.containsKey(filename)) {
                throw InvalidParameterException("The requested filename does not exist in the document list.")
            }
            synchronized(doc!!) { XMLUtils.writeXML(doc, file) }
        }
    }

    val fileList: MutableIterator<String?>
        get() = historyDocuments.keys.iterator()


    @Throws(InvalidParameterException::class, RuntimeException::class)
    fun getDocumentForFile(filename: String?): Document? {
        var retVal: Document? = null
        synchronized(historyDocuments) {
            if (!historyDocuments.containsKey(filename)) {
                throw InvalidParameterException("The requested filename does not exist in the document list.")
            }
            val obj = historyDocuments[filename]
            if (obj is Document) {
                // Document already loaded. Use it directly
                retVal = obj
            } else if (obj is File) {
                val file = obj
                try {
                    retVal = historyServiceImpl.parse(file)
                } catch (e: Exception) {
                    // throw new RuntimeException("Error occurred while "
                    // + "parsing XML document.", e);
                    // log.error("Error occurred while parsing XML document.", e);
                    Timber.e(e, "Error occurred while parsing XML document.")

                    // will try to fix the xml file
                    retVal = getFixedDocument(file)

                    // if is not fixed return
                    if (retVal == null) return null
                }

                // Cache the loaded document for reuse if configured
                if (historyServiceImpl.isCacheEnabled) historyDocuments[filename] = retVal
            } else {
                // TODO: Assert: Assert.fail("Internal error - the data type " +
                // "should be either Document or File.");
            }
        }
        return retVal
    }

    /**
     * Methods trying to fix histry xml files if corrupted
     * Returns the fixed document as xml Document if file cannot be fixed return null
     *
     * @param file File the file trying to fix
     * @return Document the fixed doc
     */
    fun getFixedDocument(file: File?): Document? {
        Timber.i("Will try to fix file : %s", file)
        val resultDocStr = StringBuilder("<history>")
        try {
            val inReader = BufferedReader(FileReader(file))
            var line: String
            while (inReader.readLine().also { line = it } != null) {
                // find the next start of record node
                if (!line.contains("<record")) {
                    continue
                }
                val record = getRecordNodeString(line, inReader).toString()
                if (isValidXML(record)) {
                    resultDocStr.append(record)
                }
            }
        } catch (ex1: Exception) {
            Timber.e("File cannot be fixed. Erro reading! %s", ex1.localizedMessage)
        }
        resultDocStr.append("</history>")
        return try {
            val result = historyServiceImpl.parse(ByteArrayInputStream(resultDocStr.toString().toByteArray(charset("UTF-8"))))

            // parsing is ok . lets overwrite with correct values
            Timber.log(TimberLog.FINER, "File fixed will write to disk!")
            XMLUtils.writeXML(result, file)
            result
        } catch (ex: Exception) {
            println("again cannot parse " + ex.message)
            null
        }
    }

    /**
     * Returns the string containing the record node from the xml - the supplied Reader
     *
     * @param startingLine String
     * @param inReader BufferedReader
     * @return StringBuffer
     */
    private fun getRecordNodeString(startingLine: String, inReader: BufferedReader): StringBuffer? {
        return try {
            val result = StringBuffer(startingLine)
            var line: String
            while (inReader.readLine().also { line = it } != null) {
                // find the next start of record node
                if (line.contains("</record>")) {
                    result.append(line)
                    break
                }
                result.append(line)
            }
            result
        } catch (ex: IOException) {
            Timber.w("Error reading record %s", ex.localizedMessage)
            null
        }
    }

    /**
     * Checks whether the given xml is valid
     *
     * @param str String
     * @return boolean
     */
    private fun isValidXML(str: String): Boolean {
        try {
            historyServiceImpl.parse(ByteArrayInputStream(str.toByteArray(charset("UTF-8"))))
        } catch (ex: Exception) {
            Timber.e("not valid xml %s: %s", str, ex.message)
            return false
        }
        return true
    }

    companion object {
        /**
         * The supported filetype.
         */
        const val SUPPORTED_FILETYPE = "xml"
    }
}