package org.atalk.hmos.gui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class UnreadCountCustomView : View {
    private var unreadCount = 0
    private var paint: Paint? = null
    private var textPaint: Paint? = null
    private var backgroundColor = -0xe2b8a3

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initXMLAttrs(context, attrs)
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initXMLAttrs(context, attrs)
        init()
    }

    // Currently not support in aTalk
    private fun initXMLAttrs(context: Context, attrs: AttributeSet?) {
        // TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.UnreadCountCustomView);
        // setBackgroundColor(a.getColor(a.getIndex(0), ContextCompat.getColor(context, R.color.green700)));
        // a.recycle();
    }

    fun init() {
        paint = Paint()
        paint!!.color = backgroundColor
        paint!!.isAntiAlias = true
        textPaint = Paint()
        textPaint!!.color = Color.WHITE
        textPaint!!.textAlign = Paint.Align.CENTER
        textPaint!!.isAntiAlias = true
        textPaint!!.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midx = width / 2.0f
        val midy = height / 2.0f
        val radius = Math.min(width, height) / 2.0f
        val textOffset = width / 6.0f
        textPaint!!.textSize = 0.95f * radius
        canvas.drawCircle(midx, midy, radius * 0.94f, paint!!)
        canvas.drawText(if (unreadCount > 999) "\u221E" else unreadCount.toString(), midx, midy + textOffset, textPaint!!)
    }

    fun setUnreadCount(unreadCount: Int) {
        this.unreadCount = unreadCount
        invalidate()
    }

    override fun setBackgroundColor(backgroundColor: Int) {
        this.backgroundColor = backgroundColor
    }
}