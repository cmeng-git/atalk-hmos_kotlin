/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

/**
 * Implements a circular `byte` array.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class CircularByteArray(length: Int) {
    /**
     * The elements of this `CircularByteArray`.
     */
    private val elements: ByteArray

    /**
     * The index at which the next invocation of [.push] is to insert an element.
     */
    private var tail: Int

    /**
     * Initializes a new `CircularBufferArray` instance with a specific length.
     *
     * @param length
     * the length i.e. the number of elements of the new instance
     */
    init {
        elements = ByteArray(length)
        tail = 0
    }

    /**
     * Adds a specific element at the end of this `CircularByteArray`.
     *
     * @param element
     * the element to add at the end of this `CircularByteArray`
     */
    @Synchronized
    fun push(element: Byte) {
        var tail = tail
        elements[tail] = element
        tail++
        if (tail >= elements.size) tail = 0
        this.tail = tail
    }

    /**
     * Copies the elements of this `CircularByteArray` into a new `byte` array.
     *
     * @return a new `byte` array which contains the same elements and in the same order as
     * this `CircularByteArray`
     */
    @Synchronized
    fun toArray(): ByteArray? {
        val elements = elements
        val array: ByteArray?
        if (elements == null) {
            array = null
        } else {
            array = ByteArray(elements.size)
            var i = 0
            var index = tail
            while (i < elements.size) {
                array[i] = elements[index]
                index++
                if (index >= elements.size) index = 0
                i++
            }
        }
        return array
    }
}