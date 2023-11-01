/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

/**
 *
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
interface QueryResultSet<T> : BidirectionalIterator<T> {
    /**
     * A strongly-typed variant of `next()`.
     *
     * @return the next history record.
     *
     * @throws NoSuchElementException
     * iteration has no more elements.
     */
    @Throws(NoSuchElementException::class)
    fun nextRecord(): T

    /**
     * A strongly-typed variant of `prev()`.
     *
     * @return the previous history record.
     *
     * @throws NoSuchElementException
     * iteration has no more elements.
     */
    @Throws(NoSuchElementException::class)
    fun prevRecord(): T
}