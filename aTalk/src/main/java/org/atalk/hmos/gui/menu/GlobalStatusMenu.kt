package org.atalk.hmos.gui.menu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusListener
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.account.StatusListAdapter
import org.atalk.hmos.gui.widgets.ActionMenuItem
import org.atalk.service.osgi.OSGiActivity
import org.osgi.framework.Bundle
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.beans.PropertyChangeEvent

class GlobalStatusMenu(private val mActivity: FragmentActivity) : OSGiActivity(), PopupWindow.OnDismissListener, ServiceListener, ProviderPresenceStatusListener {
    private val mInflater: LayoutInflater
    private val mWindow = PopupWindow(mActivity)
    private val mWindowManager: WindowManager
    private var mRootView: View? = null
    private var mBackground: Drawable? = null
    private var mDidAction = false
    private var mAnimStyle: Int
    private var mInsertPos = 0
    private var mChildPos: Int
    private var mTrack: ViewGroup? = null
    private var mArrowUp: ImageView? = null
    private var mArrowDown: ImageView? = null
    private var mScroller: ScrollView? = null
    private var mItemClickListener: OnActionItemClickListener? = null
    private var mDismissListener: OnDismissListener? = null
    private var rootWidth = 0
    private val globalStatus: GlobalStatusService?
    private val actionItems = ArrayList<ActionMenuItem>()

    init {
        mWindow.setTouchInterceptor(View.OnTouchListener setTouchInterceptor@{ v: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                mWindow.dismiss()
                return@setTouchInterceptor true
            }
            false
        })
        mWindowManager = mActivity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mInflater = mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        setRootViewId(R.layout.status_menu)
        mAnimStyle = ANIM_AUTO
        mChildPos = 0
        globalStatus = ServiceUtils.getService(AndroidGUIActivator.bundleContext, GlobalStatusService::class.java)

