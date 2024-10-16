package org.fossify.home.views

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import kotlinx.collections.immutable.toImmutableList
import org.fossify.home.R
import org.fossify.home.extensions.config
import org.fossify.home.extensions.getCellCount
import org.fossify.home.helpers.MAX_CLICK_DURATION
import org.fossify.home.models.HomeScreenGridItem

@SuppressLint("ViewConstructor")
class MyAppWidgetResizeFrame(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var resizeWidgetLinePaint: Paint
    private var resizeWidgetLineDotPaint: Paint
    private var actionDownCoords = PointF()
    private var actionDownMS = 0L
    private var frameRect = Rect(0, 0, 0, 0)    // coords in pixels
    private var cellsRect = Rect(0, 0, 0, 0)    // cell IDs like 0, 1, 2..
    private var cellWidth = 0
    private var cellHeight = 0
    private var minResizeWidthCells = 1
    private var minResizeHeightCells = 1
    private val occupiedCells = ArrayList<Pair<Int, Int>>()
    private var resizedItem: HomeScreenGridItem? = null
    private var sideMargins = Rect()
    private val lineDotRadius = context.resources.getDimension(R.dimen.resize_frame_dot_radius)
    private val MAX_TOUCH_LINE_DISTANCE = lineDotRadius * 5     // how close we have to be to the widgets side to drag it
    var onClickListener: (() -> Unit)? = null
    var onResizeListener: ((cellsRect: Rect) -> Unit)? = null

    private val DRAGGING_NONE = 0
    private val DRAGGING_LEFT = 1
    private val DRAGGING_TOP = 2
    private val DRAGGING_RIGHT = 3
    private val DRAGGING_BOTTOM = 4
    private var dragDirection = DRAGGING_NONE

    init {
        background = ColorDrawable(Color.TRANSPARENT)

        resizeWidgetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = context.resources.getDimension(org.fossify.commons.R.dimen.tiny_margin)
            style = Paint.Style.STROKE
        }

        resizeWidgetLineDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
    }

    fun updateFrameCoords(
        coords: Rect,
        cellWidth: Int,
        cellHeight: Int,
        sideMargins: Rect,
        gridItem: HomeScreenGridItem,
        allGridItems: ArrayList<HomeScreenGridItem>
    ) {
        frameRect = coords
        cellsRect = Rect(gridItem.left, gridItem.top, gridItem.right, gridItem.bottom)
        this.cellWidth = cellWidth
        this.cellHeight = cellHeight
        this.sideMargins = sideMargins
        this.resizedItem = gridItem
        val providerInfo = gridItem.providerInfo ?: AppWidgetManager.getInstance(context)!!.installedProviders.firstOrNull {
            it.provider.className == gridItem.className
        } ?: return

        minResizeWidthCells = Math.min(context.config.homeColumnCount, context.getCellCount(providerInfo.minResizeWidth))
        minResizeHeightCells = Math.min(context.config.homeRowCount, context.getCellCount(providerInfo.minResizeHeight))
        redrawFrame()

        occupiedCells.clear()
        allGridItems.toImmutableList().forEach { item ->
            for (xCell in item.left..item.right) {
                for (yCell in item.top..item.bottom) {
                    occupiedCells.add(Pair(xCell, yCell))
                }
            }
        }
    }

    private fun redrawFrame() {
        layoutParams.width = frameRect.right - frameRect.left
        layoutParams.height = frameRect.bottom - frameRect.top
        x = frameRect.left.toFloat()
        y = frameRect.top.toFloat()
        requestLayout()
    }

    private fun cellChanged() {
        onResizeListener?.invoke(cellsRect)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                actionDownCoords.x = event.rawX
                actionDownCoords.y = event.rawY
                actionDownMS = System.currentTimeMillis()
                dragDirection = DRAGGING_NONE
                if (event.x < MAX_TOUCH_LINE_DISTANCE) {
                    dragDirection = DRAGGING_LEFT
                } else if (event.y < MAX_TOUCH_LINE_DISTANCE) {
                    dragDirection = DRAGGING_TOP
                } else if (event.x > width - MAX_TOUCH_LINE_DISTANCE) {
                    dragDirection = DRAGGING_RIGHT
                } else if (event.y > height - MAX_TOUCH_LINE_DISTANCE) {
                    dragDirection = DRAGGING_BOTTOM
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val minWidth = minResizeWidthCells * cellWidth
                val minHeight = minResizeHeightCells * cellHeight
                when (dragDirection) {
                    DRAGGING_LEFT -> {
                        val newWidth = frameRect.right - event.rawX.toInt()
                        val wantedLeft = if (newWidth >= minWidth) {
                            event.rawX.toInt()
                        } else {
                            frameRect.right - minWidth
                        }

                        val wantedLeftCellX = roundToClosestMultiplyOfNumber(wantedLeft - sideMargins.left, cellWidth) / cellWidth
                        var areAllCellsFree = true
                        for (xCell in wantedLeftCellX..cellsRect.right) {
                            for (yCell in cellsRect.top..cellsRect.bottom) {
                                if (occupiedCells.contains(Pair(xCell, yCell))) {
                                    areAllCellsFree = false
                                    break
                                }
                            }
                        }

                        if (areAllCellsFree && cellsRect.left != wantedLeftCellX) {
                            cellsRect.left = wantedLeftCellX
                            cellChanged()
                        }

                        frameRect.left = wantedLeft
                    }

                    DRAGGING_TOP -> {
                        val newHeight = frameRect.bottom - event.rawY.toInt()
                        val wantedTop = if (newHeight >= minHeight) {
                            event.rawY.toInt()
                        } else {
                            frameRect.bottom - minHeight
                        }

                        val wantedTopCellY = roundToClosestMultiplyOfNumber(wantedTop - sideMargins.top, cellHeight) / cellHeight
                        var areAllCellsFree = true
                        for (xCell in cellsRect.left..cellsRect.right) {
                            for (yCell in wantedTopCellY..cellsRect.bottom) {
                                if (occupiedCells.contains(Pair(xCell, yCell))) {
                                    areAllCellsFree = false
                                    break
                                }
                            }
                        }

                        if (areAllCellsFree && cellsRect.top != wantedTopCellY) {
                            cellsRect.top = wantedTopCellY
                            cellChanged()
                        }

                        frameRect.top = wantedTop
                    }

                    DRAGGING_RIGHT -> {
                        val newWidth = event.rawX.toInt() - frameRect.left
                        val wantedRight = if (newWidth >= minWidth) {
                            event.rawX.toInt()
                        } else {
                            frameRect.left + minWidth
                        }

                        val wantedRightCellX = roundToClosestMultiplyOfNumber(wantedRight - sideMargins.left, cellWidth) / cellWidth - 1
                        var areAllCellsFree = true
                        for (xCell in cellsRect.left..wantedRightCellX) {
                            for (yCell in cellsRect.top..cellsRect.bottom) {
                                if (occupiedCells.contains(Pair(xCell, yCell))) {
                                    areAllCellsFree = false
                                    break
                                }
                            }
                        }

                        if (areAllCellsFree && cellsRect.right != wantedRightCellX) {
                            cellsRect.right = wantedRightCellX
                            cellChanged()
                        }

                        frameRect.right = wantedRight
                    }

                    DRAGGING_BOTTOM -> {
                        val newHeight = event.rawY.toInt() - frameRect.top
                        val wantedBottom = if (newHeight >= minHeight) {
                            event.rawY.toInt()
                        } else {
                            frameRect.top + minHeight
                        }

                        val wantedBottomCellY = roundToClosestMultiplyOfNumber(wantedBottom - sideMargins.top, cellHeight) / cellHeight - 1
                        var areAllCellsFree = true
                        for (xCell in cellsRect.left..cellsRect.right) {
                            for (yCell in cellsRect.top..wantedBottomCellY) {
                                if (occupiedCells.contains(Pair(xCell, yCell))) {
                                    areAllCellsFree = false
                                    break
                                }
                            }
                        }

                        if (wantedBottomCellY == context.config.homeRowCount - 1) {
                            areAllCellsFree = false
                        }

                        if (areAllCellsFree && cellsRect.bottom != wantedBottomCellY) {
                            cellsRect.bottom = wantedBottomCellY
                            cellChanged()
                        }

                        frameRect.bottom = wantedBottom
                    }
                }

                if (dragDirection != DRAGGING_NONE) {
                    redrawFrame()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragDirection == DRAGGING_NONE) {
                    onClickListener?.invoke()
                } else if (System.currentTimeMillis() - actionDownMS < MAX_CLICK_DURATION) {
                    onClickListener?.invoke()
                    dragDirection = DRAGGING_NONE
                } else {
                    when (dragDirection) {
                        DRAGGING_LEFT -> {
                            val wantedLeft = roundToClosestMultiplyOfNumber(frameRect.left - sideMargins.left, cellWidth)
                            val wantedLeftCellX = wantedLeft / cellWidth
                            var areAllCellsFree = true
                            for (xCell in wantedLeftCellX..cellsRect.right) {
                                for (yCell in cellsRect.top..cellsRect.bottom) {
                                    if (occupiedCells.contains(Pair(xCell, yCell))) {
                                        areAllCellsFree = false
                                        break
                                    }
                                }
                            }

                            if (areAllCellsFree) {
                                frameRect.left = wantedLeft + sideMargins.left
                                cellsRect.left = wantedLeftCellX
                            } else {
                                frameRect.left = cellsRect.left * cellWidth + sideMargins.left
                            }
                        }

                        DRAGGING_TOP -> {
                            val wantedTop = roundToClosestMultiplyOfNumber(frameRect.top - sideMargins.top, cellHeight)
                            val wantedTopCellY = wantedTop / cellHeight
                            var areAllCellsFree = true
                            for (xCell in cellsRect.left..cellsRect.right) {
                                for (yCell in wantedTopCellY..cellsRect.bottom) {
                                    if (occupiedCells.contains(Pair(xCell, yCell))) {
                                        areAllCellsFree = false
                                        break
                                    }
                                }
                            }

                            if (areAllCellsFree) {
                                frameRect.top = wantedTop + sideMargins.top
                                cellsRect.top = wantedTopCellY
                            } else {
                                frameRect.top = cellsRect.top * cellHeight + sideMargins.top
                            }
                        }

                        DRAGGING_RIGHT -> {
                            val wantedRight = roundToClosestMultiplyOfNumber(frameRect.right - sideMargins.left, cellWidth)
                            val wantedRightCellX = wantedRight / cellWidth - 1
                            var areAllCellsFree = true
                            for (xCell in cellsRect.left..wantedRightCellX + 1) {
                                for (yCell in cellsRect.top..cellsRect.bottom) {
                                    if (occupiedCells.contains(Pair(xCell, yCell))) {
                                        areAllCellsFree = false
                                        break
                                    }
                                }
                            }

                            if (areAllCellsFree) {
                                frameRect.right = wantedRight + sideMargins.left
                                cellsRect.right = wantedRightCellX
                            } else {
                                frameRect.right = (cellsRect.right + 1) * cellWidth + sideMargins.left
                            }
                        }

                        DRAGGING_BOTTOM -> {
                            val wantedBottom = roundToClosestMultiplyOfNumber(frameRect.bottom - sideMargins.top, cellHeight)
                            val wantedBottomCellY = wantedBottom / cellHeight - 1
                            var areAllCellsFree = true
                            for (xCell in cellsRect.left..cellsRect.right) {
                                for (yCell in cellsRect.top..wantedBottomCellY + 1) {
                                    if (occupiedCells.contains(Pair(xCell, yCell))) {
                                        areAllCellsFree = false
                                        break
                                    }
                                }
                            }

                            if (wantedBottomCellY == context.config.homeRowCount - 1) {
                                areAllCellsFree = false
                            }

                            if (areAllCellsFree) {
                                frameRect.bottom = wantedBottom + sideMargins.top
                                cellsRect.bottom = wantedBottomCellY
                            } else {
                                frameRect.bottom = (cellsRect.bottom + 1) * cellHeight + sideMargins.top
                            }
                        }
                    }
                    redrawFrame()
                }
            }
        }

        return true
    }

    private fun roundToClosestMultiplyOfNumber(value: Int, number: Int): Int {
        return number * (Math.round(Math.abs(value / number.toDouble()))).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (x != 0f || y != 0f) {
            canvas.drawRect(lineDotRadius, lineDotRadius, width.toFloat() - lineDotRadius, height.toFloat() - lineDotRadius, resizeWidgetLinePaint)

            canvas.drawCircle(lineDotRadius, height / 2f, lineDotRadius, resizeWidgetLineDotPaint)
            canvas.drawCircle(width / 2f, lineDotRadius, lineDotRadius, resizeWidgetLineDotPaint)
            canvas.drawCircle(width - lineDotRadius, height / 2f, lineDotRadius, resizeWidgetLineDotPaint)
            canvas.drawCircle(width / 2f, height - lineDotRadius, lineDotRadius, resizeWidgetLineDotPaint)
        }
    }
}
