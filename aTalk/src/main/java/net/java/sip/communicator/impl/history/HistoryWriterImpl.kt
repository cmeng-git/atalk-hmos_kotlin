/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history

import net.java.sip.communicator.impl.history.HistoryReaderImpl.Companion.filterFilesByDate
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.history.HistoryWriter
import net.java.sip.communicator.service.history.HistoryWriter.HistoryRecordUpdater
import net.java.sip.communicator.service.history.records.HistoryRecord
import org.atalk.util.xml.XMLUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.IOException
import java.security.InvalidParameterException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
class HistoryWriterImpl(private val historyImpl: HistoryImpl) : HistoryWriter {
    private val docCreateLock = Any()
    private val docWriteLock = Any()
    private val structPropertyNames: Array<String>
    private var currentDoc: Document? = null
    private var currentFile: String? = null
    private var currentDocElements = -1

    init {
        val struct = historyImpl.getHistoryRecordsStructure()
        structPropertyNames = struct.propertyNames
    }

    @Throws(IOException::class)
    override fun addRecord(record: HistoryRecord) {
        this.addRecord(record.propertyNames, record.propertyValues, record.timestamp, -1)
    }

    @Throws(IOException::class)
    override fun addRecord(propertyValues: Array<String>) {
        addRecord(structPropertyNames, propertyValues, Date(), -1)
    }

    @Throws(IOException::class)
    override fun addRecord(propertyValues: Array<String>, timestamp: Date) {
        this.addRecord(structPropertyNames, propertyValues, timestamp, -1)
    }

    /**
     * Stores the passed propertyValues complying with the historyRecordStructure.
     *
     * @param propertyValues
     * The values of the record.
     * @param maxNumberOfRecords
     * the maximum number of records to keep or value of -1 to ignore this param.
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun addRecord(propertyValues: Array<String>, maxNumberOfRecords: Int) {
        addRecord(structPropertyNames, propertyValues, Date(), maxNumberOfRecords)
    }

    /**
     * Adds new record to the current history document when the record property name ends with _CDATA this is removed
     * from the property name and a CDATA text node is created to store the text value
     *
     * @param propertyNames
     * String[]
     * @param propertyValues
     * String[]
     * @param date
     * Date
     * @param maxNumberOfRecords
     * the maximum number of records to keep or value of -1 to ignore this param.
     * @throws InvalidParameterException
     * @throws IOException
     */
    @Throws(InvalidParameterException::class, IOException::class)
    private fun addRecord(propertyNames: Array<String>, propertyValues: Array<String>, date: Date?, maxNumberOfRecords: Int) {
        // Synchronized to assure that two concurrent threads can insert records
        // safely.
        synchronized(docCreateLock) {
            if (currentDoc == null || currentDocElements > MAX_RECORDS_PER_FILE) {
                createNewDoc(date, currentDoc == null)
            }
        }
        synchronized(currentDoc!!) {
            val root = currentDoc!!.firstChild
            synchronized(root) {

                // if we have setting for max number of records,
                // check the number and when exceed them, remove the first one
                if (maxNumberOfRecords > -1 && currentDocElements >= maxNumberOfRecords) {
                    // lets remove the first one
                    removeFirstRecord(root)
                }
                val elem = createRecord(currentDoc, propertyNames, propertyValues, date)
                root.appendChild(elem)
                currentDocElements++
            }
        }

        // write changes
        synchronized(docWriteLock) { if (historyImpl.historyServiceImpl.isCacheEnabled) historyImpl.writeFile(currentFile!!) else historyImpl.writeFile(currentFile!!, currentDoc) }
    }

    /**
     * Creates a record element for the supplied `doc` and populates it with the property names from
     * `propertyNames` and corresponding values from `propertyValues`. The `date` will be used for
     * the record timestamp attribute.
     *
     * @param doc
     * the parent of the element.
     * @param propertyNames
     * property names for the element
     * @param propertyValues
     * values for the properties
     * @param date
     * the of creation of the record
     * @return the newly created element.
     */
    private fun createRecord(doc: Document?, propertyNames: Array<String>, propertyValues: Array<String>, date: Date?): Element {
        val elem = doc!!.createElement("record")
        val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
        elem.setAttribute("timestamp", sdf.format(date!!))
        for (i in propertyNames.indices) {
            var propertyName = propertyNames[i]
            if (propertyName.endsWith(CDATA_SUFFIX)) {
                if (propertyValues[i] != null) {
                    propertyName = propertyName.replaceFirst(CDATA_SUFFIX.toRegex(), "")
                    val propertyElement = doc.createElement(propertyName)
                    val value = doc.createCDATASection(propertyValues[i].replace("\u0000".toRegex(), " "))
                    propertyElement.appendChild(value)
                    elem.appendChild(propertyElement)
                }
            } else {
                if (propertyValues[i] != null) {
                    val propertyElement = doc.createElement(propertyName)
                    val value = doc.createTextNode(propertyValues[i].replace("\u0000".toRegex(), " "))
                    propertyElement.appendChild(value)
                    elem.appendChild(propertyElement)
                }
            }
        }
        return elem
    }

