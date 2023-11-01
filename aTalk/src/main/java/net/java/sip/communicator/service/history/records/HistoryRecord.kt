/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.records

import java.util.*

/**
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
class HistoryRecord {
    /**
     * Map containing the property-value pair
     */
    private val mProperties: MutableMap<String, String>
    val timestamp: Date
    val propertyNames: Array<String>
    val propertyValues: Array<String>

    constructor(properties: MutableMap<String, String>, timestamp: String) {
        mProperties = properties
        val time = timestamp.toLong()
        this.timestamp = Date(time)
        propertyNames = properties.keys.toTypedArray()
        propertyValues = properties.values.toTypedArray()
    }

    /**
     * Constructs an entry containing multiple name-value pairs, where the names are taken from
     * the defined structure. The timestamp is set to the time this object is created.
     *
     * @param entryStructure
     * @param propertyValues
     */
    constructor(entryStructure: HistoryRecordStructure, propertyValues: Array<String>) : this(entryStructure.propertyNames, propertyValues, Date()) {}

    /**
     * Constructs an entry containing multiple name-value pairs, where the names are taken from
     * the defined structure.
     *
     * @param entryStructure
     * @param propertyValues
     * @param timestamp
     */
    constructor(entryStructure: HistoryRecordStructure, propertyValues: Array<String>,
            timestamp: Date) : this(entryStructure.propertyNames, propertyValues, timestamp) {
    }

    /**
     * Constructs an entry containing multiple name-value pairs, where the name is not unique. The
     * timestamp is set to the time this object is created.
     *
     * @param propertyNames
     * @param propertyValues
     */
    @JvmOverloads
    constructor(propertyNames: Array<String>, propertyValues: Array<String>, timestamp: Date = Date()) {
        // TODO: Validate: Assert.assertNonNull(propertyNames, "The property names should be non-null.");
        // TODO: Validate: Assert.assertNonNull(mPropertyValues, "The property values should be non-null.");
        // TODO: Validate: Assert.assertNonNull(timestamp, "The timestamp should be non-null.");
        // TODO: Validate Assert.assertTrue(propertyNames.length == mPropertyValues.length,
        // "The length of the property names and property values should be equal.");

        this.propertyNames = propertyNames
        this.propertyValues = propertyValues
        this.timestamp = timestamp
        mProperties = Hashtable()
        for (i in propertyNames.indices) {
            mProperties[propertyNames[i]] = propertyValues[i]
        }
    }

    val properties: Map<String, String>
        get() = mProperties

    /**
     * Returns the String representation of this HistoryRecord.
     *
     * @return the String representation of this HistoryRecord
     */
    override fun toString(): String {
        val s = StringBuilder("History Record: ")
        for (i in propertyNames.indices) {
            s.append(propertyNames[i])
            s.append('=')
            s.append(propertyValues[i])
            s.append('\n')
        }
        return s.toString()
    }
}