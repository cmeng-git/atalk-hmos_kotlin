/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.metahistory

import net.java.sip.communicator.service.callhistory.CallHistoryService
import net.java.sip.communicator.service.callhistory.CallPeerRecord
import net.java.sip.communicator.service.callhistory.CallRecord
import net.java.sip.communicator.service.callhistory.event.CallHistorySearchProgressListener
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.history.event.HistorySearchProgressListener
import net.java.sip.communicator.service.history.event.ProgressEvent
import net.java.sip.communicator.service.metahistory.MetaHistoryService
import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.msghistory.event.MessageHistorySearchProgressListener
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * The Meta History Service is wrapper around the other known history services. Query them all at
 * once, sort the result and return all merged records in one collection.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class MetaHistoryServiceImpl : MetaHistoryService, ServiceListener {
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private var bundleContext: BundleContext? = null

    /**
     * Caching of the used services
     */
    private val services = Hashtable<String?, Any>()
    private val progressListeners = ArrayList<HistorySearchProgressListener?>()

    /**
     * Returns all the records for the descriptor after the given date.
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByStartDate(services: Array<String>, descriptor: Any, startDate: Date): Collection<Any?> {
        val listenWrapper = MessageProgressWrapper(services.size)
        val result = TreeSet(RecordsComparator())
        for (i in services.indices) {
            val name = services[i]
            val serv = getService(name)
            if (serv is MessageHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                if (descriptor is MetaContact) {
                    result.addAll(serv.findByStartDate(descriptor, startDate))
                } else if (descriptor is ChatRoom) {
                    result.addAll(serv.findByStartDate(descriptor, startDate))
                }
                serv.removeSearchProgressListener(listenWrapper)
            } else if (serv is CallHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                result.addAll(serv.findByStartDate(startDate)!!)
                serv.removeSearchProgressListener(listenWrapper)
            }
        }
        listenWrapper.fireLastProgress(startDate, null, null)
        return result
    }

    /**
     * Returns all the records before the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param endDate Date the date of the last record to return
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByEndDate(services: Array<String>, descriptor: Any, endDate: Date): Collection<Any?> {
        val listenWrapper = MessageProgressWrapper(services.size)
        val result = TreeSet(RecordsComparator())
        for (i in services.indices) {
            val name = services[i]
            val serv = getService(name)
            if (serv is MessageHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                if (descriptor is MetaContact) {
                    result.addAll(serv.findByEndDate(descriptor, endDate))
                } else if (descriptor is ChatRoom) {
                    result.addAll(serv.findByEndDate(descriptor, endDate))
                }
                serv.removeSearchProgressListener(listenWrapper)
            } else if (serv is CallHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                result.addAll(serv.findByEndDate(endDate)!!)
                serv.removeSearchProgressListener(listenWrapper)
            }
        }
        listenWrapper.fireLastProgress(null, endDate, null)
        return result
    }

    /**
     * Returns all the records between the given dates
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByPeriod(services: Array<String>, descriptor: Any, startDate: Date, endDate: Date): Collection<Any?> {
        val listenWrapper = MessageProgressWrapper(services.size)
        val result = LinkedList<Any?>()
        for (i in services.indices) {
            val name = services[i]
            val serv = getService(name)
            if (serv is MessageHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                if (descriptor is MetaContact) {
                    result.addAll(serv.findByPeriod(descriptor, startDate, endDate))
                } else if (descriptor is ChatRoom) {
                    result.addAll(serv.findByPeriod(descriptor, startDate, endDate))
                }
                serv.removeSearchProgressListener(listenWrapper)
            } else if (serv is CallHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                result.addAll(serv.findByPeriod(startDate, endDate)!!)
                serv.removeSearchProgressListener(listenWrapper)
            }
        }
        listenWrapper.fireLastProgress(startDate, endDate, null)
        Collections.sort(result, RecordsComparator() as Comparator<in Any?>)
        return result
    }

    /**
     * Returns all the records between the given dates and having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     * @param keywords array of keywords
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByPeriod(services: Array<String>, descriptor: Any, startDate: Date,
            endDate: Date, keywords: Array<String>): Collection<Any?> {
        return findByPeriod(services, descriptor, startDate, endDate, keywords, false)
    }

    /**
     * Returns all the records between the given dates and having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByPeriod(services: Array<String>, descriptor: Any, startDate: Date,
            endDate: Date, keywords: Array<String>, caseSensitive: Boolean): Collection<Any?> {
        val listenWrapper = MessageProgressWrapper(services.size)
        val result = TreeSet(RecordsComparator())
        for (i in services.indices) {
            val name = services[i]
            val serv = getService(name)
            if (serv is MessageHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                if (descriptor is MetaContact) {
                    result.addAll(serv.findByPeriod(descriptor, startDate, endDate, keywords, caseSensitive))
                } else if (descriptor is ChatRoom) {
                    result.addAll(serv.findByPeriod(descriptor, startDate, endDate, keywords, caseSensitive))
                }
                serv.removeSearchProgressListener(listenWrapper)
            } else if (serv is CallHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                val cs = serv.findByPeriod(startDate, endDate)
                for (callRecord in cs!!) {
                    if (matchCallPeer(callRecord!!.peerRecords, keywords, caseSensitive)) result.add(callRecord)
                }
                serv.removeSearchProgressListener(listenWrapper)
            }
        }
        listenWrapper.fireLastProgress(startDate, endDate, keywords)
        return result
    }

    /**
     * Returns all the records having the given keyword
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keyword keyword
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByKeyword(services: Array<String>, descriptor: Any, keyword: String): Collection<Any?> {
        return findByKeyword(services, descriptor, keyword, false)
    }

    /**
     * Returns all the records having the given keyword
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByKeyword(services: Array<String>, descriptor: Any, keyword: String, caseSensitive: Boolean): Collection<Any?> {
        return findByKeywords(services, descriptor, arrayOf(keyword), caseSensitive)
    }

    /**
     * Returns all the records having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keywords keyword
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByKeywords(services: Array<String>, descriptor: Any, keywords: Array<String>): Collection<Any?> {
        return findByKeywords(services, descriptor, keywords, false)
    }

    /**
     * Returns all the records having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findByKeywords(services: Array<String>, descriptor: Any, keywords: Array<String>, caseSensitive: Boolean): Collection<Any?> {
        val listenWrapper = MessageProgressWrapper(services.size)
        val result = TreeSet(RecordsComparator())
        for (i in services.indices) {
            val name = services[i]
            val serv = getService(name)
            if (serv is MessageHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                if (descriptor is MetaContact) {
                    result.addAll(serv.findByKeywords(descriptor, keywords, caseSensitive))
                } else if (descriptor is ChatRoom) {
                    result.addAll(serv.findByKeywords(descriptor, keywords, caseSensitive))
                }
                serv.removeSearchProgressListener(listenWrapper)
            } else if (serv is CallHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)

                // this will get all call records
                val cs = serv.findByEndDate(Date())
                for (callRecord in cs!!) {
                    if (matchCallPeer(callRecord!!.peerRecords, keywords, caseSensitive)) result.add(callRecord)
                }
                serv.removeSearchProgressListener(listenWrapper)
            }
        }
        listenWrapper.fireLastProgress(null, null, keywords)
        return result
    }

    /**
     * Returns the supplied number of recent records.
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param count messages count
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findLast(services: Array<String>, descriptor: Any, count: Int): Collection<Any> {
        val listenWrapper = MessageProgressWrapper(services.size)
        val result = TreeSet(RecordsComparator())
        for (i in services.indices) {
            val name = services[i]
            val serv = getService(name)
            if (serv is MessageHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)

                // will also get fileHistory for metaContact and chatRoom
                if (descriptor is MetaContact) {
                    result.addAll(serv.findLast(descriptor, count))
                } else if (descriptor is ChatRoom) {
                    result.addAll(serv.findLast(descriptor, count))
                }
                serv.removeSearchProgressListener(listenWrapper)
            } else if (serv is CallHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                result.addAll(serv.findLast(count))
                serv.removeSearchProgressListener(listenWrapper)
            }
        }
        listenWrapper.fireLastProgress(null, null, null)
        val resultAsList = LinkedList(result)
        var startIndex = resultAsList.size - count
        if (startIndex < 0) startIndex = 0
        return resultAsList.subList(startIndex, resultAsList.size)
    }

    /**
     * Returns the supplied number of recent records after the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate messages after date
     * @param count messages count
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findFirstMessagesAfter(services: Array<String>, descriptor: Any, startDate: Date, count: Int): Collection<Any> {
        val listenWrapper = MessageProgressWrapper(services.size)
        val result = TreeSet(RecordsComparator())
        for (i in services.indices) {
            val name = services[i]
            val serv = getService(name)
            if (serv is MessageHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                if (descriptor is MetaContact) {
                    result.addAll(serv.findFirstMessagesAfter(descriptor, startDate, count))
                } else if (descriptor is ChatRoom) {
                    result.addAll(serv.findFirstMessagesAfter(descriptor, startDate, count))
                }
                serv.removeSearchProgressListener(listenWrapper)
            } else if (serv is CallHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                val col = serv.findByStartDate(startDate)
                if (col!!.size > count) {
                    // before we make a sublist make sure there are sorted in the right order
                    val l = LinkedList(col)
                    Collections.sort(l, RecordsComparator() as Comparator<in Any?>)
                    result.addAll(l.subList(0, count))
                } else result.addAll(col)
                serv.removeSearchProgressListener(listenWrapper)
            }
        }
        listenWrapper.fireLastProgress(startDate, null, null)
        val resultAsList = LinkedList(result)
        var toIndex = count
        if (toIndex > resultAsList.size) toIndex = resultAsList.size
        return resultAsList.subList(0, toIndex)
    }

    /**
     * Returns the supplied number of recent records before the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param endDate messages before date
     * @param count messages count
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    override fun findLastMessagesBefore(services: Array<String>, descriptor: Any, endDate: Date, count: Int): Collection<Any> {
        val listenWrapper = MessageProgressWrapper(services.size)
        val result = TreeSet(RecordsComparator())
        for (i in services.indices) {
            val name = services[i]
            val serv = getService(name)
            if (serv is MessageHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                if (descriptor is MetaContact) {
                    result.addAll(serv.findLastMessagesBefore(descriptor, endDate, count))
                } else if (descriptor is ChatRoom) {
                    result.addAll(serv.findLastMessagesBefore(descriptor, endDate, count))
                }
                serv.removeSearchProgressListener(listenWrapper)
            } else if (serv is CallHistoryService) {
                listenWrapper.setIx(i)
                serv.addSearchProgressListener(listenWrapper)
                val col = serv.findByEndDate(endDate)
                if (col!!.size > count) {
                    val l = LinkedList(col)
                    result.addAll(l.subList(l.size - count, l.size))
                } else result.addAll(col)
                serv.removeSearchProgressListener(listenWrapper)
            }
        }
        listenWrapper.fireLastProgress(endDate, null, null)
        val resultAsList = LinkedList(result)
        var startIndex = resultAsList.size - count
        if (startIndex < 0) startIndex = 0
        return resultAsList.subList(startIndex, resultAsList.size)
    }

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    override fun addSearchProgressListener(listener: HistorySearchProgressListener) {
        synchronized(progressListeners) { if (!progressListeners.contains(listener)) progressListeners.add(listener) }
    }

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    override fun removeSearchProgressListener(listener: HistorySearchProgressListener) {
        synchronized(progressListeners) { progressListeners.remove(listener) }
    }

    private fun getService(name: String): Any? {
        var serv = services[name]
        if (serv == null) {
            val refHistory = bundleContext!!.getServiceReference(name)
            serv = bundleContext!!.getService<Any>(refHistory as ServiceReference<Any>)
        }
        return serv
    }

    private fun matchAnyCallPeer(cps: List<CallPeerRecord>, keywords: Array<String>,
            caseSensitive: Boolean): Boolean {
        for (callPeer in cps) {
            for (k in keywords) {
                if (caseSensitive && callPeer.peerAddress!!.contains(k)) return true else if (callPeer.peerAddress!!.lowercase(Locale.getDefault()).contains(k.lowercase(Locale.getDefault()))) return true
            }
        }
        return false
    }

    private fun matchCallPeer(cps: List<CallPeerRecord>, keywords: Array<String>,
            caseSensitive: Boolean): Boolean {
        for (callPeer in cps) {
            var match = false
            for (kw in keywords) {
                if (caseSensitive) {
                    if (callPeer.peerAddress!!.contains(kw)) {
                        match = true
                    } else {
                        match = false
                        break
                    }
                } else if (callPeer.peerAddress!!.lowercase(Locale.getDefault()).contains(kw.lowercase(Locale.getDefault()))) {
                    match = true
                } else {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

    override fun serviceChanged(serviceEvent: ServiceEvent) {
        if (serviceEvent.type == ServiceEvent.UNREGISTERING) {
            val sService = bundleContext!!.getService(serviceEvent.serviceReference)
            services.remove(sService.javaClass.name)
        }
    }

    /**
     * starts the service.
     *
     * @param bc BundleContext
     */
    fun start(bc: BundleContext) {
        Timber.d("Starting the call history implementation.")
        bundleContext = bc
        services.clear()

        // start listening for newly register or removed services
        bc.addServiceListener(this)
    }

    /**
     * stops the service.
     *
     * @param bc BundleContext
     */
    fun stop(bc: BundleContext) {
        bc.removeServiceListener(this)
        services.clear()
    }

    /**
     * Used to compare various records to be ordered in TreeSet according their timestamp.
     */
    private class RecordsComparator : Comparator<Any> {
        private fun getDate(o: Any): Date? {
            var date: Date? = Date(0)
            if (o is MessageDeliveredEvent) date = o.getTimestamp()
            else if (o is MessageReceivedEvent) date = o.getTimestamp()
            else if (o is ChatRoomMessageDeliveredEvent) date = o.getTimestamp()
            else if (o is ChatRoomMessageReceivedEvent) date = o.getTimestamp()
            else if (o is CallRecord) date = o.startTime
            else if (o is FileRecord) date = o.date
            return date
        }

        override fun compare(o1: Any, o2: Any): Int {
            val date1 = getDate(o1)
            val date2 = getDate(o2)
            return date1!!.compareTo(date2)
        }
    }

    private inner class MessageProgressWrapper(private val count: Int) : MessageHistorySearchProgressListener, CallHistorySearchProgressListener {
        private var ix = 0
        fun setIx(ix: Int) {
            this.ix = ix
        }

        private fun fireProgress(origProgress: Int, maxVal: Int, startDate: Date?, endDate: Date?, keywords: Array<String>?) {
            val ev = ProgressEvent(this@MetaHistoryServiceImpl, startDate, endDate, keywords)
            val part1 = origProgress / (maxVal.toDouble() * count)
            val convProgress = (part1 * HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE
                    + ix * HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE / count)
            ev.progress = convProgress.toInt()
            fireEvent(ev)
        }

        private fun fireEvent(ev: ProgressEvent) {
            var listeners: Iterable<HistorySearchProgressListener?>
            synchronized(progressListeners) { listeners = ArrayList(progressListeners) }
            for (listener in listeners) listener!!.progressChanged(ev)
        }

        fun fireLastProgress(startDate: Date?, endDate: Date?, keywords: Array<String>?) {
            val ev = ProgressEvent(this@MetaHistoryServiceImpl, startDate, endDate, keywords)
            ev.progress = HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE
            fireEvent(ev)
        }

        override fun progressChanged(evt: net.java.sip.communicator.service.msghistory.event.ProgressEvent) {
            fireProgress(evt.progress, MessageHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE,
                    evt.startDate, evt.endDate, evt.keywords)
        }

        override fun progressChanged(evt: net.java.sip.communicator.service.callhistory.event.ProgressEvent) {
            fireProgress(evt.progress, CallHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE,
                    evt.startDate, evt.endDate, null)
        }
    }
}