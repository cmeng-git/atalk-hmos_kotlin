/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import java.lang.ref.SoftReference
import javax.media.Buffer

/**
 * Caches `short` arrays for the purposes of reducing garbage collection.
 *
 * @author Lyubomir Marinov
 */
internal class ShortArrayCache {
    /**
     * The cache of `short` arrays managed by this instance for the purposes of reducing
     * garbage collection.
     */
    private var elements: SoftReference<Array<ShortArray?>>? = null

    /**
     * The number of elements at the head of [.elements] which are currently utilized.
     * Introduced to limit the scope of iteration.
     */
    private var length = 0

    /**
     * Allocates a `short` array with length/size greater than or equal to a specific
     * number. The returned array may be a newly-initialized instance or one of the elements
     * cached/pooled by this instance.
     *
     * @param minSize
     * the minimum length/size of the array to be returned
     * @return a `short` array with length/size greater than or equal to `minSize`
     */
    @Synchronized
    fun allocateShortArray(minSize: Int): ShortArray {
        val elements = if (elements == null) null else elements!!.get()
        if (elements != null) {
            for (i in 0 until length) {
                val element = elements[i]
                if (element != null && element.size >= minSize) {
                    elements[i] = null
                    return element
                }
            }
        }
        return ShortArray(minSize)
    }

    /**
     * Returns a specific non-`null` `short` array into the cache/pool implemented by
     * this instance.
     *
     * @param shortArray
     * the `short` array to be returned into the cache/pool implemented by this
     * instance. If `null` , the method does nothing.
     */
    @Synchronized
    fun deallocateShortArray(shortArray: ShortArray?) {
        if (shortArray == null) return
        var elements: Array<ShortArray?>? = null
        if (this.elements == null || this.elements!!.get().also { elements = it!! } == null) {
            elements = arrayOfNulls(8)
            this.elements = SoftReference(elements)
            length = 0
        }
        if (length != 0) for (i in 0 until length) if (elements!![i].contentEquals(shortArray)) return
        if (length == elements!!.size) {
            /*
			 * Compact the non-null elements at the head of the storage in order to possibly
			 * prevent reallocation.
			 */
            var newLength = 0
            for (i in 0 until length) {
                val element = elements!![i]
                if (element != null) {
                    if (i != newLength) {
                        elements!![newLength] = element
                        elements!![i] = null
                    }
                    newLength++
                }
            }
            if (newLength == length) {
                // Expand the storage.
                val newElements = arrayOfNulls<ShortArray>(elements!!.size + 4)
                System.arraycopy(elements!!, 0, newElements, 0, elements!!.size)
                elements = newElements
                this.elements = SoftReference(elements)
            } else {
                length = newLength
            }
        }
        elements!![length++] = shortArray
    }

    /**
     * Ensures that the `data` property of a specific `Buffer` is set to an
     * `short` array with length/size greater than or equal to a specific number.
     *
     * @param buffer
     * the `Buffer` the `data` property of which is to be validated
     * @param newSize
     * the minimum length/size of the `short` array to be set as the value of the
     * `data` property of the specified `buffer` and to be returned
     * @return the value of the `data` property of the specified `buffer` which is
     * guaranteed to have a length/size of at least `newSize` elements
     */
    fun validateShortArraySize(buffer: Buffer?, newSize: Int): ShortArray {
        val data = buffer!!.data
        var shortArray: ShortArray?
        if (data is ShortArray) {
            shortArray = data
            if (shortArray.size < newSize) {
                deallocateShortArray(shortArray)
                shortArray = null
            }
        } else shortArray = null
        if (shortArray == null) {
            shortArray = allocateShortArray(newSize)
            buffer.data = shortArray
        }
        return shortArray
    }
}