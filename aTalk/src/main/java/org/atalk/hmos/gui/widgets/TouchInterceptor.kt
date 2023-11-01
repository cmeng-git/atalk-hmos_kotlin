/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ListView
import org.atalk.hmos.R
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * Modified version of class of the same name from android source of the music app. The widget displays a list of items.
 * User can set order of items by dragging them on the screen.<br></br>
 * This `View` requires following XML attributes:<br></br>
 * - `itemHeight` the height of list item<br></br>
 * - `itemExpandedHeight` the height that will be set to expanded item(the one tha makes space for dragged item)<br></br>
 * - `dragRegionStartX` and `dragRegionEndX` item can be grabbed when start x coordinate is between them
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class TouchInterceptor(context: Context, attrs: AttributeSet?) : ListView(context, attrs) {
    /**
     * The view representing dragged item
     */
    private var dragView: ImageView? = null

    /**
     * The [android.view.WindowManager] used to display dragged view
     */
    private var windowManager: WindowManager? = null

    /**
     * Layout parameters of dragged view
     */
    private var windowParams: WindowManager.LayoutParams? = null

    /**
     * At which position is the item currently being dragged. Note that this takes in to account header items.
     */
    private var dragPos = 0

    /**
     * At which position was the item being dragged originally
     */
    private var srcDragPos = 0

    /**
     * At what x offset inside the item did the user grab it
     */
    private var dragPointX = 0

    /**
     * At what y offset inside the item did the user grab it
     */
    private var dragPointY = 0

    /**
     * The difference between screen coordinates and coordinates in this view
     */
    private var xOffset = 0

    /**
     * The difference between screen coordinates and coordinates in this view
     */
    private var yOffset = 0

    /**
     * The [DragListener]
     */
    private var dragListener: DragListener? = null

    /**
     * The [DropListener]
     */
    private var dropListener: DropListener? = null

    /**
     * Upper boundary, when reached the view is scrolled up
     */
    private var upperBound = 0

    /**
     * Lower boundary, when reached the view is scrolled down
     */
    private var lowerBound = 0

    /**
     * View's height
     */
    private var height = 0

    /**
     *
     */
    private val tempRect = Rect()

    /**
     * The background of dragged `View`
     */
    private var dragBitmap: Bitmap? = null

    /**
     * The touch slop
     */
    private val touchSlop: Int

    /**
     * Normal list item's height
     */
    private var itemHeightNormal = 0

    /**
     * The height of expanded item
     */
    private var itemHeightExpanded = 0

    /**
     * Half of the normal item's height
     */
    private var itemHeightHalf = 0

    /**
     * Start x coordinate of draggable area
     */
    private var dragRegionStartX = 0

    /**
     * End x coordinate of draggable area
     */
    private var dragRegionEndX = 0

    /**
     * Creates new instance of TouchInterceptor. The attribute set must contain: - `itemHeight` the
     * height of list item<br></br>
     * - `itemExpandedHeight` the height that will be set to expanded item<br></br>
     * - `dragRegionStartX` and `dragRegionEndX` item can be grabbed when start x coordinate is between  them
     */
    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.TouchInterceptor, 0, 0)
        try {
            itemHeightNormal = a.getDimensionPixelSize(R.styleable.TouchInterceptor_itemHeight, -1)
            itemHeightHalf = itemHeightNormal / 2
            require(itemHeightNormal != -1) { "Item height attribute unspecified" }
            itemHeightExpanded = a.getDimensionPixelSize(R.styleable.TouchInterceptor_itemExpandedHeight, -1)
            require(itemHeightExpanded != -1) { "Expanded item height attribute unspecified" }
            dragRegionStartX = a.getDimensionPixelSize(R.styleable.TouchInterceptor_dragRegionStartX, -1)
            dragRegionEndX = a.getDimensionPixelSize(R.styleable.TouchInterceptor_dragRegionEndX, -1)
            require(!(dragRegionStartX == -1 || dragRegionEndX == -1)) { "Drag region attributes unspecified" }
        } finally {
            a.recycle()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (dragListener != null || dropListener != null) {
            if (!isEnabled) {
                return true
            }
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    val x = ev.x.toInt()
                    val y = ev.y.toInt()
                    val itemIndex = pointToPosition(x, y)
                    if (itemIndex != INVALID_POSITION) {
                        val item = getChildAt(itemIndex - firstVisiblePosition)
                        dragPointX = x - (item?.left ?: 0)
                        dragPointY = y - (item?.top ?: 0)
                        xOffset = ev.rawX.toInt() - x
                        yOffset = ev.rawY.toInt() - y
                        Timber.d("Dragging %s, %s sxy: %s, %s", x, y, dragRegionStartX, dragRegionEndX)
                        if (x in dragRegionStartX..dragRegionEndX) {
                            item!!.isDrawingCacheEnabled = true
                            // Create a copy of the drawing cache so that it does
                            // not get recycled by the framework when the list tries
                            // to clean up memory
                            val bitmap = Bitmap.createBitmap(item.drawingCache)
                            startDragging(bitmap, x, y)
                            dragPos = itemIndex
                            srcDragPos = dragPos
                            height = getHeight()
                            val touchSlop = touchSlop
                            upperBound = min(y - touchSlop, height / 3)
                            lowerBound = max(y + touchSlop, height * 2 / 3)
                            return false
                        }
                        stopDragging()
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    /*
     * pointToPosition() doesn't consider invisible views, but we need to, so implement a slightly different version.
     */
    private fun myPointToPosition(x: Int, y: Int): Int {
        if (y < 0) {
            // when dragging off the top of the screen, calculate position
            // by going back from a visible item
            val pos = myPointToPosition(x, y + itemHeightNormal)
            if (pos > 0) {
                return pos - 1
            }
        }
        val frame = tempRect
        val count = childCount
        for (i in count - 1 downTo 0) {
            val child = getChildAt(i)
            child.getHitRect(frame)
            if (frame.contains(x, y)) {
                return firstVisiblePosition + i
            }
        }
        return INVALID_POSITION
    }

    private fun getItemForPosition(y: Int): Int {
        val adjustedy = y - dragPointY - itemHeightHalf
        var pos = myPointToPosition(0, adjustedy)
        if (pos >= 0) {
            if (pos <= srcDragPos) {
                pos += 1
            }
        }
        else if (adjustedy < 0) {
            // this shouldn't happen anymore now that myPointToPosition deals
            // with this situation
            pos = 0
        }
        return pos
    }

    private fun adjustScrollBounds(y: Int) {
        if (y >= height / 3) {
            upperBound = height / 3
        }
        if (y <= height * 2 / 3) {
            lowerBound = height * 2 / 3
        }
    }

    /*
     * Restore size and visibility for all listitems
     */
    private fun unExpandViews(deletion: Boolean) {
        val y0 = if (getChildAt(0) == null) 0 else getChildAt(0).top
        var i = 0
        while (true) {
            var v = getChildAt(i)
            if (v == null) {
                if (deletion) {
                    // HACK force update of mItemCount
                    val position = firstVisiblePosition
                    getChildAt(0)
                    adapter = adapter
                    setSelectionFromTop(position, y0)
                    // end hack
                }
                try {
                    // force children to be recreated where needed
                    layoutChildren()
                    v = getChildAt(i)
                } catch (ex: IllegalStateException) {
                    // layoutChildren throws this sometimes, presumably because
                    // we're in the process of being torn down but are still
                    // getting touch events
                }
                if (v == null) {
                    return
                }
            }
            val params = v.layoutParams
            params.height = itemHeightNormal
            v.layoutParams = params
            v.visibility = VISIBLE
            i++
        }
    }

    /*
     * Adjust visibility and size to make it appear as though an item is being dragged around and other items are making
     * room for it: If dropping the item would result in it still being in the same place, then make the dragged
     * listitem's size normal, but make the item invisible. Otherwise, if the dragged listitem is still on screen, make
     * it as small as possible and expand the item below the insert point. If the dragged item is not on screen, only
     * expand the item below the current insertpoint.
     */
    private fun doExpansion() {
        var childnum = dragPos - firstVisiblePosition
        if (dragPos > srcDragPos) {
            childnum++
        }
        val numheaders = headerViewsCount
        val first = getChildAt(srcDragPos - firstVisiblePosition)
        var i = 0
        while (true) {
            val vv = getChildAt(i) ?: break
            var height = itemHeightNormal
            var visibility = VISIBLE
            if (dragPos < numheaders && i == numheaders) {
                // dragging on top of the header item, so adjust the item below
                // instead
                if (vv == first) {
                    visibility = INVISIBLE
                }
                else {
                    height = itemHeightExpanded
                }
            }
            else if (vv == first) {
                // processing the item that is being dragged
                if (dragPos == srcDragPos || getPositionForView(vv) == count - 1) {
                    // hovering over the original location
                    visibility = INVISIBLE
                }
                else {
                    // not hovering over it
                    // Ideally the item would be completely gone, but neither
                    // setting its size to 0 nor settings visibility to GONE
                    // has the desired effect.
                    height = 1
                }
            }
            else if (i == childnum) {
                if (dragPos >= numheaders && dragPos < count - 1) {
                    height = itemHeightExpanded
                }
            }
            val params = vv.layoutParams
            params.height = height
            vv.layoutParams = params
            vv.visibility = visibility
            i++
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if ((dragListener != null || dropListener != null) && dragView != null) {
            when (val action = ev.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val r = tempRect
                    dragView!!.getDrawingRect(r)
                    stopDragging()
                    if (dropListener != null && dragPos >= 0 && dragPos < count) {
                        dropListener!!.drop(srcDragPos, dragPos)
                    }
                    unExpandViews(false)
                }
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val x = ev.x.toInt()
                    val y = ev.y.toInt()
                    dragView(x, y)
                    val itemnum = getItemForPosition(y)
                    if (itemnum >= 0) {
                        if (action == MotionEvent.ACTION_DOWN || itemnum != dragPos) {
                            if (dragListener != null) {
                                dragListener!!.drag(dragPos, itemnum)
                            }
                            dragPos = itemnum
                            doExpansion()
                        }
                        var speed = 0
                        adjustScrollBounds(y)
                        if (y > lowerBound) {
                            // scroll the list up a bit
                            speed = if (lastVisiblePosition < count - 1) {
                                if (y > (height + lowerBound) / 2) 16 else 4
                            }
                            else {
                                1
                            }
                        }
                        else if (y < upperBound) {
                            // scroll the list down a bit
                            speed = if (y < upperBound / 2) -16 else -4
                            val y0 = if (getChildAt(0) == null) 0 else getChildAt(0).top
                            if (firstVisiblePosition == 0 && y0 >= paddingTop) {
                                // if we're already at the top, don't try to
                                // scroll, because it causes the framework to
                                // do some extra drawing that messes up our animation
                                speed = 0
                            }
                        }
                        if (speed != 0) {
                            smoothScrollBy(speed, 30)
                        }
                    }
                }
            }
            return true
        }
        return super.onTouchEvent(ev)
    }

    private fun startDragging(bm: Bitmap, x: Int, y: Int) {
        stopDragging()
        windowParams = WindowManager.LayoutParams()
        windowParams!!.gravity = Gravity.TOP or Gravity.START
        windowParams!!.x = xOffset
        windowParams!!.y = y - dragPointY + yOffset
        windowParams!!.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams!!.width = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams!!.flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        windowParams!!.format = PixelFormat.TRANSLUCENT
        windowParams!!.windowAnimations = 0
        val context = context
        val v = ImageView(context)
        val backGroundColor = context.resources.getColor(R.color.blue, null)
        v.setBackgroundColor(backGroundColor)
        v.setPadding(0, 0, 0, 0)
        v.setImageBitmap(bm)
        dragBitmap = bm
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager!!.addView(v, windowParams)
        dragView = v
    }

    private fun dragView(x: Int, y: Int) {
        windowParams!!.x = xOffset
        windowParams!!.y = y - dragPointY + yOffset
        windowManager!!.updateViewLayout(dragView, windowParams)
    }

    private fun stopDragging() {
        if (dragView != null) {
            dragView!!.visibility = GONE
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(dragView)
            dragView!!.setImageDrawable(null)
            dragView = null
        }
        if (dragBitmap != null) {
            dragBitmap!!.recycle()
            dragBitmap = null
        }
    }

    /**
     * Sets the [DragListener] that will be notified about drag events.
     *
     * @param l the drag listener
     */
    fun setDragListener(l: DragListener?) {
        dragListener = l
    }

    /**
     * Sets the [DropListener] that will be notified about drop events.
     *
     * @param l the drop listener
     */
    fun setDropListener(l: DropListener?) {
        dropListener = l
    }

    /**
     * Drag events occur when currently dragged item moves around the screen over other items.
     */
    interface DragListener {
        /**
         * Fired when item is dragged over other list item.
         *
         * @param from the position of dragged item
         * @param to the position of currently selected item
         */
        fun drag(from: Int, to: Int)
    }

    /**
     * Drop events occur when the dragged item is finally dropped.
     */
    interface DropListener {
        /**
         * Fired when dragged item is dropped on other list item.
         *
         * @param from the position of source item
         * @param to the destination drop position
         */
        fun drop(from: Int, to: Int)
    }
}