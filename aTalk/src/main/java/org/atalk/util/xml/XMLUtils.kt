/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.util.xml

import android.text.TextUtils
import org.atalk.util.*
import org.w3c.dom.*
import timber.log.Timber
import java.io.*
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Common XML Tasks
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
open class XMLUtils {
    companion object {
        /**
         * The string identifying the `DocumentBuilderFactory`feature which controls whether
         * inclusion of external general entities is allowed. See
         * []//xerces.apache.org/xerces-j/features.html.external-general-entities"">&quot;https://xerces.apache.org/xerces-j/features.html#external-general-entities&quot; and
         * []//xerces.apache.org/xerces2-j/features.html.external-general-entities"">&quot;https://xerces.apache.org/xerces2-j/features.html#external-general-entities&quot;
         */
        private const val FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"

        /**
         * The string identifying the `DocumentBuilderFactory`feature which controls whether
         * inclusion of external parameter entities is allowed. See
         * []//xerces.apache.org/xerces-j/features.html.external-parameter-entities"">&quot;https://xerces.apache.org/xerces-j/features.html#external-parameter-entities&quot; and
         * []//xerces.apache.org/xerces2-j/features.html.external-parameter-entities"">&quot;https://xerces.apache.org/xerces2-j/features.html#external-parameter-entities&quot;
         */
        private const val FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"

        /**
         * The string identifying the `DocumentBuilderFactory`feature which controls whether
         * DOCTYPE declaration is allowed. See
         * []//xerces.apache.org/xerces2-j/features.html.disallow-doctype-decl"">&quot;https://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl&quot;
         */
        private const val FEATURE_DISSALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"

        /**
         * Extracts from node the attribute with the specified name.
         *
         * @param node the node whose attribute we'd like to extract.
         * @param name the name of the attribute to extract.
         * @return a String containing the trimmed value of the attribute or null if no such attribute exists
         */
        fun getAttribute(node: Node?, name: String?): String? {
            if (node == null) return null
            val attribute = node.attributes.getNamedItem(name)
            return attribute?.nodeValue?.trim { it <= ' ' }
        }

        /**
         * Extracts the String content of a TXT element.
         *
         * @param parentNode the node containing the data that we'd like to get.
         * @return the string contained by the node or null if none existed.
         */
        fun getText(parentNode: Element?): String? {
            val text = getTextNode(parentNode)
            return text?.data
        }

        /**
         * Sets data to be the TEXT content of element
         *
         * @param parentNode the parent element.
         * @param data the data to set.
         */
        fun setText(parentNode: Element, data: String?) {
            if (data == null) return
            var txt = getTextNode(parentNode)
            if (txt != null) txt.data = data else {
                txt = parentNode.ownerDocument.createTextNode(data)
                parentNode.appendChild(txt)
            }
        }

        /**
         * Sets data to be the CDATA content of element
         *
         * @param element the parent element.
         * @param data the data to set.
         */
        fun setCData(element: Element, data: String?) {
            if (data == null) return
            var txt = getCDataNode(element)
            if (txt != null) txt.data = data else {
                txt = element.ownerDocument.createCDATASection(data)
                element.appendChild(txt)
            }
        }

        /**
         * Extract the CDATA content of the specified element.
         *
         * @param element the element whose data we need
         * @return a String containing the CDATA value of element.
         */
        private fun getCData(element: Element?): String? {
            val text = getCDataNode(element)
            return text?.data?.trim { it <= ' ' }
        }

        /**
         * Returns element's CDATA child node (if it has one).
         *
         * @param element the element whose CDATA we need to get.
         * @return a CDATASection object containing the specified element's CDATA content
         */
        private fun getCDataNode(element: Element?): CDATASection? {
            return getChildByType(element, Node.CDATA_SECTION_NODE) as CDATASection?
        }

        /**
         * Returns element's TEXT child node (if it has one).
         *
         * @param element the element whose TEXT we need to get.
         * @return a `Text` object containing the specified element's text content.
         */
        private fun getTextNode(element: Element?): Text? {
            return getChildByType(element, Node.TEXT_NODE) as Text?
        }

        /**
         * Returns first of the `element`'s child nodes that is of type `nodeType`.
         *
         * @param element the element whose child we need.
         * @param nodeType the type of the child we need.
         * @return a child of the specified `nodeType` or null if none was found.
         */
        private fun getChildByType(element: Element?, nodeType: Short): Node? {
            if (element == null) return null
            val nodes = element.childNodes
            if (nodes == null || nodes.length < 1) return null
            var node: Node
            var data: String?
            for (i in 0 until nodes.length) {
                node = nodes.item(i)
                val type = node.nodeType
                if (type == nodeType) {
                    if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                        data = (node as Text).data
                        if (data == null || data.trim { it <= ' ' }.isEmpty()) {
                            // continue;
                        }
                    } else {
                        return node
                    }
                }
            }
            return null
        }

