package org.atalk.hmos.gui.call.telephony

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.loader.content.AsyncTaskLoader
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp

class RecipientLoader : AsyncTaskLoader<List<RecipientSelectView.Recipient>> {
    private var query: String?
    private var addresses: Array<Address>?
    private var contactUri: Uri?
    private var lookupKeyUri: Uri?
    private var contentResolver: ContentResolver
    private var cachedRecipients: List<RecipientSelectView.Recipient>? = null
    private var observerContact: ForceLoadContentObserver? = null
    private val observerKey: ForceLoadContentObserver? = null

    constructor(context: Context, query: String?) : super(context) {
        this.query = query
        lookupKeyUri = null
        addresses = null
        contactUri = null
        contentResolver = context.contentResolver
    }

    constructor(context: Context, vararg addresses: Address) : super(context) {
        query = null
        this.addresses = addresses as Array<Address>
        contactUri = null
        lookupKeyUri = null
        contentResolver = context.contentResolver
    }

    constructor(context: Context, contactUri: Uri?, isLookupKey: Boolean) : super(context) {
        query = null
        addresses = null
        this.contactUri = if (isLookupKey) null else contactUri
        lookupKeyUri = if (isLookupKey) contactUri else null
        contentResolver = context.contentResolver
    }

    override fun loadInBackground(): List<RecipientSelectView.Recipient> {
        val recipients = ArrayList<RecipientSelectView.Recipient>()
        val recipientMap = HashMap<String?, RecipientSelectView.Recipient>()
        when {
            addresses != null -> {
                fillContactDataFromAddresses(addresses, recipients, recipientMap)
            }
            contactUri != null -> {
                fillContactDataFromPhoneContentUri(contactUri!!, recipients, recipientMap)
            }
            query != null -> {
                fillContactDataFromQuery(query!!, recipients, recipientMap)
            }
            lookupKeyUri != null -> {
                fillContactDataFromLookupKey(lookupKeyUri!!, recipients, recipientMap)
            }
            else -> {
                throw IllegalStateException("loader must be initialized with query or list of addresses!")
            }
        }
        return if (recipients.isEmpty()) {
            recipients
        }
        else recipients
    }

    private fun fillContactDataFromAddresses(addresses: Array<Address>?, recipients: MutableList<RecipientSelectView.Recipient>,
            recipientMap: MutableMap<String?, RecipientSelectView.Recipient>) {
        for (address in addresses!!) {
            // TODO actually query contacts - not sure if this is possible in a single query tho :(
            val recipient = RecipientSelectView.Recipient(address)
            recipients.add(recipient)
            recipientMap[address.getAddress()] = recipient
        }
    }

    private fun fillContactDataFromPhoneContentUri(contactUri: Uri, recipients: MutableList<RecipientSelectView.Recipient>,
            recipientMap: MutableMap<String?, RecipientSelectView.Recipient>) {
        val cursor = contentResolver.query(contactUri, PROJECTION, null, null, null)
                ?: return
        fillContactDataFromCursor(cursor, recipients, recipientMap)
    }

