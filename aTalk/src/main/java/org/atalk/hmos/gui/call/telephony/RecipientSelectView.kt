package org.atalk.hmos.gui.call.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Patterns
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import com.tokenautocomplete.TokenCompleteTextView
import org.apache.james.mime4j.util.CharsetUtil
import org.atalk.hmos.R
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.call.telephony.AlternateRecipientAdapter.AlternateRecipientListener
import timber.log.Timber
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class RecipientSelectView : TokenCompleteTextView<RecipientSelectView.Recipient?>, LoaderManager.LoaderCallbacks<List<RecipientSelectView.Recipient>>, AlternateRecipientListener {
    private var adapter: RecipientAdapter? = null
    private var loaderManager: LoaderManager? = null
    private var alternatesPopup: ListPopupWindow? = null
    private var alternatesAdapter: AlternateRecipientAdapter? = null
    private var alternatesPopupRecipient: Recipient? = null
    private var listener: TokenListener<Recipient?>? = null

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initView(context)
    }

    private fun initView(context: Context) {
        alternatesPopup = ListPopupWindow(context)
        alternatesAdapter = AlternateRecipientAdapter(context, this)
        alternatesPopup!!.setAdapter(alternatesAdapter)

        // allow only single entry
        setTokenLimit(1)

        // if a token is completed, pick an entry based on best guess.
        // Note that we override performCompletion, so this doesn't actually do anything
        performBestGuess(true)
        adapter = RecipientAdapter(context)
        setAdapter(adapter)
        isLongClickable = true

        // cmeng - must init loaderManager in initView to take care of screen rotation
        loaderManager = LoaderManager.getInstance(aTalk.getFragment(aTalk.CL_FRAGMENT)!!)
    }

    override fun getViewForObject(recipient: Recipient?): View {
        val view = inflateLayout()
        val holder = RecipientTokenViewHolder(view)
        view.tag = holder
        bindObjectView(recipient, view)
        return view
    }

    @SuppressLint("InflateParams")
    private fun inflateLayout(): View {
        val layoutInflater = LayoutInflater.from(context)
        return layoutInflater.inflate(R.layout.recipient_token_item, null, false)
    }

    private fun bindObjectView(recipient: Recipient?, view: View) {
        val holder = view.tag as RecipientTokenViewHolder
        holder.vName.text = recipient!!.displayNameOrPhone
        holder.vPhone.text = recipient.phone
        RecipientAdapter.setContactPhotoOrPlaceholder(context, holder.vContactPhoto, recipient)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val text = text
        if (text != null && action == MotionEvent.ACTION_UP) {
            val offset = getOffsetForPosition(event.x, event.y)
            if (offset != -1) {
                val links = text.getSpans(offset, offset, RecipientTokenSpan::class.java)
                if (links.isNotEmpty()) {
                    showAlternates(links[0].token!!)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun defaultObject(completionText: String): Recipient? {
        val parsedAddresses = Address.parse(completionText)
        if (!CharsetUtil.isASCII(completionText)) {
            error = context.getString(R.string.recipient_error_non_ascii)
            return null
        }
        return if (parsedAddresses.isEmpty() || parsedAddresses[0]!!.getAddress() == null) {
            // aTalk telephony call can go with text string only i.e. not Address
            // setError(getContext().getString(R.string.recipient_error_parse_failed));
            null
        } else Recipient(parsedAddresses[0]!!)
    }

    val isEmpty: Boolean
        get() = objects.isEmpty()

    fun setLoaderManager(loaderManager: LoaderManager?) {
        this.loaderManager = loaderManager
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (loaderManager != null) {
            loaderManager!!.destroyLoader(LOADER_ID_ALTERNATES)
            loaderManager!!.destroyLoader(LOADER_ID_FILTERING)
            loaderManager = null
        }
    }

    override fun onFocusChanged(hasFocus: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(hasFocus, direction, previous)
        if (hasFocus) {
            displayKeyboard()
        }
    }

    /**
     * TokenCompleteTextView removes composing strings, and etc, but leaves internal composition
     * predictions partially constructed. Changing either/or the Selection or Candidate start/end
     * positions, forces the IMM to reset cleaner.
     */
    override fun replaceText(text: CharSequence) {
        super.replaceText(text)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.updateSelection(this, selectionStart, selectionEnd, -1, -1)
    }

    private fun displayKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun showDropDown() {
        val cursorIsValid = adapter != null
        if (!cursorIsValid) {
            return
        }
        super.showDropDown()
    }

    override fun performCompletion() {
        if (listSelection == ListView.INVALID_POSITION && enoughToFilter()) {
            val recipientText = defaultObject(currentCompletionText())
            if (recipientText != null) {
                replaceText(convertSelectionToString(recipientText))
            }
        } else {
            super.performCompletion()
        }
    }

    override fun performFiltering(text: CharSequence, keyCode: Int) {
        if (loaderManager == null) {
            return
        }
        val query = text.toString()
        if (TextUtils.isEmpty(query) || query.length < MINIMUM_LENGTH_FOR_FILTERING) {
            loaderManager!!.destroyLoader(LOADER_ID_FILTERING)
            return
        }
        val args = Bundle()
        args.putString(ARG_QUERY, query)
        loaderManager!!.restartLoader(LOADER_ID_FILTERING, args, this)
    }

    private fun redrawAllTokens() {
        val text = text ?: return
        val recipientSpans = text.getSpans(0, text.length, RecipientTokenSpan::class.java)
        for (recipientSpan in recipientSpans) {
            bindObjectView(recipientSpan.token, recipientSpan.view)
        }
        invalidate()
    }

    fun addRecipients(vararg recipients: Recipient?) {
        for (recipient in recipients) {
            addObjectSync(recipient)
        }
    }

    val addresses: Array<Address?>
        get() {
            val recipients = objects
            val address = arrayOfNulls<Address>(recipients.size)
            for (i in address.indices) {
                address[i] = recipients[i]!!.address
            }
            return address
        }

    private fun showAlternates(recipient: Recipient) {
        if (loaderManager == null) {
            return
        }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        alternatesPopupRecipient = recipient
        loaderManager!!.restartLoader(LOADER_ID_ALTERNATES, null, this@RecipientSelectView)
    }

    private fun postShowAlternatesPopup(data: List<Recipient?>) {
        // We delay this call so the soft keyboard is gone by the time the popup is layout
        Handler().post { showAlternatesPopup(data) }
    }

    private fun showAlternatesPopup(data: List<Recipient?>) {
        if (loaderManager == null) {
            return
        }

        // Copy anchor settings from the autocomplete dropdown
        val anchorView = rootView.findViewById<View>(dropDownAnchor)
        alternatesPopup!!.anchorView = anchorView
        alternatesPopup!!.width = dropDownWidth
        alternatesAdapter!!.setCurrentRecipient(alternatesPopupRecipient)
        alternatesAdapter!!.setAlternateRecipientInfo(data as MutableList<Recipient?>)

        // Clear the checked item.
        alternatesPopup!!.show()
        val listView = alternatesPopup!!.listView
        if (listView != null) listView.choiceMode = ListView.CHOICE_MODE_SINGLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        alternatesPopup!!.dismiss()
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<Recipient>> {
        when (id) {
            LOADER_ID_FILTERING -> {
                val query = if (args != null && args.containsKey(ARG_QUERY)) args.getString(ARG_QUERY) else ""
                adapter!!.setHighlight(query)
                return RecipientLoader(context, query)
            }
            LOADER_ID_ALTERNATES -> {
                val contactLookupUri = alternatesPopupRecipient!!.contactLookupUri
                return if (contactLookupUri != null) {
                    RecipientLoader(context, contactLookupUri, true)
                } else {
                    RecipientLoader(context, alternatesPopupRecipient!!.address)
                }
            }
        }
        throw IllegalStateException("Unknown Loader ID: $id")
    }

    override fun onLoadFinished(loader: Loader<List<Recipient>>, data: List<Recipient>) {
        if (loaderManager == null) {
            return
        }
        when (loader.id) {
            LOADER_ID_FILTERING -> {
                adapter!!.setRecipients(data)
            }
            LOADER_ID_ALTERNATES -> {
                postShowAlternatesPopup(data)
                loaderManager!!.destroyLoader(LOADER_ID_ALTERNATES)
            }
        }
    }

    override fun onLoaderReset(loader: Loader<List<Recipient>>) {
        if (loader.id == LOADER_ID_FILTERING) {
            adapter!!.setHighlight(null)
            adapter!!.setRecipients(null)
        }
    }

    fun tryPerformCompletion(): Boolean {
        if (!hasUncompletedText()) {
            return false
        }
        val previousNumRecipients = tokenCount
        performCompletion()
        val numRecipients = tokenCount
        return previousNumRecipients != numRecipients
    }

    private val tokenCount: Int
        get() = objects.size

    private fun hasUncompletedText(): Boolean {
        val currentCompletionText = currentCompletionText()
        return !TextUtils.isEmpty(currentCompletionText) && !isPlaceholderText(currentCompletionText)
    }

    override fun onRecipientRemove(currentRecipient: Recipient?) {
        alternatesPopup!!.dismiss()
        removeObjectSync(currentRecipient)
    }

    override fun onRecipientChange(currentRecipient: Recipient?, alternateRecipient: Recipient) {
        alternatesPopup!!.dismiss()
        val currentRecipients = objects
        val indexOfRecipient = currentRecipients.indexOf(currentRecipient)
        if (indexOfRecipient == -1) {
            Timber.e("Tried to refresh invalid view token!")
            return
        }
        val cRecipient = currentRecipients[indexOfRecipient]
        cRecipient!!.address = alternateRecipient.address
        cRecipient.addressLabel = alternateRecipient.addressLabel
        val recipientTokenView = getTokenViewForRecipient(cRecipient)
        if (recipientTokenView == null) {
            Timber.e("Tried to refresh invalid view token!")
            return
        }
        bindObjectView(cRecipient, recipientTokenView)
        if (listener != null) {
            listener!!.onTokenChanged(cRecipient)
        }
        invalidate()
    }

    /**
     * This method builds the span given a address object. We override it with identical
     * functionality, but using the custom RecipientTokenSpan class which allows us to
     * retrieve the view for redrawing at a later point.
     */
    override fun buildSpanForObject(obj: Recipient?): TokenImageSpan? {
        if (obj == null) {
            return null
        }
        val tokenView = getViewForObject(obj)
        return RecipientTokenSpan(tokenView, obj)
    }

    /**
     * Find the token view tied to a given address. This method relies on spans to
     * be of the RecipientTokenSpan class, as created by the buildSpanForObject method.
     */
    private fun getTokenViewForRecipient(currentRecipient: Recipient?): View? {
        val text = text ?: return null
        val recipientSpans = text.getSpans(0, text.length, RecipientTokenSpan::class.java)
        for (recipientSpan in recipientSpans) {
            if (recipientSpan.token == currentRecipient) {
                return recipientSpan.view
            }
        }
        return null
    }

    /**
     * We use a specialized version of TokenCompleteTextView.TokenListener as well,
     * adding a callback for onTokenChanged.
     */
    fun setTokenListener(listener: TokenListener<Recipient?>?) {
        super.setTokenListener(listener)
        this.listener = listener
    }

    interface TokenListener<T> : TokenCompleteTextView.TokenListener<T> {
        fun onTokenChanged(token: T)
    }

    private inner class RecipientTokenSpan(view: View?, token: Recipient?) : TokenImageSpan(view, token) {
        lateinit var view: View

        init {
            this.view = view
        }
    }

    private class RecipientTokenViewHolder(view: View) {
        val vName: TextView
        val vPhone: TextView
        val vContactPhoto: ImageView

        init {
            vName = view.findViewById(android.R.id.text1)
            vPhone = view.findViewById(android.R.id.text2)
            vContactPhoto = view.findViewById(R.id.contact_photo)
        }
    }

    class Recipient : Serializable {
        // null means the address is not associated with a contact
        val contactId: Long?
        private val contactLookupKey: String?
        var address: Address
        var addressLabel: String? = null

        @Transient
        // null if the contact has no photo. transient because we serialize this manually, see below.
        var photoThumbnailUri: Uri? = null

        constructor(address: Address) {
            this.address = address
            contactId = null
            contactLookupKey = null
        }

        constructor(name: String?, phone: String?, addressLabel: String?, contactId: Long, lookupKey: String?) {
            address = Address(phone, name)
            this.contactId = contactId
            this.addressLabel = addressLabel
            contactLookupKey = lookupKey
        }

        val displayNameOrPhone: String?
            get() {
                val displayName = displayName
                return displayName ?: address.getAddress()
            }
        val phone: String?
            get() = address.getAddress()

        fun getDisplayNameOrUnknown(context: Context): String {
            val displayName = displayName
            return displayName ?: context.getString(R.string.unknown_recipient)
        }

        fun getNameOrUnknown(context: Context): String {
            val name = address.getPerson()
            return name ?: context.getString(R.string.unknown_recipient)
        }

        private val displayName: String?
            get() {
                if (TextUtils.isEmpty(address.getPerson())) {
                    return null
                }
                var displayName = address.getPerson()
                if (addressLabel != null) {
                    displayName += " ($addressLabel)"
                }
                return displayName
            }
        val contactLookupUri: Uri?
            get() = if (contactId == null) {
                null
            } else ContactsContract.Contacts.getLookupUri(contactId, contactLookupKey)

        override fun equals(other: Any?): Boolean {
            // Equality is entirely up to the address
            return other is Recipient && address == other.address
        }

        @Throws(IOException::class)
        private fun writeObject(oos: ObjectOutputStream) {
            oos.defaultWriteObject()

            // custom serialization, Android's Uri class is not serializable
            if (photoThumbnailUri != null) {
                oos.writeInt(1)
                oos.writeUTF(photoThumbnailUri.toString())
            } else {
                oos.writeInt(0)
            }
        }

        @Throws(ClassNotFoundException::class, IOException::class)
        private fun readObject(ois: ObjectInputStream) {
            ois.defaultReadObject()
            // custom deserialization, Android's Uri class is not serializable
            if (ois.readInt() != 0) {
                val uriString = ois.readUTF()
                photoThumbnailUri = Uri.parse(uriString)
            }
        }

        companion object {
            fun isValidPhoneNum(target: CharSequence?): Boolean {
                return (target != null && target.length >= 4
                        && Patterns.PHONE.matcher(target).matches())
            }
        }
    }

    companion object {
        private const val MINIMUM_LENGTH_FOR_FILTERING = 2
        private const val ARG_QUERY = "query"
        private const val LOADER_ID_FILTERING = 0
        private const val LOADER_ID_ALTERNATES = 1
        private fun isPlaceholderText(currentCompletionText: String): Boolean {
            // TODO string matching here is sort of a hack, but it's somewhat reliable and the info isn't easily available
            return currentCompletionText.startsWith("+") && currentCompletionText.substring(1).matches("[0-9]+".toRegex())
        }
    }

}