    /**
     * Finds the oldest node by timestamp in current root and deletes it.
     *
     * @param root
     * where to search for records
     */
    private fun removeFirstRecord(root: Node) {
        val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
        val nodes = (root as Element).getElementsByTagName("record")
        var oldestNode: Node? = null
        var oldestTimeStamp: Date? = null
        var node: Node
        for (i in 0 until nodes.length) {
            node = nodes.item(i)
            var timestamp: Date?
            val ts = node.attributes.getNamedItem("timestamp").nodeValue
            timestamp = try {
                sdf.parse(ts)
            } catch (e: ParseException) {
                Date(ts.toLong())
            }
            if (oldestNode == null || oldestTimeStamp!!.after(timestamp)) {
                oldestNode = node
                oldestTimeStamp = timestamp
            }
        }
        if (oldestNode != null) root.removeChild(oldestNode)
    }

    /**
     * Inserts a record from the passed `propertyValues` complying with the current historyRecordStructure. First
     * searches for the file to use to import the record, as files hold records with consecutive times and this fact is
     * used for searching and filtering records by date. This is why when inserting an old record we need to insert it
     * on the correct position.
     *
     * @param propertyValues
     * The values of the record.
     * @param timestamp
     * The timestamp of the record.
     * @param timestampProperty
     * the property name for the timestamp of the record
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun insertRecord(propertyValues: Array<String>, timestamp: Date, timestampProperty: String?) {
        val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
        val fileIterator = filterFilesByDate(historyImpl.fileList, timestamp, null).iterator()
        var filename: String?
        while (fileIterator.hasNext()) {
            filename = fileIterator.next()
            val doc = historyImpl.getDocumentForFile(filename) ?: continue
            val nodes = doc.getElementsByTagName("record")
            var changed = false
            var node: Node?
            for (i in 0 until nodes.length) {
                node = nodes.item(i)
                val idNode = XMLUtils.findChild(node as Element?, timestampProperty)
                        ?: continue
                val nestedNode = idNode.firstChild ?: continue

                // Get nested TEXT node's value
                val nodeValue = nestedNode.nodeValue
                val nodeTimeStamp = try {
                    sdf.parse(nodeValue)!!
                } catch (e: ParseException) {
                    Date(nodeValue.toLong())
                }
                if (nodeTimeStamp.before(timestamp)) continue
                val newElem = createRecord(doc, structPropertyNames, propertyValues, timestamp)
                doc.firstChild.insertBefore(newElem, node)
                changed = true
                break
            }
            if (changed) {
                // write changes
                synchronized(docWriteLock) { historyImpl.writeFile(filename!!, doc) }

                // this prevents that the current writer, which holds instance for the last document he is editing will
                // not override our last changes to the document
                if (filename == currentFile) {
                    currentDoc = doc
                }
                break
            }
        }
    }

    /**
     * If no file is currently loaded loads the last opened file. If it does not exists or if the current file was set -
     * create a new file.
     *
     * @param date
     * Date
     * @param loadLastFile
     * boolean
     */
    private fun createNewDoc(date: Date?, loadLastFile: Boolean) {
        var loaded = false
        if (loadLastFile) {
            val files = historyImpl.fileList
            var file: String? = null
            while (files.hasNext()) {
                file = files.next()
            }
            if (file != null) {
                currentDoc = historyImpl.getDocumentForFile(file)
                currentFile = file
                loaded = true
            }

            // if something happened and file was not loaded then we must create new one
            if (currentDoc == null) {
                loaded = false
            }
        }
        if (!loaded) {
            currentFile = date!!.time.toString()
            currentFile += ".xml"
            currentDoc = historyImpl.createDocument(currentFile)
        }

        // TODO: Assert: Assert.assertNonNull(this.currentDoc, "There should be a current document created.");
        currentDocElements = currentDoc!!.firstChild.childNodes.length
    }