    private fun fillContactDataFromLookupKey(lookupKeyUri: Uri, recipients: MutableList<RecipientSelectView.Recipient>,
            recipientMap: MutableMap<String?, RecipientSelectView.Recipient>) {
        // We could use the contact id from the URI directly, but getting it from the lookup key is safer
        val contactContentUri = ContactsContract.Contacts.lookupContact(contentResolver, lookupKeyUri)
                ?: return
        val contactIdStr = getContactIdFromContactUri(contactContentUri)
        val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                PROJECTION, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(contactIdStr), null)
                ?: return
        fillContactDataFromCursor(cursor, recipients, recipientMap)
    }

    private fun getNicknameCursor(nickname_: String): Cursor? {
        var nickname = nickname_
        nickname = "%$nickname%"
        val queryUriForNickname = ContactsContract.Data.CONTENT_URI
        try {
            return contentResolver.query(queryUriForNickname, PROJECTION_NICKNAME,
                    ContactsContract.CommonDataKinds.Nickname.NAME + " LIKE ? AND " + ContactsContract.Contacts.Data.MIMETYPE + " = ?", arrayOf(nickname, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE),
                    null)
        } catch (e: Exception) {
            aTalkApp.showToastMessage("Contact Access Exception:\n${e.message}")
        }
        return null
    }

    private fun fillContactDataFromQuery(query: String, recipients: MutableList<RecipientSelectView.Recipient>,
            recipientMap: MutableMap<String?, RecipientSelectView.Recipient>) {
        var foundValidCursor = false
        foundValidCursor = foundValidCursor or fillContactDataFromNickname(query, recipients, recipientMap)
        foundValidCursor = foundValidCursor or fillContactDataFromNameAndPhone(query, recipients, recipientMap)
        if (foundValidCursor) {
            registerContentObserver()
        }
    }

    private fun registerContentObserver() {
        if (observerContact != null) {
            observerContact = ForceLoadContentObserver()
            contentResolver.registerContentObserver(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, false, observerContact!!)
        }
    }

    private fun fillContactDataFromNickname(nickname: String, recipients: MutableList<RecipientSelectView.Recipient>,
            recipientMap: MutableMap<String?, RecipientSelectView.Recipient>): Boolean {
        var hasContact = false
        val queryUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val nCursor = getNicknameCursor(nickname) ?: return hasContact
        nCursor.use { nicknameCursor ->
            while (nicknameCursor.moveToNext()) {
                val id = nicknameCursor.getString(INDEX_CONTACT_ID_FOR_NICKNAME)
                val selection = ContactsContract.Data.CONTACT_ID + " = ?"
                val cursor = contentResolver
                        .query(queryUri, PROJECTION, selection, arrayOf(id), SORT_ORDER)
                val contactNickname = nicknameCursor.getString(INDEX_NICKNAME)
                fillContactDataFromCursor(cursor, recipients, recipientMap, contactNickname)
                hasContact = true
            }
        }
        return hasContact
    }

    private fun fillContactDataFromNameAndPhone(query_: String, recipients: MutableList<RecipientSelectView.Recipient>,
            recipientMap: MutableMap<String?, RecipientSelectView.Recipient>): Boolean {
        var query = query_
        query = "%$query%"
        val queryUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val selection = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? " +
                " OR (" + ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ? AND " + ContactsContract.Contacts.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "')"
        val selectionArgs = arrayOf(query, query)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(queryUri, PROJECTION, selection, selectionArgs, SORT_ORDER)
        } catch (e: SecurityException) {
            aTalkApp.showToastMessage(R.string.contacts_permission_denied_feedback)
        }
        if (cursor == null) {
            return false
        }
        fillContactDataFromCursor(cursor, recipients, recipientMap)
        return true
    }

    private fun fillContactDataFromCursor(cursor: Cursor?, recipients: MutableList<RecipientSelectView.Recipient>,
            recipientMap: MutableMap<String?, RecipientSelectView.Recipient>, prefilledName: String? = null) {
        while (cursor!!.moveToNext()) {
            val name = prefilledName ?: cursor.getString(INDEX_NAME)
            val phone = cursor.getString(INDEX_PHONE)
            val contactId = cursor.getLong(INDEX_CONTACT_ID)
            val lookupKey = cursor.getString(INDEX_LOOKUP_KEY)

            // phone is invalid or already exists? just skip and use the first default
            if (!RecipientSelectView.Recipient.isValidPhoneNum(phone) || recipientMap.containsKey(phone)) {
                continue
            }
            val phoneType = cursor.getInt(INDEX_PHONE_TYPE)
            var phoneLabel: String? = null
            when (phoneType) {
                ContactsContract.CommonDataKinds.Phone.TYPE_HOME, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK, ContactsContract.CommonDataKinds.Phone.TYPE_OTHER, ContactsContract.CommonDataKinds.Phone.TYPE_ISDN, ContactsContract.CommonDataKinds.Phone.TYPE_MAIN, ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE, ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT -> {
                    phoneLabel = context.getString(ContactsContract.CommonDataKinds.Phone.getTypeLabelResource(phoneType))
                }
                ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> {
                    phoneLabel = cursor.getString(INDEX_PHONE_CUSTOM_LABEL)
                }
            }
            val recipient = RecipientSelectView.Recipient(name, phone, phoneLabel, contactId, lookupKey)
            recipient.photoThumbnailUri = if (cursor.isNull(INDEX_PHOTO_URI)) null else Uri.parse(cursor.getString(INDEX_PHOTO_URI))
            recipientMap[phone] = recipient
            recipients.add(recipient)
        }
        cursor.close()
    }

    override fun deliverResult(data: List<RecipientSelectView.Recipient>?) {
        cachedRecipients = data
        if (isStarted) {
            super.deliverResult(data)
        }
    }

    override fun onStartLoading() {
        if (cachedRecipients != null) {
            super.deliverResult(cachedRecipients)
            return
        }
        if (takeContentChanged() || cachedRecipients == null) {
            forceLoad()
        }
    }

    override fun onAbandon() {
        super.onAbandon()
        if (observerKey != null) {
            contentResolver.unregisterContentObserver(observerKey)
        }
        if (observerContact != null) {
            contentResolver.unregisterContentObserver(observerContact!!)
        }
    }

    companion object {
        /*
     * Indexes of the fields in the projection. This must match the order in {@link #PROJECTION}.
     */
        private const val INDEX_NAME = 1
        private const val INDEX_LOOKUP_KEY = 2
        private const val INDEX_PHONE = 3
        private const val INDEX_PHONE_TYPE = 4
        private const val INDEX_PHONE_CUSTOM_LABEL = 5
        private const val INDEX_CONTACT_ID = 6
        private const val INDEX_PHOTO_URI = 7
        private val PROJECTION = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DATA,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
        )
        private const val SORT_ORDER = "" +
                ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED + " DESC, " + ContactsContract.Contacts.SORT_KEY_PRIMARY
        private val PROJECTION_NICKNAME = arrayOf(
                ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Nickname.NAME
        )
        private const val INDEX_CONTACT_ID_FOR_NICKNAME = 0
        private const val INDEX_NICKNAME = 1
        private fun getContactIdFromContactUri(contactUri: Uri): String? {
            return contactUri.lastPathSegment
        }
    }
}