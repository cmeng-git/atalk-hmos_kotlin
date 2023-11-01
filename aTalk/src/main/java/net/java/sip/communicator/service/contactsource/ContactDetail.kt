/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.service.contactsource

import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.apache.commons.lang3.StringUtils
import java.util.*

/**
 * The `ContactDetail` is a detail of a `SourceContact`
 * corresponding to a specific address (phone number, email, identifier, etc.),
 * which defines the different possible types of communication and the preferred
 * `ProtocolProviderService`s to go through.
 *
 *
 * Example: A `ContactDetail` could define two types of communication,
 * by declaring two supported operation sets
 * `OperationSetBasicInstantMessaging` to indicate the support of instant
 * messages and `OperationSetBasicTelephony` to indicate the support of
 * telephony. It may then specify a certain `ProtocolProviderService` to
 * go through only for instant messages. This would mean that for sending an
 * instant message to this `ContactDetail` one should obtain an instance
 * of the `OperationSetBasicInstantMessaging` from the specific
 * `ProtocolProviderService` and send a message through it. However when
 * no provider is specified for telephony operations, then one should try to
 * obtain all currently available telephony providers and let the user make
 * their choice.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
open class ContactDetail @JvmOverloads constructor(
        /**
         * The address of this contact detail. This should be the address through which the contact
         * could be reached by one of the supported `OperationSet`s (e.g. by IM, call).
         */
        var detail: String?,
        detailDisplayName: String? = null,
        category: Category? = null,
        subCategories: Array<SubCategory?>? = null) {

    /**
     * Creates enum within the specified value.
     */
    /**
     * Defines all possible categories for a `ContactDetail`.
     */
    enum class Category
    (
            /**
             * Current enum value.
             */
            private val value: String) {
        /**
         * The standard/well-known category of a `ContactDetail`
         * representing personal details, like name, last name, nickname.
         */
        Personal("Personal"),

        /**
         * The standard/well-known category of a `ContactDetail`
         * representing personal details, like web address.
         */
        Web("Web"),

        /**
         * The standard/well-known category of a `ContactDetail`
         * representing organization details, like organization name and job title.
         */
        Organization("Organization"),

        /**
         * The standard/well-known category of a `ContactDetail` representing an e-mail
         * address.
         */
        Email("Email"),

        /**
         * The standard/well-known category of a `ContactDetail` representing a contact
         * address for instant messaging.
         */
        InstantMessaging("InstantMessaging"),

        /**
         * The standard/well-known category of a `ContactDetail` representing a phone number.
         */
        Phone("Phone"),

        /**
         * The standard/well-known category of a `ContactDetail` representing a postal
         * address.
         */
        Address("Address");

        /**
         * Gets the value.
         *
         * @return the value
         */
        fun value(): String {
            return value
        }

        companion object {
            /**
             * Creates enum from its value.
             *
             * @param value the enum's value.
             * @return created enum.
             */
            fun fromString(value: String?): Category? {
                if (value != null) {
                    for (category in values()) {
                        if (value.equals(category.value(), ignoreCase = true)) {
                            return category
                        }
                    }
                    return null
                }
                return null
            }
        }
    }

    /**
     * Defines all possible sub-categories for a `ContactDetail`.
     */
    /**
     * Creates enum within the specified value.
     *
     * @param value the value to set.
     */
    enum class SubCategory
    (
            /**
             * Current enum value.
             */
            private val value: String) {
        /**
         * The standard/well-known label of a `ContactDetail`
         * representing a name. It could be an organization name or a personal name.
         */
        Name("Name"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a last name.
         */
        LastName("LastName"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a nickname.
         */
        Nickname("Nickname"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a postal code.
         */
        HomePage("HomePage"),

        /**
         * The standard/well-known label of a `ContactDetail` representing an address of a
         * contact at their home.
         */
        Home("Home"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a mobile
         * contact address (e.g. a cell phone number).
         */
        Mobile("Mobile"),

        /**
         * The standard/well-known label of a `ContactDetail` representing an address of a
         * contact at their work.
         */
        Work("Work"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a fax number.
         */
        Fax("Fax"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a different
         * number.
         */
        Other("Other"),

        /**
         * The standard/well-known label of a `ContactDetail` representing an IM network
         * (like for example jabber).
         */
        AIM("AIM"), ICQ("ICQ"), Jabber("XMPP"), Skype("Skype"), Yahoo("Yahoo"), GoogleTalk("GoogleTalk"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a country name.
         */
        Country("Country"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a state name.
         */
        State("State"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a city name.
         */
        City("City"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a street address.
         */
        Street("Street"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a postal code.
         */
        PostalCode("PostalCode"),

        /**
         * The standard/well-known label of a `ContactDetail` representing a job title.
         */
        JobTitle("JobTitle");

        /**
         * Gets the value.
         *
         * @return the value
         */
        fun value(): String {
            return value
        }

        companion object {
            /**
             * Creates enum from its value.
             *
             * @param value the enum's value.
             * @return created enum.
             */
            fun fromString(value: String?): SubCategory? {
                if (value != null) {
                    for (subCategory in values()) {
                        if (value.equals(subCategory.value(), ignoreCase = true)) {
                            return subCategory
                        }
                    }
                    return null
                }
                return null
            }
        }
    }

    /**
     * Gets the category, if any, of this `ContactQuery`.
     *
     * @return the category of this `ContactQuery` if it has any;
     * otherwise, `null`
     */
    /**
     * The category of this `ContactQuery`.
     */
    val category: Category?
    /**
     * Returns the contact address corresponding to this detail.
     *
     * @return the contact address corresponding to this detail
     */
    /**
     * Returns the display name of this detail. By default returns the detail value.
     *
     * @return the display name of this detail
     */
    /**
     * The display name of this detail.
     */
    var displayName: String? = null

    /**
     * The set of labels of this `ContactDetail`. The labels may be
     * arbitrary and may include any of the standard/well-known labels defined
     * by the `LABEL_XXX` constants of the `ContactDetail` class.
     */
    private val subCategories = LinkedList<SubCategory>()

    /**
     * A mapping of `OperationSet` classes and preferred protocol providers for them.
     */
    private var preferredProviders: Map<Class<out OperationSet?>, ProtocolProviderService>? = null

    /**
     * A mapping of `OperationSet` classes and preferred protocol name for them.
     */
    private var preferredProtocols: Map<Class<out OperationSet?>, String>? = null

    /**
     * A list of all supported `OperationSet` classes.
     */
    private var supportedOpSets: MutableList<Class<out OperationSet?>>? = null

    /**
     * Initializes a new `ContactDetail` instance which is to represent a specific contact
     * address and which is to be optionally labeled with a specific set of labels.
     *
     * @param contactDetailValue the contact detail value to be represented by the new `ContactDetail` instance
     * @param category
     */
    constructor(contactDetailValue: String?,
            category: Category?) : this(contactDetailValue, null, category, null) {
    }

    /**
     * Initializes a new `ContactDetail` instance which is to represent a
     * specific contact address and which is to be optionally labeled with a
     * specific set of labels.
     *
     * @param contactDetailValue the contact detail value to be represented by the new `ContactDetail` instance
     * @param category
     * @param subCategories the set of sub categories with which the new `ContactDetail` instance is to be
     * labeled.
     */
    constructor(contactDetailValue: String?,
            category: Category?,
            subCategories: Array<SubCategory?>?) : this(contactDetailValue, null, category, subCategories) {
    }
    /**
     * Initializes a new `ContactDetail` instance which is to represent a specific contact
     * address and which is to be optionally labeled with a specific set of labels.
     *
     * @param detail the contact detail value to be represented by the new `ContactDetail` instance
     * @param detailDisplayName the display name of this detail
     * @param category
     * @param subCategories the set of sub categories with which the new `ContactDetail` instance is to be
     * labeled.
     */
    /**
     * Creates a `ContactDetail` by specifying the contact address, corresponding to this
     * detail.
     *
     * @param detail the contact detail value corresponding to this detail
     */
    /**
     * Creates a `ContactDetail` by specifying the contact address,
     * corresponding to this detail.
     *
     * @param detail the contact detail value corresponding to this detail
     * @param detailDisplayName the display name of this detail
     */
    /**
     * Initializes a new `ContactDetail` instance which is to represent a specific contact
     * address and which is to be optionally labeled with a specific set of labels.
     *
     * contactDetailValue the contact detail value to be represented by the new `ContactDetail` instance
     * detailDisplayName the display name of this detail
     * category
     */
    init {
        // the value of the detail
        if (StringUtils.isNotEmpty(detailDisplayName)) {
            displayName = detailDisplayName
        } else if (category == Category.Phone) {
            displayName = ContactSourceActivator.phoneNumberI18nService!!.formatForDisplay(detail)
        } else {
            displayName = detail
        }

        // category & labels
        this.category = category
        if (subCategories != null) {
            for (subCategory in subCategories) {
                if (subCategory != null
                        && !this.subCategories.contains(subCategory)) {
                    this.subCategories.add(subCategory)
                }
            }
        }
    }

    /**
     * Sets a mapping of preferred `ProtocolProviderServices` for a specific
     * `OperationSet`.
     *
     * @param preferredProviders a mapping of preferred `ProtocolProviderService`s for specific
     * `OperationSet` classes
     */
    fun setPreferredProviders(
            preferredProviders: Map<Class<out OperationSet?>, ProtocolProviderService>?) {
        this.preferredProviders = preferredProviders
    }

    /**
     * Sets a mapping of a preferred `preferredProtocol` for a specific
     * `OperationSet`. The preferred protocols are meant to be set by
     * contact source implementations that don't have a specific protocol
     * providers to suggest, but are able to propose just the name of the
     * protocol to be used for a specific operation. If both - preferred
     * provider and preferred protocol are set, then the preferred protocol
     * provider should be prioritized.
     *
     * @param preferredProtocols a mapping of preferred
     * `ProtocolProviderService`s for specific `OperationSet` classes
     */
    fun setPreferredProtocols(
            preferredProtocols: Map<Class<out OperationSet?>, String>) {
        this.preferredProtocols = preferredProtocols

        // protocol added so an opset is supported, add it if missing
        for (opsetClass in preferredProtocols.keys) {
            if (supportedOpSets == null || !supportedOpSets!!.contains(opsetClass)) addSupportedOpSet(opsetClass)
        }
    }

    /**
     * Creates a `ContactDetail` by specifying the corresponding contact
     * address and a list of all `supportedOpSets`, indicating what are
     * the supporting actions with this contact detail (e.g. sending a message,
     * making a call, etc.)
     *
     * @param supportedOpSets a list of all `supportedOpSets`, indicating what are the supporting actions
     * with this contact detail (e.g. sending a message, making a call, etc.)
     */
    fun setSupportedOpSets(
            supportedOpSets: MutableList<Class<out OperationSet?>>?) {
        this.supportedOpSets = supportedOpSets
    }

    /**
     * Adds a supported OpSet to the list of supported OpSets.
     *
     * @param supportedOpSet the OpSet to support.
     */
    fun addSupportedOpSet(supportedOpSet: Class<out OperationSet?>) {
        if (supportedOpSets == null) {
            supportedOpSets = ArrayList(2)
        }
        supportedOpSets!!.add(supportedOpSet)
    }

    /**
     * Returns the preferred `ProtocolProviderService` when using the
     * given `opSetClass`.
     *
     * @param opSetClass the `OperationSet` class corresponding to a
     * certain action (e.g. sending an instant message, making a call, etc.).
     * @return the preferred `ProtocolProviderService` corresponding to
     * the given `opSetClass`
     */
    fun getPreferredProtocolProvider(
            opSetClass: Class<out OperationSet?>): ProtocolProviderService? {
        return if (preferredProviders != null && preferredProviders!!.isNotEmpty()) preferredProviders!![opSetClass] else null
    }

    /**
     * Returns the name of the preferred protocol for the operation given by
     * the `opSetClass`. The preferred protocols are meant to be set by
     * contact source implementations that don't have a specific protocol
     * providers to suggest, but are able to propose just the name of the
     * protocol to be used for a specific operation. If both - preferred
     * provider and preferred protocol are set, then the preferred protocol
     * provider should be prioritized.
     *
     * @param opSetClass the `OperationSet` class corresponding to a
     * certain action (e.g. sending an instant message, making a call, etc.).
     * @return the name of the preferred protocol for the operation given by
     * the `opSetClass`
     */
    fun getPreferredProtocol(opSetClass: Class<out OperationSet?>): String? {
        return if (preferredProtocols != null && preferredProtocols!!.isNotEmpty()) preferredProtocols!![opSetClass] else null
    }

    /**
     * Returns a list of all supported `OperationSet` classes, which
     * would indicate what are the supported actions by this contact
     * (e.g. write a message, make a call, etc.)
     *
     * @return a list of all supported `OperationSet` classes
     */
    val supportedOperationSets: List<Class<out OperationSet?>>?
        get() = supportedOpSets

    /**
     * Determines whether the set of labels of this `ContactDetail`
     * contains a specific label. The labels may be arbitrary and may include
     * any of the standard/well-known labels defined by the `LABEL_XXX`
     * constants of the `ContactDetail` class.
     *
     * @param subCategory the subCategory to be determined whether
     * it is contained in this `ContactDetail`
     * @return `true` if the specified `label` is contained in the
     * set of labels of this `ContactDetail`
     */
    fun containsSubCategory(subCategory: SubCategory): Boolean {
        return subCategories.contains(subCategory)
    }

    /**
     * Gets the set of labels of this `ContactDetail`. The labels may be
     * arbitrary and may include any of the standard/well-known labels defined
     * by the `LABEL_XXX` constants of the `ContactDetail` class.
     *
     * @return the set of labels of this `ContactDetail`. If this
     * `ContactDetail` has no labels, the returned `Collection` is
     * empty.
     */
    fun getSubCategories(): Collection<SubCategory> {
        return Collections.unmodifiableCollection(subCategories)
    }
}