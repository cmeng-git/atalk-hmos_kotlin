/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

/**
 * Object which can store user specific key-values.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
open class DataObject {
    /**
     * The user-specific key-value associations stored in this instance.
     *
     *
     * Like the Widget implementation of Eclipse SWT, the storage type takes
     * into account that there are likely to be many
     * `DataObject` instances and `Map`s are thus
     * likely to impose increased memory use. While an array may very well
     * perform worse than a `Map` with respect to search, the
     * mechanism of user-defined key-value associations explicitly states that
     * it is not guaranteed to be optimized for any particular use and only
     * covers the most basic cases and performance-savvy code will likely
     * implement a more optimized solution anyway.
     *
     */
    private var data: Array<Any?>? = null

    /**
     * Gets the user data associated with this instance and a specific key.
     *
     * @param key the key of the user data associated with this instance to be retrieved
     * @return an `Object` which represents the value associated with
     * this instance and the specified `key`; `null` if no
     * association with the specified `key` exists in this instance
     */
    fun getData(key: Any?): Any? {
        if (key == null) throw NullPointerException("key")
        val index = dataIndexOf(key)
        return if (index == -1) null else data!![index + 1]
    }

    /**
     * Determines the index in `#data` of a specific key.
     *
     * @param key the key to retrieve the index in `#data` of
     * @return the index in `#data` of the specified `key`
     * if it is contained; `-1` if `key` is not
     * contained in `#data`
     */
    private fun dataIndexOf(key: Any): Int {
        if (data != null) {
            var index = 0
            while (index < data!!.size) {
                if (key == data!![index]) return index
                index += 2
            }
        }
        return -1
    }

    /**
     * Sets a
     * user-specific association in this instance in the form of a key-value
     * pair. If the specified `key` is already associated in this
     * instance with a value, the existing value is overwritten with the specified `value`.
     *
     *
     * The user-defined association created by this method and stored in this
     * instance is not serialized by this instance and is thus only meant for runtime use.
     *
     *
     *
     * The storage of the user data is implementation-specific and is thus not
     * guaranteed to be optimized for execution time and memory use.
     *
     *
     * @param key the key to associate in this instance with the specified value
     * @param value the value to be associated in this instance with the specified `key`
     */
    fun setData(key: Any?, value: Any?) {
        if (key == null) throw NullPointerException("key")
        val index = dataIndexOf(key)
        if (index == -1) {
            /*
             * If value is null, remove the association with key (or just don't add it).
             */
            if (value != null) {
                if (data == null) {
                    data = arrayOf(key, value)
                } else {
                    var length = data!!.size
                    val newData = arrayOfNulls<Any>(length + 2)
                    System.arraycopy(data, 0, newData, 0, length)
                    data = newData
                    data!![length++] = key
                    data!![length] = value
                }
            }
        } else {
            if (value == null) {
                val length = data!!.size - 2
                data = if (length > 0) {
                    val newData = arrayOfNulls<Any>(length)
                    System.arraycopy(data, 0, newData, 0, index)
                    System.arraycopy(data, index + 2, newData, index, length - index)
                    newData
                } else null
            } else data!![index + 1] = value
        }
    }
}