/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.util

import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.util.*

/**
 * Provides HTML and XML entity utilities.
 *
 * @author [Alexander Day Chaffee](mailto:alex@purpletech.com)
 * @author [Gary Gregory](mailto:ggregory@seagullsw.com)
 * @version $Id$
 * @see [ISO Entities](http://hotwired.lycos.com/webmonkey/reference/special_characters/)
 *
 * @see [HTML 3.2 Character Entities for ISO Latin-1](https://www.w3.org/TR/REC-html32.latin1)
 *
 * @see [HTML 4.0 Character entity references](https://www.w3.org/TR/REC-html40/sgml/entities.html)
 *
 * @see [HTML 4.01 Character References](https://www.w3.org/TR/html401/charset.html.h-5.3)
 *
 * @see [HTML 4.01 Code positions](https://www.w3.org/TR/html401/charset.html.code-position)
 *
 * @since 2.0
 */
internal class Entities {
    internal interface EntityMap {
        /**
         * Add an entry to this entity map.
         *
         * @param name the entity name
         * @param value the entity value
         */
        fun add(name: String, value: Int)

        /**
         * Returns the name of the entity identified by the specified value.
         *
         * @param value the value to locate
         * @return entity name associated with the specified value
         */
        fun name(value: Int): String?

        /**
         * Returns the value of the entity identified by the specified name.
         *
         * @param name the name to locate
         * @return entity value associated with the specified name
         */
        fun value(name: String): Int
    }

    internal open class PrimitiveEntityMap : EntityMap {
        private val mapNameToValue = HashMap<String, Int>()
        private val mapValueToName = IntHashMap()

        /**
         * {@inheritDoc}
         */
        override fun add(name: String, value: Int) {
            mapNameToValue[name] = value
            mapValueToName.put(value, name)
        }

        /**
         * {@inheritDoc}
         */
        override fun name(value: Int): String? {
            return mapValueToName[value] as String?
        }

        /**
         * {@inheritDoc}
         */
        override fun value(name: String): Int {
            val value = mapNameToValue[name]
            return value ?: -1
        }
    }

    internal abstract class MapIntMap : EntityMap {
        protected var mapNameToValue: MutableMap<String, Int>? = null
        protected var mapValueToName: MutableMap<Int, String>? = null

        /**
         * {@inheritDoc}
         */
        override fun add(name: String, value: Int) {
            mapNameToValue!![name] = value
            mapValueToName!![value] = name
        }

        /**
         * {@inheritDoc}
         */
        override fun name(value: Int): String? {
            return mapValueToName!![value]
        }

        /**
         * {@inheritDoc}
         */
        override fun value(name: String): Int {
            val value = mapNameToValue!![name]
            return value ?: -1
        }
    }

    internal class HashEntityMap : MapIntMap() {
        /**
         * Constructs a new instance of `HashEntityMap`.
         */
        init {
            mapNameToValue = HashMap()
            mapValueToName = HashMap()
        }
    }

    internal class TreeEntityMap : MapIntMap() {
        /**
         * Constructs a new instance of `TreeEntityMap`.
         */
        init {
            mapNameToValue = TreeMap()
            mapValueToName = TreeMap()
        }
    }

    internal class LookupEntityMap : PrimitiveEntityMap() {
        private var lookupTable: Array<String?>? = null

        /**
         * {@inheritDoc}
         */
        override fun name(value: Int): String? {
            return if (value < LOOKUP_TABLE_SIZE) {
                lookupTable()!![value]
            } else super.name(value)
        }

        /**
         * Returns the lookup table for this entity map. The lookup table is created if it has not been previously.
         *
         * @return the lookup table
         */
        private fun lookupTable(): Array<String?>? {
            if (lookupTable == null) {
                createLookupTable()
            }
            return lookupTable
        }

        /**
         * Creates an entity lookup table of LOOKUP_TABLE_SIZE elements, initialized with entity names.
         */
        private fun createLookupTable() {
            lookupTable = arrayOfNulls(LOOKUP_TABLE_SIZE)
            for (i in 0 until LOOKUP_TABLE_SIZE) {
                lookupTable!![i] = super.name(i)
            }
        }
    }

