package org.fossify.home.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.RelativeLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toRect
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.google.android.material.math.MathUtils
import kotlinx.collections.immutable.toImmutableList
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isSPlus
import org.fossify.home.R
import org.fossify.home.activities.MainActivity
import org.fossify.home.databinding.HomeScreenGridBinding
import org.fossify.home.extensions.config
import org.fossify.home.extensions.getDrawableForPackageName
import org.fossify.home.extensions.homeScreenGridItemsDB
import org.fossify.home.helpers.*
import org.fossify.home.models.HomeScreenGridItem
import kotlin.math.*

class HomeScreenGrid(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private lateinit var binding: HomeScreenGridBinding
    private var columnCount = context.config.homeColumnCount
    private var rowCount = context.config.homeRowCount
    private var pageIndicatorsYPos = 0
    private val cells = mutableMapOf<Point, Rect>()
    private var dockCellY = 0
    var cellWidth = 0
    var cellHeight = 0

    private var iconMargin = (context.resources.getDimension(R.dimen.icon_side_margin) * 5 / columnCount).toInt()
    private var labelSideMargin = context.resources.getDimension(org.fossify.commons.R.dimen.small_margin).toInt()
    private var roundedCornerRadius = context.resources.getDimension(org.fossify.commons.R.dimen.activity_margin)
    private var folderPadding = context.resources.getDimension(org.fossify.commons.R.dimen.medium_margin)
    private var pageIndicatorRadius = context.resources.getDimension(R.dimen.page_indicator_dot_radius)
    private var pageIndicatorMargin = context.resources.getDimension(R.dimen.page_indicator_margin)
    private var textPaint: TextPaint
    private var contrastTextPaint: TextPaint
    private var folderTitleTextPaint: TextPaint
    private var dragShadowCirclePaint: Paint
    private var emptyPageIndicatorPaint: Paint
    private var currentPageIndicatorPaint: Paint
    private var folderBackgroundPaint: Paint
    private var folderIconBackgroundPaint: Paint
    private var draggedItem: HomeScreenGridItem? = null
    private var resizedWidget: HomeScreenGridItem? = null
    private var isFirstDraw = true
    private var iconSize = 0

    private val pager = AnimatedGridPager(
        getMaxPage = ::getMaxPage,
        redrawGrid = ::redrawGrid,
        getWidth = { width },
        getHandler = { handler },
        getNextPageBound = { right - sideMargins.right - cellWidth / 2 },
        getPrevPageBound = { left + sideMargins.left + cellWidth / 2 },
        pageChangeStarted = {
            widgetViews.forEach { it.resetTouches() }
            closeFolder()
            accessibilityHelper.invalidateRoot()
        }
    )

    private var currentlyOpenFolder: HomeScreenFolder? = null
    private var draggingLeftFolderAt: Long? = null
    private var draggingEnteredNewFolderAt: Long? = null

    // apply fake margins at the home screen. Real ones would cause the icons be cut at dragging at screen sides
    var sideMargins = Rect()

    private var gridItems = ArrayList<HomeScreenGridItem>()
    private var gridCenters = ArrayList<Point>()
    private var draggedItemCurrentCoords = Pair(-1, -1)
    private var widgetViews = ArrayList<MyAppWidgetHostView>()

    val appWidgetHost = MyAppWidgetHost(context, WIDGET_HOST_ID)
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    private val accessibilityHelper = HomeScreenGridTouchHelper(this)
    var itemClickListener: ((HomeScreenGridItem) -> Unit)? = null
    var itemLongClickListener: ((HomeScreenGridItem) -> Unit)? = null


    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)

        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(org.fossify.commons.R.dimen.smaller_text_size)
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
        }

        contrastTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperTextColor()
            textSize = context.resources.getDimension(org.fossify.commons.R.dimen.smaller_text_size)
            setShadowLayer(2f, 0f, 0f, context.getProperTextColor().getContrastColor())
        }

        folderTitleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperTextColor()
            textSize = context.resources.getDimension(org.fossify.commons.R.dimen.medium_text_size)
        }

        dragShadowCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(org.fossify.commons.R.color.hint_white)
            strokeWidth = context.resources.getDimension(org.fossify.commons.R.dimen.small_margin)
            style = Paint.Style.STROKE
        }

        emptyPageIndicatorPaint = Paint(dragShadowCirclePaint).apply {
            strokeWidth = context.resources.getDimension(R.dimen.page_indicator_stroke_width)
        }
        currentPageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(android.R.color.white)
            style = Paint.Style.FILL
        }

        folderBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperBackgroundColor()
            style = Paint.Style.FILL
        }

        folderIconBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperBackgroundColor().adjustAlpha(0.9f)
            style = Paint.Style.FILL
        }

        val sideMargin = context.resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()
        sideMargins.apply {
            top = context.statusBarHeight
            bottom = context.navigationBarHeight
            left = sideMargin
            right = sideMargin
        }

        fetchGridItems()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = HomeScreenGridBinding.bind(this)
    }

    fun fetchGridItems() {
        ensureBackgroundThread {
            val providers = appWidgetManager.installedProviders
            gridItems = context.homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
            gridItems.toImmutableList().forEach { item ->
                if (item.type == ITEM_TYPE_ICON) {
                    item.drawable = context.getDrawableForPackageName(item.packageName)
                } else if (item.type == ITEM_TYPE_FOLDER) {
                    item.drawable = item.toFolder().generateDrawable()
                } else if (item.type == ITEM_TYPE_SHORTCUT) {
                    if (item.icon != null) {
                        item.drawable = BitmapDrawable(item.icon)
                    } else {
                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.deleteById(item.id!!)
                        }
                    }
                }

                item.providerInfo = providers.firstOrNull { it.provider.className == item.className }
            }

            redrawGrid()
        }
    }

    fun resizeGrid(newRowCount: Int, newColumnCount: Int) {
        if (columnCount != newColumnCount || rowCount != newRowCount) {
            rowCount = newRowCount
            columnCount = newColumnCount
            cells.clear()
            gridCenters.clear()
            iconMargin = (context.resources.getDimension(R.dimen.icon_side_margin) * 5 / columnCount).toInt()
            isFirstDraw = true
            gridItems.filter { it.type == ITEM_TYPE_WIDGET }.forEach {
                appWidgetHost.deleteAppWidgetId(it.widgetId)
            }
            widgetViews.forEach { removeView(it) }
            widgetViews.clear()
            redrawGrid()
        }
    }

    fun updateColors() {
        folderTitleTextPaint.color = context.getProperTextColor()
        contrastTextPaint.color = context.getProperTextColor()
        contrastTextPaint.setShadowLayer(2f, 0f, 0f, context.getProperTextColor().getContrastColor())
        folderBackgroundPaint.color = context.getProperBackgroundColor()
    }

    fun removeAppIcon(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            removeItemFromHomeScreen(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
            }

            gridItems.removeIf { it.id == item.id }
            if (pager.isOutsideOfPageRange()) {
                post {
                    prevPage()
                }
            }
            redrawGrid()
        }
    }

    fun removeWidget(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            removeItemFromHomeScreen(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
                widgetViews.removeIf { it.tag == item.widgetId }
            }
            gridItems.removeIf { it.id == item.id }
        }
    }

    private fun removeItemFromHomeScreen(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            if (item.id != null) {
                context.homeScreenGridItemsDB.deleteById(item.id!!)
                if (item.parentId != null) {
                    gridItems.filter { it.parentId == item.parentId && it.left > item.left && it.id != item.id }.forEach {
                        it.left -= 1
                    }

                    context.homeScreenGridItemsDB.shiftFolderItems(item.parentId!!, item.left, -1, item.id)
                }
            }

            if (item.type == ITEM_TYPE_WIDGET) {
                appWidgetHost.deleteAppWidgetId(item.widgetId)
            }

            if (item.page != 0 && gridItems.none { it.page == item.page && it.id != item.id && it.parentId == null }) {
                deletePage(item.page)
            }
        }
    }

    fun itemDraggingStarted(draggedGridItem: HomeScreenGridItem) {
        draggedItem = draggedGridItem

        if (draggedGridItem.type == ITEM_TYPE_WIDGET) {
            closeFolder()
        }

        if (draggedItem!!.drawable == null) {
            if (draggedItem?.type == ITEM_TYPE_FOLDER) {
                draggedItem!!.drawable = draggedGridItem.toFolder().generateDrawable()
            } else {
                draggedItem!!.drawable = context.getDrawableForPackageName(draggedGridItem.packageName)
            }
        }

        redrawGrid()
    }

    fun draggedItemMoved(x: Int, y: Int) {
        if (draggedItem == null) {
            return
        }

        currentlyOpenFolder?.also { folder ->
            if (folder.getDrawingRect().contains(x.toFloat(), y.toFloat())) {
                draggingLeftFolderAt = null
            } else {
                draggingLeftFolderAt.also {
                    if (it == null) {
                        draggingLeftFolderAt = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - it > FOLDER_CLOSE_HOLD_THRESHOLD) {
                        closeFolder()
                    }
                }
            }
        }

        if (draggedItemCurrentCoords.first == -1 && draggedItemCurrentCoords.second == -1 && draggedItem != null) {
            if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                val draggedWidgetView = widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }
                if (draggedWidgetView != null) {
                    draggedWidgetView.buildDrawingCache()
                    draggedItem!!.drawable = Bitmap.createBitmap(draggedWidgetView.drawingCache).toDrawable(context.resources)
                    draggedWidgetView.beGone()
                }
            }
        }

        draggedItemCurrentCoords = Pair(x, y)

        if (draggedItem?.type != ITEM_TYPE_FOLDER && draggedItem?.type != ITEM_TYPE_WIDGET) {
            val center = gridCenters.minBy {
                abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
            }
            val coveredCell = getClosestGridCells(center)
            if (coveredCell != null) {
                val coveredFolder = gridItems.firstOrNull { it.type == ITEM_TYPE_FOLDER && it.left == coveredCell.x && it.top == coveredCell.y }
                if (coveredFolder != null && coveredFolder.id != draggedItem?.id && currentlyOpenFolder == null) {
                    draggingEnteredNewFolderAt.also {
                        if (it == null) {
                            draggingEnteredNewFolderAt = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - it > FOLDER_OPEN_HOLD_THRESHOLD) {
                            if (coveredFolder.toFolder().getItems()
                                    .count() >= HomeScreenGridItem.FOLDER_MAX_CAPACITY && draggedItem?.parentId != coveredFolder.id
                            ) {
                                performHapticFeedback()
                                draggingEnteredNewFolderAt = null
                            } else {
                                openFolder(coveredFolder)
                            }
                        }
                    }
                } else {
                    draggingEnteredNewFolderAt = null
                }
            } else {
                draggingEnteredNewFolderAt = null
            }
        }

        pager.handleItemMovement(x, y)
        redrawGrid()
    }

    // figure out at which cell was the item dropped, if it is empty
    fun itemDraggingStopped() {
        widgetViews.forEach {
            it.hasLongPressed = false
        }

        if (draggedItem == null) {
            return
        }

        pager.itemMovementStopped()
        when (draggedItem!!.type) {
            ITEM_TYPE_FOLDER -> moveItem()
            ITEM_TYPE_ICON, ITEM_TYPE_SHORTCUT -> addAppIconOrShortcut()
            ITEM_TYPE_WIDGET -> addWidget()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun widgetLongPressed(item: HomeScreenGridItem) {
        resizedWidget = item
        redrawGrid()

        val widgetView = widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }
        binding.resizeFrame.beGone()
        if (widgetView != null) {
            val viewX = widgetView.x.toInt()
            val viewY = widgetView.y.toInt()
            val frameRect = Rect(viewX, viewY, viewX + widgetView.width, viewY + widgetView.height)
            val otherGridItems = gridItems.filterVisibleOnCurrentPageOnly()
                .filter { !it.outOfBounds() }
                .filter { it.widgetId != item.widgetId }.toMutableList() as ArrayList<HomeScreenGridItem>
            binding.resizeFrame.updateFrameCoords(frameRect, cellWidth, cellHeight, sideMargins, item, otherGridItems)
            binding.resizeFrame.beVisible()
            binding.resizeFrame.z = 1f     // make sure the frame isnt behind the widget itself
            binding.resizeFrame.onClickListener = {
                hideResizeLines()
            }

            binding.resizeFrame.onResizeListener = { cellsRect ->
                item.left = cellsRect.left
                item.top = cellsRect.top
                item.right = cellsRect.right
                item.bottom = if (cellsRect.bottom > rowCount - 2) {
                    rowCount - 2
                } else {
                    cellsRect.bottom
                }
                updateWidgetPositionAndSize(widgetView, item)

                val widgetTargetCells = ArrayList<Pair<Int, Int>>()
                for (xCell in item.left..item.right) {
                    for (yCell in item.top..item.bottom) {
                        widgetTargetCells.add(Pair(xCell, yCell))
                    }
                }

                gridItems.filterVisibleOnCurrentPageOnly().filter { it.id != item.id }.forEach { gridItem ->
                    for (xCell in gridItem.left..gridItem.right) {
                        for (yCell in gridItem.getDockAdjustedTop(rowCount)..gridItem.getDockAdjustedBottom(rowCount)) {
                            val cell = Pair(xCell, yCell)
                            val isAnyCellOccupied = widgetTargetCells.contains(cell)
                            if (isAnyCellOccupied) {
                                if (gridItem.type == ITEM_TYPE_WIDGET && gridItem.outOfBounds()) {
                                    removeWidget(gridItem)
                                }
                            }
                        }
                    }
                }

                ensureBackgroundThread {
                    context.homeScreenGridItemsDB.updateItemPosition(item.left, item.top, item.right, item.bottom, item.page, false, null, item.id!!)
                }
            }

            widgetView.ignoreTouches = true
            widgetView.setOnTouchListener { v, event ->
                binding.resizeFrame.onTouchEvent(event)
                return@setOnTouchListener true
            }
        }
    }

    fun hideResizeLines() {
        if (resizedWidget == null) {
            return
        }

        binding.resizeFrame.beGone()
        widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }?.apply {
            ignoreTouches = false
            setOnTouchListener(null)
        }
        resizedWidget = null
    }

    private fun moveItem() {
        val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }
        val center = gridCenters.minBy {
            abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
        }

        var redrawIcons = false
        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val xIndex = gridCells.x
            val yIndex = gridCells.y

            // check if the destination cell is empty
            var isDroppingPositionValid = true
            val wantedCell = Pair(xIndex, yIndex)
            // No moving folder into the dock
            if (draggedHomeGridItem?.type == ITEM_TYPE_FOLDER && yIndex == rowCount - 1) {
                isDroppingPositionValid = false
            } else {
                gridItems.filterVisibleOnCurrentPageOnly().forEach { item ->
                    for (xCell in item.left..item.right) {
                        for (yCell in item.getDockAdjustedTop(rowCount)..item.getDockAdjustedBottom(rowCount)) {
                            val cell = Pair(xCell, yCell)
                            val isAnyCellOccupied = wantedCell == cell
                            if (isAnyCellOccupied) {
                                if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                    removeWidget(item)
                                } else {
                                    isDroppingPositionValid = false
                                }
                                return@forEach
                            }
                        }
                    }
                }
            }

            if (isDroppingPositionValid) {
                draggedHomeGridItem?.apply {
                    val oldPage = page
                    left = xIndex
                    top = yIndex
                    right = xIndex
                    bottom = yIndex
                    page = pager.getCurrentPage()
                    docked = yIndex == rowCount - 1

                    ensureBackgroundThread {
                        context.homeScreenGridItemsDB.updateItemPosition(left, top, right, bottom, page, docked, parentId, id!!)

                        if (page != oldPage && oldPage != 0) {
                            if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                                deletePage(oldPage)
                            }
                        }
                    }
                }
                redrawIcons = true
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            redrawGrid()
        }
    }

    private fun addAppIconOrShortcut() {
        var isDroppingPositionValid: Boolean = false
        var potentialParent: HomeScreenGridItem? = null
        var xIndex: Int? = null
        var yIndex: Int? = null
        var redrawIcons = false

        val folder = currentlyOpenFolder
        if (folder != null && folder.getItemsDrawingRect().contains(
                (draggedItemCurrentCoords.first).toFloat(),
                (draggedItemCurrentCoords.second).toFloat()
            )
        ) {
            val center = folder.getItemsGridCenters().minBy {
                abs(it.second - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.third - draggedItemCurrentCoords.second + sideMargins.top)
            }
            isDroppingPositionValid = true
            potentialParent = folder.item
            xIndex = center.first
            yIndex = 0
            redrawIcons = true
        } else {
            val center = gridCenters.minBy {
                Math.abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
            }

            val gridCells = getClosestGridCells(center)
            if (gridCells != null) {
                xIndex = gridCells.x
                yIndex = gridCells.y

                // check if the destination cell is empty or a folder
                isDroppingPositionValid = true
                val wantedCell = Pair(xIndex, yIndex)
                gridItems.filterVisibleOnCurrentPageOnly().filter { it.id != draggedItem?.id }.forEach { item ->
                    for (xCell in item.left..item.right) {
                        for (yCell in item.getDockAdjustedTop(rowCount)..item.getDockAdjustedBottom(rowCount)) {
                            val cell = Pair(xCell, yCell)
                            val isAnyCellOccupied = wantedCell == cell
                            if (isAnyCellOccupied) {
                                if (item.type != ITEM_TYPE_WIDGET && !item.docked) {
                                    potentialParent = item
                                } else {
                                    if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                        removeWidget(item)
                                    } else {
                                        isDroppingPositionValid = false
                                    }
                                }
                                return@forEach
                            }
                        }
                    }
                }
            }
        }

        if (isDroppingPositionValid) {
            val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }

            if (potentialParent != null) {
                if (potentialParent?.type == ITEM_TYPE_FOLDER) {
                    addAppIconOrShortcut(
                        draggedHomeGridItem,
                        xIndex!!,
                        yIndex!!,
                        potentialParent?.id,
                        toFolderEnd = potentialParent != currentlyOpenFolder?.item
                    )
                } else {
                    val parentItem = potentialParent!!.copy(
                        type = ITEM_TYPE_FOLDER,
                        id = null,
                        title = resources.getString(org.fossify.commons.R.string.folder)
                    )
                    ensureBackgroundThread {
                        val newId = context.homeScreenGridItemsDB.insert(parentItem)
                        parentItem.id = newId
                        potentialParent?.apply {
                            parentId = newId
                            left = 0
                            context.homeScreenGridItemsDB.updateItemPosition(left, top, right, bottom, page, docked, newId, id!!)
                        }
                        (context as? MainActivity)?.runOnUiThread {
                            gridItems.add(parentItem)
                            addAppIconOrShortcut(draggedHomeGridItem, xIndex!!, yIndex!!, newId)
                        }
                    }
                }
                return
            } else {
                addAppIconOrShortcut(draggedHomeGridItem, xIndex!!, yIndex!!)
                return
            }
        } else {
            performHapticFeedback()
            redrawIcons = true
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            redrawGrid()
        }
    }

    private fun addAppIconOrShortcut(
        draggedHomeGridItem: HomeScreenGridItem?,
        xIndex: Int,
        yIndex: Int,
        newParentId: Long? = null,
        toFolderEnd: Boolean = true
    ) {
        if (newParentId != null && newParentId != draggedHomeGridItem?.parentId) {
            gridItems.firstOrNull { it.id == newParentId }?.also {
                if (it.toFolder().getItems().count() >= HomeScreenGridItem.FOLDER_MAX_CAPACITY) {
                    performHapticFeedback()
                    draggedItem = null
                    draggedItemCurrentCoords = Pair(-1, -1)
                    redrawGrid()
                    return
                }
            }
        }

        val finalXIndex = if (newParentId != null) {
            if (toFolderEnd) {
                gridItems.firstOrNull { it.id == newParentId }?.toFolder()?.getItems()?.maxOf { it.left + 1 } ?: 0
            } else {
                min(xIndex, gridItems.firstOrNull { it.id == newParentId }?.toFolder()?.getItems()?.maxOf {
                    if (draggedHomeGridItem?.parentId == newParentId) {
                        it.left
                    } else {
                        it.left + 1
                    }
                } ?: 0)
            }
        } else {
            xIndex
        }
        // we are moving an existing home screen item from one place to another
        if (draggedHomeGridItem != null) {
            draggedHomeGridItem.apply {
                val oldParentId = parentId
                val oldLeft = left
                val oldPage = page
                left = finalXIndex
                top = yIndex
                right = finalXIndex
                bottom = yIndex
                page = pager.getCurrentPage()
                docked = yIndex == rowCount - 1
                parentId = newParentId

                val oldParent = gridItems.firstOrNull { it.id == oldParentId }
                val deleteOldParent = if (oldParent?.toFolder()?.getItems()?.isEmpty() == true) {
                    gridItems.remove(oldParent)
                    true
                } else {
                    false
                }

                ensureBackgroundThread {
                    context.homeScreenGridItemsDB.updateItemPosition(left, top, right, bottom, page, docked, newParentId, id!!)
                    if (deleteOldParent && oldParentId != null) {
                        context.homeScreenGridItemsDB.deleteById(oldParentId)
                    } else if (oldParentId != null && gridItems.none { it.parentId == oldParentId && it.left == oldLeft }) {
                        gridItems.filter { it.parentId == oldParentId && it.left > oldLeft && it.id != id }.forEach {
                            it.left -= 1
                        }
                        context.homeScreenGridItemsDB.shiftFolderItems(oldParentId, oldLeft, -1, id)
                    }

                    if (newParentId != null && gridItems.any { it.parentId == newParentId && it.left == left } && (newParentId != oldParentId || left != oldLeft)) {
                        gridItems.filter { it.parentId == newParentId && it.left >= left && it.id != id }.forEach {
                            it.left += 1
                        }

                        context.homeScreenGridItemsDB.shiftFolderItems(newParentId, left - 1, +1, id)
                    }

                    if (page != oldPage && oldPage != 0) {
                        if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                            deletePage(oldPage)
                        }
                    }
                }
            }
        } else if (draggedItem != null) {
            // we are dragging a new item at the home screen from the All Apps fragment
            val newHomeScreenGridItem = HomeScreenGridItem(
                null,
                finalXIndex,
                yIndex,
                finalXIndex,
                yIndex,
                pager.getCurrentPage(),
                draggedItem!!.packageName,
                draggedItem!!.activityName,
                draggedItem!!.title,
                draggedItem!!.type,
                "",
                -1,
                "",
                draggedItem!!.icon,
                yIndex == rowCount - 1,
                newParentId,
                draggedItem!!.drawable,
                draggedItem!!.providerInfo,
                draggedItem!!.activityInfo
            )

            fun finalizeFolderOrder(newItem: HomeScreenGridItem) {
                if (newParentId != null && gridItems.any { it.parentId == newParentId && it.left == newItem.left }) {
                    gridItems.filter { it.parentId == newParentId && it.left >= newItem.left && it.id != newItem.id }.forEach {
                        it.left += 1
                    }

                    context.homeScreenGridItemsDB.shiftFolderItems(newParentId, newItem.left - 1, +1, newItem.id)
                }
            }

            if (newHomeScreenGridItem.type == ITEM_TYPE_ICON) {
                ensureBackgroundThread {
                    storeAndShowGridItem(newHomeScreenGridItem)
                    finalizeFolderOrder(newHomeScreenGridItem)
                }
            } else if (newHomeScreenGridItem.type == ITEM_TYPE_SHORTCUT) {
                (context as? MainActivity)?.handleShorcutCreation(newHomeScreenGridItem.activityInfo!!) { shortcutId, label, icon ->
                    ensureBackgroundThread {
                        newHomeScreenGridItem.shortcutId = shortcutId
                        newHomeScreenGridItem.title = label
                        newHomeScreenGridItem.icon = icon.toBitmap()
                        newHomeScreenGridItem.drawable = icon
                        storeAndShowGridItem(newHomeScreenGridItem)
                        finalizeFolderOrder(newHomeScreenGridItem)
                    }
                }
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        redrawGrid()
    }

    fun storeAndShowGridItem(item: HomeScreenGridItem) {
        val newId = context.homeScreenGridItemsDB.insert(item)
        item.id = newId
        gridItems.add(item)
        redrawGrid()
    }

    private fun addWidget() {
        val center = gridCenters.minBy {
            Math.abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
        }

        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val widgetRect = getWidgetOccupiedRect(gridCells)
            val widgetTargetCells = ArrayList<Pair<Int, Int>>()
            for (xCell in widgetRect.left..widgetRect.right) {
                for (yCell in widgetRect.top..widgetRect.bottom) {
                    widgetTargetCells.add(Pair(xCell, yCell))
                }
            }

            var areAllCellsEmpty = true
            gridItems.filterVisibleOnCurrentPageOnly().filter { it.id != draggedItem?.id }.forEach { item ->
                for (xCell in item.left..item.right) {
                    for (yCell in item.getDockAdjustedTop(rowCount)..item.getDockAdjustedBottom(rowCount)) {
                        val cell = Pair(xCell, yCell)
                        val isAnyCellOccupied = widgetTargetCells.contains(cell)
                        if (isAnyCellOccupied) {
                            if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                removeWidget(item)
                            } else {
                                areAllCellsEmpty = false
                                return@forEach
                            }
                        }
                    }
                }
            }

            if (areAllCellsEmpty) {
                val widgetItem = draggedItem!!.copy()
                val oldPage = widgetItem.page
                widgetItem.apply {
                    left = widgetRect.left
                    top = widgetRect.top
                    right = widgetRect.right
                    bottom = widgetRect.bottom
                    page = pager.getCurrentPage()
                }

                ensureBackgroundThread {
                    // store the new widget at creating it, else just move the existing one
                    if (widgetItem.id == null) {
                        val itemId = context.homeScreenGridItemsDB.insert(widgetItem)
                        widgetItem.id = itemId
                        post {
                            bindWidget(widgetItem)
                        }
                    } else {
                        context.homeScreenGridItemsDB.updateItemPosition(
                            widgetItem.left,
                            widgetItem.top,
                            widgetItem.right,
                            widgetItem.bottom,
                            pager.getCurrentPage(),
                            false,
                            null,
                            widgetItem.id!!
                        )
                        val widgetView = widgetViews.firstOrNull { it.tag == widgetItem.widgetId }
                        if (widgetView != null && !widgetItem.outOfBounds()) {
                            post {
                                val widgetPos = calculateWidgetPos(widgetItem.getTopLeft(rowCount))
                                widgetView.x = widgetPos.x.toFloat()
                                widgetView.y = widgetPos.y.toFloat()
                                widgetView.beVisible()
                            }
                        }

                        gridItems.firstOrNull { it.id == widgetItem.id }?.apply {
                            left = widgetItem.left
                            right = widgetItem.right
                            top = widgetItem.top
                            bottom = widgetItem.bottom
                            page = pager.getCurrentPage()
                        }


                        if (widgetItem.page != oldPage && oldPage != 0) {
                            if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                                deletePage(oldPage)
                            }
                        }
                    }
                }
            } else {
                performHapticFeedback()
                widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }?.apply {
                    post {
                        beVisible()
                    }
                }
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        redrawGrid()
    }

    private fun bindWidget(item: HomeScreenGridItem) {
        if (item.outOfBounds()) {
            return
        }

        val activity = context as MainActivity
        val appWidgetProviderInfo = item.providerInfo ?: appWidgetManager!!.installedProviders.firstOrNull { it.provider.className == item.className }
        if (appWidgetProviderInfo != null) {
            item.widgetId = appWidgetHost.allocateAppWidgetId()
            ensureBackgroundThread {
                context.homeScreenGridItemsDB.updateWidgetId(item.widgetId, item.id!!)
            }

            activity.handleWidgetBinding(appWidgetManager, item.widgetId, appWidgetProviderInfo) { canBind ->
                if (canBind) {
                    if (appWidgetProviderInfo.configure != null) {
                        activity.handleWidgetConfigureScreen(appWidgetHost, item.widgetId) { success ->
                            if (success) {
                                placeAppWidget(appWidgetProviderInfo, item)
                            } else {
                                removeItemFromHomeScreen(item)
                            }
                        }
                    } else {
                        placeAppWidget(appWidgetProviderInfo, item)
                    }
                } else {
                    removeItemFromHomeScreen(item)
                }

                if (pager.isOutsideOfPageRange()) {
                    prevPage(redraw = true)
                }
            }
        }
    }

    private fun placeAppWidget(appWidgetProviderInfo: AppWidgetProviderInfo, item: HomeScreenGridItem) {
        // we have to pass the base context here, else there will be errors with the themes
        val widgetView = appWidgetHost.createView((context as MainActivity).baseContext, item.widgetId, appWidgetProviderInfo) as MyAppWidgetHostView
        widgetView.tag = item.widgetId
        widgetView.setAppWidget(item.widgetId, appWidgetProviderInfo)
        widgetView.longPressListener = { x, y ->
            val activity = context as? MainActivity
            if (activity?.isAllAppsFragmentExpanded() == false) {
                activity.showHomeIconMenu(x, widgetView.y, item, false)
                performHapticFeedback()
            }
        }

        widgetView.onIgnoreInterceptedListener = {
            hideResizeLines()
        }

        val widgetSize = updateWidgetPositionAndSize(widgetView, item)
        addView(widgetView, widgetSize.width, widgetSize.height)
        widgetViews.add(widgetView)

        // remove the drawable so that it gets refreshed on long press
        item.drawable = null
        // Delete existing widget if it has already been loaded to the list
        gridItems.removeIf { it.id == item.id }
        gridItems.add(item)
    }

    private fun updateWidgetPositionAndSize(widgetView: AppWidgetHostView, item: HomeScreenGridItem): Size {
        val currentViewPosition = pager.getCurrentViewPositionInFullPageSpace() * width.toFloat()
        val widgetPos = calculateWidgetPos(item.getTopLeft(rowCount))
        widgetView.x = widgetPos.x + width * item.page - currentViewPosition
        widgetView.y = widgetPos.y.toFloat()
        val widgetWidth = item.getWidthInCells() * cellWidth
        val widgetHeight = item.getHeightInCells() * cellHeight

        val density = context.resources.displayMetrics.density
        val widgetDpWidth = (widgetWidth / density).toInt()
        val widgetDpHeight = (widgetHeight / density).toInt()

        if (isSPlus()) {
            val sizes = listOf(SizeF(widgetDpWidth.toFloat(), widgetDpHeight.toFloat()))
            widgetView.updateAppWidgetSize(Bundle(), sizes)
        } else {
            widgetView.updateAppWidgetSize(Bundle(), widgetDpWidth, widgetDpHeight, widgetDpWidth, widgetDpHeight)
        }

        widgetView.layoutParams?.width = widgetWidth
        widgetView.layoutParams?.height = widgetHeight
        return Size(widgetWidth, widgetHeight)
    }

    private fun calculateWidgetPos(topLeft: Point): Point {
        val cell = cells[topLeft]!!
        return Point(
            cell.left + sideMargins.left,
            cell.top + sideMargins.top
        )
    }

    // convert stuff like 102x192 to grid cells like 0x1
    private fun getClosestGridCells(center: Point): Point? {
        return cells.entries.firstOrNull { (_, cell) -> center.x == cell.centerX() && center.y == cell.centerY() }?.key
    }

    private fun redrawGrid() {
        post {
            setWillNotDraw(false)
            invalidate()
            binding.drawingArea.invalidate()
        }
    }

    private fun getFakeWidth() = width - sideMargins.left - sideMargins.right

    private fun getFakeHeight() = height - sideMargins.top - sideMargins.bottom

    fun drawInto(canvas: Canvas) {
        if (cells.isEmpty()) {
            fillCellSizes()
        }

        val currentXFactor = pager.getXFactorForCurrentPage()
        val lastXFactor = pager.getXFactorForLastPage()

        fun handleMainGridItemDrawing(item: HomeScreenGridItem, xFactor: Float) {
            val offsetX = sideMargins.left + (this@HomeScreenGrid.width * xFactor).toInt()
            val offsetY = sideMargins.top
            cells[item.getTopLeft(rowCount)]!!.withOffset(offsetX, offsetY) {
                canvas.drawItemInCell(item, this)
            }
        }

        gridItems.filter { it.isSingleCellType() && pager.isItemOnCurrentPage(it) && !it.docked && it.parentId == null }
            .forEach { item ->
                if (item.outOfBounds()) {
                    return@forEach
                }

                handleMainGridItemDrawing(item, currentXFactor)
            }
        gridItems.filter { it.isSingleCellType() && it.docked && it.parentId == null }
            .forEach { item ->
                if (item.outOfBounds()) {
                    return@forEach
                }

                handleMainGridItemDrawing(item, 0f)
            }
        if (pager.isAnimatingPageChange()) {
            gridItems.filter { it.isSingleCellType() && pager.isItemOnLastPage(it) && !it.docked && it.parentId == null }
                .forEach { item ->
                    if (item.outOfBounds()) {
                        return@forEach
                    }

                    handleMainGridItemDrawing(item, lastXFactor)
                }
        }

        if (pager.isSwiped()) {
            gridItems.filter {
                it.isSingleCellType()
                    && pager.isItemInSwipeRange(it)
                    && !it.docked
                    && it.parentId == null
            }.forEach { item ->
                if (item.outOfBounds()) {
                    return@forEach
                }

                handleMainGridItemDrawing(item, lastXFactor)
            }
        }

        if (isFirstDraw) {
            gridItems.filter { it.type == ITEM_TYPE_WIDGET && !it.outOfBounds() }.forEach { item ->
                val appWidgetProviderInfo = item.providerInfo ?: appWidgetManager!!.installedProviders.firstOrNull { it.provider.className == item.className }
                placeAppWidget(appWidgetProviderInfo!!, item)
            }
        } else {
            gridItems.filter { it.type == ITEM_TYPE_WIDGET && !it.outOfBounds() }.forEach { item ->
                widgetViews.firstOrNull { it.tag == item.widgetId }?.also {
                    updateWidgetPositionAndSize(it, item)
                }
            }
        }

        // Only draw page indicators when there is a need for it
        if (pager.shouldDisplayPageChangeIndicator()) {
            val pageCount = pager.getPageCount()
            val pageIndicatorsRequiredWidth = pageCount * pageIndicatorRadius * 2 + pageCount * (pageIndicatorMargin - 1)
            val usableWidth = getFakeWidth()
            val pageIndicatorsStart = (usableWidth - pageIndicatorsRequiredWidth) / 2 + sideMargins.left
            var currentPageIndicatorLeft = pageIndicatorsStart
            val pageIndicatorY = pageIndicatorsYPos.toFloat() + sideMargins.top + iconMargin
            val pageIndicatorStep = pageIndicatorRadius * 2 + pageIndicatorMargin
            emptyPageIndicatorPaint.alpha = pager.getPageChangeIndicatorsAlpha()
            // Draw empty page indicators
            for (page in 0 until pageCount) {
                canvas.drawCircle(currentPageIndicatorLeft + pageIndicatorRadius, pageIndicatorY, pageIndicatorRadius, emptyPageIndicatorPaint)
                currentPageIndicatorLeft += pageIndicatorStep
            }

            // Draw current page indicator on exact position
            val currentIndicatorPosition = pageIndicatorsStart + pager.getCurrentViewPositionInFullPageSpace() * pageIndicatorStep
            currentPageIndicatorPaint.alpha = pager.getPageChangeIndicatorsAlpha()
            canvas.drawCircle(currentIndicatorPosition + pageIndicatorRadius, pageIndicatorY, pageIndicatorRadius, currentPageIndicatorPaint)
        }

        val folder = currentlyOpenFolder
        if (folder != null && folder.getItems().isNotEmpty()) {
            val items = folder.getItems()
            val folderRect = folder.getDrawingRect()

            val currentViewPosition = pager.getCurrentViewPositionInFullPageSpace() * width.toFloat()
            val rectOffset = width * folder.item.page - currentViewPosition
            folderRect.offset(rectOffset, 0f)

            canvas.withScale(folder.scale, folder.scale, folderRect.centerX(), folderRect.centerY()) {
                canvas.drawRoundRect(folderRect, roundedCornerRadius / folder.scale, roundedCornerRadius / folder.scale, folderBackgroundPaint)
                val textX = folderRect.left + folderPadding
                val textY = folderRect.top + folderPadding
                val staticLayout = StaticLayout.Builder
                    .obtain(
                        folder.item.title,
                        0,
                        folder.item.title.length,
                        folderTitleTextPaint,
                        (folderRect.width() - 2 * folderPadding * folder.scale).toInt()
                    )
                    .setMaxLines(1)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()

                withTranslation(textX, textY) {
                    staticLayout.draw(canvas)
                }

                items.forEach { item ->
                    val itemRect = folder.getItemRect(item)
                    canvas.drawItemInCell(item, itemRect)
                }
            }
        }

        if (draggedItem != null && draggedItemCurrentCoords.first != -1 && draggedItemCurrentCoords.second != -1) {
            if (draggedItem!!.isSingleCellType()) {
                if (folder != null && folder.getItemsDrawingRect().contains(
                        (draggedItemCurrentCoords.first).toFloat(),
                        (draggedItemCurrentCoords.second).toFloat()
                    )
                ) {

                    val center = folder.getItemsGridCenters().minBy {
                        abs(it.second - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.third - draggedItemCurrentCoords.second + sideMargins.top)
                    }
                    val cellSize = folder.getCellSize()

                    val shadowX = center.second - cellSize / 2 + iconMargin + iconSize / 2f
                    val shadowY = center.third - cellSize / 2 + iconMargin + iconSize / 2

                    canvas.drawCircle(shadowX, shadowY.toFloat(), iconSize / 2f, dragShadowCirclePaint)
                } else {
                    // draw a circle under the current cell
                    val center = gridCenters.minBy {
                        abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
                    }

                    val gridCells = getClosestGridCells(center)
                    if (gridCells != null) {
                        val cell = cells[gridCells]!!
                        val shadowX = cell.left + iconMargin + iconSize / 2f + sideMargins.left
                        val shadowY = if (gridCells.y == rowCount - 1) {
                            cellHeight - iconMargin - iconSize / 2f
                        } else {
                            iconMargin + iconSize / 2f
                        } + sideMargins.top + cell.top

                        canvas.drawCircle(shadowX, shadowY, iconSize / 2f, dragShadowCirclePaint)
                    }
                }

                // show the app icon itself at dragging, move it above the finger a bit to make it visible
                val drawableX = (draggedItemCurrentCoords.first - iconSize / 1.5f).toInt()
                val drawableY = (draggedItemCurrentCoords.second - iconSize / 1.2f).toInt()
                val newDrawable = if (draggedItem?.type == ITEM_TYPE_FOLDER) {
                    draggedItem!!.toFolder().generateDrawable()
                } else {
                    draggedItem!!.drawable?.constantState?.newDrawable()?.mutate()
                }
                newDrawable?.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                newDrawable?.draw(canvas)
            } else if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                // at first draw we are loading the widget from the database at some exact spot, not dragging it
                if (!isFirstDraw) {
                    val center = gridCenters.minBy {
                        Math.abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
                    }

                    val gridCells = getClosestGridCells(center)
                    if (gridCells != null) {
                        val widgetRect = getWidgetOccupiedRect(gridCells)
                        val widgetPos = calculateWidgetPos(Point(widgetRect.left, widgetRect.top))
                        val leftSide = widgetPos.x.toFloat()
                        val topSide = widgetPos.y.toFloat()
                        val rightSide = leftSide + draggedItem!!.getWidthInCells() * cellWidth
                        val bottomSide = topSide + draggedItem!!.getHeightInCells() * cellHeight
                        canvas.drawRoundRect(leftSide, topSide, rightSide, bottomSide, roundedCornerRadius, roundedCornerRadius, dragShadowCirclePaint)
                    }

                    // show the widget preview itself at dragging
                    draggedItem!!.drawable?.also { drawable ->
                        val aspectRatio = drawable.minimumHeight / drawable.minimumWidth.toFloat()
                        val drawableX = (draggedItemCurrentCoords.first - drawable.minimumWidth / 2f).toInt()
                        val drawableY = (draggedItemCurrentCoords.second - drawable.minimumHeight / 3f).toInt()
                        val drawableWidth = draggedItem!!.getWidthInCells() * cellWidth - iconMargin * (draggedItem!!.getWidthInCells() - 1)
                        drawable.setBounds(
                            drawableX,
                            drawableY,
                            drawableX + drawableWidth,
                            (drawableY + drawableWidth * aspectRatio).toInt()
                        )
                        drawable.draw(canvas)
                    }
                }
            }
        }

        isFirstDraw = false
    }

    private fun fillCellSizes() {
        cellWidth = getFakeWidth() / columnCount
        cellHeight = getFakeHeight() / rowCount
        val extraXMargin = if (cellWidth > cellHeight) {
            (cellWidth - cellHeight) / 2
        } else {
            0
        }
        val extraYMargin = if (cellHeight > cellWidth) {
            (cellHeight - cellWidth) / 2
        } else {
            0
        }
        iconSize = min(cellWidth, cellHeight) - 2 * iconMargin
        pageIndicatorsYPos = (rowCount - 1) * cellHeight + extraYMargin
        for (i in 0 until columnCount) {
            for (j in 0 until rowCount) {
                val yMarginToAdd = if (j == rowCount - 1) 0 else extraYMargin
                val rect = Rect(
                    i * cellWidth + extraXMargin,
                    j * cellHeight + yMarginToAdd,
                    (i + 1) * cellWidth - extraXMargin,
                    (j + 1) * cellHeight - yMarginToAdd,
                )
                cells[Point(i, j)] = rect
                gridCenters.add(Point(rect.centerX(), rect.centerY()))
                if (j == rowCount - 1) {
                    dockCellY = j * cellHeight
                }
            }
        }
    }

    fun fragmentExpanded() {
        widgetViews.forEach {
            it.ignoreTouches = true
        }
        closeFolder(true)
    }

    fun fragmentCollapsed() {
        widgetViews.forEach {
            it.ignoreTouches = false
        }
    }

    // get the clickable area around the icon, it includes text too
    fun getClickableRect(item: HomeScreenGridItem): Rect {
        if (cells.isEmpty()) {
            fillCellSizes()
        }

        val folder = currentlyOpenFolder
        val clickableLeft: Int
        val clickableTop: Int
        if (folder != null && item.parentId == folder.item.id) {
            val itemRect = folder.getItemRect(item)
            clickableLeft = itemRect.left
            clickableTop = itemRect.top - iconMargin
        } else {
            val cell = cells[item.getTopLeft(rowCount)]!!
            clickableLeft = cell.left + sideMargins.left
            clickableTop = if (item.docked) {
                dockCellY + cellHeight - iconSize - iconMargin
            } else {
                cell.top - iconMargin
            } + sideMargins.top
        }

        return Rect(clickableLeft, clickableTop, clickableLeft + iconSize + 2 * iconMargin, clickableTop + iconSize + 2 * iconMargin)
    }

    // drag the center of the widget, not the top left corner
    private fun getWidgetOccupiedRect(item: Point): Rect {
        val left = item.x - floor((draggedItem!!.getWidthInCells() - 1) / 2.0).toInt()
        val rect = Rect(left, item.y, left + draggedItem!!.getWidthInCells() - 1, item.y + draggedItem!!.getHeightInCells() - 1)
        if (rect.left < 0) {
            rect.right -= rect.left
            rect.left = 0
        } else if (rect.right > columnCount - 1) {
            val diff = rect.right - columnCount + 1
            rect.right -= diff
            rect.left -= diff
        }

        if (rect.top < 0) {
            rect.bottom -= rect.top
            rect.top = 0
        } else if (rect.bottom > rowCount - 2) {
            val diff = rect.bottom - rowCount + 2
            rect.bottom -= diff
            rect.top -= diff
        }

        return rect
    }

    fun isClickingGridItem(x: Int, y: Int): HomeScreenGridItem? {
        if (pager.isAnimatingPageChange() || pager.isSwiped()) {
            return null
        }

        currentlyOpenFolder?.also { folder ->
            folder.getItems().forEach { gridItem ->
                val rect = getClickableRect(gridItem)
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return gridItem
                }
            }
        }

        // if a folder is open, we only want to allow clicks on items in the folder
        if (currentlyOpenFolder != null) {
            return null
        }

        for (gridItem in gridItems.filterVisibleOnCurrentPageOnly()) {
            if (gridItem.outOfBounds()) {
                continue
            }

            if (gridItem.isSingleCellType()) {
                val rect = getClickableRect(gridItem)
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return gridItem
                }
            } else if (gridItem.type == ITEM_TYPE_WIDGET) {
                val widgetPos = calculateWidgetPos(gridItem.getTopLeft(rowCount))
                val left = widgetPos.x.toFloat()
                val top = widgetPos.y.toFloat()
                val right = left + gridItem.getWidthInCells() * cellWidth
                val bottom = top + gridItem.getHeightInCells() * cellHeight

                if (x >= left && x <= right && y >= top && y <= bottom) {
                    return gridItem
                }
            }
        }

        return null
    }

    fun intoViewSpaceCoords(screenSpaceX: Float, screenSpaceY: Float): Pair<Float, Float> {
        val viewLocation = IntArray(2)
        getLocationOnScreen(viewLocation)
        val x = screenSpaceX - viewLocation[0]
        val y = screenSpaceY - viewLocation[1]
        return Pair(x, y)
    }

    private fun HomeScreenGridItem.outOfBounds(): Boolean {
        return (left >= columnCount
            || right >= columnCount
            || (!docked && (top >= rowCount - 1 || bottom >= rowCount - 1))
            || (type == ITEM_TYPE_WIDGET && (bottom - top > rowCount - 1 || right - left > columnCount - 1))
            )
    }

    private inner class HomeScreenGridTouchHelper(host: View) : ExploreByTouchHelper(host) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val item = isClickingGridItem(x.toInt(), y.toInt())

            return if (item != null) {
                item.id?.toInt() ?: INVALID_ID
            } else {
                INVALID_ID
            }
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>?) {
            val sorted =
                gridItems.filter { it.visibleOnCurrentPage() || (currentlyOpenFolder != null && it.parentId == currentlyOpenFolder?.item?.id) }.sortedBy {
                    (if (it.parentId == null) it.getDockAdjustedTop(rowCount) else 1) * 100 + it.left
                }
            sorted.forEachIndexed { index, homeScreenGridItem ->
                virtualViewIds?.add(index, homeScreenGridItem.id?.toInt() ?: index)
            }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val viewLocation = IntArray(2)
            getLocationOnScreen(viewLocation)

            // home screen
            if (virtualViewId == -1) {
                node.text = context.getString(R.string.app_name)
                val viewBounds = Rect(left, top, right, bottom)
                val onScreenBounds = Rect(viewBounds)
                onScreenBounds.offset(viewLocation[0], viewLocation[1])
                node.setBoundsInScreen(onScreenBounds)
                node.setBoundsInParent(viewBounds)

                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
                node.setParent(this@HomeScreenGrid)
            }

            val item = gridItems.firstOrNull { it.id?.toInt() == virtualViewId } ?: throw IllegalArgumentException("Unknown id")

            node.text = if (item.type == ITEM_TYPE_WIDGET) {
                item.providerInfo?.loadLabel(context.packageManager) ?: item.title
            } else {
                item.title
            }

            val viewBounds = if (item == currentlyOpenFolder?.item) {
                currentlyOpenFolder?.getDrawingRect()?.toRect()
            } else if (item.type == ITEM_TYPE_WIDGET) {
                val widgetPos = calculateWidgetPos(item.getTopLeft(rowCount))
                val left = widgetPos.x
                val top = widgetPos.y
                val right = left + item.getWidthInCells() * cellWidth
                val bottom = top + item.getHeightInCells() * cellHeight

                Rect(left, top, right, bottom)
            } else {
                getClickableRect(item)
            }
            val onScreenBounds = Rect(viewBounds)
            onScreenBounds.offset(viewLocation[0], viewLocation[1])
            node.setBoundsInScreen(onScreenBounds)
            node.setBoundsInParent(viewBounds)

            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
            node.setParent(this@HomeScreenGrid)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val item = gridItems.firstOrNull { it.id?.toInt() == virtualViewId } ?: throw IllegalArgumentException("Unknown id")
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> itemClickListener?.apply {
                    if (item == currentlyOpenFolder?.item) {
                        closeFolder(true)
                    } else {
                        invoke(item)
                    }
                    return true
                }

                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> itemLongClickListener?.apply {
                    invoke(item)
                    return true
                }
            }

            return false
        }
    }

    private fun deletePage(page: Int) {
        gridItems.filter { it.page > page }.forEach {
            it.page -= 1
        }
        context.homeScreenGridItemsDB.shiftPage(page, -1)

        if (pager.isOutsideOfPageRange()) {
            post {
                prevPage()
            }
        }
    }

    private fun getMaxPage() = gridItems.filter { !it.docked && !it.outOfBounds() }.maxOfOrNull { it.page } ?: 0

    fun nextPage(redraw: Boolean = false): Boolean {
        return pager.nextPage(redraw)
    }

    fun prevPage(redraw: Boolean = false): Boolean {
        return pager.prevPage(redraw)
    }

    fun skipToPage(targetPage: Int): Boolean {
        return pager.skipToPage(targetPage)
    }

    fun getCurrentIconSize(): Int = iconSize


    fun setSwipeMovement(diffX: Float) {
        if (draggedItem == null) {
            pager.setSwipeMovement(diffX)
        }
    }

    fun finalizeSwipe() {
        pager.finalizeSwipe()
    }

    fun openFolder(folder: HomeScreenGridItem) {
        if (currentlyOpenFolder == null) {
            currentlyOpenFolder = folder.toFolder(animateOpening = true)
            redrawGrid()
        } else if (currentlyOpenFolder?.item?.id != folder.id) {
            closeFolder()
        }
        accessibilityHelper.invalidateRoot()
    }

    fun closeFolder(redraw: Boolean = false) {
        currentlyOpenFolder?.animateClosing {
            currentlyOpenFolder = null
            if (redraw) {
                redrawGrid()
            }
            accessibilityHelper.invalidateRoot()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return if (currentlyOpenFolder != null) {
            true
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }

    private fun Canvas.drawItemInCell(item: HomeScreenGridItem, cell: Rect) {
        if (item.id != draggedItem?.id) {
            val drawableX = cell.left + iconMargin

            val drawable = if (item.type == ITEM_TYPE_FOLDER) {
                item.toFolder().generateDrawable()
            } else {
                item.drawable?.constantState?.newDrawable()?.mutate()
            }

            if (item.docked) {
                val drawableY = dockCellY + cellHeight - iconMargin - iconSize + sideMargins.top

                drawable?.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
            } else {
                val drawableY = cell.top + iconMargin
                drawable?.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)

                if (item.id != draggedItem?.id && item.title.isNotEmpty()) {
                    val textX = cell.left.toFloat() + labelSideMargin
                    val textY = cell.top.toFloat() + iconSize + iconMargin + labelSideMargin
                    val textPaintToUse = if (item.parentId == null) {
                        textPaint
                    } else {
                        contrastTextPaint
                    }
                    val staticLayout = StaticLayout.Builder
                        .obtain(item.title, 0, item.title.length, textPaintToUse, cellWidth - 2 * labelSideMargin)
                        .setMaxLines(2)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .build()

                    withTranslation(textX, textY) {
                        staticLayout.draw(this)
                    }
                }
            }

            drawable?.draw(this)
        }
    }

    private fun Rect.withOffset(offsetX: Int, offsetY: Int, block: Rect.() -> Unit) {
        offset(offsetX, offsetY)
        try {
            block()
        } finally {
            offset(-offsetX, -offsetY)
        }
    }

    private fun ArrayList<HomeScreenGridItem>.filterVisibleOnCurrentPageOnly() = filter { it.visibleOnCurrentPage() }

    private fun HomeScreenGridItem.visibleOnCurrentPage() = (pager.isItemOnCurrentPage(this) || docked) && parentId == null

    private fun HomeScreenGridItem.isSingleCellType() = (type == ITEM_TYPE_ICON || type == ITEM_TYPE_SHORTCUT || type == ITEM_TYPE_FOLDER)

    private fun HomeScreenGridItem.toFolder(animateOpening: Boolean = false) = HomeScreenFolder(this, animateOpening)

    private inner class HomeScreenFolder(
        val item: HomeScreenGridItem,
        animateOpening: Boolean
    ) {
        var scale: Float = 1f
        private var closing = false

        init {
            if (animateOpening) {
                scale = 0f
                post {
                    ValueAnimator.ofFloat(0f, 1f)
                        .apply {
                            interpolator = DecelerateInterpolator()
                            addUpdateListener {
                                scale = it.animatedValue as Float
                                redrawGrid()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    super.onAnimationEnd(animation)
                                    scale = 1f
                                    redrawGrid()
                                }
                            })
                            duration = FOLDER_ANIMATION_DURATION
                            start()
                        }
                }
            }
        }

        fun getItems() =
            gridItems.filter { it.isSingleCellType() && it.parentId == item.id }

        fun generateDrawable(): Drawable? {
            if (iconSize == 0) {
                return null
            }

            val items = getItems()
            val itemsCount = getItems().count()

            if (itemsCount == 0) {
                return null
            }

            val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val circlePath = Path().apply { addCircle((iconSize / 2).toFloat(), (iconSize / 2).toFloat(), (iconSize / 2).toFloat(), Path.Direction.CCW) }
            canvas.clipPath(circlePath)
            canvas.drawPaint(folderIconBackgroundPaint)
            val folderColumnCount = ceil(sqrt(itemsCount.toDouble())).roundToInt()
            val folderRowCount = ceil(itemsCount.toFloat() / folderColumnCount).roundToInt()
            val scaledCellSize = (iconSize.toFloat() / folderColumnCount)
            val scaledGap = scaledCellSize / 4f
            val scaledIconSize = (iconSize - (folderColumnCount + 1) * scaledGap) / folderColumnCount
            val extraYMargin = if (folderRowCount < folderColumnCount) (scaledIconSize + scaledGap) / 2 else 0f
            items.forEach {
                val (row, column) = getItemPosition(it)
                val drawableX = (scaledGap + column * scaledIconSize + column * scaledGap).toInt()
                val drawableY = (extraYMargin + scaledGap + row * scaledIconSize + row * scaledGap).toInt()
                val newDrawable = it.drawable?.constantState?.newDrawable()?.mutate()
                newDrawable?.setBounds(drawableX, drawableY, drawableX + scaledIconSize.toInt(), drawableY + scaledIconSize.toInt())
                newDrawable?.draw(canvas)
            }
            return BitmapDrawable(resources, bitmap)
        }

        fun getDrawingRect(): RectF {
            val count = getItems().count()
            if (count == 0) {
                return RectF(0f, 0f, 0f, 0f)
            }
            val columnsCount = ceil(sqrt(count.toDouble())).toInt()
            val rowsCount = ceil(count.toFloat() / columnsCount).toInt()
            val cellSize = getCellSize()
            val gap = getGapSize()
            val yGap = gap + textPaint.textSize + 2 * labelSideMargin
            val cell = cells[item.getTopLeft(rowCount)]!!
            val centerX = sideMargins.left + cell.centerX()
            val centerY = sideMargins.top + cell.centerY()
            val folderDialogWidth = columnsCount * cellSize + 2 * folderPadding + (columnsCount - 1) * gap
            val folderDialogHeight = rowsCount * cellSize + 3 * folderPadding + folderTitleTextPaint.textSize + rowsCount * yGap
            var folderDialogTop = centerY - folderDialogHeight / 2
            var folderDialogLeft = centerX - folderDialogWidth / 2

            if (folderDialogLeft < left + sideMargins.left) {
                folderDialogLeft += left + sideMargins.left - folderDialogLeft
            }
            if (folderDialogLeft + folderDialogWidth > right - sideMargins.right) {
                folderDialogLeft -= folderDialogLeft + folderDialogWidth - (right - sideMargins.right)
            }
            if (folderDialogTop < top + sideMargins.top) {
                folderDialogTop += top + sideMargins.top - folderDialogTop
            }
            if (folderDialogTop + folderDialogHeight > bottom - sideMargins.bottom) {
                folderDialogTop -= folderDialogTop + folderDialogHeight - (bottom - sideMargins.bottom)
            }

            return RectF(folderDialogLeft, folderDialogTop, folderDialogLeft + folderDialogWidth, folderDialogTop + folderDialogHeight)
        }

        fun getItemsDrawingRect(): RectF {
            val folderRect = getDrawingRect()
            return RectF(
                folderRect.left + folderPadding,
                folderRect.top + folderPadding * 2 + folderTitleTextPaint.textSize,
                folderRect.right - folderPadding,
                folderRect.bottom - folderPadding
            )
        }

        fun getItemsGridCenters(): List<Triple<Int, Int, Int>> {
            val count = getItems().count()
            if (count == 0) {
                return emptyList()
            }

            val columnsCount = ceil(sqrt(count.toDouble())).roundToInt()
            val rowsCount = ceil(count.toFloat() / columnsCount).roundToInt()
            val folderItemsRect = getItemsDrawingRect()
            val cellSize = getCellSize()
            val gap = getGapSize()
            val yGap = gap + textPaint.textSize + 2 * labelSideMargin
            return (0 until columnsCount * rowsCount)
                .toList()
                .map { Pair(it % columnsCount, it / columnsCount) }
                .mapIndexed { index, (x, y) ->
                    Triple(
                        index,
                        (folderItemsRect.left + x * cellSize + x * gap + cellSize / 2).toInt(),
                        (folderItemsRect.top + y * cellSize + y * yGap + cellSize / 2).toInt()
                    )
                }
        }

        private fun getItemPosition(item: HomeScreenGridItem): Pair<Int, Int> {
            val count = getItems().count()
            val columnsCount = ceil(sqrt(count.toDouble())).roundToInt()
            val column = item.left % columnsCount
            val row = item.left / columnsCount
            return Pair(row, column)
        }

        fun getItemRect(item: HomeScreenGridItem): Rect {
            val (row, column) = getItemPosition(item)
            val itemsRect = getItemsDrawingRect()
            val cellSize = getCellSize()
            val gapSize = getGapSize()
            val yGapSize = gapSize + textPaint.textSize + 2 * labelSideMargin
            val left = (itemsRect.left + column * cellSize + column * gapSize).roundToInt()
            val top = (itemsRect.top + row * cellSize + row * yGapSize).roundToInt()
            return Rect(
                left,
                top,
                left + cellSize,
                top + cellSize
            )
        }

        fun animateClosing(callback: () -> Unit) {
            post {
                if (closing) {
                    return@post
                }
                closing = true
                ValueAnimator.ofFloat(scale, 0.2f)
                    .apply {
                        interpolator = DecelerateInterpolator()
                        addUpdateListener {
                            scale = it.animatedValue as Float
                            redrawGrid()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)
                                scale = 0.2f
                                callback()
                            }
                        })
                        duration = (FOLDER_ANIMATION_DURATION * (max(0f, scale - 0.2f))).toLong()
                        start()
                    }
            }
        }

        fun getCellSize(): Int = min(cellWidth, cellHeight)

        private fun getGapSize(): Float {
            val cellSize = getCellSize()
            return if (cellSize == cellWidth) {
                0f
            } else {
                cellSize / 5f
            }
        }
    }

    companion object {
        private const val FOLDER_OPEN_HOLD_THRESHOLD = 500L
        private const val FOLDER_CLOSE_HOLD_THRESHOLD = 300L
        private const val FOLDER_ANIMATION_DURATION = 200L
    }
}

