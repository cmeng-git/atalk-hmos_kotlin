/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration.xml

import org.atalk.impl.configuration.ConfigurationStore
import org.atalk.util.xml.DOMElementWriter
import org.atalk.util.xml.XMLException
import org.atalk.util.xml.XMLUtils
import org.atalk.util.xml.XMLUtils.Companion.findChild
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Implements a `ConfigurationStore` which serializes property name-value associations
 * in XML format.
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class XMLConfigurationStore : ConfigurationStore {
    /**
     * The list of properties currently registered in the configuration service.
     */
    private var properties = Hashtable<String, Any?>()

    /**
     * Contains the properties that were initially loaded from the configuration file or (if the
     * properties have been modified and saved since initially loaded) those that were last
     * written to the file.We use the property so that we could determine which properties are
     * new and do not have a corresponding node in the XMLDocument object.
     */
    private var fileExtractedProperties: MutableMap<String, Any?> = Hashtable()

    /**
     * The XML Document containing the configuration file this service loaded.
     */
    private var propertiesDocument: Document? = null

    /**
     * Returns a copy of the Map containing all configuration properties
     *
     * @return a Map clone of the current configuration property set.
     */
    private fun cloneProperties(): MutableMap<String, Any?> {
        return properties.clone() as MutableMap<String, Any?>
    }

    /**
     * Creates a new runtime XML document which is to contain the properties managed by this `ConfigurationStore`
     * .
     *
     * @return a new runtime XML `Document` which is to contain the properties managed by
     * this `ConfigurationStore`
     */
    private fun createPropertiesDocument(): Document? {
        if (propertiesDocument == null) {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = try {
                factory.newDocumentBuilder()
            } catch (ex: ParserConfigurationException) {
                Timber.e(ex, "Failed to create a DocumentBuilder")
                return null
            }
            propertiesDocument = builder.newDocument()
            propertiesDocument!!.appendChild(propertiesDocument!!.createElement("sip-communicator"))
        }
        return propertiesDocument
    }

    /**
     * Implements [ConfigurationStore.getProperty]. Gets the value in this
     * `ConfigurationStore` of a property with a specific name.
     *
     * @param name the name of the property to get the value of
     * @return the value in this `ConfigurationStore` of the property with the specified
     * name; `null` if the property with the specified name does not have an association
     * with a value in this `ConfigurationStore`
     * @see ConfigurationStore.getProperty
     */
    override fun getProperty(name: String): Any? {
        val value = properties[name]

        // if this is a property reference make sure we return the referenced
        // value and not the reference itself
        return if (value is PropertyReference) value.value else value
    }

    /**
     * Implements {ConfigurationStore#getPropertyNames(String)}. Gets the names of the properties
     * which have values associated in this `ConfigurationStore`.
     *
     * @return an array of `String`s which specify the names of the properties that have
     * values associated in this `ConfigurationStore`; an empty array if this instance
     * contains no property values
     * @see ConfigurationStore.getPropertyNames
     */
    override fun getPropertyNames(name: String): Array<String?> {
        val propertyNames = properties.keys
        return propertyNames.toTypedArray()
    }

    /**
     * Implements {ConfigurationStore#isSystemProperty(String)}. Determines whether a specific
     * name stands for a system property.
     *
     * @param name the name of a property which is to be determined whether it is a system property
     * @return `true` if the specified name stands for a system property; `false`, otherwise
     * @see ConfigurationStore.isSystemProperty
     */
    override fun isSystemProperty(name: String): Boolean {
        return properties[name] is PropertyReference
    }

    /**
     * Loads the contents of the specified configuration file into the local properties object.
     *
     * @param file a reference to the configuration file to load.
     * @return a hashTable containing all properties extracted from the specified file.
     * @throws IOException if the specified file does not exist
     * @throws XMLException if there is a problem with the file syntax.
     */
    @Throws(IOException::class, XMLException::class)
    private fun loadConfiguration(file: File?): MutableMap<String, Any?> {
        return try {
            val factory = XMLUtils.newDocumentBuilderFactory()
            val builder = factory.newDocumentBuilder()
            val props = Hashtable<String, Any?>()

            // if the file is empty (or contains only sth insignificant)
            // ignore it and create a new document.
            propertiesDocument = if (file!!.length() < "<sip-communicator>".length * 2) createPropertiesDocument() else builder.parse(file)
            val root = propertiesDocument!!.firstChild
            var currentNode: Node
            val children = root.childNodes
            for (i in 0 until children.length) {
                currentNode = children.item(i)
                if (currentNode.nodeType == Node.ELEMENT_NODE) {
                    val propertyName = DOMElementWriter.decodeName(currentNode.nodeName)
                    loadNode(currentNode, propertyName, props)
                }
            }
            props
        } catch (ex: SAXException) {
            Timber.e(ex, "Error parsing configuration file")
            throw XMLException(ex.message, ex)
        } catch (ex: ParserConfigurationException) {
            // it is not highly probable that this might happen - so lets just log it.
            Timber.e(ex, "Error finding configuration for default parsers")
            Hashtable()
        }
    }

    /**
     * Loads the contents of the specified node and its children into the local properties. Any
     * nodes marked as "system" will also be resolved in the system properties.
     *
     * @param node the root node that we should load together with its children
     * @param propertyName a String containing the prefix describing the route to the specified node including
     * its one name
     * @param props the dictionary object where all properties extracted from this node and its children
     * should be recorded.
     */
    private fun loadNode(node: Node, propertyName: String, props: MutableMap<String, Any?>) {
        var currentNode: Node
        val children = node.childNodes
        for (i in 0 until children.length) {
            currentNode = children.item(i)
            if (currentNode.nodeType == Node.ELEMENT_NODE) {
                val newProp = (propertyName + "."
                        + DOMElementWriter.decodeName(currentNode.nodeName))
                val value = XMLUtils.getAttribute(currentNode, ATTRIBUTE_VALUE)

                // the value attr is present we must handle the desired property
                if (value != null) {
                    val propertyType = XMLUtils.getAttribute(currentNode, SYSTEM_ATTRIBUTE_NAME)

                    // if the property is marked as "system", we should resolve it against the
                    // system properties and only store a reference locally. this is normally
                    // done for properties that are supposed to configure underlying libraries.
                    if (propertyType != null && propertyType == SYSTEM_ATTRIBUTE_TRUE) {
                        props[newProp] = PropertyReference(newProp)
                        System.setProperty(newProp, value)
                    } else props[newProp] = value
                }

                // load child nodes
                loadNode(currentNode, newProp, props)
            }
        }
    }

    /**
     * Creates new entries in the XML `doc` for every element in the `newProperties` table.
     *
     * @param doc the XML `Document` where the new entries should be created
     * @param newProperties the table containing the properties that are to be introduced in the document.
     */
    private fun processNewProperties(doc: Document?, newProperties: Map<String, Any?>) {
        for (entry in newProperties.entries) {
            var value = entry.value
            var system: Boolean
            if ((value is PropertyReference).also { system = it }) value = (value as PropertyReference).value!!
            processNewProperty(doc, entry.key, value.toString(), system)
        }
    }

    /**
     * Creates an entry in the XML `doc` for the specified key value pair.
     *
     * @param doc the XML `document` to update.
     * @param key the value of the `name` attribute for the new entry
     * @param value the value of the `value` attribute for the new entry
     * @param isSystem specifies whether this is a system property (system attribute will be set to true).
     */
    private fun processNewProperty(doc: Document?, key: String?, value: String, isSystem: Boolean) {
        val tokenizer = StringTokenizer(key, ".")
        val toks = arrayOfNulls<String>(tokenizer.countTokens())
        var i = 0
        while (tokenizer.hasMoreTokens()) {
            toks[i++] = DOMElementWriter.encodeName(tokenizer.nextToken())
        }
        val nodeName = toks[toks.size - 1]
        val parent = XMLConfUtils.createLastPathComponent(doc, toks, toks.size - 1)
        var newNode: Element? = findChild(parent, nodeName)
        if (newNode == null) {
            newNode = doc!!.createElement(nodeName)
            parent!!.appendChild(newNode)
        }
        newNode!!.setAttribute("value", value)
        if (isSystem) newNode.setAttribute(SYSTEM_ATTRIBUTE_NAME, SYSTEM_ATTRIBUTE_TRUE)
    }

    /**
     * Implements [ConfigurationStore.reloadConfiguration]. Removes all property
     * name-value associations currently present in this `ConfigurationStore` and
     * de-serializes new property name-value associations from a specific `File` which
     * presumably is in the format represented by this instance.
     *
     * @param file the `File` to be read and to deserialize new property name-value associations
     * from into this instance
     * @throws IOException if there is an input error while reading from the specified `file`
     * @throws XMLException if parsing the contents of the specified `file` fails
     * @see ConfigurationStore.reloadConfiguration
     */
    @Throws(IOException::class, XMLException::class)
    override fun reloadConfiguration(file: File?) {
        properties = Hashtable()
        fileExtractedProperties = loadConfiguration(file)
        properties.putAll(fileExtractedProperties)
    }

    /**
     * Implements [ConfigurationStore.removeProperty]. Removes the value association
     * in this `ConfigurationStore` of the property with a specific name. If the property
     * with the specified name is not associated with a value in this `ConfigurationStore`, does nothing.
     *
     * @param name the name of the property which is to have its value association in this
     * `ConfigurationStore` removed
     * @see ConfigurationStore.removeProperty
     */
    override fun removeProperty(name: String) {
        properties.remove(name)
        fileExtractedProperties.remove(name)
    }

    /**
     * Implements [ConfigurationStore.setNonSystemProperty]. Sets the value
     * of a non-system property with a specific name to a specific value in this
     * `ConfigurationStore`.
     *
     * @param name the name of the non-system property to be set to the specified value in this
     * `ConfigurationStore`
     * @param value the value to be assigned to the non-system property with the specified name in this
     * `ConfigurationStore`
     * @see ConfigurationStore.setNonSystemProperty
     */
    override fun setNonSystemProperty(name: String, value: Any?) {
        properties[name] = value
    }

    /**
     * Implements [ConfigurationStore.setSystemProperty]. Sets a property with a
     * specific name to be considered a system property by the `ConfigurationStore`.
     *
     * @param name the name of the property to be set as a system property in this `ConfigurationStore`
     * @see ConfigurationStore.setSystemProperty
     */
    override fun setSystemProperty(name: String) {
        setNonSystemProperty(name, PropertyReference(name))
    }

    /**
     * Implements [ConfigurationStore.storeConfiguration]. Stores/serializes
     * the property name-value associations currently present in this `ConfigurationStore`
     * into a specific `OutputStream` in the format represented by this instance.
     *
     * @param out the `OutputStream` to receive the serialized form of the property name-value
     * associations currently present in this `ConfigurationStore`
     * @see ConfigurationStore.storeConfiguration
     */
    override fun storeConfiguration(out: OutputStream) {
        // resolve the properties that were initially in the file - back to the document.
        if (propertiesDocument == null) propertiesDocument = createPropertiesDocument()
        val root = propertiesDocument!!.firstChild
        val children = root.childNodes
        for (i in 0 until children.length) {
            val currentNode = children.item(i)
            if (currentNode.nodeType == Node.ELEMENT_NODE) {
                val propertyName = DOMElementWriter.decodeName(currentNode.nodeName)
                updateNode(currentNode, propertyName, properties)
            }
        }

        // create in the document the properties that were added by other bundles after the
        // initial property load.
        val newlyAddedProperties = cloneProperties()

        // remove those that were originally there;
        for (propName in fileExtractedProperties.keys) newlyAddedProperties.remove(propName)
        processNewProperties(propertiesDocument, newlyAddedProperties)
        XMLUtils.indentedWriteXML(propertiesDocument, out)
    }

    /**
     * Updates the value of the specified node and its children to reflect those in the properties
     * file. Nodes marked as "system" will be updated from the specified properties object and
     * not from the system properties since if any intentional change (through a configuration
     * form) has occurred it will have been made there.
     *
     * @param node the root node that we should update together with its children
     * @param propertyName a String containing the prefix describing the dot-separated route to the specified
     * node including its one name
     * @param props the dictionary object where the up to date values of the node should be queried.
     */
    private fun updateNode(node: Node, propertyName: String, props: Map<String?, Any?>) {
        var currentNode: Node
        val children = node.childNodes
        for (i in 0 until children.length) {
            currentNode = children.item(i)
            if (currentNode.nodeType == Node.ELEMENT_NODE) {
                val newProp = (propertyName + "."
                        + DOMElementWriter.decodeName(currentNode.nodeName))
                val attr = (currentNode as Element).getAttributeNode(ATTRIBUTE_VALUE)
                if (attr != null) {
                    // update the corresponding node
                    val value = props[newProp]
                    if (value == null) {
                        node.removeChild(currentNode)
                        continue
                    }
                    val isSystem = value is PropertyReference
                    val prop = if (isSystem) (value as PropertyReference).value.toString() else value.toString()
                    attr.nodeValue = prop

                    // in case the property has changed to system since the last load - update the
                    // conf file accordingly.
                    if (isSystem) currentNode.setAttribute(SYSTEM_ATTRIBUTE_NAME, SYSTEM_ATTRIBUTE_TRUE) else currentNode.removeAttribute(SYSTEM_ATTRIBUTE_NAME)
                }

                // update child nodes
                updateNode(currentNode, newProp, props)
            }
        }
    }

    /**
     * We use property references when we'd like to store system properties. Simply storing System
     * properties in our properties Map would not be enough since it will lead to mismatching
     * values for the same property in the System property set and in our local set of properties.
     * Storing them only in the System property  set OTOH is a bit clumsy since it obliges bundles
     * to use to different configuration property sources. For that reason, every time we get
     * handed a property labeled as System, instead of storing its actual value in the
     *
     *
     * local property set we store a PropertyReference instance that will retrieve it from the
     * system properties when  necessary.
     */
    private class PropertyReference
    /**
     * Initializes a new `PropertyReference` instance which is to represent a system
     * property with a specific name.
     *
     * @param propertyName the name of the system property to be represented by the new instance
     */
    (
            /**
             * The name of the system property represented by this instance.
             */
            private val propertyName: String) {
        /**
         * Return the actual value of the property as recorded in the System properties.
         *
         * @return the valued of the property as recorded in the System props.
         */
        val value: Any?
            get() = System.getProperty(propertyName)
    }

    companion object {
        /**
         * Name of the xml attribute containing property values
         */
        private const val ATTRIBUTE_VALUE = "value"

        /**
         * Name of the xml attribute indicating that a property is to be resolved in the system properties
         */
        private const val SYSTEM_ATTRIBUTE_NAME = "system"

        /**
         * The value of the Name of the xml attribute containing property values
         */
        private const val SYSTEM_ATTRIBUTE_TRUE = "true"
    }
}