    internal open class ArrayEntityMap : EntityMap {
        private var growBy = 100
        protected var size = 0
        protected var names: Array<String?>
        protected var values: IntArray

        /**
         * Constructs a new instance of `ArrayEntityMap`.
         */
        constructor() {
            names = arrayOfNulls(growBy)
            values = IntArray(growBy)
        }

        /**
         * Constructs a new instance of `ArrayEntityMap` specifying the size by which the array should
         * grow.
         *
         * @param growBy array will be initialized to and will grow by this amount
         */
        constructor(growBy: Int) {
            this.growBy = growBy
            names = arrayOfNulls(growBy)
            values = IntArray(growBy)
        }

        /**
         * {@inheritDoc}
         */
        override fun add(name: String, value: Int) {
            ensureCapacity(size + 1)
            names[size] = name
            values[size] = value
            size++
        }

        /**
         * Verifies the capacity of the entity array, adjusting the size if necessary.
         *
         * @param capacity size the array should be
         */
        protected fun ensureCapacity(capacity: Int) {
            if (capacity > names.size) {
                val newSize = Math.max(capacity, size + growBy)
                val newNames = arrayOfNulls<String>(newSize)
                System.arraycopy(names, 0, newNames, 0, size)
                names = newNames
                val newValues = IntArray(newSize)
                System.arraycopy(values, 0, newValues, 0, size)
                values = newValues
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun name(value: Int): String? {
            for (i in 0 until size) {
                if (values[i] == value) {
                    return names[i]
                }
            }
            return null
        }

        /**
         * {@inheritDoc}
         */
        override fun value(name: String): Int {
            for (i in 0 until size) {
                if (names[i] == name) {
                    return values[i]
                }
            }
            return -1
        }
    }

    internal class BinaryEntityMap : ArrayEntityMap {
        /**
         * Constructs a new instance of `BinaryEntityMap`.
         */
        constructor() : super() {}

        /**
         * Constructs a new instance of `ArrayEntityMap` specifying the size by which the underlying array
         * should grow.
         *
         * @param growBy array will be initialized to and will grow by this amount
         */
        constructor(growBy: Int) : super(growBy) {}

        /**
         * Performs a binary search of the entity array for the specified key. This method is based on code in
         * [java.util.Arrays].
         *
         * @param key the key to be found
         * @return the index of the entity array matching the specified key
         */
        private fun binarySearch(key: Int): Int {
            var low = 0
            var high = size - 1
            while (low <= high) {
                val mid = low + high shr 1
                val midVal = values[mid]
                if (midVal < key) {
                    low = mid + 1
                } else if (midVal > key) {
                    high = mid - 1
                } else {
                    return mid // key found
                }
            }
            return -(low + 1) // key not found.
        }

        /**
         * {@inheritDoc}
         */
        override fun add(name: String, value: Int) {
            ensureCapacity(size + 1)
            var insertAt = binarySearch(value)
            if (insertAt > 0) {
                return  // note: this means you can't insert the same value twice
            }
            insertAt = -(insertAt + 1) // binarySearch returns it negative and off-by-one
            System.arraycopy(values, insertAt, values, insertAt + 1, size - insertAt)
            values[insertAt] = value
            System.arraycopy(names, insertAt, names, insertAt + 1, size - insertAt)
            names[insertAt] = name
            size++
        }

        /**
         * {@inheritDoc}
         */
        override fun name(value: Int): String? {
            val index = binarySearch(value)
            return if (index < 0) {
                null
            } else names[index]
        }
    }

    // package scoped for testing
    var map: EntityMap = LookupEntityMap()

    /**
     * Adds entities to this entity.
     *
     * @param entityArray array of entities to be added
     */
    fun addEntities(entityArray: Array<Array<String>>) {
        for (i in entityArray.indices) {
            addEntity(entityArray[i][0], entityArray[i][1].toInt())
        }
    }

    /**
     * Add an entity to this entity.
     *
     * @param name name of the entity
     * @param value vale of the entity
     */
    fun addEntity(name: String, value: Int) {
        map.add(name, value)
    }

    /**
     * Returns the name of the entity identified by the specified value.
     *
     * @param value the value to locate
     * @return entity name associated with the specified value
     */
    fun entityName(value: Int): String? {
        return map.name(value)
    }

    /**
     * Returns the value of the entity identified by the specified name.
     *
     * @param name the name to locate
     * @return entity value associated with the specified name
     */
    fun entityValue(name: String): Int {
        return map.value(name)
    }

    /**
     * Escapes the characters in a `String`.
     *
     * For example, if you have called addEntity(&quot;foo&quot;, 0xA1), escape(&quot;\u00A1&quot;) will return
     * &quot;&amp;foo;&quot;
     *
     * @param str The `String` to escape.
     * @return A new escaped `String`.
     */
    fun escape(str: String): String {
        val stringWriter = createStringWriter(str)
        try {
            this.escape(stringWriter, str)
        } catch (e: IOException) {
            // This should never happen because ALL the StringWriter methods called by #escape(Writer, String) do not
            // throw IOExceptions.
            throw RuntimeException(e)
        }
        return stringWriter.toString()
    }

    /**
     * Escapes the characters in the `String` passed and writes the result to the `Writer`
     * passed.
     *
     * @param writer The `Writer` to write the results of the escaping to. Assumed to be a non-null value.
     * @param str The `String` to escape. Assumed to be a non-null value.
     * @throws IOException when `Writer` passed throws the exception from calls
     * to the [Writer.write] methods.
     * @see .escape
     * @see Writer
     */
    @Throws(IOException::class)
    fun escape(writer: Writer, str: String) {
        val len = str.length
        for (i in 0 until len) {
            val c = str[i]
            val entityName = entityName(c.code)
            if (entityName == null) {
                if (c.code > 0x7F) {
                    writer.write("&#")
                    writer.write(Integer.toString(c.code, 10))
                    writer.write(';'.code)
                } else {
                    writer.write(c.code)
                }
            } else {
                writer.write('&'.code)
                writer.write(entityName)
                writer.write(';'.code)
            }
        }
    }

    /**
     * Unescapes the entities in a `String`.
     *
     * For example, if you have called addEntity(&quot;foo&quot;, 0xA1), unescape(&quot;&amp;foo;&quot;) will return
     * &quot;\u00A1&quot;
     *
     * @param str The `String` to escape.
     * @return A new escaped `String`.
     */
    fun unescape(str: String): String {
        val firstAmp = str.indexOf('&')
        return if (firstAmp < 0) {
            str
        } else {
            val stringWriter = createStringWriter(str)
            try {
                doUnescape(stringWriter, str, firstAmp)
            } catch (e: IOException) {
                // This should never happen because ALL the StringWriter methods called by #escape(Writer, String)
                // do not throw IOExceptions.
                throw RuntimeException(e)
            }
            stringWriter.toString()
        }
    }

    /**
     * Make the StringWriter 10% larger than the source String to avoid growing the writer
     *
     * @param str The source string
     * @return A newly created StringWriter
     */
    private fun createStringWriter(str: String): StringWriter {
        return StringWriter((str.length + str.length * 0.1).toInt())
    }

    /**
     * Unescapes the escaped entities in the `String` passed and writes the result to the
     * `Writer` passed.
     *
     * @param writer The `Writer` to write the results to; assumed to be non-null.
     * @param str The source `String` to unescape; assumed to be non-null.
     * @throws IOException when `Writer` passed throws the exception from calls to the [Writer.write]
     * methods.
     * @see .escape
     * @see Writer
     */
    @Throws(IOException::class)
    fun unescape(writer: Writer, str: String) {
        val firstAmp = str.indexOf('&')
        if (firstAmp < 0) {
            writer.write(str)
        } else {
            doUnescape(writer, str, firstAmp)
        }
    }

    /**
     * Underlying unescape method that allows the optimisation of not starting from the 0 index again.
     *
     * @param writer The `Writer` to write the results to; assumed to be non-null.
     * @param str The source `String` to unescape; assumed to be non-null.
     * @param firstAmp The `int` index of the first ampersand in the source String.
     * @throws IOException when `Writer` passed throws the exception from calls to the [Writer.write]
     * methods.
     */
    @Throws(IOException::class)
    private fun doUnescape(writer: Writer, str: String, firstAmp: Int) {
        writer.write(str, 0, firstAmp)
        val len = str.length
        var i = firstAmp
        while (i < len) {
            val c = str[i]
            if (c == '&') {
                val nextIdx = i + 1
                val semiColonIdx = str.indexOf(';', nextIdx)
                if (semiColonIdx == -1) {
                    writer.write(c.code)
                    i++
                    continue
                }
                val amphersandIdx = str.indexOf('&', i + 1)
                if (amphersandIdx != -1 && amphersandIdx < semiColonIdx) {
                    // Then the text looks like &...&...;
                    writer.write(c.code)
                    i++
                    continue
                }
                val entityContent = str.substring(nextIdx, semiColonIdx)
                var entityValue = -1
                val entityContentLen = entityContent.length
                if (entityContentLen > 0) {
                    if (entityContent[0] == '#') { // escaped value content is an integer (decimal or
                        // hexidecimal)
                        if (entityContentLen > 1) {
                            val isHexChar = entityContent[1]
                            try {
                                entityValue = when (isHexChar) {
                                    'X', 'x' -> {
                                        entityContent.substring(2).toInt(16)
                                    }
                                    else -> {
                                        entityContent.substring(1).toInt(10)
                                    }
                                }
                                if (entityValue > 0xFFFF) {
                                    entityValue = -1
                                }
                            } catch (e: NumberFormatException) {
                                entityValue = -1
                            }
                        }
                    } else { // escaped value content is an entity name
                        entityValue = entityValue(entityContent)
                    }
                }
                if (entityValue == -1) {
                    writer.write('&'.code)
                    writer.write(entityContent)
                    writer.write(';'.code)
                } else {
                    writer.write(entityValue)
                }
                i = semiColonIdx // move index up to the semi-colon
            } else {
                writer.write(c.code)
            }
            i++
        }
    }

    /**
     *
     * A hash map that uses primitive ints for the key rather than objects.
     *
     *
     * Note that this class is for internal optimization purposes only, and may
     * not be supported in future releases of Jakarta Commons Lang.  Utilities of
     * this sort may be included in future releases of Jakarta Commons Collections.
     *
     * @author Justin Couch
     * @author Alex Chaffee (alex@apache.org)
     * @author Stephen Colebourne
     * @version $Revision$
     * @see java.util.HashMap
     *
     * @since 2.0
     */
    internal class IntHashMap @JvmOverloads constructor(initialCapacity_: Int = 20, loadFactor: Float = 0.75f) {
        /**
         * The hash table data.
         */
        @Transient
        private var table: Array<Entry?>

        /**
         * The total number of entries in the hash table.
         */
        @Transient
        private var count = 0

        /**
         * The table is rehashed when its size exceeds this threshold.  (The
         * value of this field is (int)(capacity * loadFactor).)
         *
         * @serial
         */
        private var threshold: Int

        /**
         * The load factor for the hashtable.
         *
         * @serial
         */
        private val loadFactor: Float

        /**
         *
         * Innerclass that acts as a datastructure to create a new entry in the table.
         */
        private class Entry // this.key = key;
        /**
         *
         * Create a new entry with the given values.
         *
         * @param hash The code used to hash the
        object with
         * @param key The key used to enter this in the table
         * @param value The value for this key
         * @param next A reference to the next entry in the table
         */
        constructor(val hash: Int, key: Int, //            final int key;
                var value: Any?, var next: Entry?) {
        }
        /**
         *
         * Constructs a new, empty hashtable with the specified initial
         * capacity and the specified load factor.
         *
         * @param initialCapacity_ the initial capacity of the hashtable.
         * @param loadFactor the load factor of the hashtable.
         * @throws IllegalArgumentException if the initial capacity is less
         * than zero, or if the load factor is nonpositive.
         */
        /**
         *
         * Constructs a new, empty hashtable with a default capacity and load
         * factor, which is `20` and `0.75` respectively.
         */
        /**
         *
         * Constructs a new, empty hashtable with the specified initial capacity
         * and default load factor, which is `0.75`.
         *
         * @param initialCapacity the initial capacity of the hashtable.
         * @throws IllegalArgumentException if the initial capacity is less
         * than zero.
         */
        init {
            var initialCapacity = initialCapacity_
            require(initialCapacity >= 0) { "Illegal Capacity: $initialCapacity" }
            require(loadFactor > 0) { "Illegal Load: $loadFactor" }
            if (initialCapacity == 0) {
                initialCapacity = 1
            }
            this.loadFactor = loadFactor
            table = arrayOfNulls(initialCapacity)
            threshold = (initialCapacity * loadFactor).toInt()
        }

        /**
         *
         * Returns the number of keys in this hashtable.
         *
         * @return the number of keys in this hashtable.
         */
        fun size(): Int {
            return count
        }

        /**
         *
         * Tests if this hashtable maps no keys to values.
         *
         * @return `true` if this hashtable maps no keys to values; `false` otherwise.
         */
        val isEmpty: Boolean
            get() = count == 0

        /**
         *
         * Tests if some key maps into the specified value in this hashtable.
         * This operation is more expensive than the `containsKey` method.
         *
         *
         * Note that this method is identical in functionality to containsValue,
         * (which is part of the Map interface in the collections framework).
         *
         * @param value a value to search for.
         * @return `true` if and only if some key maps to the
         * `value` argument in this hashtable as
         * determined by the `equals` method;
         * `false` otherwise.
         * @throws NullPointerException if the value is `null`.
         * @see .containsKey
         * @see .containsValue
         * @see java.util.Map
         */
        operator fun contains(value: Any?): Boolean {
            if (value == null) {
                throw NullPointerException()
            }
            val tab = table
            var i = tab.size
            while (i-- > 0) {
                var e = tab[i]
                while (e != null) {
                    if (e.value == value) {
                        return true
                    }
                    e = e.next
                }
            }
            return false
        }

        /**
         *
         * Returns `true` if this HashMap maps one or more keys
         * to this value.
         *
         *
         * Note that this method is identical in functionality to contains
         * (which predates the Map interface).
         *
         * @param value value whose presence in this HashMap is to be tested.
         * @return boolean `true` if the value is contained
         * @see java.util.Map
         *
         * @since JDK1.2
         */
        fun containsValue(value: Any?): Boolean {
            return contains(value)
        }

        /**
         *
         * Tests if the specified object is a key in this hashtable.
         *
         * @param key possible key.
         * @return `true` if and only if the specified object is a
         * key in this hashtable, as determined by the `equals`
         * method; `false` otherwise.
         * @see .contains
         */
        fun containsKey(key: Int): Boolean {
            val tab = table
            val index = (key and 0x7FFFFFFF) % tab.size
            var e = tab[index]
            while (e != null) {
                if (e.hash == key) {
                    return true
                }
                e = e.next
            }
            return false
        }

        /**
         *
         * Returns the value to which the specified key is mapped in this map.
         *
         * @param key a key in the hashtable.
         * @return the value to which the key is mapped in this hashtable;
         * `null` if the key is not mapped to any value in
         * this hashtable.
         * @see .put
         */
        operator fun get(key: Int): Any? {
            val tab = table
            val index = (key and 0x7FFFFFFF) % tab.size
            var e = tab[index]
            while (e != null) {
                if (e.hash == key) {
                    return e.value
                }
                e = e.next
            }
            return null
        }

        /**
         *
         * Increases the capacity of and internally reorganizes this
         * hashtable, in order to accommodate and access its entries more efficiently.
         *
         *
         * This method is called automatically when the number of keys
         * in the hashtable exceeds this hashtable's capacity and load factor.
         */
        protected fun rehash() {
            val oldCapacity = table.size
            val oldMap = table
            val newCapacity = oldCapacity * 2 + 1
            val newMap = arrayOfNulls<Entry>(newCapacity)
            threshold = (newCapacity * loadFactor).toInt()
            table = newMap
            var i = oldCapacity
            while (i-- > 0) {
                var old = oldMap[i]
                while (old != null) {
                    val e = old
                    old = old.next
                    val index = (e.hash and 0x7FFFFFFF) % newCapacity
                    e.next = newMap[index]
                    newMap[index] = e
                }
            }
        }

        /**
         *
         * Maps the specified `key` to the specified
         * `value` in this hashtable. The key cannot be
         * `null`.
         *
         *
         * The value can be retrieved by calling the `get` method
         * with a key that is equal to the original key.
         *
         * @param key the hashtable key.
         * @param value the value.
         * @return the previous value of the specified key in this hashtable,
         * or `null` if it did not have one.
         * @throws NullPointerException if the key is `null`.
         * @see .get
         */
        fun put(key: Int, value: Any?): Any? {
            // Makes sure the key is not already in the hashtable.
            var tab = table
            var index = (key and 0x7FFFFFFF) % tab.size
            run {
                var e = tab[index]
                while (e != null) {
                    if (e.hash == key) {
                        val old: Any? = e.value
                        e.value = value
                        return old
                    }
                    e = e.next
                }
            }
            if (count >= threshold) {
                // Rehash the table if the threshold is exceeded
                rehash()
                tab = table
                index = (key and 0x7FFFFFFF) % tab.size
            }

            // Creates the new entry.
            val e = Entry(key, key, value, tab[index])
            tab[index] = e
            count++
            return null
        }

        /**
         *
         * Removes the key (and its corresponding value) from this hashtable.
         *
         *
         * This method does nothing if the key is not present in the hashtable.
         *
         * @param key the key that needs to be removed.
         * @return the value to which the key had been mapped in this hashtable,
         * or `null` if the key did not have a mapping.
         */
        fun remove(key: Int): Any? {
            val tab = table
            val index = (key and 0x7FFFFFFF) % tab.size
            var e = tab[index]
            var prev: Entry? = null
            while (e != null) {
                if (e.hash == key) {
                    if (prev != null) {
                        prev.next = e.next
                    } else {
                        tab[index] = e.next
                    }
                    count--
                    val oldValue: Any? = e.value
                    e.value = null
                    return oldValue
                }
                prev = e
                e = e.next
            }
            return null
        }

        /**
         *
         * Clears this hashtable so that it contains no keys.
         */
        @Synchronized
        fun clear() {
            val tab = table
            var index = tab.size
            while (--index >= 0) {
                tab[index] = null
            }
            count = 0
        }
    }

    companion object {
        private val BASIC_ARRAY = arrayOf(arrayOf("quot", "34"), arrayOf("amp", "38"), arrayOf("lt", "60"), arrayOf("gt", "62"))
        private val APOS_ARRAY = arrayOf(arrayOf("apos", "39"))

        // package scoped for testing
        val ISO8859_1_ARRAY = arrayOf(arrayOf("nbsp", "160"), arrayOf("iexcl", "161"), arrayOf("cent", "162"), arrayOf("pound", "163"), arrayOf("curren", "164"), arrayOf("yen", "165"), arrayOf("brvbar", "166"), arrayOf("sect", "167"), arrayOf("uml", "168"), arrayOf("copy", "169"), arrayOf("ordf", "170"), arrayOf("laquo", "171"), arrayOf("not", "172"), arrayOf("shy", "173"), arrayOf("reg", "174"), arrayOf("macr", "175"), arrayOf("deg", "176"), arrayOf("plusmn", "177"), arrayOf("sup2", "178"), arrayOf("sup3", "179"), arrayOf("acute", "180"), arrayOf("micro", "181"), arrayOf("para", "182"), arrayOf("middot", "183"), arrayOf("cedil", "184"), arrayOf("sup1", "185"), arrayOf("ordm", "186"), arrayOf("raquo", "187"), arrayOf("frac14", "188"), arrayOf("frac12", "189"), arrayOf("frac34", "190"), arrayOf("iquest", "191"), arrayOf("Agrave", "192"), arrayOf("Aacute", "193"), arrayOf("Acirc", "194"), arrayOf("Atilde", "195"), arrayOf("Auml", "196"), arrayOf("Aring", "197"), arrayOf("AElig", "198"), arrayOf("Ccedil", "199"), arrayOf("Egrave", "200"), arrayOf("Eacute", "201"), arrayOf("Ecirc", "202"), arrayOf("Euml", "203"), arrayOf("Igrave", "204"), arrayOf("Iacute", "205"), arrayOf("Icirc", "206"), arrayOf("Iuml", "207"), arrayOf("ETH", "208"), arrayOf("Ntilde", "209"), arrayOf("Ograve", "210"), arrayOf("Oacute", "211"), arrayOf("Ocirc", "212"), arrayOf("Otilde", "213"), arrayOf("Ouml", "214"), arrayOf("times", "215"), arrayOf("Oslash", "216"), arrayOf("Ugrave", "217"), arrayOf("Uacute", "218"), arrayOf("Ucirc", "219"), arrayOf("Uuml", "220"), arrayOf("Yacute", "221"), arrayOf("THORN", "222"), arrayOf("szlig", "223"), arrayOf("agrave", "224"), arrayOf("aacute", "225"), arrayOf("acirc", "226"), arrayOf("atilde", "227"), arrayOf("auml", "228"), arrayOf("aring", "229"), arrayOf("aelig", "230"), arrayOf("ccedil", "231"), arrayOf("egrave", "232"), arrayOf("eacute", "233"), arrayOf("ecirc", "234"), arrayOf("euml", "235"), arrayOf("igrave", "236"), arrayOf("iacute", "237"), arrayOf("icirc", "238"), arrayOf("iuml", "239"), arrayOf("eth", "240"), arrayOf("ntilde", "241"), arrayOf("ograve", "242"), arrayOf("oacute", "243"), arrayOf("ocirc", "244"), arrayOf("otilde", "245"), arrayOf("ouml", "246"), arrayOf("divide", "247"), arrayOf("oslash", "248"), arrayOf("ugrave", "249"), arrayOf("uacute", "250"), arrayOf("ucirc", "251"), arrayOf("uuml", "252"), arrayOf("yacute", "253"), arrayOf("thorn", "254"), arrayOf("yuml", "255"))

        // https://www.w3.org/TR/REC-html40/sgml/entities.html package scoped for testing
        val HTML40_ARRAY = arrayOf(arrayOf("fnof", "402"), arrayOf("Alpha", "913"), arrayOf("Beta", "914"), arrayOf("Gamma", "915"), arrayOf("Delta", "916"), arrayOf("Epsilon", "917"), arrayOf("Zeta", "918"), arrayOf("Eta", "919"), arrayOf("Theta", "920"), arrayOf("Iota", "921"), arrayOf("Kappa", "922"), arrayOf("Lambda", "923"), arrayOf("Mu", "924"), arrayOf("Nu", "925"), arrayOf("Xi", "926"), arrayOf("Omicron", "927"), arrayOf("Pi", "928"), arrayOf("Rho", "929"), arrayOf("Sigma", "931"), arrayOf("Tau", "932"), arrayOf("Upsilon", "933"), arrayOf("Phi", "934"), arrayOf("Chi", "935"), arrayOf("Psi", "936"), arrayOf("Omega", "937"), arrayOf("alpha", "945"), arrayOf("beta", "946"), arrayOf("gamma", "947"), arrayOf("delta", "948"), arrayOf("epsilon", "949"), arrayOf("zeta", "950"), arrayOf("eta", "951"), arrayOf("theta", "952"), arrayOf("iota", "953"), arrayOf("kappa", "954"), arrayOf("lambda", "955"), arrayOf("mu", "956"), arrayOf("nu", "957"), arrayOf("xi", "958"), arrayOf("omicron", "959"), arrayOf("pi", "960"), arrayOf("rho", "961"), arrayOf("sigmaf", "962"), arrayOf("sigma", "963"), arrayOf("tau", "964"), arrayOf("upsilon", "965"), arrayOf("phi", "966"), arrayOf("chi", "967"), arrayOf("psi", "968"), arrayOf("omega", "969"), arrayOf("thetasym", "977"), arrayOf("upsih", "978"), arrayOf("piv", "982"), arrayOf("bull", "8226"), arrayOf("hellip", "8230"), arrayOf("prime", "8242"), arrayOf("Prime", "8243"), arrayOf("oline", "8254"), arrayOf("frasl", "8260"), arrayOf("weierp", "8472"), arrayOf("image", "8465"), arrayOf("real", "8476"), arrayOf("trade", "8482"), arrayOf("alefsym", "8501"), arrayOf("larr", "8592"), arrayOf("uarr", "8593"), arrayOf("rarr", "8594"), arrayOf("darr", "8595"), arrayOf("harr", "8596"), arrayOf("crarr", "8629"), arrayOf("lArr", "8656"), arrayOf("uArr", "8657"), arrayOf("rArr", "8658"), arrayOf("dArr", "8659"), arrayOf("hArr", "8660"), arrayOf("forall", "8704"), arrayOf("part", "8706"), arrayOf("exist", "8707"), arrayOf("empty", "8709"), arrayOf("nabla", "8711"), arrayOf("isin", "8712"), arrayOf("notin", "8713"), arrayOf("ni", "8715"), arrayOf("prod", "8719"), arrayOf("sum", "8721"), arrayOf("minus", "8722"), arrayOf("lowast", "8727"), arrayOf("radic", "8730"), arrayOf("prop", "8733"), arrayOf("infin", "8734"), arrayOf("ang", "8736"), arrayOf("and", "8743"), arrayOf("or", "8744"), arrayOf("cap", "8745"), arrayOf("cup", "8746"), arrayOf("int", "8747"), arrayOf("there4", "8756"), arrayOf("sim", "8764"), arrayOf("cong", "8773"), arrayOf("asymp", "8776"), arrayOf("ne", "8800"), arrayOf("equiv", "8801"), arrayOf("le", "8804"), arrayOf("ge", "8805"), arrayOf("sub", "8834"), arrayOf("sup", "8835"), arrayOf("sube", "8838"), arrayOf("supe", "8839"), arrayOf("oplus", "8853"), arrayOf("otimes", "8855"), arrayOf("perp", "8869"), arrayOf("sdot", "8901"), arrayOf("lceil", "8968"), arrayOf("rceil", "8969"), arrayOf("lfloor", "8970"), arrayOf("rfloor", "8971"), arrayOf("lang", "9001"), arrayOf("rang", "9002"), arrayOf("loz", "9674"), arrayOf("spades", "9824"), arrayOf("clubs", "9827"), arrayOf("hearts", "9829"), arrayOf("diams", "9830"), arrayOf("OElig", "338"), arrayOf("oelig", "339"), arrayOf("Scaron", "352"), arrayOf("scaron", "353"), arrayOf("Yuml", "376"), arrayOf("circ", "710"), arrayOf("tilde", "732"), arrayOf("ensp", "8194"), arrayOf("emsp", "8195"), arrayOf("thinsp", "8201"), arrayOf("zwnj", "8204"), arrayOf("zwj", "8205"), arrayOf("lrm", "8206"), arrayOf("rlm", "8207"), arrayOf("ndash", "8211"), arrayOf("mdash", "8212"), arrayOf("lsquo", "8216"), arrayOf("rsquo", "8217"), arrayOf("sbquo", "8218"), arrayOf("ldquo", "8220"), arrayOf("rdquo", "8221"), arrayOf("bdquo", "8222"), arrayOf("dagger", "8224"), arrayOf("Dagger", "8225"), arrayOf("permil", "8240"), arrayOf("lsaquo", "8249"), arrayOf("rsaquo", "8250"), arrayOf("euro", "8364"))

        private const val LOOKUP_TABLE_SIZE = 256

        /**
         * The set of entities supported by standard XML.
         */
        var XML: Entities? = null

        /**
         * The set of entities supported by HTML 3.2.
         */
        var HTML32: Entities? = null

        /**
         * The set of entities supported by HTML 4.0.
         */
        var HTML40: Entities? = null

        init {
            XML = Entities()
            XML!!.addEntities(BASIC_ARRAY)
            XML!!.addEntities(APOS_ARRAY)
        }

        init {
            HTML32 = Entities()
            HTML32!!.addEntities(BASIC_ARRAY)
            HTML32!!.addEntities(ISO8859_1_ARRAY)
        }

        init {
            HTML40 = Entities()
            fillWithHtml40Entities(HTML40)
        }

        /**
         * Fills the specified entities instance with HTML 40 entities.
         *
         * @param entities the instance to be filled.
         */
        fun fillWithHtml40Entities(entities: Entities?) {
            entities!!.addEntities(BASIC_ARRAY)
            entities.addEntities(ISO8859_1_ARRAY)
            entities.addEntities(HTML40_ARRAY)
        }
    }
}