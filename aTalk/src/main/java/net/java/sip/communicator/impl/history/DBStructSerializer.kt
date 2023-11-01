/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history

import net.java.sip.communicator.service.history.History
import net.java.sip.communicator.service.history.HistoryID
import net.java.sip.communicator.service.history.records.HistoryRecordStructure
import org.atalk.util.xml.XMLUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.text.ParseException

/**
 *
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
class DBStructSerializer
/**
 * Constructor.
 *
 * @param historyService
 * the history service
 */
(private val historyService: HistoryServiceImpl) {
    /**
     * Write the history.
     *
     * @param dbDatFile
     * the database file
     * @param history
     * the history to write
     * @throws IOException
     * if write failed for any reason
     */
    @Throws(IOException::class)
    fun writeHistory(dbDatFile: File?, history: History) {
        val builder = historyService.documentBuilder
        val doc = builder.newDocument()
        val root = doc.createElement("dbstruct")
        root.setAttribute("version", "1.0")
        val structure = createStructureTag(doc, history.getHistoryRecordsStructure())
        val id = createIDTag(doc, history.getID())
        root.appendChild(structure)
        root.appendChild(id)
        doc.appendChild(root)
        XMLUtils.writeXML(doc, dbDatFile)
    }

    private fun createIDTag(doc: Document, historyID: HistoryID?): Element {
        val idroot = doc.createElement("id")
        var current = idroot
        val idelements = historyID!!.getID()
        for (i in idelements.indices) {
            val idnode = doc.createElement("component")
            idnode.setAttribute("value", idelements[i])
            current.appendChild(idnode)
            current = idnode
        }
        return idroot
    }

    private fun createStructureTag(doc: Document, recordStructure: HistoryRecordStructure?): Element {
        val structure = doc.createElement("structure")
        val propertyNames = recordStructure!!.propertyNames
        val count = recordStructure.getPropertyCount()
        for (i in 0 until count) {
            val property = doc.createElement("property")
            property.setAttribute("name", propertyNames[i])
            structure.appendChild(property)
        }
        return structure
    }

    /**
     * This method parses an XML file, and returns a History object created with the information from it. The parsing is
     * non-validating, so if a malformed XML is passed the results are undefined. The file should be with the following
     * structure:
     *
     * <dbstruct version="1.0"> <id value="idcomponent1"> <id value="idcomponent2"> <id value="idcomponent3"></id> </id>
    </id> *
     *
     * <structure> <property name="propertyName" type="textType"></property> <property name="propertyName" type="textType"></property>
     * <property name="propertyName" type="textType"></property> </structure> </dbstruct>
     *
     * @param dbDatFile
     * The file to be parsed.
     * @return A History object corresponding to this dbstruct file.
     * @throws SAXException
     * Thrown if an error occurs during XML parsing.
     * @throws IOException
     * Thrown if an IO error occurs.
     * @throws ParseException
     * Thrown if there is error in the XML data format.
     */
    @Throws(SAXException::class, IOException::class, ParseException::class)
    fun loadHistory(dbDatFile: File): History {
        val doc = historyService.parse(dbDatFile)
        val root = doc.firstChild
        val id = loadID(root)
        val structure = loadStructure(root)
        return HistoryImpl(id, dbDatFile.parentFile!!, structure, historyService)
    }

    /**
     * This method parses a "structure" tag and returns the corresponding HistoryRecordStructure.
     *
     * @throws ParseException
     * Thrown if there is no structure tag.
     */
    @Throws(ParseException::class)
    private fun loadStructure(root: Node): HistoryRecordStructure {
        val structNode = findElement(root, "structure")
                ?: throw ParseException("There is no structure tag defined!", 0)
        val nodes = structNode.childNodes
        val count = nodes.length
        val propertyNames = ArrayList<String>(count)
        for (i in 0 until count) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && "property" == node.nodeName) {
                val parameter = node as Element
                val paramName = parameter.getAttribute("name") ?: continue
                propertyNames.add(paramName)
            }
        }
        val names = arrayOfNulls<String>(propertyNames.size)
        propertyNames.toArray(names)
        return HistoryRecordStructure(names)
    }

    @Throws(ParseException::class)
    private fun loadID(parent: Node): HistoryID {
        val idnode = findElement(parent, "id")
        val al = loadID(ArrayList(), idnode)
        val id = arrayOfNulls<String>(al.size)
        al.toArray(id)
        return HistoryID.createFromID(id)
    }

    @Throws(ParseException::class)
    private fun loadID(loadedIDs: ArrayList<String>, parent: Node?): ArrayList<String> {
        val node = findElement(parent, "component")
        if (node != null) {
            val idValue = node.getAttribute("value")
            if (idValue != null) {
                loadedIDs.add(idValue)
            } else {
                throw ParseException("There is an ID object without value.", 0)
            }
        } else {
            // no more nodes
            return loadedIDs
        }
        return loadID(loadedIDs, node)
    }

    /**
     * This method seraches through all children of a given node and returns the first with the name matching the given
     * one. If no node is found, null is returned.
     */
    private fun findElement(parent: Node?, name: String): Element? {
        var retVal: Element? = null
        val nodes = parent!!.childNodes
        val count = nodes.length
        for (i in 0 until count) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && name == node.nodeName) {
                retVal = node as Element
                break
            }
        }
        return retVal
    }
}