/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.records

/**
 * @author Alexander Pelov
 */
class HistoryRecordStructure(propertyNames: Array<String?>) {
    val propertyNames: Array<String>

    /**
     * Creates an entry structure object used to define the shape of the data stored in the history.
     *
     * Note that the property names are not unique, i.e. a single property may have 0, 1 or more values.
     *
     * @param propertyNames
     */
    init {
        // TODO: Validate: Assert.assertNonNull(propertyNames, "Parameter propertyNames should be non-null.");
        this.propertyNames = Array(propertyNames.size){""}
        System.arraycopy(propertyNames, 0, this.propertyNames, 0, this.propertyNames.size)
    }

    fun getPropertyCount(): Int {
        return propertyNames.size
    }
}