/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcontacts

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.GenericSourceContact
import org.atalk.hmos.aTalkApp

/**
 * Android source contact class.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidContact(contactSource: ContactSourceService,
        private val id: Long,
        private val lookUpKey: String,
        displayName: String?,
        private val thumbnailUri: String,
        private val photoUri: String,
        private val photoId: String,
        private val phone: String) : GenericSourceContact(contactSource, displayName!!, ArrayList()) {
    init {
        val contactAddress = phone
        setDisplayDetails(phone)
    }
    /*
         * String address = super.getContactAddress(); if(address == null) { Context ctx = aTalkApp.globalContext;
         *
         * Cursor result = ctx.getContentResolver()
         *  .query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, new
         * String[]{ContactsContract.CommonDataKinds.Email.DATA},
         * ContactsContract.CommonDataKinds.Email.CONTACT_ID+"=?", new String[]{String.valueOf(id)
         * },null);
         *
         * if(result.moveToNext()) { int adrIdx = result.getColumnIndex( ContactsContract .CommonDataKinds.Email.DATA);
         * address = result.getString(adrIdx); setContactAddress(address); } result.close(); }
         * return address;
         */

    /**
     * {@inheritDoc}
     */
    override var contactAddress: String?
        get() = super.contactAddress
        /*
              * String address = super.getContactAddress(); if(address == null) { Context ctx =
              * aTalkApp.globalContext;
              *
              * Cursor result = ctx.getContentResolver().query( ContactsContract.CommonDataKinds.Email
              * .CONTENT_URI, new
              * String[]{ContactsContract.CommonDataKinds.Email.DATA},
              * ContactsContract.CommonDataKinds.Email.CONTACT_ID+"=?", new String[]{String.valueOf(id)
              * },null);
              *
              * if(result.moveToNext()) { int adrIdx = result.getColumnIndex( ContactsContract
              * .CommonDataKinds.Email.DATA);
              * address = result.getString(adrIdx); setContactAddress(address); } result.close(); }
              * return address;
              */
        set(contactAddress) {
            super.contactAddress = contactAddress
        }
    // @Override
    // public String getDisplayDetails()
    // {
    // String details = super.getDisplayDetails();
    // if(details == null)
    // {
    // Context ctx = aTalkApp.globalContext;
    //
    // Cursor result = ctx.getContentResolver().query(
    // ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
    // new String[]{ContactsContract.CommonDataKinds.Phone.DATA},
    // ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY+"=?",
    // new String[]{String.valueOf(lookUpKey)},null);
    //
    // if(result.moveToNext())
    // {
    // int adrIdx = result.getColumnIndex(
    // ContactsContract.CommonDataKinds.Phone.DATA);
    // details = result.getString(adrIdx);
    // setDisplayDetails(details);
    // }
    // while (result.moveToNext())
    // {
    // int adrIdx = result.getColumnIndex(
    // ContactsContract.CommonDataKinds.Phone.DATA);
    // Timber.e("%s", getDisplayName()
    // +" has more phones: "+result.getString(adrIdx));
    // }
    // result.close();
    // }
    // return details;
    // }

    /*
    if (thumbnailUri != null)
    {
        Uri uri = Uri . parse thumbnailUri; System.out.println
        * uri; try {
        Bitmap
        * bitmap = MediaStore.Images.Media.getBitmap(ctx.getContentResolver(), uri); System
        * .err.println("BITMAP: "
        * +bitmap);
    } catch (IOException e) {
        throw new RuntimeException e; }
        *
        *
    }
    */

    /**
     * {@inheritDoc}
     */
    override var image: ByteArray? = null
        get() {
            var image = super.image
            if (image == null) {
                val ctx = aTalkApp.globalContext
                val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
                val photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
                val cursor = ctx.contentResolver.query(photoUri, arrayOf(ContactsContract.Contacts.Photo.PHOTO), null, null, null)
                        ?: return null
                try {
                    if (cursor.moveToFirst()) {
                        image = cursor.getBlob(0)
                        field = image
                    }
                } finally {
                    cursor.close()
                }
                /*
             * if (thumbnailUri != null) { Uri uri = Uri.parse(thumbnailUri); System.out.println
             * (uri); try { Bitmap
             * bitmap = MediaStore.Images.Media.getBitmap( ctx.getContentResolver(), uri); System
             * .err.println("BITMAP: "
             * + bitmap); } catch (IOException e) { throw new RuntimeException(e); }
             *
             * }
             */
            }
            return image!!
        }
        set(image) {
            super.image = image
        }
}