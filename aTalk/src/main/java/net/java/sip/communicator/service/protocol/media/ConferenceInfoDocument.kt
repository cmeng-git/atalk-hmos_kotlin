/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import org.atalk.util.xml.XMLException
import org.atalk.util.xml.XMLUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.io.UnsupportedEncodingException
import java.util.*
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * A class that represents a Conference Information XML document as defined in RFC4575. It wraps
 * around a DOM `Document` providing convenience functions.
 *
 * []//tools.ietf.org/html/rfc4575"">&quot;https://tools.ietf.org/html/rfc4575&quot;
 *
 * @author Boris Grozev
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class ConferenceInfoDocument {
    /**
     * Returns the `Document` that this instance wraps around.
     *
     * @return the `Document` that this instance wraps around.
     */
    /**
     * The `Document` object that we wrap around.
     */
    var document: Document? = null
        private set

    /**
     * The single `conference-info` element of `document`
     */
    private var conferenceInfo: Element?

    /**
     * The `conference-description` child element of `conference-info`.
     */
    private var conferenceDescription: Element?

    /**
     * The `conference-state` child element of `conference-info`.
     */
    private var conferenceState: Element?

    /**
     * The `conference-state` child element of `conference-state`.
     */
    var userCount: Element? = null

    /**
     * The `users` child element of `conference-info`.
     */
    private var users: Element?

    /**
     * A list of `User`s representing the children of `users`
     */
    val usersList: MutableList<User> = LinkedList()

    /**
     * Creates a new `ConferenceInfoDocument` instance.
     *
     * @throws XMLException if a document failed to be created.
     */
    constructor() {
        document = try {
            XMLUtils.createDocument()
        } catch (e: Exception) {
            Timber.e(e, "Failed to create a new document.")
            throw XMLException(e.message)
        }
        conferenceInfo = document!!.createElementNS(NAMESPACE, CONFERENCE_INFO_ELEMENT)
        document!!.appendChild(conferenceInfo)
        version = 1
        conferenceDescription = document!!.createElement(CONFERENCE_DESCRIPTION_ELEMENT)
        conferenceInfo!!.appendChild(conferenceDescription)
        conferenceState = document!!.createElement(CONFERENCE_STATE_ELEMENT)
        conferenceInfo!!.appendChild(conferenceState)
        setUserCount(0)
        users = document!!.createElement(USERS_ELEMENT)
        conferenceInfo!!.appendChild(users)
    }

    /**
     * Creates a new `ConferenceInfoDocument` instance and populates it by parsing the XML in
     * `xml`
     *
     * @param xml the XML string to parse
     * @throws XMLException If parsing failed
     */
    constructor(xml: String) {
        val bytes = try {
            xml.toByteArray(charset("UTF-8"))
        } catch (uee: UnsupportedEncodingException) {
            Timber.w(uee, "Failed to gets bytes from String for the UTF-8 charset")
            xml.toByteArray()
        }
        document = try {
            XMLUtils.newDocumentBuilderFactory().newDocumentBuilder()
                    .parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw XMLException(e.message)
        }
        conferenceInfo = document!!.documentElement
        if (conferenceInfo == null) {
            throw XMLException("Could not parse conference-info document,"
                    + " conference-info element not found")
        }
        conferenceDescription = XMLUtils.findChild(conferenceInfo,
                CONFERENCE_DESCRIPTION_ELEMENT)
        // conference-description is mandatory
        if (conferenceDescription == null) {
            throw XMLException("Could not parse conference-info document,"
                    + " conference-description element not found")
        }
        conferenceState = XMLUtils.findChild(conferenceInfo, CONFERENCE_STATE_ELEMENT)
        if (conferenceState != null) userCount = XMLUtils.findChild(conferenceState, USER_COUNT_ELEMENT)
        users = XMLUtils.findChild(conferenceInfo, USERS_ELEMENT)
        if (users == null) {
            throw XMLException("Could not parse conference-info document,"
                    + " 'users' element not found")
        }
        val usersNodeList = users!!.getElementsByTagName(USER_ELEMENT)
        for (i in 0 until usersNodeList.length) {
            val user = User(usersNodeList.item(i) as Element)
            usersList.add(user)
        }
    }

    /**
     * Creates a new `ConferenceInfoDocument` instance that represents a copy of
     * `confInfo`
     *
     * @param confInfo the document to copy
     * @throws XMLException if a document failed to be created.
     */
    constructor(confInfo: ConferenceInfoDocument) : this() {

        // temporary
        val sid = confInfo.sid
        if (sid != null && sid != "") this.sid = sid
        entity = confInfo.entity
        state = confInfo.state
        setUserCount(confInfo.getUserCount())
        usersState = confInfo.usersState
        version = confInfo.version
        for (user in confInfo.getUsers()) addUser(user)
    }
    /**
     * Returns the value of the `version` attribute of the `conference-info` element,
     * or -1 if there is no `version` attribute or if it's value couldn't be parsed as an
     * integer.
     *
     * @return the value of the `version` attribute of the `conference-info` element,
     * or -1 if there is no `version` attribute or if it's value couldn't be parsed
     * as an integer.
     */
    /**
     * Sets the `version` attribute of the `conference-info` element.
     */
    var version: Int
        get() {
            val versionString = conferenceInfo!!.getAttribute(VERSION_ATTR_NAME)
                    ?: return -1
            var version = -1
            try {
                version = versionString.toInt()
            } catch (e: NumberFormatException) {
                Timber.i("Failed to parse version string: %s", versionString)
            }
            return version
        }
        set(version) {
            conferenceInfo!!.setAttribute(VERSION_ATTR_NAME, Integer.toString(version))
        }
    /**
     * Gets the value of the `state` attribute of the `conference-info` element.
     *
     * @return the value of the `state` attribute of the `conference-info` element.
     */
    /**
     * Sets the value of the `state` attribute of the `conference-info` element.
     */
    var state: State?
        get() = getState(conferenceInfo)
        set(state) {
            setState(conferenceInfo, state)
        }
    /**
     * Returns the value of the `state` attribute of the `users` child of the
     * `conference-info` element.
     *
     * @return the value of the `state` attribute of the `users` child of the
     * `conference-info` element.
     */
    /**
     * Sets the `state` attribute of the `users` chuld of the `conference-info` element.
     */
    var usersState: State?
        get() = getState(users)
        set(state) {
            setState(users, state)
        }
    /**
     * Gets the value of the `sid` attribute of the `conference-info` element. This is
     * not part of RFC4575 and is here because we are temporarily using it in our XMPP
     * implementation. TODO: remote it when we define another way to handle the Jingle SID
     */
    /**
     * Sets the value of the `sid` attribute of the `conference-info` element. This is
     * not part of RFC4575 and is here because we are temporarily using it in our XMPP
     * implementation. TODO: remote it when we define another way to handle the Jingle SID
     *
     * @param sid the value to set the `sid` attribute of the `conference-info` element
     * to.
     */
    var sid: String?
        get() = conferenceInfo!!.getAttribute("sid")
        set(sid) {
            if (sid == null || sid == "") conferenceInfo!!.removeAttribute("sid") else conferenceInfo!!.setAttribute("sid", sid)
        }
    /**
     * Gets the value of the `entity` attribute of the `conference-info` element.
     *
     * @return The value of the `entity` attribute of the `conference-info` element.
     */
    /**
     * Sets the value of the `entity` attribute of the `conference-info` element.
     *
     * entity the value to set the `entity` attribute of the `conference-info` document to.
     */
    var entity: String?
        get() = conferenceInfo!!.getAttribute(ENTITY_ATTR_NAME)
        set(entity) {
            if (entity == null || entity == "") conferenceInfo!!.removeAttribute(ENTITY_ATTR_NAME) else conferenceInfo!!.setAttribute(ENTITY_ATTR_NAME, entity)
        }

    /**
     * Sets the content of the `user-count` child element of the `conference-state`
     * child element of `conference-info`
     *
     * @param count the value to set the content of `user-count` to
     */
    fun setUserCount(count: Int) {
        // conference-state and its user-count child aren't mandatory
        if (userCount != null) {
            userCount!!.textContent = count.toString()
        } else {
            if (conferenceState == null) {
                conferenceState = document!!.createElement(CONFERENCE_STATE_ELEMENT)
                conferenceInfo!!.appendChild(conferenceState)
            }
            userCount = document!!.createElement(USER_COUNT_ELEMENT)
            userCount!!.textContent = count.toString()
            conferenceState!!.appendChild(userCount)
        }
    }

    /**
     * Returns the content of the `user-count` child of the `conference-state` child
     * of `conference-info`, parsed as an integer, if they exist. Returns -1 if either there
     * isn't a `conference-state` element, it doesn't have a `user-count` child, or
     * parsing as integer failed.
     *
     * @return the content of the `user-count` child of the `conference-state` child
     * of `conference-info` element.
     */
    private fun getUserCount(): Int {
        var ret = -1
        try {
            ret = userCount!!.textContent.toInt()
        } catch (e: Exception) {
            Timber.w("Could not parse user-count field")
        }
        return ret
    }

    /**
     * Returns the XML representation of the `conference-info` tree, or `null` if an
     * error occurs while trying to get it.
     *
     * @return the XML representation of the `conference-info` tree, or `null` if an
     * error occurs while trying to get it.
     */
    fun toXml(enclosingNamespace: String?): String? {
        return try {
            val transformer = TransformerFactory.newInstance().newTransformer()
            val buffer = StringWriter()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            transformer.transform(DOMSource(conferenceInfo), StreamResult(buffer))
            buffer.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the XML representation of the document (from the `conference-info` element
     * down), or an error string in case the XML cannot be generated for some reason.
     *
     * @return the XML representation of the document or an error string.
     */
    override fun toString(): String {
        val s = toXml(null)
        return s ?: "Could not get conference-info XML"
    }

    /**
     * Returns the list of `User` that represents the `user` children of the
     * `users` child element of `conference-info`
     *
     * @return the list of `User` that represents the `user` children of the
     * `users` child element of `conference-info`
     */
    private fun getUsers(): List<User> {
        return usersList
    }

    /**
     * Searches this document's `User`s and returns the one with `entity` attribute
     * `entity`, or `null` if one wasn't found.
     *
     * @param entity The value of the `entity` attribute to search for.
     * @return the `User` of this document with `entity` attribute `entity`, or
     * `null` if one wasn't found.
     */
    fun getUser(entity: String?): User? {
        if (entity == null) return null
        for (u in usersList) {
            if (entity == u.entity) return u
        }
        return null
    }

    /**
     * Creates a new `User` instance, adds it to the document and returns it.
     *
     * @param entity The value to use for the `entity` attribute of the new `User`.
     * @return the newly created `User` instance.
     */
    fun addNewUser(entity: String?): User {
        val userElement = document!!.createElement(USER_ELEMENT)
        val user = User(userElement)
        user.entity = entity
        users!!.appendChild(userElement)
        usersList.add(user)
        return user
    }

    /**
     * Adds a copy of `user` to this `ConferenceInfoDocument`
     *
     * @param user the `User` to add a copy of
     */
    fun addUser(user: User) {
        val newUser = addNewUser(user.entity)
        newUser.displayText = user.displayText
        newUser.state = user.state
        for (endpoint in user.endpoints) newUser.addEndpoint(endpoint)
    }

    /**
     * Removes a specific `User` (the one with entity `entity`) from the document.
     *
     * @param entity the entity of the `User` to remove.
     */
    fun removeUser(entity: String?) {
        val user = getUser(entity)
        if (user != null) {
            usersList.remove(user)
            users!!.removeChild(user.userElement)
        }
    }

    /**
     * Returns the `State` corresponding to the `state` attribute of an
     * `Element`. Default to `State.FULL` which is the RFC4575 default.
     *
     * @param element the `Element`
     * @return the `State` corresponding to the `state` attribute of an
     * `Element`.
     */
    private fun getState(element: Element?): State {
        val state = State.parseString(element!!.getAttribute(STATE_ATTR_NAME))
        return state ?: State.FULL
    }

    /**
     * Sets the "state" attribute of `element` to `state`. If `state` is
     * `State.FULL` removes the "state" attribute, because this is the default value.
     *
     * @param element The `Element` for which to set the "state" attribute of.
     * @param state the `State` which to set.
     */
    private fun setState(element: Element?, state: State?) {
        if (element != null) {
            if (state == State.FULL || state == null) element.removeAttribute(STATE_ATTR_NAME) else element.setAttribute(STATE_ATTR_NAME, state.toString())
        }
    }

    /**
     * Sets the `status` child element of `element`. If `statusString` is
     * `null`, the child element is removed if present.
     *
     * @param element the `Element` for which to set the `status` child element.
     * @param statusString the `String` to use for the text content of the `status` element
     */
    private fun setStatus(element: Element, statusString: String?) {
        var statusElement = XMLUtils.findChild(element, STATUS_ELEMENT)
        if (statusString == null || statusString == "") {
            if (statusElement != null) element.removeChild(statusElement)
        } else {
            if (statusElement == null) {
                statusElement = document!!.createElement(STATUS_ELEMENT)
                element.appendChild(statusElement)
            }
            statusElement!!.textContent = statusString
        }
    }

    /**
     * Represents the possible values for the `state` attribute (see RFC4575)
     */
    /**
     * Creates a `State` instance with the specified name.
     */
    enum class State
    (val description: String) {

        /**
         * State `full`
         */
        FULL("full"),

        /**
         * State `partial`
         */
        PARTIAL("partial"),

        /**
         * State `deleted`
         */
        DELETED("deleted");

        /**
         * Returns the name of this `State`
         *
         * @return the name of this `State`
         */
        override fun toString(): String {
            return description
        }

        companion object {
            /**
             * Returns a `State` value corresponding to the specified `name`
             *
             * @return a `State` value corresponding to the specified `name`
             */
            fun parseString(name: String): State? {
                return when (name) {
                    FULL.toString() -> FULL
                    PARTIAL.toString() -> PARTIAL
                    DELETED.toString() -> DELETED
                    else -> null
                }
            }
        }
    }

    /**
     * Wraps around an `Element` and represents a `user` element (child of the
     * `users` element). See RFC4575.
     */
    inner class User(
            /**
             * The underlying `Element`.
             */
            val userElement: Element) {
        /**
         * The list of `Endpoint`s representing the `endpoint` children of this
         * `User`'s element.
         */
        private val endpointsList: MutableList<Endpoint> = LinkedList()

        /**
         * Creates a new `User` instance with the specified `Element` as its underlying element.
         */
        init {
            val endpointsNodeList = userElement.getElementsByTagName(ENDPOINT_ELEMENT)
            for (i in 0 until endpointsNodeList.length) {
                val endpoint = Endpoint(endpointsNodeList.item(i) as Element)
                endpointsList.add(endpoint)
            }
        }
        /**
         * Returns the value of the `entity` attribute of this `User`'s element.
         *
         * @return the value of the `entity` attribute of this `User`'s element.
         */
        var entity: String?
            get() = userElement.getAttribute(ENTITY_ATTR_NAME)
            set(entity) {
                if (entity == null || entity == "") userElement.removeAttribute(ENTITY_ATTR_NAME) else userElement.setAttribute(ENTITY_ATTR_NAME, entity)
            }

        /**
         * Sets the `state` attribute of this `User`'s element to `state`
         *
         * state the value to use for the `state` attribute.
         */
        var state: State?
            get() = getState(userElement)
            set(state) {
                setState(userElement, state)
            }
        /**
         * Returns the text content of the `display-text` child element of this `User`
         * 's element, if it has such a child. Returns `null` otherwise.
         *
         * @return the text content of the `display-text` child element of this `User`
         * 's element, if it has such a child. Returns `null` otherwise.
         */
        /**
         * Sets the `display-text` child element to this `User`'s element.
         *
         * text the text content to use for the `display-text` element.
         */
        var displayText: String?
            get() {
                val displayText = XMLUtils.findChild(userElement, DISPLAY_TEXT_ELEMENT)
                return displayText?.textContent
            }
            set(text) {
                var displayText = XMLUtils.findChild(userElement, DISPLAY_TEXT_ELEMENT)
                if (text == null || text == "") {
                    if (displayText != null) userElement.removeChild(displayText)
                } else {
                    if (displayText == null) {
                        displayText = document!!.createElement(DISPLAY_TEXT_ELEMENT)
                        userElement.appendChild(displayText)
                    }
                    displayText!!.textContent = text
                }
            }

        /**
         * Returns the list of `Endpoint`s which represent the `endpoint` children of
         * this `User`'s element.
         *
         * @return the list of `Endpoint`s which represent the `endpoint` children of
         * this `User`'s element.
         */
        val endpoints: List<Endpoint>
            get() = endpointsList

        /**
         * Searches this `User`'s associated `Endpoint`s and returns the one with
         * `entity` attribute `entity`, or `null` if one wasn't found.
         *
         * @param entity The value of the `entity` attribute to search for.
         * @return The `Endpoint` with `entity` attribute `entity`, or
         * `null` if one wasn't found.
         */
        fun getEndpoint(entity: String?): Endpoint? {
            if (entity == null) return null
            for (e in endpointsList) {
                if (entity == e.entity) return e
            }
            return null
        }

        /**
         * Creates a new `Endpoint` instance, adds it to this `User` and returns it.
         *
         * @param entity The value to use for the `entity` attribute of the new `Endpoint`.
         * @return the newly created `Endpoint` instance.
         */
        fun addNewEndpoint(entity: String?): Endpoint {
            val endpointElement = document!!.createElement(ENDPOINT_ELEMENT)
            val endpoint = Endpoint(endpointElement)
            endpoint.entity = entity
            userElement.appendChild(endpointElement)
            endpointsList.add(endpoint)
            return endpoint
        }

        /**
         * Adds a copy of `endpoint` to this `User`
         *
         * @param endpoint the `Endpoint` to add a copy of
         */
        fun addEndpoint(endpoint: Endpoint) {
            val newEndpoint = addNewEndpoint(endpoint.entity)
            newEndpoint.status = endpoint.status
            newEndpoint.state = endpoint.state
            for (media in endpoint.medias) newEndpoint.addMedia(media)
        }

        /**
         * Removes a specific `Endpoint` (the one with entity `entity`) from this 'User`.
         *
         * @param entity the `entity` of the `Endpoint` to remove
         */
        fun removeEndpoint(entity: String?) {
            val endpoint = getEndpoint(entity)
            if (endpoint != null) {
                endpointsList.remove(endpoint)
                userElement.removeChild(endpoint.endpointElement)
            }
        }
    }

    /**
     * Wraps around an `Element` and represents an `endpoint` element. See RFC4575.
     */
    inner class Endpoint(
            /**
             * The underlying `Element`.
             */
            val endpointElement: Element) {
        /**
         * The list of `Media`s representing the `media` children elements of this `Endpoint`'s element.
         */
        private val mediasList: MutableList<Media> = LinkedList()

        /**
         * Creates a new `Endpoint` instance with the specified `Element` as its underlying element.
         */
        init {
            val mediaNodeList = endpointElement.getElementsByTagName(MEDIA_ELEMENT)
            for (i in 0 until mediaNodeList.length) {
                val media = Media(mediaNodeList.item(i) as Element)
                mediasList.add(media)
            }
        }
        /**
         * Returns the `entity` attribute of this `Endpoint`'s element.
         *
         * @return the `entity` attribute of this `Endpoint`'s element.
         */
        /**
         * Sets the `entity` attribute of this `Endpoint`'s element to `entity`
         *
         *  entity the value to set for the `entity` attribute.
         */
        var entity: String?
            get() = endpointElement.getAttribute(ENTITY_ATTR_NAME)
            set(entity) {
                if (entity == null || entity == "") endpointElement.removeAttribute(ENTITY_ATTR_NAME) else endpointElement.setAttribute(ENTITY_ATTR_NAME, entity)
            }
        /**
         * Returns the value of the `state` attribute of this `Endpoint`'s element
         *
         * @return the value of the `state` attribute of this `Endpoint`'s element
         */
        /**
         * Sets the `state` attribute of this `User`'s element to `state`
         *
         * state the value to use for the `state` attribute.
         */
        var state: State?
            get() = getState(endpointElement)
            set(state) {
                setState(endpointElement, state)
            }
        /**
         * Returns the `EndpointStatusType` corresponding to the `status` child of
         * this `Endpoint` 's element, or `null`.
         *
         * @return the `EndpointStatusType` corresponding to the `status` child of
         * this `Endpoint` 's element, or `null`.
         */
        /**
         * Sets the `status` child element of this `Endpoint`'s element.
         *
         * status the value to be used for the text content of the `status` element.
         */
        var status: EndpointStatusType?
            get() {
                val statusElement = XMLUtils.findChild(endpointElement, STATUS_ELEMENT)
                return if (statusElement == null) null else EndpointStatusType.parseString(statusElement.textContent)
            }
            set(status) {
                setStatus(endpointElement,
                        status?.toString())
            }

        /**
         * Returns the list of `Media`s which represent the `media` children of this `Endpoint`'s element.
         *
         * @return the list of `Media`s which represent the `media` children of this
         * `Endpoint`'s element.
         */
        val medias: List<Media>
            get() = mediasList

        /**
         * Searches this `Endpoint`'s associated `Media`s and returns the one with
         * `id` attribute `id`, or `null` if one wasn't found.
         *
         * @param id The value of the `id` attribute to search for.
         * @return The `Media`s with `id` attribute `id`, or `null` if
         * one wasn't found.
         */
        fun getMedia(id: String?): Media? {
            if (id == null) return null
            for (m in mediasList) {
                if (id == m.id) return m
            }
            return null
        }

        /**
         * Creates a new `Media` instance, adds it to this `Endpoint` and returns it.
         *
         * @param id The value to use for the `id` attribute of the new `Media`'s
         * element.
         * @return the newly created `Media` instance.
         */
        fun addNewMedia(id: String?): Media {
            val mediaElement = document!!.createElement(MEDIA_ELEMENT)
            val media = Media(mediaElement)
            media.id = id
            endpointElement.appendChild(mediaElement)
            mediasList.add(media)
            return media
        }

        /**
         * Adds a copy of `media` to this `Endpoint`
         *
         * @param media the `Media` to add a copy of
         */
        fun addMedia(media: Media) {
            val newMedia = addNewMedia(media.id)
            newMedia.srcId = media.srcId
            newMedia.type = media.type
            newMedia.status = media.status
        }

        /**
         * Removes a specific `Media` (the one with id `id`) from this
         * `Endpoint`.
         *
         * @param id the `id` of the `Media` to remove.
         */
        fun removeMedia(id: String?) {
            val media = getMedia(id)
            if (media != null) {
                mediasList.remove(media)
                endpointElement.removeChild(media.mediaElement)
            }
        }
    }

    /**
     * Wraps around an `Element` and represents a `media` element. See RFC4575.
     */
    inner class Media
    /**
     * Creates a new `Media` instance with the specified `Element` as its underlying element.
     */
    (
            /**
             * The underlying `Element`.
             */
            val mediaElement: Element) {
        /**
         * Returns the `id` attribute of this `Media`'s element.
         *
         * @return the `id` attribute of this `Media`'s element.
         */
        /**
         * Sets the `id` attribute of this `Media`'s element to `id`
         *
         * id the value to set for the `id` attribute.
         */
        var id: String?
            get() = mediaElement.getAttribute(ID_ATTR_NAME)
            set(id) {
                if (id == null || id == "") mediaElement.removeAttribute(ID_ATTR_NAME) else mediaElement.setAttribute(ID_ATTR_NAME, id)
            }
        /**
         * Returns the text content of the `src-id` child element of this `Media`'s
         * element, if it has such a child. Returns `null` otherwise.
         *
         * @return the text content of the `src-id` child element of this `Media`'s
         * element, if it has such a child. Returns `null` otherwise.
         */
        /**
         * Sets the `src-id` child element of this `Media`'s element.
         */
        var srcId: String?
            get() {
                val srcIdElement = XMLUtils.findChild(mediaElement, SRC_ID_ELEMENT)
                return srcIdElement?.textContent
            }
            set(srcId) {
                var srcIdElement = XMLUtils.findChild(mediaElement, SRC_ID_ELEMENT)
                if (srcId == null || srcId == "") {
                    if (srcIdElement != null) mediaElement.removeChild(srcIdElement)
                } else {
                    if (srcIdElement == null) {
                        srcIdElement = document!!.createElement(SRC_ID_ELEMENT)
                        mediaElement.appendChild(srcIdElement)
                    }
                    srcIdElement!!.textContent = srcId
                }
            }
        /**
         * Returns the text content of the `type` child element of this `Media`'s
         * element, if it has such a child. Returns `null` otherwise.
         *
         * @return the text content of the `type` child element of this `Media`'s
         * element, if it has such a child. Returns `null` otherwise.
         */
        /**
         * Sets the `type` child element of this `Media`'s element.
         */
        var type: String?
            get() {
                val typeElement = XMLUtils.findChild(mediaElement, TYPE_ELEMENT)
                return typeElement?.textContent
            }
            set(type) {
                var typeElement = XMLUtils.findChild(mediaElement, TYPE_ELEMENT)
                if (type == null || type == "") {
                    if (typeElement != null) mediaElement.removeChild(typeElement)
                } else {
                    if (typeElement == null) {
                        typeElement = document!!.createElement(TYPE_ELEMENT)
                        mediaElement.appendChild(typeElement)
                    }
                    typeElement!!.textContent = type
                }
            }
        /**
         * Returns the text content of the `status` child element of this `Media`'s
         * element, if it has such a child. Returns `null` otherwise.
         *
         * @return the text content of the `status` child element of this `Media`'s
         * element, if it has such a child. Returns `null` otherwise.
         */
        /**
         * Sets the `status` child element of this `Media`'s element.
         *
         * status the value to be used for the text content of the `status` element.
         */
        var status: String?
            get() {
                val statusElement = XMLUtils.findChild(mediaElement, STATUS_ELEMENT)
                return statusElement?.textContent
            }
            set(status) {
                setStatus(mediaElement, status)
            }
    }

    /**
     * Endpoint status type.
     *
     * @author Sebastien Vincent
     */
    enum class EndpointStatusType
    /**
     * Creates a `EndPointType` instance with the specified name.
     *
     * @param type type name.
     */
    (
            /**
             * The name of this type.
             */
            private val type: String) {
        /**
         * Pending.
         */
        pending("pending"),

        /**
         * Dialing-out.
         */
        dialing_out("dialing-out"),

        /**
         * Dialing-in.
         */
        dialing_in("dialing-in"),

        /**
         * Alerting.
         */
        alerting("alerting"),

        /**
         * On-hold.
         */
        on_hold("on-hold"),

        /**
         * Connected.
         */
        connected("connected"),

        /**
         * Muted via focus.
         */
        muted_via_focus("mute-via-focus"),

        /**
         * Disconnecting.
         */
        disconnecting("disconnecting"),

        /**
         * Disconnected.
         */
        disconnected("disconnected");

        /**
         * Returns the type name.
         *
         * @return type name
         */
        override fun toString(): String {
            return type
        }

        companion object {
            /**
             * Returns a `EndPointType`.
             *
             * @param typeStr the `String` that we'd like to parse.
             * @return an EndPointType.
             * @throws IllegalArgumentException in case `typeStr` is not a valid `EndPointType`.
             */
            @Throws(IllegalArgumentException::class)
            fun parseString(typeStr: String): EndpointStatusType {
                for (value in values()) if (value.toString() == typeStr) return value
                throw IllegalArgumentException("$typeStr is not a valid reason")
            }
        }
    }

    companion object {
        /**
         * The namespace of the conference-info element.
         */
        const val NAMESPACE = "urn:ietf:params:xml:ns:conference-info"

        /**
         * The name of the "conference-info" element.
         */
        const val CONFERENCE_INFO_ELEMENT = "conference-info"

        /**
         * The name of the "conference-description" element.
         */
        const val CONFERENCE_DESCRIPTION_ELEMENT = "conference-description"

        /**
         * The name of the "conference-state" element.
         */
        const val CONFERENCE_STATE_ELEMENT = "conference-state"

        /**
         * The name of the "state" attribute.
         */
        const val STATE_ATTR_NAME = "state"

        /**
         * The name of the "entity" attribute.
         */
        const val ENTITY_ATTR_NAME = "entity"

        /**
         * The name of the "version" attribute.
         */
        const val VERSION_ATTR_NAME = "version"

        /**
         * The name of the "user" element.
         */
        const val USER_ELEMENT = "user"

        /**
         * The name of the "users" element.
         */
        const val USERS_ELEMENT = "users"

        /**
         * The name of the "endpoint" element.
         */
        const val ENDPOINT_ELEMENT = "endpoint"

        /**
         * The name of the "media" element.
         */
        const val MEDIA_ELEMENT = "media"

        /**
         * The name of the "id" attribute.
         */
        const val ID_ATTR_NAME = "id"

        /**
         * The name of the "status" element.
         */
        const val STATUS_ELEMENT = "status"

        /**
         * The name of the "src-id" element.
         */
        const val SRC_ID_ELEMENT = "src-id"

        /**
         * The name of the "type" element.
         */
        const val TYPE_ELEMENT = "type"

        /**
         * The name of the "user-count" element.
         */
        const val USER_COUNT_ELEMENT = "user-count"

        /**
         * The mane of the "display-text" element.
         */
        const val DISPLAY_TEXT_ELEMENT = "display-text"
    }
}