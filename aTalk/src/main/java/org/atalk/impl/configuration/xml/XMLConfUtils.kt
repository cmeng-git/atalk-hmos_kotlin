/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration.xml

import org.atalk.util.xml.XMLUtils
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Common XML Tasks.
 *
 * @author Damian Minkov
 * @author Emil Ivov
 */
object XMLConfUtils : XMLUtils() {
    /**
     * Returns the element which is at the end of the specified String chain.
     * <great...grandparent>...<grandparent>.<parent>.<child>
     *
     * @param parent
     * the xml element that is the parent of the root of this chain.
     * @param chain
     * a String array containing the names of all the child's parent nodes.
     * @return the node represented by the specified chain
    </child></parent></grandparent></great...grandparent> */
    fun getChildElementByChain(parent: Element?, chain: Array<String?>?): Element? {
        if (chain == null) return null
        var e = parent
        for (i in chain.indices) {
            if (e == null) return null
            e = findChild(e, chain[i])
        }
        return e
    }

    /**
     * Creates (only if necessary) and returns the element which is at the end of the specified
     * path.
     *
     * @param doc
     * the target document where the specified path should be created
     * @param path
     * an array of `String` elements which represents the path to be created. Each
     * element of `path` up to and including the index `pathLength - 1`
     * must be valid  XML (element) names
     * @param pathLength
     * the length of the specified `path`
     * @return the component at the end of the newly created path.
     */
    fun createLastPathComponent(doc: Document?, path: Array<String?>?, pathLength: Int): Element? {
        requireNotNull(doc) { "doc must not be null" }
        requireNotNull(path) { "path must not be null" }
        val parent = doc.firstChild as Element
                ?: throw IllegalArgumentException("parent must not be null")
        var e = parent
        for (i in 0 until pathLength) {
            val pathEl = path[i]
            var newEl = findChild(e, pathEl)
            if (newEl == null) {
                newEl = doc.createElement(pathEl)
                e.appendChild(newEl)
            }
            e = newEl!!
        }
        return e
    }
}