    /**
     * Updates a record by searching for record with idProperty which have idValue and updating/creating the property
     * with newValue.
     *
     * @param idProperty
     * name of the id property
     * @param idValue
     * value of the id property
     * @param property
     * the property to change
     * @param newValue
     * the value of the changed property.
     */
    @Throws(IOException::class)
    override fun updateRecord(idProperty: String?, idValue: String?, property: String?, newValue: String?) {
        val fileIterator = historyImpl.fileList
        var filename: String?
        while (fileIterator.hasNext()) {
            filename = fileIterator.next()
            val doc = historyImpl.getDocumentForFile(filename) ?: continue
            val nodes = doc.getElementsByTagName("record")
            var changed = false
            var node: Node
            for (i in 0 until nodes.length) {
                node = nodes.item(i)
                val idNode = XMLUtils.findChild(node as Element, idProperty)
                        ?: continue
                val nestedNode = idNode.firstChild ?: continue

                // Get nested TEXT node's value
                val nodeValue = nestedNode.nodeValue
                if (nodeValue != idValue) continue
                val changedNode = XMLUtils.findChild(node, property)
                if (changedNode != null) {
                    val changedNestedNode = changedNode.firstChild
                    changedNestedNode.nodeValue = newValue
                } else {
                    val propertyElement = currentDoc!!.createElement(property)
                    val value = currentDoc!!.createTextNode(newValue!!.replace("\u0000".toRegex(), " "))
                    propertyElement.appendChild(value)
                    node.appendChild(propertyElement)
                }

                // change the timestamp, to reflect there was a change
                val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
                node.setAttribute("timestamp", sdf.format(Date()))
                changed = true
                break
            }
            if (changed) {
                // write changes
                synchronized(docWriteLock) { historyImpl.writeFile(filename!!, doc) }

                // this prevents that the current writer, which holds instance for the last document he is editing will
                // not override our last changes to the document
                if (filename == currentFile) {
                    currentDoc = doc
                }
                break
            }
        }
    }

    /**
     * Updates history record using given `HistoryRecordUpdater` instance to find which is the record to be
     * updated and to get the new values for the fields
     *
     * @param updater
     * the `HistoryRecordUpdater` instance.
     */
    @Throws(IOException::class)
    override fun updateRecord(updater: HistoryRecordUpdater) {
        val fileIterator = historyImpl.fileList
        var filename: String?
        while (fileIterator.hasNext()) {
            filename = fileIterator.next()
            val doc = historyImpl.getDocumentForFile(filename) ?: continue
            val nodes = doc.getElementsByTagName("record")
            var changed = false
            var node: Node
            for (i in 0 until nodes.length) {
                node = nodes.item(i)
                updater.setHistoryRecord(createHistoryRecordFromNode(node))
                if (!updater.isMatching()) continue

                // change the timestamp, to reflect there was a change
                val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
                (node as Element).setAttribute("timestamp", sdf.format(Date()))
                val updates = updater.getUpdateChanges()
                for (nodeName in updates!!.keys) {
                    val changedNode = XMLUtils.findChild(node, nodeName)
                    if (changedNode != null) {
                        val changedNestedNode = changedNode.firstChild
                        changedNestedNode.nodeValue = updates[nodeName]
                        changed = true
                    }
                }
            }
            if (changed) {
                // write changes
                synchronized(docWriteLock) { historyImpl.writeFile(filename!!, doc) }

                // this prevents that the current writer, which holds instance for the last document he is editing will
                // not override our last changes to the document
                if (filename == currentFile) {
                    currentDoc = doc
                }
                break
            }
        }
    }

    /**
     * Creates `HistoryRecord` instance from `Node` object.
     *
     * @param node
     * the node
     * @return the `HistoryRecord` instance
     */
    private fun createHistoryRecordFromNode(node: Node): HistoryRecord {
        val structure = historyImpl.getHistoryRecordsStructure()
        val propertyValues = Array(structure.getPropertyCount()) {""}
        var i = 0
        for (propertyName in structure.propertyNames) {
            val childNode = XMLUtils.findChild(node as Element, propertyName)
            if (childNode == null) {
                i++
                continue
            }
            propertyValues[i] = childNode.textContent
            i++
        }
        return HistoryRecord(structure, propertyValues)
    }

    companion object {
        /**
         * Maximum records per file.
         */
        const val MAX_RECORDS_PER_FILE = 150
        private const val CDATA_SUFFIX = "_CDATA"
    }
}