        // start listening for newly register or removed protocol providers
        // cmeng: bundleContext can be null from field ??? can have problem in status update when blocked
        // This happens when Activity is recreated by the system after OSGi service has been
        // killed (and the whole process)
        if (AndroidGUIActivator.bundleContext == null) {
            Timber.e("OSGi service probably not initialized")
        }
        else
            AndroidGUIActivator.bundleContext!!.addServiceListener(this)
    }

    /**
     * Get action item at an index
     *
     * @param index Index of item (position from callback)
     * @return Action Item at the position
     */
    private fun getActionItem(index: Int): ActionMenuItem {
        return actionItems[index]
    }

    /**
     * On dismiss
     */
    override fun onDismiss() {
        if (!mDidAction && mDismissListener != null) {
            mDismissListener!!.onDismiss()
        }
    }

    /**
     * Set root view.
     *
     * @param id Layout resource id
     */
    private fun setRootViewId(id: Int) {
        mRootView = mInflater.inflate(id, null)
        mTrack = mRootView!!.findViewById(R.id.tracks)
        mArrowDown = mRootView!!.findViewById(R.id.arrow_down)
        mArrowUp = mRootView!!.findViewById(R.id.arrow_up)
        mScroller = mRootView!!.findViewById(R.id.scroller)

        // This was previously defined on show() method, moved here to prevent force close
        // that occurred when tapping fastly on a view to show quick action dialog.
        // Thank to zammbi (github.com/zammbi)
        mRootView!!.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        setContentView(mRootView)
    }

    /**
     * Set listener for action item clicked.
     *
     * @param listener Listener
     */
    fun setOnActionItemClickListener(listener: OnActionItemClickListener?) {
        mItemClickListener = listener
    }

    /**
     * Set animation style
     *
     * @param mAnimStyle animation style, default is set to ANIM_AUTO
     */
    fun setAnimStyle(mAnimStyle: Int) {
        this.mAnimStyle = mAnimStyle
    }

    /**
     * On show
     */
    private fun onShow() {}

    /**
     * On pre show
     */
    private fun preShow() {
        checkNotNull(mRootView) { "setContentView was not called with a view to display." }
        onShow()
        if (mBackground == null) mWindow.setBackgroundDrawable(BitmapDrawable(null, null as Bitmap?)) else mWindow.setBackgroundDrawable(mBackground)
        mWindow.width = WindowManager.LayoutParams.WRAP_CONTENT
        mWindow.height = WindowManager.LayoutParams.WRAP_CONTENT
        mWindow.isTouchable = true
        mWindow.isFocusable = true
        mWindow.isOutsideTouchable = true
        mWindow.contentView = mRootView
    }

    /**
     * Set background drawable.
     *
     * @param background Background drawable
     */
    fun setBackgroundDrawable(background: Drawable?) {
        mBackground = background
    }

    /**
     * Set content view.
     *
     * @param root Root view
     */
    override fun setContentView(root: View?) {
        mRootView = root
        mWindow.contentView = root
    }

    /**
     * Set content view.
     *
     * @param layoutResID Resource id
     */
    override fun setContentView(layoutResID: Int) {
        val inflater = mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        setContentView(inflater.inflate(layoutResID, null))
    }

    /**
     * Set listener for window dismissed. This listener will only be fired if the quick action
     * dialog is dismissed by clicking outside the dialog or clicking on sticky item.
     */
    fun setOnDismissListener(listener: OnDismissListener?) {
        mDismissListener = listener
    }

    /**
     * Dismiss the popup window.
     */
    fun dismiss() {
        mWindow.dismiss()
    }

    /**
     * Listener for item click
     */
    interface OnActionItemClickListener {
        fun onItemClick(source: GlobalStatusMenu?, pos: Int, actionId: Int)
    }

    /**
     * Listener for window dismiss
     */
    interface OnDismissListener {
        fun onDismiss()
    }

    /**
     * Add action item
     *
     * @param action [ActionMenuItem]
     */
    fun addActionItem(action: ActionMenuItem) {
        actionItems.add(action)
        val title = action.title
        val icon = action.icon
        val container = mInflater.inflate(R.layout.status_menu_item, null)
        val img = container.findViewById<ImageView>(R.id.iv_icon)
        val text = container.findViewById<TextView>(R.id.tv_title)
        if (icon != null) img.setImageDrawable(icon) else img.visibility = View.GONE
        if (title != null) text.text = title else text.visibility = View.GONE
        val pos = mChildPos
        val actionId = action.actionId
        container.setOnClickListener { v: View? ->
            if (mItemClickListener != null) mItemClickListener!!.onItemClick(this@GlobalStatusMenu, pos, actionId)
            if (!getActionItem(pos).isSticky) {
                mDidAction = true
                dismiss()
            }
        }
        container.isFocusable = true
        container.isClickable = true
        mTrack!!.addView(container, mInsertPos)
        mChildPos++
        mInsertPos++
    }

    /**
     * Add action item for protocolProvider
     *
     * @param action [ActionMenuItem]
     */
    fun addActionItem(action: ActionMenuItem, pps: ProtocolProviderService) {
        actionItems.add(action)
        val title = action.title
        val icon = action.icon
        val container = mInflater.inflate(R.layout.status_menu_item_spinner, null)
        val img = container.findViewById<ImageView>(R.id.iv_icon)
        val text = container.findViewById<TextView>(R.id.tv_title)
        accountSpinner[pps] = container
        if (icon != null) img.setImageDrawable(icon) else img.visibility = View.GONE
        if (title != null) text.text = title else text.visibility = View.GONE

        // WindowManager$BadTokenException
        val accountPresence = pps.getOperationSet(OperationSetPresence::class.java)
        val presenceStatuses = accountPresence!!.getSupportedStatusSet()

        // Create spinner with presence status list for the given pps
        // Note: xml layout has forced to use Spinner.MODE_DIALOG, other Note-5 crashes when use MODE_DROPDOWN
        val statusSpinner = container.findViewById<Spinner>(R.id.presenceSpinner)
        val statusAdapter = StatusListAdapter(mActivity, R.layout.account_presence_status_row, presenceStatuses)
        statusAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        statusSpinner.adapter = statusAdapter

        // Default state to offline
        val offline = accountPresence.getPresenceStatus(JabberStatusEnum.OFFLINE)
        statusSpinner.setSelection(presenceStatuses.indexOf(offline), false)

        // Setup adapter listener for onItemSelected
        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                val selectedStatus = statusSpinner.selectedItem as PresenceStatus
                val statusMessage = selectedStatus.statusName
                Thread {
                    // Try to publish selected status
                    try {
                        // cmeng: set state to false to force it to execute offline->online
                        globalStatus?.publishStatus(pps, selectedStatus, false)
                        if (pps.isRegistered) {
                            accountPresence.publishPresenceStatus(selectedStatus, statusMessage)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Account presence publish error.")
                    }
                }.start()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Should not happen in single selection mode
            }
        }
        container.isFocusable = true
        container.isClickable = true
        val presenceOpSet = pps.getOperationSet(OperationSetPresence::class.java)
        presenceOpSet!!.addProviderPresenceStatusListener(this)
        mTrack!!.addView(container, mInsertPos)
        mChildPos++
        mInsertPos++
    }

    /**
     * Show quick action popup. Popup is automatically positioned, on top or bottom of anchor view.
     */
    fun show(anchor: View) {
        preShow()
        var xPos: Int
        val yPos: Int
        val arrowPos: Int
        mDidAction = false
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorRect = Rect(location[0], location[1], location[0] + anchor.width, location[1] + anchor.height)

        // mRootView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mRootView!!.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        val rootHeight = mRootView!!.measuredHeight
        if (rootWidth == 0) {
            rootWidth = mRootView!!.measuredWidth
        }
        val screenSize = Point()
        mWindowManager.defaultDisplay.getSize(screenSize)

        // automatically get X coord of popup (top left)
        if (anchorRect.left + rootWidth > screenSize.x) {
            xPos = anchorRect.left - (rootWidth - anchor.width)
            xPos = if (xPos < 0) 0 else xPos
            arrowPos = anchorRect.centerX() - xPos
        }
        else {
            xPos = if (anchor.width > rootWidth) anchorRect.centerX() - rootWidth / 2 else anchorRect.left
            arrowPos = anchorRect.centerX() - xPos
        }
        val dyTop = anchorRect.top
        val dyBottom = screenSize.y - anchorRect.bottom
        val onTop = dyTop > dyBottom
        if (onTop) {
            if (rootHeight > dyTop) {
                yPos = 15
                val l = mScroller!!.layoutParams
                l.height = dyTop - anchor.height
            }
            else {
                yPos = anchorRect.top - rootHeight
            }
        }
        else {
            yPos = anchorRect.bottom
            if (rootHeight > dyBottom) {
                val l = mScroller!!.layoutParams
                l.height = dyBottom
            }
        }
        showArrow(if (onTop) R.id.arrow_down else R.id.arrow_up, arrowPos)
        setAnimationStyle(screenSize.x, anchorRect.centerX(), onTop)
        mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos)
    }

    /**
     * Set animation style
     *
     * @param screenWidth screen width
     * @param requestedX distance from left edge
     * @param onTop flag to indicate where the popup should be displayed. Set TRUE if displayed on top of anchor
     * view and vice versa
     */
    private fun setAnimationStyle(screenWidth: Int, requestedX: Int, onTop: Boolean) {
        val arrowPos = requestedX - mArrowUp!!.measuredWidth / 2
        when (mAnimStyle) {
            ANIM_GROW_FROM_LEFT -> mWindow.animationStyle = if (onTop) R.style.Animations_PopUpMenu_Left else R.style.Animations_PopDownMenu_Left
            ANIM_GROW_FROM_RIGHT -> mWindow.animationStyle = if (onTop) R.style.Animations_PopUpMenu_Right else R.style.Animations_PopDownMenu_Right
            ANIM_GROW_FROM_CENTER -> mWindow.animationStyle = if (onTop) R.style.Animations_PopUpMenu_Center else R.style.Animations_PopDownMenu_Center
            ANIM_REFLECT -> mWindow.animationStyle = if (onTop) R.style.Animations_PopUpMenu_Reflect else R.style.Animations_PopDownMenu_Reflect
            ANIM_AUTO -> if (arrowPos <= screenWidth / 4) {
                mWindow.animationStyle = if (onTop) R.style.Animations_PopUpMenu_Left else R.style.Animations_PopDownMenu_Left
            }
            else if (arrowPos > screenWidth / 4 && arrowPos < 3 * (screenWidth / 4)) {
                mWindow.animationStyle = if (onTop) R.style.Animations_PopUpMenu_Center else R.style.Animations_PopDownMenu_Center
            }
            else {
                mWindow.animationStyle = if (onTop) R.style.Animations_PopUpMenu_Right else R.style.Animations_PopDownMenu_Right
            }
        }
    }

    /**
     * Show arrow
     *
     * @param whichArrow arrow type resource id
     * @param requestedX distance from left screen
     */
    private fun showArrow(whichArrow: Int, requestedX: Int) {
        val showArrow = if (whichArrow == R.id.arrow_up) mArrowUp else mArrowDown
        val hideArrow = if (whichArrow == R.id.arrow_up) mArrowDown else mArrowUp
        val arrowWidth = mArrowUp!!.measuredWidth
        showArrow!!.visibility = View.VISIBLE
        val param = showArrow.layoutParams as ViewGroup.MarginLayoutParams
        param.leftMargin = requestedX - arrowWidth / 2
        hideArrow!!.visibility = View.INVISIBLE
    }

    override fun providerStatusChanged(evt: ProviderPresenceStatusChangeEvent) {
        val pps = evt.getProvider()
        // Timber.w("### PPS presence status change: " + pps + " => " + evt.getNewStatus());

        // do not proceed if spinnerContainer is null
        val spinnerContainer = accountSpinner[pps]
        if (spinnerContainer == null) {
            Timber.e("No presence status spinner setup for: %s", pps)
            return
        }
        val presenceStatus = evt.getNewStatus()
        val statusSpinner = spinnerContainer.findViewById<Spinner>(R.id.presenceSpinner)
        val statusAdapter = statusSpinner.adapter as StatusListAdapter
        mActivity.runOnUiThread { statusSpinner.setSelection(statusAdapter.getPosition(presenceStatus)) }
    }

    override fun providerStatusMessageChanged(evt: PropertyChangeEvent) {
        // Timber.w("### PPS Status message change: " + evt.getSource() + " => " + evt.getNewValue());
    }

    /**
     * Implements the `ServiceListener` method. Verifies whether the received event concerning
     * a `ProtocolProviderService` and take the necessary action.
     *
     * @param event The `ServiceEvent` object.
     */
    override fun serviceChanged(event: ServiceEvent) {
        // if the event is caused by a bundle being stopped, we don't want to know
        val serviceRef = event.serviceReference
        if (serviceRef.bundle.state == Bundle.STOPPING) return

        // bundleContext == null on exit
        val bundleContext = AndroidGUIActivator.bundleContext ?: return
        val service = bundleContext.getService(serviceRef as ServiceReference<Any>)

        // we don't care if the source service is not a protocol provider
        if (service is ProtocolProviderService) {
            // Timber.w("## ProtocolServiceProvider Add or Remove: " + event.getType());
            val pps = service
            when (event.type) {
                ServiceEvent.REGISTERED -> addMenuItemPPS(pps)
                ServiceEvent.UNREGISTERING -> removeMenuItemPPS(pps)
            }
        }
    }

    /**
     * When the PPS is being registered i.e. enabled on account list
     * 1. Create a new entry in the status menu
     *
     * @param pps new provider to be added to the status menu
     */
    private fun addMenuItemPPS(pps: ProtocolProviderService) {
        if (!accountSpinner.containsKey(pps)) {
            // Timber.w("## ProtocolServiceProvider Added: " + pps);
            val accountId = pps.accountID
            val userJid = accountId.accountJid
            val icon =  ResourcesCompat.getDrawable(aTalkApp.appResources, R.drawable.jabber_status_online, null)!!
            val actionItem = ActionMenuItem(mChildPos++, userJid, icon)
            addActionItem(actionItem, pps)
        }
    }

    /**
     * When a pps is unregister i.e. disabled on account list:
     * 1. Remove ProviderPresenceStatusListener for this pps
     * 2. Remove the spinner view from the status menu
     * 3. Remove entry in the accountSpinner
     * 4. Readjust all the required pointer
     *
     * @param pps provider to be removed
     */
    private fun removeMenuItemPPS(pps: ProtocolProviderService) {
        if (accountSpinner.containsKey(pps)) {
            val presenceOpSet = pps.getOperationSet(OperationSetPresence::class.java)
            presenceOpSet!!.removeProviderPresenceStatusListener(this)
            val spinnerContainer = accountSpinner[pps]
            (spinnerContainer!!.parent as ViewGroup).removeView(spinnerContainer)
            accountSpinner.remove(pps)
            mChildPos--
            mInsertPos--
        }
    }

    companion object {
        private val accountSpinner = HashMap<ProtocolProviderService, View>()
        private const val ANIM_GROW_FROM_LEFT = 1
        private const val ANIM_GROW_FROM_RIGHT = 2
        private const val ANIM_GROW_FROM_CENTER = 3
        const val ANIM_REFLECT = 4
        private const val ANIM_AUTO = 5
    }
}