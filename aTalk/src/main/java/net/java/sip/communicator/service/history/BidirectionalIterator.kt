/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

/**
 * The standard Java Iterator is uni-directional, allowing the user to explore the contents of a collection in one way
 * only. This interface defines a bi-directional iterator, permiting the user to go forwards and backwards in a
 * collection.
 *
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
interface BidirectionalIterator<T> : MutableIterator<T> {
    /**
     * Returns true if the iteration has elements preceeding the current one. (In other words, returns true if
     * `prev` would return an element rather than throwing an exception.)
     *
     * @return true if the iterator has preceeding elements.
     */
    fun hasPrev(): Boolean

    /**
     * Returns the previous element in the iteration.
     *
     * @return the previous element in the iteration.
     *
     * @throws NoSuchElementException
     * iteration has no more elements.
     */
    @Throws(NoSuchElementException::class)
    fun prev(): T
}