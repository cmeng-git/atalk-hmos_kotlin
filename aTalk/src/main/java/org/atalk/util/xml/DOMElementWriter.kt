/*
 * Copyright @ 2015 Atlassian Pty Ltd
 * Copyright  2000-2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util.xml

import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.*

/**
 * Writes a DOM tree to a given Writer.
 *
 *
 *
 * Utility class used by [XMLUtils] and
 * [net.java.sip.communicator.slick.runner.SipCommunicatorSlickRunner].
 *
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class DOMElementWriter {
    /**
     * Don't try to be too smart but at least recognize the predefined entities.
     */
    protected var knownEntities = arrayOf("gt", "amp", "lt", "apos", "quot")

    /**
     * Writes a DOM tree to a stream in UTF8 encoding. Note that
     * it prepends the &lt;?xml version='1.0' encoding='UTF-8'?&gt;.
     * The indent number is set to 0 and a 2-space indent.
     *
     * @param root
     * the root element of the DOM tree.
     * @param out
     * the outputstream to write to.
     * @throws IOException
     * if an error happens while writing to the stream.
     */
    @Throws(IOException::class)
    fun write(root: Element, out: OutputStream?) {
        val wri = OutputStreamWriter(out, "UTF-8")
        wri.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + lSep)
        write(root, wri, 0, "  ")
        wri.flush()
    }

    /**
     * Writes a DOM tree to a stream.
     *
     * @param element
     * the Root DOM element of the tree
     * @param out
     * where to send the output
     * @param indent
     * number of
     * @param indentWith
     * string that should be used to indent the corresponding tag.
     * @throws IOException
     * if an error happens while writing to the stream.
     */
    @Throws(IOException::class)
    fun write(element: Node, out: Writer, indent: Int,
              indentWith: String?) {
        // Write indent characters
        for (i in 0 until indent) {
            out.write(indentWith)
        }
        if (element.nodeType == Node.COMMENT_NODE) {
            out.write("<!--")
            out.write(encode(element.nodeValue))
            out.write("-->")
        } else {
            // Write element
            out.write("<")
            out.write((element as Element).tagName)

            // Write attributes
            val attrs = element.getAttributes()
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i) as Attr
                out.write(" ")
                out.write(attr.name)
                out.write("=\"")
                out.write(encode(attr.value))
                out.write("\"")
            }
            out.write(">")
        }
        // Write child elements and text
        var hasChildren = false
        val children = element.childNodes
        var i = 0
        while (element.hasChildNodes() && i < children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.ELEMENT_NODE, Node.COMMENT_NODE -> {
                    if (!hasChildren) {
                        out.write(lSep)
                        hasChildren = true
                    }
                    write(child, out, indent + 1, indentWith)
                }
                Node.TEXT_NODE ->
                    //if this is a new line don't print it as we print our own.
                    if (child.nodeValue != null
                            && (!child.nodeValue.contains("\n")
                                    || child.nodeValue.trim { it <= ' ' }.isNotEmpty())) out.write(encode(child.nodeValue))
                Node.CDATA_SECTION_NODE -> {
                    out.write("<![CDATA[")
                    out.write(encodedata((child as Text).data))
                    out.write("]]>")
                }
                Node.ENTITY_REFERENCE_NODE -> {
                    out.write('&'.code)
                    out.write(child.nodeName)
                    out.write(';'.code)
                }
                Node.PROCESSING_INSTRUCTION_NODE -> {
                    out.write("<?")
                    out.write(child.nodeName)
                    val data = child.nodeValue
                    if (data != null && data.isNotEmpty()) {
                        out.write(' '.code)
                        out.write(data)
                    }
                    out.write("?>")
                }
            }
            i++
        }

        // If we had child elements, we need to indent before we close the element, otherwise
        // we're on the same line and don't need to indent
        if (hasChildren) {
            for (i in 0 until indent) {
                out.write(indentWith)
            }
        }

        // Write element close
        if (element.nodeType == Node.ELEMENT_NODE) {
            out.write("</")
            out.write((element as Element).tagName)
            out.write(">")
        }
        out.write(lSep)
        out.flush()
    }

    /**
     * Escape &lt;, &gt; &amp; &apos;, &quot; as their entities and
     * drop characters that are illegal in XML documents.
     *
     * @param value
     * the value to encode
     * @return a String containing the encoded element.
     */
    fun encode(value: String): String {
        val sb = StringBuffer()
        val len = value.length
        for (i in 0 until len) {
            val c = value[i]
            when (c) {
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '\'' -> sb.append("&apos;")
                '\"' -> sb.append("&quot;")
                '&' -> {
                    val nextSemi = value.indexOf(";", i)
                    if (nextSemi < 0
                            || !isReference(value.substring(i, nextSemi + 1))) sb.append("&amp;") else sb.append('&')
                }
                else -> if (isLegalCharacter(c)) sb.append(c)
            }
        }
        return sb.substring(0)
    }

    /**
     * Drop characters that are illegal in XML documents.
     *
     *
     *
     * Also ensure that we are not including an `]]>`
     * marker by replacing that sequence with
     * `&#x5d;&#x5d;&gt;`.
     *
     *
     *
     * See XML 1.0 2.2 [https://www.w3.org/TR/1998/REC-xml-19980210#charsets](https://www.w3.org/TR/1998/REC-xml-19980210#charsets) and
     * 2.7 [https://www.w3.org/TR/1998/REC-xml-19980210#sec-cdata-sect](https://www.w3.org/TR/1998/REC-xml-19980210#sec-cdata-sect).
     *
     * @param value
     * the value to encode
     * @return a String containing the encoded value.
     */
    fun encodedata(value: String): String {
        val sb = StringBuffer()
        val len = value.length
        for (i in 0 until len) {
            val c = value[i]
            if (isLegalCharacter(c)) {
                sb.append(c)
            }
        }
        var result = sb.substring(0)
        var cdEnd = result.indexOf("]]>")
        while (cdEnd != -1) {
            sb.setLength(cdEnd)
            sb.append("&#x5d;&#x5d;&gt;")
                    .append(result.substring(cdEnd + 3))
            result = sb.substring(0)
            cdEnd = result.indexOf("]]>")
        }
        return result
    }

    /**
     * Is the given argument a character or entity reference?
     *
     * @param ent
     * the string whose nature we need to determine.
     * @return `true` if `ent` is an entity reference and
     * `false` otherwise.
     */
    fun isReference(ent: String): Boolean {
        if (ent[0] != '&' || !ent.endsWith(";")) return false
        if (ent[1] == '#') {
            return if (ent[2] == 'x') {
                try {
                    ent.substring(3, ent.length - 1).toInt(16)
                    true
                } catch (nfe: NumberFormatException) {
                    false
                }
            } else {
                try {
                    ent.substring(2, ent.length - 1).toInt()
                    true
                } catch (nfe: NumberFormatException) {
                    false
                }
            }
        }
        val name = ent.substring(1, ent.length - 1)
        for (knownEntity in knownEntities) {
            if (name == knownEntity) {
                return true
            }
        }
        return false
    }

    /**
     * Is the given character allowed inside an XML document?
     *
     *
     *
     * See XML 1.0 2.2 [
 * https://www.w3.org/TR/1998/REC-xml-19980210#charsets](https://www.w3.org/TR/1998/REC-xml-19980210#charsets).
     *
     * @param c
     * the character whose nature we'd like to determine.
     * @return true if c is a legal character and false otherwise
     * @since 1.10, Ant 1.5
     */
    fun isLegalCharacter(c: Char): Boolean {
        if (c.code == 0x9 || c.code == 0xA || c.code == 0xD) {
            return true
        } else if (c.code < 0x20) {
            return false
        } else if (c.code <= 0xD7FF) {
            return true
        } else if (c.code < 0xE000) {
            return false
        } else if (c.code <= 0xFFFD) {
            return true
        }
        return false
    }

    companion object {
        /**
         * The system-specific line separator as defined by the well-known system property.
         */
        private val lSep = System.getProperty("line.separator")

        /**
         * Decodes an XML (element) name according to
         * https://www.w3.org/TR/xml/#NT-Name.
         *
         * @param name
         * the XML (element) name to be decoded
         * @return a `String` which represents `name` decoded
         * according to https://www.w3.org/TR/xml/#NT-Name
         */
        fun decodeName(name: String): String {
            val length = name.length
            val value = StringBuilder(length)
            var i = 0
            while (i < length) {
                val start = name.indexOf('_', i)

                /*
                 * If there's nothing else to decode, append whatever's left and finish.
                 */
                if (start == -1) {
                    value.append(name, i, length)
                    break
                }

                /*
			 * We may have to decode from start (inclusive). Append from i to start (exclusive).
             */
                if (i != start) value.append(name, i, start)

                // Determine whether we'll actually decode.
                val end = start + 6 /* xHHHH_ */
                if (end < length && name[start + 1] == 'x' && name[end] == '_'
                        && isHexDigit(name[start + 2])
                        && isHexDigit(name[start + 3])
                        && isHexDigit(name[start + 4])
                        && isHexDigit(name[start + 5])) {
                    val c = name.substring(start + 2, end).toInt(16).toChar()

                    /*
                     * We've decoded a character. But is it really a character we'd have encoded in
                     * the first place? We don't want to accidentally decode a string just because it
                     * looked like an encoded character.
                     */
                    if (if (start == 0) !isNameStartChar(c) else !isNameChar(c)) {
                        value.append(c)
                        i = end + 1
                        continue
                    }
                }

                // We didn't really have to decode and the string was a literal.
                value.append(name[start])
                i = start + 1
            }
            return value.toString()
        }

        /**
         * Encodes a specific `String` so that it is a valid XML (element)
         * name according to https://www.w3.org/TR/xml/#NT-Name.
         *
         * @param value
         * the `String` to be encoded so that it is a valid XML name
         * @return a `String` which represents `value` encoded so that
         * it is a valid XML (element) name
         */
        fun encodeName(value: String): String {
            val length = value.length
            val name = StringBuilder()
            for (i in 0 until length) {
                val c = value[i]
                if (i == 0) {
                    if (isNameStartChar(c)) {
                        name.append(c)
                        continue
                    }
                } else if (isNameChar(c)) {
                    name.append(c)
                    continue
                }
                name.append("_x")
                if (c.code <= 0x000F) name.append("000") else if (c.code <= 0x00FF) name.append("00") else if (c.code <= 0x0FFF) name.append('0')
                name.append(Integer.toHexString(c.code).uppercase(Locale.getDefault()))
                name.append('_')
            }
            return name.toString()
        }

        /**
         * Determines whether a specific character represents a hex digit.
         *
         * @param c
         * the character to be checked whether it represents a hex digit
         * @return `true` if the specified character represents a hex digit;
         * otherwise, `false`
         */
        private fun isHexDigit(c: Char): Boolean {
            return '0' <= c && c <= '9' || 'A' <= c && c <= 'F' || 'a' <= c && c <= 'f'
        }

        /**
         * Determines whether a specific characters is a `NameChar` as
         * defined by https://www.w3.org/TR/xml/#NT-Name.
         *
         * @param c
         * the character which is to be determines whether it is a
         * `NameChar`
         * @return `true` if the specified character is a `NameChar`;
         * otherwise, `false`
         */
        private fun isNameChar(c: Char): Boolean {
            return if (isNameStartChar(c)) true else if (c == '-' || c == '.') true else if ('0' <= c && c <= '9') true else if (c.code == 0xB7) true else if (c.code < 0x0300) false else if (c.code <= 0x036F) true else if (c.code < 0x203F) false else if (c.code <= 0x2040) true else false
        }

        /**
         * Determines whether a specific characters is a `NameStartChar` as
         * defined by https://www.w3.org/TR/xml/#NT-Name.
         *
         * @param c
         * the character to be determined whether it is a
         * `NameStartChar`
         * @return `true` if the specified character is a
         * `NameStartChar`; otherwise, `false`
         */
        private fun isNameStartChar(c: Char): Boolean {
            return if (c == ':' || c == '_') true else if ('A' <= c && c <= 'Z') true else if ('a' <= c && c <= 'z') true else if (c.code < 0xC0) false else if (c.code <= 0xD6) true else if (c.code < 0xD8) false else if (c.code <= 0xF6) true else if (c.code < 0xF8) false else if (c.code <= 0x2FF) true else if (c.code < 0x370) false else if (c.code <= 0x37D) true else if (c.code < 0x37F) false else if (c.code <= 0x1FFF) true else if (c.code < 0x200C) false else if (c.code <= 0x200D) true else if (c.code < 0x2070) false else if (c.code <= 0x218F) true else if (c.code < 0x2C00) false else if (c.code <= 0x2FEF) true else if (c.code < 0x3001) false else if (c.code <= 0xD7FF) true else if (c.code < 0xF900) false else if (c.code <= 0xFDCF) true else if (c.code < 0xFDF0) false else if (c.code <= 0xFFFD) true else false
        }
    }
}