        /**
         * Writes the specified document to the given file adding indentation. The default encoding is UTF-8.
         *
         * @param out the output File
         * @param document the document to write
         * @throws java.io.IOException in case a TransformerException is thrown by the underlying Transformer.
         */
        @Throws(IOException::class)
        fun writeXML(document: Document?, out: File?) {
            val fos = FileOutputStream(out)
            // indentedWriteXML(document, fos);
            writeXML(document, StreamResult(OutputStreamWriter(fos, "UTF-8")), null, null)
            fos.close()
        }

        /**
         * Writes the specified document to the given file adding indentation. The default encoding is UTF-8.
         *
         * @param writer the writer to use when writing the File
         * @param document the document to write
         * @throws java.io.IOException in case a TransformerException is thrown by the underlying Transformer.
         */
        @Throws(IOException::class)
        fun writeXML(document: Document?, writer: Writer) {
            writeXML(document, StreamResult(writer), null, null)
            writer.close()
        }

        /**
         * Writes the specified document to the given file adding indentation. The default encoding is UTF-8.
         *
         * @param streamResult the streamResult object where the document should be written
         * @param document the document to write
         * @param doctypeSystem the doctype system of the xml document that we should record in the file or null if
         * none is specified.
         * @param doctypePublic the public identifier to be used in the document type declaration.
         * @throws java.io.IOException in case a TransformerException is thrown by the underlying Transformer.
         */
        @Throws(IOException::class)
        fun writeXML(document: Document?, streamResult: StreamResult?,
                doctypeSystem: String?, doctypePublic: String?) {
            try {
                val domSource = DOMSource(document)
                val tf = TransformerFactory.newInstance()

                // not working for jdk 1.4
                try {
                    tf.setAttribute("indent-number", 4)
                } catch (ignore: Exception) {
                }
                val serializer = tf.newTransformer()
                if (doctypeSystem != null) serializer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doctypeSystem)
                if (doctypePublic != null) serializer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctypePublic)
                // not working for jdk 1.5
                serializer.setOutputProperty("{http://xml.apache.org/xalan}indent-amount", "4")
                serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                serializer.setOutputProperty(OutputKeys.INDENT, "yes")
                serializer.transform(domSource, streamResult)
            } catch (ex: TransformerException) {
                Timber.e(ex, "Error saving configuration file")
                throw IOException("Failed to write the configuration file: "
                        + ex.messageAndLocation)
            } catch (ex: IllegalArgumentException) {
                // this one is thrown by the setOutputProperty or in other words
                // - shouldn't happen. so let's just log it down in case ...
                Timber.e(ex, "Error saving configuration file")
            }
        }

        /**
         * A simple implementation of XML writing that also allows for indentation.
         *
         * @param doc the Document that we will be writing.
         * @param out an OutputStream to write the document through.
         */
        fun indentedWriteXML(doc: Document?, out: OutputStream?) {
            if (out != null) {
                try {
                    val wri = OutputStreamWriter(out, "UTF-8")
                    // wri.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"+lSep);
                    // (new DOMElementWriter()).write(rootElement, wri, 0, "  ");
                    // wri.flush();
                    // wri.close();
                    writeXML(doc, StreamResult(wri), null, null)
                    out.close()
                } catch (exc: IOException) {
                    throw RuntimeException("Unable to write xml", exc)
                }
            }
        }

        /**
         * Whenever you'd need to print a configuration node and/or its children.
         *
         * @param root the root node to print.
         * @param out the print stream that should be used to outpu
         * @param recurse boolean
         * @param prefix String
         */
        private fun printChildElements(root: Element, out: PrintStream, recurse: Boolean,
                prefix: String) {
            out.print(prefix + "<" + root.nodeName)
            val attrs = root.attributes
            var node: Node
            for (i in 0 until attrs.length) {
                node = attrs.item(i)
                out.print(" " + node.nodeName + "=\"" + node.nodeValue + "\"")
            }
            out.println(">")
            var data = getText(root)!!.trim { it <= ' ' }
            if (!TextUtils.isEmpty(data)) out.println(prefix + "\t" + data)
            data = getCData(root)!!.trim { it <= ' ' }
            if (!TextUtils.isEmpty(data)) out.println("$prefix\t<![CDATA[$data]]>")
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                node = nodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    if (recurse) printChildElements(node as Element, out, recurse, prefix + "\t") else out.println(prefix + node.nodeName)
                }
            }
            out.println(prefix + "</" + root.nodeName + ">")
        }

        /**
         * Returns the child element with the specified tagName for the specified parent element.
         *
         * @param parent The parent whose child we're looking for.
         * @param tagName the name of the child to find
         * @return The child with the specified name or null if no such child was found.
         * @throws NullPointerException if parent or tagName are null
         */
        fun findChild(parent: Element?, tagName: String?): Element? {
            if (parent == null || tagName == null) throw NullPointerException("Parent or tagname were null! "
                    + "parent = " + parent
                    + "; tagName = " + tagName)
            val nodes = parent.childNodes
            var node: Node
            val len = nodes.length
            for (i in 0 until len) {
                node = nodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == tagName) return node as Element
            }
            return null
        }

        /**
         * Returns the children elements with the specified tagName for the specified parent element.
         *
         * @param parent The parent whose children we're looking for.
         * @param tagName the name of the child to find
         * @return List of the children with the specified name
         * @throws NullPointerException if parent or tagName are null
         */
        fun findChildren(parent: Element?, tagName: String?): List<Element> {
            if (parent == null || tagName == null) throw NullPointerException("Parent or tagname were null! "
                    + "parent = " + parent
                    + "; tagName = " + tagName)
            val result = ArrayList<Element>()
            val nodes = parent.childNodes
            var node: Node
            val len = nodes.length
            for (i in 0 until len) {
                node = nodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    if (element.nodeName == tagName) result.add(element)
                }
            }
            return result
        }

        /**
         * Looks through all child elements of the specified root (recursively) and returns the first
         * element that corresponds to all parameters.
         *
         * @param root the Element where the search should begin
         * @param tagName the name of the node we're looking for
         * @param keyAttributeName the name of an attribute that the node has to have
         * @param keyAttributeValue the value that attribute must have
         * @return the Element in the tree under root that matches the specified parameters.
         * @throws NullPointerException if any of the arguments is null.
         */
        private fun locateElement(root: Element, tagName: String, keyAttributeName: String?,
                keyAttributeValue: String): Element? {
            val nodes = root.childNodes
            val len = nodes.length
            for (i in 0 until len) {
                val node = nodes.item(i)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val element = node as Element

                // is this the node we're looking for?
                if (node.getNodeName() == tagName) {
                    val attr = element.getAttribute(keyAttributeName)
                    if (attr != null && attr == keyAttributeValue) return element
                }

                // look inside.
                val child = locateElement(element, tagName, keyAttributeName, keyAttributeValue)
                if (child != null) return child
            }
            return null
        }

        /**
         * Looks through all child elements of the specified root (recursively) and returns the
         * elements that corresponds to all parameters.
         *
         * @param root the Element where the search should begin
         * @param tagName the name of the node we're looking for
         * @param keyAttributeName the name of an attribute that the node has to have
         * @param keyAttributeValue the value that attribute must have
         * @return list of Elements in the tree under root that match the specified parameters.
         * @throws NullPointerException if any of the arguments is null.
         */
        fun locateElements(root: Element, tagName: String,
                keyAttributeName: String?, keyAttributeValue: String): List<Element> {
            val result = ArrayList<Element>()
            val nodes = root.childNodes
            var node: Node
            val len = nodes.length
            for (i in 0 until len) {
                node = nodes.item(i)
                if (node.nodeType != Node.ELEMENT_NODE) continue

                // is this the node we're looking for?
                if (node.nodeName == tagName) {
                    val element = node as Element
                    val attr = element.getAttribute(keyAttributeName)
                    if (attr != null && attr == keyAttributeValue) result.add(element)
                }

                // look inside.
                val childs = locateElements(node as Element, tagName, keyAttributeName, keyAttributeValue)
                if (childs != null) result.addAll(childs)
            }
            return result
        }

        /**
         * Indicates whether namespace is one of the standard xml namespace.
         *
         * @param ns the namespace to analyze.
         * @return true if namespace is one of the standard xml namespace otherwise false.
         */
        fun isStandardXmlNamespace(ns: String): Boolean {
            var namespace = ns
            namespace = normalizeNamespace(namespace)
            return normalizeNamespace(XMLConstants.XML_NS_URI) == namespace || normalizeNamespace(XMLConstants.XMLNS_ATTRIBUTE_NS_URI) == namespace || normalizeNamespace(XMLConstants.W3C_XML_SCHEMA_NS_URI) == namespace || normalizeNamespace(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI) == namespace
        }

        /**
         * Gets the node namespace.
         *
         * @param node the `Element` or `Attr` node to analyze.
         * @return the node namespace or null.
         */
        fun getNamespaceUri(node: Node): String? {
            var prefix = node.prefix
            val namespaceUri = node.namespaceURI
            if (!TextUtils.isEmpty(namespaceUri)) return normalizeNamespace(namespaceUri)
            if (XMLConstants.XMLNS_ATTRIBUTE == node.nodeName || XMLConstants.XMLNS_ATTRIBUTE == prefix) return normalizeNamespace(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)
            val rootElement = node.ownerDocument.documentElement
            var parentNode: Node? = null
            while (parentNode != rootElement) {
                if (parentNode == null) {
                    if (node.nodeType == Node.ATTRIBUTE_NODE) {
                        parentNode = (node as Attr).ownerElement
                        // If attribute doesn't have prefix - it has its parent namespace
                        if (TextUtils.isEmpty(prefix)) prefix = parentNode.getPrefix()
                    } else if (node.nodeType == Node.ELEMENT_NODE) parentNode = node.parentNode else return null
                } else parentNode = parentNode.parentNode
                val parentPrefix = parentNode!!.prefix
                val parentNamespaceUri = parentNode.namespaceURI
                if (TextUtils.isEmpty(prefix)) {
                    val xmlnsAttribute = parentNode.attributes.getNamedItem("xmlns")
                    if (xmlnsAttribute != null) return (xmlnsAttribute as Attr).value
                } else if (prefix == parentPrefix) {
                    if (!TextUtils.isEmpty(parentNamespaceUri)) return normalizeNamespace(parentNamespaceUri)
                }
            }
            return if ("xml" == prefix) normalizeNamespace(XMLConstants.XML_NS_URI) else null
        }

        /**
         * Normalizes the namespace.
         *
         * @param namespace the namespace to normalize.
         * @return normalized namespace.
         */
        private fun normalizeNamespace(namespace: String): String {
            return if (namespace.endsWith("/")) {
                namespace.substring(0, namespace.length - 1)
            } else namespace
        }

        /**
         * Indicates whether element has any child element.
         *
         * @param element the namespace to analyze.
         * @return true if element has any child element otherwise false.
         */
        fun hasChildElements(element: Element): Boolean {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    return true
                }
            }
            return false
        }
        /**
         * Creates W3C Document from the xml.
         *
         * xml the xml that needs to be converted.
         * allowExternalEntities whether parsing of XML external entities and DOCTYPE declarations should be allowed.
         * @return the W3C Document.
         * @throws Exception is there is some error during operation.
         */
        /**
         * Creates W3C Document from the xml.
         *
         * @param xml the xml that needs to be converted.
         * @return the W3C Document.
         * @throws Exception is there is some error during operation.
         */
        /**
         * Creates W3C Document.
         *
         * @return the W3C Document.
         * @throws Exception is there is some error during operation.
         */
        @JvmOverloads
        @Throws(Exception::class)
        fun createDocument(xml: String? = null, allowExternalEntities: Boolean = false): Document {
            val builderFactory = newDocumentBuilderFactory(allowExternalEntities)
            builderFactory.isNamespaceAware = true
            val documentBuilder = builderFactory.newDocumentBuilder()
            return if (!TextUtils.isEmpty(xml)) {
                val input = StringUtils.fromString(xml!!)
                documentBuilder.parse(input)
            } else {
                documentBuilder.newDocument()
            }
        }

        /**
         * Creates XML from W3C Document from the xml.
         *
         * @param document the xml that needs to be converted.
         * @return the XML.
         * @throws Exception is there is some error during operation.
         */
        @Throws(Exception::class)
        fun createXml(document: Document?): String {
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            val stringWriter = StringWriter()
            val result = StreamResult(stringWriter)
            val source = DOMSource(document)
            transformer.transform(source, result)
            return stringWriter.toString()
        }
        /**
         * Creates and returns a new `DocumentBuilderFactory` instance, and sets the default
         * set of features.
         *
         * allowExternalEntities whether parsing of XML external entities and DOCTYPE declarations should be allowed.
         * @return the created factory
         * @throws ParserConfigurationException if setting a feature fails.
         */
        /**
         * Creates and returns a new `DocumentBuilderFactory` instance, and sets the default
         * set of features.
         *
         * @return the created factory
         * @throws ParserConfigurationException if setting a feature fails.
         */
        @JvmOverloads
        @Throws(ParserConfigurationException::class)
        fun newDocumentBuilderFactory(allowExternalEntities: Boolean = false): DocumentBuilderFactory {
            val factory = DocumentBuilderFactory.newInstance()
            if (!allowExternalEntities) disableExternalEntities(factory)
            return factory
        }

        /**
         * Tries to set the features which disable inclusion of XML external entities and DOCTYPE
         * declarations.
         *
         * @param factory the factory
         * @throws javax.xml.parsers.ParserConfigurationException if setting any of the features fails.
         */
        @Throws(ParserConfigurationException::class)
        fun disableExternalEntities(factory: DocumentBuilderFactory) {
            // It seems that currently the android parsers do not support the below features, but also
            // do not support external entities and thus are not vulnerable to the attacks which
            // setting these features aims to prevent. In other words, it is (currently) safe to ignore
            // the exceptions on android.

            // cmeng - org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl in android does not
            // support; don't try - just return
            if (OSUtils.IS_ANDROID) return
            try {
                factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false)
            } catch (pce: ParserConfigurationException) {
                if (OSUtils.IS_ANDROID) Timber.w("Failed to set feature: %s", FEATURE_EXTERNAL_GENERAL_ENTITIES) else throw pce
            }
            try {
                factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false)
            } catch (pce: ParserConfigurationException) {
                if (OSUtils.IS_ANDROID) Timber.w("Failed to set feature: %s", FEATURE_EXTERNAL_PARAMETER_ENTITIES) else throw pce
            }
            try {
                factory.setFeature(FEATURE_DISSALLOW_DOCTYPE, true)
            } catch (pce: ParserConfigurationException) {
                if (OSUtils.IS_ANDROID) Timber.w("Failed to set feature: %s", FEATURE_DISSALLOW_DOCTYPE) else throw pce
            }
        }
    }
}