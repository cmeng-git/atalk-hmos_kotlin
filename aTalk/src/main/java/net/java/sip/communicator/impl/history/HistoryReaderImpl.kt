/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history

import net.java.sip.communicator.service.history.HistoryReader
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.history.QueryResultSet
import net.java.sip.communicator.service.history.event.HistorySearchProgressListener
import net.java.sip.communicator.service.history.event.ProgressEvent
import net.java.sip.communicator.service.history.records.HistoryRecord
import org.apache.commons.text.StringEscapeUtils
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class HistoryReaderImpl
/**
 * Creates an instance of `HistoryReaderImpl`.
 *
 * @param historyImpl
 * the parent History implementation
 */
constructor(private val historyImpl: HistoryImpl) : HistoryReader {
    private val progressListeners = Vector<HistorySearchProgressListener>()

    /**
     * Searches the history for all records with timestamp after `startDate`.
     *
     * @param startDate
     * the date after all records will be returned
     * @return the found records
     * @throws RuntimeException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findByStartDate(startDate: Date?): QueryResultSet<HistoryRecord?> {
        return find(startDate, null, null, null, false)
    }

    /**
     * Searches the history for all records with timestamp before `endDate`.
     *
     * @param endDate
     * the date before which all records will be returned
     * @return the found records
     * @throws RuntimeException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findByEndDate(endDate: Date?): QueryResultSet<HistoryRecord?> {
        return find(null, endDate, null, null, false)
    }

    /**
     * Searches the history for all records with timestamp between `startDate` and `endDate`.
     *
     * @param startDate
     * start of the interval in which we search
     * @param endDate
     * end of the interval in which we search
     * @return the found records
     * @throws RuntimeException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findByPeriod(startDate: Date?, endDate: Date?): QueryResultSet<HistoryRecord?> {
        return find(startDate, endDate, null, null, false)
    }

    /**
     * Searches the history for all records containing the `keyword`.
     *
     * @param keyword
     * the keyword to search for
     * @param field
     * the field where to look for the keyword
     * @return the found records
     * @throws RuntimeException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findByKeyword(keyword: String, field: String?): QueryResultSet<HistoryRecord?> {
        return findByKeywords(arrayOf(keyword), field)
    }

    /**
     * Searches the history for all records containing all `keywords`.
     *
     * @param keywords
     * array of keywords we search for
     * @param field
     * the field where to look for the keyword
     * @return the found records
     * @throws RuntimeException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findByKeywords(keywords: Array<String>?, field: String?): QueryResultSet<HistoryRecord?> {
        return find(null, null, keywords, field, false)
    }

    /**
     * Searches for all history records containing all `keywords`, with timestamp between `startDate` and
     * `endDate`.
     *
     * @param startDate
     * start of the interval in which we search
     * @param endDate
     * end of the interval in which we search
     * @param keywords
     * array of keywords we search for
     * @param field
     * the field where to look for the keyword
     * @return the found records
     * @throws UnsupportedOperationException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(UnsupportedOperationException::class)
    override fun findByPeriod(startDate: Date?, endDate: Date?, keywords: Array<String>?, field: String?): QueryResultSet<HistoryRecord?> {
        return find(startDate, endDate, keywords, field, false)
    }

    /**
     * Returns the last `count` messages. No progress firing as this method is supposed to be used in message
     * windows and is supposed to be as quick as it can.
     *
     * @param count
     * int
     * @return QueryResultSet
     * @throws RuntimeException
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findLast(count: Int): QueryResultSet<HistoryRecord?> {
        return findLast(count, null, null, false)
    }

    /**
     * Returns the supplied number of recent messages containing all `keywords`.
     *
     * @param count
     * messages count
     * @param keywords
     * array of keywords we search for
     * @param field
     * the field where to look for the keyword
     * @param caseSensitive
     * is keywords search case sensitive
     * @return the found records
     * @throws RuntimeException
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findLast(count: Int, keywords: Array<String>?,
            field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?> {
        // the files are supposed to be ordered from oldest to newest
        val filelist = filterFilesByDate(historyImpl.fileList, null, null)
        val result = TreeSet(HistoryRecordComparator())
        var leftCount = count
        var currentFile = filelist.size - 1
        val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
        while (leftCount > 0 && currentFile >= 0) {
            val doc = historyImpl.getDocumentForFile(filelist[currentFile])
            if (doc == null) {
                currentFile--
                continue
            }

            // will get nodes and construct a List of nodes so we can easily get sublist of it
            val nodes = ArrayList<Node>()
            val nodesList = doc.getElementsByTagName("record")
            for (i in 0 until nodesList.length) {
                nodes.add(nodesList.item(i))
            }
            var lNodes: List<Node>?
            if (nodes.size > leftCount) {
                lNodes = nodes.subList(nodes.size - leftCount, nodes.size)
                leftCount = 0
            } else {
                lNodes = nodes
                leftCount -= nodes.size
            }
            val i = lNodes.iterator()
            while (i.hasNext()) {
                val node = i.next()
                val propertyNodes = node.childNodes
                var timestamp: Date?
                val ts = node.attributes.getNamedItem("timestamp").nodeValue
                timestamp = try {
                    sdf.parse(ts)
                } catch (e: ParseException) {
                    Date(ts.toLong())
                }
                val record = filterByKeyword(propertyNodes, timestamp!!, keywords, field, caseSensitive)
                if (record != null) {
                    result.add(record)
                }
            }
            currentFile--
        }
        return OrderedQueryResultSet(result)
    }

    /**
     * Searches the history for all records containing the `keyword`.
     *
     * @param keyword
     * the keyword to search for
     * @param field
     * the field where to look for the keyword
     * @param caseSensitive
     * is keywords search case sensitive
     * @return the found records
     * @throws RuntimeException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findByKeyword(keyword: String, field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?> {
        return findByKeywords(arrayOf(keyword), field, caseSensitive)
    }

    /**
     * Searches the history for all records containing all `keywords`.
     *
     * @param keywords
     * array of keywords we search for
     * @param field
     * the field where to look for the keyword
     * @param caseSensitive
     * is keywords search case sensitive
     * @return the found records
     * @throws RuntimeException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(RuntimeException::class)
    override fun findByKeywords(keywords: Array<String>?, field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?> {
        return find(null, null, keywords, field, caseSensitive)
    }

    /**
     * Searches for all history records containing all `keywords`, with timestamp between `startDate` and
     * `endDate`.
     *
     * @param startDate
     * start of the interval in which we search
     * @param endDate
     * end of the interval in which we search
     * @param keywords
     * array of keywords we search for
     * @param field
     * the field where to look for the keyword
     * @param caseSensitive
     * is keywords search case sensitive
     * @return the found records
     * @throws UnsupportedOperationException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Synchronized
    @Throws(UnsupportedOperationException::class)
    override fun findByPeriod(startDate: Date?, endDate: Date?, keywords: Array<String>?, field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?> {
        return find(startDate, endDate, keywords, field, caseSensitive)
    }

    /**
     * Returns the supplied number of recent messages after the given date
     *
     * @param date
     * messages after date
     * @param count
     * messages count
     * @return QueryResultSet the found records
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    override fun findFirstRecordsAfter(date: Date?, count: Int): QueryResultSet<HistoryRecord?> {
        val result = TreeSet(HistoryRecordComparator())
        val filelist = filterFilesByDate(historyImpl.fileList, date, null)
        var leftCount = count
        var currentFile = 0
        val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
        while (leftCount > 0 && currentFile < filelist.size) {
            val doc = historyImpl.getDocumentForFile(filelist[currentFile])
            if (doc == null) {
                currentFile++
                continue
            }
            val nodes = doc.getElementsByTagName("record")
            var node: Node
            var i = 0
            while (i < nodes.length && leftCount > 0) {
                node = nodes.item(i)
                val propertyNodes = node.childNodes
                var timestamp: Date
                val ts = node.attributes.getNamedItem("timestamp").nodeValue
                timestamp = try {
                    sdf.parse(ts)!!
                } catch (e: ParseException) {
                    Date(ts.toLong())
                }
                if (!isInPeriod(timestamp, date, null)) {
                    i++
                    continue
                }
                val nameVals = ArrayList<String>()
                var isRecordOK = true
                val len = propertyNodes.length
                for (j in 0 until len) {
                    val propertyNode = propertyNodes.item(j)
                    if (propertyNode.nodeType == Node.ELEMENT_NODE) {
                        // Get nested TEXT node's value
                        val nodeValue = propertyNode.firstChild
                        if (nodeValue != null) {
                            nameVals.add(propertyNode.nodeName)
                            nameVals.add(nodeValue.nodeValue)
                        } else isRecordOK = false
                    }
                }

                // if we found a broken record - just skip it
                if (!isRecordOK) {
                    i++
                    continue
                }
                val propertyNames = Array(nameVals.size / 2){""}
                val propertyValues = Array(propertyNames.size){""}
                for (j in propertyNames.indices) {
                    propertyNames[j] = nameVals[j * 2]
                    propertyValues[j] = nameVals[j * 2 + 1]
                }
                val record = HistoryRecord(propertyNames, propertyValues, timestamp)
                result.add(record)
                leftCount--
                i++
            }
            currentFile++
        }
        return OrderedQueryResultSet(result)
    }

    /**
     * Returns the supplied number of recent messages before the given date
     *
     * @param date
     * messages before date
     * @param count
     * messages count
     * @return QueryResultSet the found records
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    override fun findLastRecordsBefore(date: Date?, count: Int): QueryResultSet<HistoryRecord?> {
        // the files are supposed to be ordered from oldest to newest
        val filelist = filterFilesByDate(historyImpl.fileList, null, date)
        val result = TreeSet(HistoryRecordComparator())
        var leftCount = count
        var currentFile = filelist.size - 1
        val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
        while (leftCount > 0 && currentFile >= 0) {
            val doc = historyImpl.getDocumentForFile(filelist[currentFile])
            if (doc == null) {
                currentFile--
            } else {
                val nodes = doc.getElementsByTagName("record")
                var node: Node
                var i = nodes.length - 1
                while (i >= 0 && leftCount > 0) {
                    node = nodes.item(i)
                    val propertyNodes = node.childNodes
                    var timestamp: Date
                    val ts = node.attributes.getNamedItem("timestamp").nodeValue
                    timestamp = try {
                        sdf.parse(ts)!!
                    } catch (e: ParseException) {
                        Date(ts.toLong())
                    }
                    if (isInPeriod(timestamp, null, date)) {
                        val nameVals = ArrayList<String>()
                        var isRecordOK = true
                        val len = propertyNodes.length
                        for (j in 0 until len) {
                            val propertyNode = propertyNodes.item(j)
                            if (propertyNode.nodeType == Node.ELEMENT_NODE) {
                                // Get nested TEXT node's value
                                val nodeValue = propertyNode.firstChild
                                if (nodeValue != null) {
                                    nameVals.add(propertyNode.nodeName)
                                    nameVals.add(nodeValue.nodeValue)
                                } else {
                                    isRecordOK = false
                                }
                            }
                        }

                        // if we found a broken record - just skip it
                        if (isRecordOK) {
                            val propertyNames = Array(nameVals.size / 2){""}
                            val propertyValues = Array(propertyNames.size){""}
                            for (j in propertyNames.indices) {
                                propertyNames[j] = nameVals[j * 2]
                                propertyValues[j] = nameVals[j * 2 + 1]
                            }
                            val record = HistoryRecord(propertyNames, propertyValues, timestamp)
                            result.add(record)
                            leftCount--
                        }
                    }
                    i--
                }
                currentFile--
            }
        }
        return OrderedQueryResultSet(result)
    }

    private fun find(startDate: Date?, endDate: Date?, keywords: Array<String>?, field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?> {
        val result = TreeSet(HistoryRecordComparator())
        val filelist = filterFilesByDate(historyImpl.fileList, startDate, endDate)
        var currentProgress = HistorySearchProgressListener.PROGRESS_MINIMUM_VALUE.toDouble()
        var fileProgressStep = HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE.toDouble()
        if (filelist.size != 0) fileProgressStep = (HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE / filelist.size).toDouble()

        // start progress - minimum value
        fireProgressStateChanged(startDate, endDate, keywords, HistorySearchProgressListener.PROGRESS_MINIMUM_VALUE)
        val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
        for (filename in filelist) {
            val doc = historyImpl.getDocumentForFile(filename) ?: continue
            val nodes = doc.getElementsByTagName("record")
            var nodesProgressStep = fileProgressStep
            if (nodes.length != 0) nodesProgressStep = fileProgressStep / nodes.length
            var node: Node
            for (i in 0 until nodes.length) {
                node = nodes.item(i)
                var timestamp: Date
                val ts = node.attributes.getNamedItem("timestamp").nodeValue
                timestamp = try {
                    sdf.parse(ts)!!
                } catch (e: ParseException) {
                    Date(ts.toLong())
                }
                if (isInPeriod(timestamp, startDate, endDate)) {
                    val propertyNodes = node.childNodes
                    val record = filterByKeyword(propertyNodes, timestamp, keywords, field, caseSensitive)
                    if (record != null) {
                        result.add(record)
                    }
                }
                currentProgress += nodesProgressStep
                fireProgressStateChanged(startDate, endDate, keywords, currentProgress.toInt())
            }
        }

        // if maximum value is not reached fire an event
        if (currentProgress.toInt() < HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE) {
            fireProgressStateChanged(startDate, endDate, keywords, HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE)
        }
        return OrderedQueryResultSet(result)
    }

    private fun fireProgressStateChanged(startDate: Date?, endDate: Date?, keywords: Array<String>?, progress: Int) {
        val event = ProgressEvent(this, startDate, endDate, keywords, progress)
        synchronized(progressListeners) {
            val iter = progressListeners.iterator()
            while (iter.hasNext()) {
                val item = iter.next()
                item!!.progressChanged(event)
            }
        }
    }

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener
     * HistorySearchProgressListener
     */
    override fun addSearchProgressListener(listener: HistorySearchProgressListener?) {
        synchronized(progressListeners) { progressListeners.add(listener) }
    }

    /**
     * Removing progress listener
     *
     * @param listener
     * HistorySearchProgressListener
     */
    override fun removeSearchProgressListener(listener: HistorySearchProgressListener?) {
        synchronized(progressListeners) { progressListeners.remove(listener) }
    }

    /**
     * Count the number of messages that a search will return Actually only the last file is parsed and its nodes are
     * counted. We accept that the other files are full with max records, this way we escape parsing all files which
     * will significantly slow the process and for one search will parse the files twice.
     *
     * @return the number of searched messages
     * @throws UnsupportedOperationException
     * Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    @Throws(UnsupportedOperationException::class)
    override fun countRecords(): Int {
        var result = 0
        var lastFile: String? = null
        val filelistIter = historyImpl.fileList
        while (filelistIter.hasNext()) {
            lastFile = filelistIter.next()
            result += HistoryWriterImpl.MAX_RECORDS_PER_FILE
        }
        if (lastFile == null) return result
        val doc = historyImpl.getDocumentForFile(lastFile) ?: return result
        val nodes = doc.getElementsByTagName("record")
        result += nodes.length
        return result
    }

    /**
     * Used to compare HistoryRecords ant to be ordered in TreeSet
     */
    private class HistoryRecordComparator : Comparator<HistoryRecord> {
        override fun compare(h1: HistoryRecord, h2: HistoryRecord): Int {
            return h1.timestamp.compareTo(h2.timestamp)
        }
    }

    companion object {
        // regexp used for index of case(in)sensitive impl
        private const val REGEXP_END = ".*$"
        private const val REGEXP_SENSITIVE_START = "(?s)^.*"
        private const val REGEXP_INSENSITIVE_START = "(?si)^.*"

        /**
         * Evaluetes does `timestamp` is in the given time period.
         *
         * @param timestamp
         * Date
         * @param startDate
         * Date the start of the period
         * @param endDate
         * Date the end of the period
         * @return boolean
         */
        fun isInPeriod(timestamp: Date, startDate: Date?, endDate: Date?): Boolean {
            val tsLong = timestamp.time
            val startLong = startDate?.time ?: Long.MIN_VALUE
            val endLong = endDate?.time ?: Long.MAX_VALUE
            return tsLong in startLong until endLong
        }

        /**
         * If there is keyword restriction and doesn't match the conditions return null. Otherwise return the HistoryRecord
         * corresponding the given nodes.
         *
         * @param propertyNodes
         * NodeList
         * @param timestamp
         * Date
         * @param keywords
         * String[]
         * @param field
         * String
         * @param caseSensitive
         * boolean
         * @return HistoryRecord
         */
        fun filterByKeyword(propertyNodes: NodeList, timestamp: Date, keywords: Array<String>?, field: String?, caseSensitive: Boolean): HistoryRecord? {
            val nameVals = ArrayList<String>()
            val len = propertyNodes.length
            var targetNodeFound = false
            for (j in 0 until len) {
                val propertyNode = propertyNodes.item(j)
                if (propertyNode.nodeType == Node.ELEMENT_NODE) {
                    val nodeName = propertyNode.nodeName
                    val nestedNode = propertyNode.firstChild
                    if (nestedNode != null) {

                        // Get nested TEXT node's value
                        var nodeValue = nestedNode.nodeValue

                        // unescape xml chars, we have escaped when writing values
                        nodeValue = StringEscapeUtils.unescapeXml(nodeValue)
                        if (field != null && field == nodeName) {
                            targetNodeFound = true
                            if (!matchKeyword(nodeValue, keywords, caseSensitive)) // doesn't match the given keyword(s) so return nothing
                                return null
                        }
                        nameVals.add(nodeName)
                        // Get nested TEXT node's value
                        nameVals.add(nodeValue)
                    }
                }
            }

            // if we need to find a particular record but the target node is not present skip this record
            if (keywords != null && keywords.isNotEmpty() && !targetNodeFound) {
                return null
            }
            val propertyNames = Array(nameVals.size / 2){""}
            val propertyValues = Array(propertyNames.size){""}
            for (j in propertyNames.indices) {
                propertyNames[j] = nameVals[j * 2]
                propertyValues[j] = nameVals[j * 2 + 1]
            }
            return HistoryRecord(propertyNames, propertyValues, timestamp)
        }

        /**
         * Check if a value is in the given keyword(s) If no keyword(s) given must return true
         *
         * @param value
         * String
         * @param keywords
         * String[]
         * @param caseSensitive
         * boolean
         * @return boolean
         */
        private fun matchKeyword(value: String, keywords: Array<String>?, caseSensitive: Boolean): Boolean {
            if (keywords != null) {
                val regexpStart = if (caseSensitive) REGEXP_SENSITIVE_START else REGEXP_INSENSITIVE_START
                for (i in keywords.indices) {
                    if (!value.matches(Regex(regexpStart + Pattern.quote(keywords[i]) + REGEXP_END))) return false
                }

                // all keywords match return true
                return true
            }

            // if no keyword or keywords given we must not filter this record so will return true
            return true
        }
        /**
         * Used to limit the files if any starting or ending date exist So only few files to be searched.
         *
         * filelist Iterator
         * startDate Date
         * endDate Date
         * reverseOrder reverse order of files
         * @return Vector
         */
        @JvmOverloads
        fun filterFilesByDate(fileList: Iterator<String?>, startDate: Date?, endDate: Date?, reverseOrder: Boolean = false): Vector<String?> {
            if (startDate == null && endDate == null) {
                // no filtering needed then just return the same list
                val result = Vector<String?>()
                while (fileList.hasNext()) {
                    result.add(fileList.next())
                }
                Collections.sort(result) { o1: String?, o2: String? -> if (reverseOrder) return@sort o2!!.compareTo(o1!!) else return@sort o1!!.compareTo(o2!!) }
                return result
            }
            // first convert all files to long
            val files = TreeSet<Long>()
            while (fileList.hasNext()) {
                val filename = fileList.next()
                files.add(filename!!.substring(0, filename.length - 4).toLong())
            }
            val resultAsLong = TreeSet<Long>()

            // Temporary fix of a NoSuchElementException
            if (files.size == 0) {
                return Vector()
            }
            val startLong = startDate?.time ?: Long.MIN_VALUE
            val endLong = endDate?.time ?: Long.MAX_VALUE

            // get all records inclusive the one before the startDate
            for (f in files) {
                if (f in startLong..endLong) {
                    resultAsLong.add(f)
                }
            }

            // get the subset before the start date, to get its last element
            // if exists
            if (!files.isEmpty() && files.first() <= startLong) {
                val setBeforeTheInterval = files.subSet(files.first(), true, startLong, true)
                if (!setBeforeTheInterval.isEmpty()) resultAsLong.add(setBeforeTheInterval.last())
            }
            val result = Vector<String?>()
            val iter = resultAsLong.iterator()
            while (iter.hasNext()) {
                val item = iter.next()
                result.add("$item.xml")
            }
            Collections.sort(result) { o1: String?, o2: String? -> if (reverseOrder) return@sort o2!!.compareTo(o1!!) else return@sort o1!!.compareTo(o2!!) }
            return result
        }
    }
}