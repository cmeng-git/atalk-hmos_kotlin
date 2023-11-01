/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcontacts

import android.database.Cursor
import android.provider.ContactsContract
import net.java.sip.communicator.service.contactsource.AbstractContactQuery
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.SourceContact
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp

/**
 * Android contact query.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidContactQuery
/**
 * Creates new instance of `AndroidContactQuery`.
 *
 * @param contactSource parent Android contact source.
 * @param queryString query string.
 */
(contactSource: AndroidContactSource,
    /**
     * Query string
     */
    override val queryString: String) : AbstractContactQuery<AndroidContactSource?>(contactSource) {

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */

    /**
     * Results list
     */
    private val results = ArrayList<SourceContact>()

    /**
     * The thread that runs the query
     */
    private var queryThread: Thread? = null

    /**
     * Flag used to cancel the query thread
     */
    private var cancel = false
    // TODO: implement cancel, on API >= 16
    // private CancellationSignal cancelSignal = new CancellationSignal();
    /**
     * {@inheritDoc}
     */
    override fun start() {
        if (queryThread != null) return
        queryThread = Thread { doQuery() }
        queryThread!!.start()
    }

    /**
     * Executes the query.
     */
    private fun doQuery() {
        val contentResolver = aTalkApp.globalContext.contentResolver
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(CONTACTS_URI, PROJECTION, SELECTION, arrayOf(queryString), null)
            if (cancel) return

            // Get projection column ids
            val ID = cursor!!.getColumnIndex(ContactsContract.Contacts._ID)
            val LOOP_UP = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            val DISPLAY_NAME = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            // int HAS_PHONE = cursor.getColumnIndex(
            // ContactsContract.Contacts.HAS_PHONE_NUMBER);
            val THUMBNAIL_URI = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val PHOTO_URI = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val PHOTO_ID = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_ID)

            // Create results
            while (cursor.moveToNext()) {
                if (cancel) break
                val id = cursor.getLong(ID)
                val lookUp = cursor.getString(LOOP_UP)
                val displayName = cursor.getString(DISPLAY_NAME)
                val thumbnail = cursor.getString(THUMBNAIL_URI)
                val photoUri = cursor.getString(PHOTO_URI)
                val photoId = cursor.getString(PHOTO_ID)

                // Loop on all phones
                var result: Cursor? = null
                try {
                    result = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DATA),
                            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY + "=?", arrayOf(lookUp.toString()), null)
                    if (result != null) {
                        while (result.moveToNext() && !cancel) {
                            val adrIdx = result.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA)
                            val phone = result.getString(adrIdx)
                            results.add(AndroidContact(contactSource, id, lookUp, displayName,
                                    thumbnail, photoUri, photoId, phone))
                        }
                    }
                } finally {
                    result?.close()
                }
            }
            if (!cancel) {
                status = ContactQuery.QUERY_COMPLETED
            }
        } catch (e: SecurityException) {
            aTalkApp.showToastMessage("${aTalkApp.getResString(R.string.contacts_permission_denied_feedback)}\n${e.message}")
        } finally {
            cursor?.close()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun cancel() {
        cancel = true
        try {
            queryThread!!.join()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
        super.cancel()
    }

    /**
     * Returns the list of `SourceContact`s returned by this query.
     *
     * @return the list of `SourceContact`s returned by this query
     */
    override val queryResults: MutableCollection<SourceContact>
        get() = results

    companion object {
        /**
         * Selection query
         */
        private const val SELECTION = (ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? AND "
                + ContactsContract.Contacts.HAS_PHONE_NUMBER + " > 0")

        /**
         * List of projection columns that will be returned
         */
        private val PROJECTION = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_ID)

        /**
         * The uri that will be user for queries.
         */
        private val CONTACTS_URI = ContactsContract.Contacts.CONTENT_URI
    }
}