/**
 * Helper class responsible for managing current page and providing utilities for animating page changes,
 * as well as partial dragigng between pages
 */
private class AnimatedGridPager(
    private val getMaxPage: () -> Int,
    private val redrawGrid: () -> Unit,
    private val getWidth: () -> Int,
    private val getHandler: () -> Handler,
    private val getNextPageBound: () -> Int,
    private val getPrevPageBound: () -> Int,
    private val pageChangeStarted: () -> Unit
) {

    companion object {
        private const val PAGE_CHANGE_HOLD_THRESHOLD = 500L
        private const val PAGE_INDICATORS_FADE_DELAY = PAGE_CHANGE_HOLD_THRESHOLD + 300L

        private enum class PageChangeArea {
            LEFT,
            MIDDLE,
            RIGHT
        }
    }

    private var lastPage = 0
    private var currentPage = 0
    private var pageChangeLastArea = PageChangeArea.MIDDLE
    private var pageChangeLastAreaEntryTime = 0L
    private var pageChangeAnimLeftPercentage = 0f
    private var pageChangeEnabled = true
    private var pageChangeIndicatorsAlpha = 0f
    private var pageChangeSwipedPercentage = 0f

    fun getCurrentPage() = currentPage

    fun isItemOnCurrentPage(item: HomeScreenGridItem) = item.page == currentPage

    fun isItemOnLastPage(item: HomeScreenGridItem) = item.page == lastPage

    fun getPageCount() = max(getMaxPage(), currentPage) + 1


    fun isOutsideOfPageRange() = currentPage > getMaxPage()

    fun isItemInSwipeRange(item: HomeScreenGridItem) =
        ((pageChangeSwipedPercentage > 0f && item.page == currentPage - 1) || (pageChangeSwipedPercentage < 0f && item.page == currentPage + 1))

    fun isSwiped() = abs(pageChangeSwipedPercentage) > 0f

    fun isAnimatingPageChange() = pageChangeAnimLeftPercentage != 0f

    fun shouldDisplayPageChangeIndicator() = isSwiped() || isAnimatingPageChange() || pageChangeIndicatorsAlpha != 0f

    fun getPageChangeIndicatorsAlpha() = if (pageChangeIndicatorsAlpha != 0f) {
        (pageChangeIndicatorsAlpha * 255.0f).toInt()
    } else {
        255
    }

    fun getXFactorForCurrentPage(): Float {
        return if (abs(pageChangeSwipedPercentage) > 0f) {
            pageChangeSwipedPercentage
        } else {
            if (currentPage > lastPage) {
                pageChangeAnimLeftPercentage
            } else {
                -pageChangeAnimLeftPercentage
            }
        }
    }

    fun getXFactorForLastPage(): Float {
        return if (abs(pageChangeSwipedPercentage) > 0f) {
            (1 - abs(pageChangeSwipedPercentage)) * -sign(pageChangeSwipedPercentage)
        } else {
            if (currentPage > lastPage) {
                pageChangeAnimLeftPercentage - 1
            } else {
                1 - pageChangeAnimLeftPercentage
            }
        }
    }

    fun getCurrentViewPositionInFullPageSpace(): Float {
        val rangeStart = lastPage.toFloat()
        val rangeEndPage = if (abs(pageChangeSwipedPercentage) > 0f) {
            if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
        } else {
            currentPage
        }
        val rangeEnd = rangeEndPage.toFloat()
        val lerpAmount = if (pageChangeAnimLeftPercentage != 0f) {
            1 - pageChangeAnimLeftPercentage
        } else {
            abs(pageChangeSwipedPercentage)
        }
        return MathUtils.lerp(rangeStart, rangeEnd, lerpAmount)
    }

    fun setSwipeMovement(diffX: Float) {
        if (!pageChangeEnabled) {
            return
        }

        if (currentPage < getMaxPage() && diffX > 0f || currentPage > 0 && diffX < 0f) {
            pageChangeSwipedPercentage = (-diffX / getWidth().toFloat()).coerceIn(-1f, 1f)
            pageChangeStarted()
            redrawGrid()
        }
    }

    fun finalizeSwipe() {
        if (abs(pageChangeSwipedPercentage) == 0f) {
            return
        }

        if (abs(pageChangeSwipedPercentage) > 0.5f) {
            lastPage = currentPage
            currentPage = if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
            handlePageChange(true)
        } else {
            lastPage = if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
            pageChangeSwipedPercentage = sign(pageChangeSwipedPercentage) * (1 - abs(pageChangeSwipedPercentage))
            handlePageChange(true)
        }
    }

    fun handleItemMovement(x: Int, y: Int) {
        showIndicators()
        if (x > getNextPageBound()) {
            doWithPageChangeDelay(PageChangeArea.RIGHT) {
                nextOrAdditionalPage()
            }
        } else if (x < getPrevPageBound()) {
            doWithPageChangeDelay(PageChangeArea.LEFT) {
                prevPage()
            }
        } else {
            clearPageChangeFlags()
        }
    }

    fun itemMovementStopped() {
        scheduleIndicatorsFade()
    }

    fun nextPage(redraw: Boolean = false): Boolean {
        if (currentPage < getMaxPage() && pageChangeEnabled) {
            lastPage = currentPage
            currentPage++
            handlePageChange(redraw)
            return true
        }

        return false
    }

    fun prevPage(redraw: Boolean = false): Boolean {
        if (currentPage > 0 && pageChangeEnabled) {
            lastPage = currentPage
            currentPage--
            handlePageChange(redraw)
            return true
        }

        return false
    }

    fun skipToPage(targetPage: Int): Boolean {
        if (currentPage != targetPage && targetPage < getMaxPage() + 1) {
            lastPage = currentPage
            currentPage = targetPage
            handlePageChange()
            return true
        }

        return false
    }

    private val checkAndExecuteDelayedPageChange: Runnable = Runnable {
        if (System.currentTimeMillis() - pageChangeLastAreaEntryTime > PAGE_CHANGE_HOLD_THRESHOLD) {
            when (pageChangeLastArea) {
                PageChangeArea.RIGHT -> nextOrAdditionalPage(true)
                PageChangeArea.LEFT -> prevPage(true)
                else -> clearPageChangeFlags()
            }
        }
    }

    private val startFadingIndicators: Runnable = Runnable {
        ValueAnimator.ofFloat(1f, 0f)
            .apply {
                addUpdateListener {
                    pageChangeIndicatorsAlpha = it.animatedValue as Float
                    redrawGrid()
                }
                start()
            }
    }

    private fun showIndicators() {
        pageChangeIndicatorsAlpha = 1f
        getHandler().removeCallbacks(startFadingIndicators)
    }

    private fun clearPageChangeFlags() {
        pageChangeLastArea = PageChangeArea.MIDDLE
        pageChangeLastAreaEntryTime = 0
        getHandler().removeCallbacks(checkAndExecuteDelayedPageChange)
    }

    private fun schedulePageChange() {
        pageChangeLastAreaEntryTime = System.currentTimeMillis()
        getHandler().postDelayed(checkAndExecuteDelayedPageChange, PAGE_CHANGE_HOLD_THRESHOLD)
    }

    private fun scheduleIndicatorsFade() {
        pageChangeIndicatorsAlpha = 1f
        getHandler().postDelayed(startFadingIndicators, PAGE_INDICATORS_FADE_DELAY)
    }

    private fun doWithPageChangeDelay(needed: PageChangeArea, pageChangeFunction: () -> Boolean) {
        if (pageChangeLastArea != needed) {
            pageChangeLastArea = needed
            schedulePageChange()
        } else if (System.currentTimeMillis() - pageChangeLastAreaEntryTime > PAGE_CHANGE_HOLD_THRESHOLD) {
            if (pageChangeFunction()) {
                clearPageChangeFlags()
            }
        }
    }

    private fun nextOrAdditionalPage(redraw: Boolean = false): Boolean {
        if (currentPage < getMaxPage() + 1 && pageChangeEnabled) {
            lastPage = currentPage
            currentPage++
            handlePageChange(redraw)
            return true
        }

        return false
    }

    private fun handlePageChange(redraw: Boolean = false) {
        pageChangeEnabled = false
        pageChangeIndicatorsAlpha = 0f
        pageChangeStarted()
        val startingAt = 1 - abs(pageChangeSwipedPercentage)
        pageChangeSwipedPercentage = 0f
        getHandler().removeCallbacks(startFadingIndicators)
        pageChangeStarted()
        if (redraw) {
            redrawGrid()
        }
        ValueAnimator.ofFloat(startingAt, 0f)
            .apply {
                interpolator = OvershootInterpolator(1f)
                addUpdateListener {
                    if (it.animatedValue != 0f) {
                        pageChangeAnimLeftPercentage = it.animatedValue as Float
                        redrawGrid()
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        pageChangeAnimLeftPercentage = 0f
                        pageChangeEnabled = true
                        lastPage = currentPage
                        clearPageChangeFlags()
                        scheduleIndicatorsFade()
                        redrawGrid()
                    }
                })
                start()
            }
    }
}

