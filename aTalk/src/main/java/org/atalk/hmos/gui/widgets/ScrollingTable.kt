/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import org.atalk.hmos.R

/**
 * Custom layout that handles fixes table header, by measuring maximum column widths in both header and table body. Then
 * synchronizes those maximum values in header and body columns widths.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ScrollingTable : LinearLayout {
    /**
     * Create a new instance of `ScrollingTable`
     *
     * @param context the context
     */
    constructor(context: Context?) : super(context) {}

    /**
     * Creates a new instance of `ScrollingTable`.
     *
     * @param context the context
     * @param attrs the attribute set
     */
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val header = findViewById<TableLayout>(R.id.table_header)
        val body = findViewById<TableLayout>(R.id.table_body)

        // Find max column widths
        val headerWidths = findMaxWidths(header)
        val bodyWidths = findMaxWidths(body)
                ?: // Table is empty
                return
        val maxWidths = IntArray(bodyWidths.size)
        for (i in headerWidths!!.indices) {
            maxWidths[i] = Math.max(headerWidths[i], bodyWidths[i])
        }

        // Set column widths to max values
        setColumnWidths(header, maxWidths)
        setColumnWidths(body, maxWidths)
    }

    /**
     * Finds maximum columns widths in given table layout.
     *
     * @param table table layout that will be examined for max column widths.
     * @return array of max columns widths for given table, it's length is equal to table's column count.
     */
    private fun findMaxWidths(table: TableLayout): IntArray? {
        var colWidths: IntArray? = null
        for (rowNum in 0 until table.childCount) {
            val row = table.getChildAt(rowNum) as TableRow
            if (colWidths == null) colWidths = IntArray(row.childCount)
            for (colNum in 0 until row.childCount) {
                val cellWidth = row.getChildAt(colNum).width
                if (cellWidth > colWidths[colNum]) {
                    colWidths[colNum] = cellWidth
                }
            }
        }
        return colWidths
    }

    /**
     * Adjust given table columns width to sizes given in `widths` array.
     *
     * @param table the table layout which columns will be adjusted
     * @param widths array of columns widths to set
     */
    private fun setColumnWidths(table: TableLayout, widths: IntArray) {
        for (rowNum in 0 until table.childCount) {
            val row = table.getChildAt(rowNum) as TableRow
            for (colNum in 0 until row.childCount) {
                val column = row.getChildAt(colNum)
                val params = column.layoutParams as TableRow.LayoutParams
                params.width = widths[colNum]
            }
        }
    }
}