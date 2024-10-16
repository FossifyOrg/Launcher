package org.fossify.home.fragments

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.navigationBarHeight
import org.fossify.commons.extensions.navigationBarOnBottom
import org.fossify.commons.extensions.navigationBarOnSide
import org.fossify.commons.extensions.navigationBarWidth
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.home.R
import org.fossify.home.activities.MainActivity
import org.fossify.home.adapters.WidgetsAdapter
import org.fossify.home.databinding.WidgetsFragmentBinding
import org.fossify.home.extensions.config
import org.fossify.home.extensions.getInitialCellSize
import org.fossify.home.extensions.setupDrawerBackground
import org.fossify.home.helpers.ITEM_TYPE_SHORTCUT
import org.fossify.home.helpers.ITEM_TYPE_WIDGET
import org.fossify.home.interfaces.WidgetsFragmentListener
import org.fossify.home.models.AppWidget
import org.fossify.home.models.HomeScreenGridItem
import org.fossify.home.models.WidgetsListItem
import org.fossify.home.models.WidgetsListItemsHolder
import org.fossify.home.models.WidgetsListSection

class WidgetsFragment(context: Context, attributeSet: AttributeSet) :
    MyFragment<WidgetsFragmentBinding>(context, attributeSet), WidgetsFragmentListener {
    private var lastTouchCoords = Pair(0f, 0f)
    var touchDownY = -1
    var ignoreTouches = false

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        this.binding = WidgetsFragmentBinding.bind(this)
        getAppWidgets()

        binding.widgetsList.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupDrawerBackground()
    }

    fun onConfigurationChanged() {
        binding.widgetsList.scrollToPosition(0)
        setupViews()

        val appWidgets = (binding.widgetsList.adapter as? WidgetsAdapter)?.widgetListItems
        if (appWidgets != null) {
            setupAdapter(appWidgets)
        } else {
            getAppWidgets()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onInterceptTouchEvent(event)
        }

        if (ignoreTouches) {
            // some devices ACTION_MOVE keeps triggering for the whole long press duration, but we are interested in real moves only, when coords change
            if (lastTouchCoords.first != event.x || lastTouchCoords.second != event.y) {
                touchDownY = -1
                return true
            }
        }

        lastTouchCoords = Pair(event.x, event.y)
        var shouldIntercept = false

        // pull the whole fragment down if it is scrolled way to the top and the users pulls it even further
        if (touchDownY != -1) {
            shouldIntercept =
                touchDownY - event.y.toInt() < 0 && binding.widgetsList.computeVerticalScrollOffset() == 0
            if (shouldIntercept) {
                activity?.startHandlingTouches(touchDownY)
                touchDownY = -1
            }
        } else {
            touchDownY = event.y.toInt()
        }

        return shouldIntercept
    }

    @SuppressLint("WrongConstant")
    fun getAppWidgets() {
        ensureBackgroundThread {
            // get the casual widgets
            var appWidgets = ArrayList<AppWidget>()
            val manager = AppWidgetManager.getInstance(context)
            val packageManager = context.packageManager
            val infoList = manager.installedProviders
            for (info in infoList) {
                val appPackageName = info.provider.packageName
                val appMetadata = getAppMetadataFromPackage(appPackageName) ?: continue
                val appTitle = appMetadata.appTitle
                val appIcon = appMetadata.appIcon
                val widgetTitle = info.loadLabel(packageManager)
                val widgetPreviewImage =
                    info.loadPreviewImage(context, resources.displayMetrics.densityDpi) ?: appIcon
                val cellSize = context.getInitialCellSize(info, info.minWidth, info.minHeight)
                val widthCells = cellSize.width
                val heightCells = cellSize.height
                val className = info.provider.className
                val widget =
                    AppWidget(
                        appPackageName = appPackageName,
                        appTitle = appTitle,
                        appIcon = appIcon,
                        widgetTitle = widgetTitle,
                        widgetPreviewImage = widgetPreviewImage,
                        widthCells = widthCells,
                        heightCells = heightCells,
                        isShortcut = false,
                        className = className,
                        providerInfo = info,
                        activityInfo = null
                    )
                appWidgets.add(widget)
            }

            // show also the widgets that are technically shortcuts
            val intent = Intent(Intent.ACTION_CREATE_SHORTCUT, null)
            val list =
                packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
            for (info in list) {
                val componentInfo = info.activityInfo.applicationInfo
                val appTitle = componentInfo.loadLabel(packageManager).toString()
                val appPackageName = componentInfo.packageName
                val appMetadata = getAppMetadataFromPackage(appPackageName) ?: continue
                val appIcon = appMetadata.appIcon
                val widgetTitle = info.loadLabel(packageManager).toString()
                val widgetPreviewImage = packageManager.getDrawable(
                    componentInfo.packageName,
                    info.iconResource,
                    componentInfo
                )
                val widget = AppWidget(
                    appPackageName = appPackageName,
                    appTitle = appTitle,
                    appIcon = appIcon,
                    widgetTitle = widgetTitle,
                    widgetPreviewImage = widgetPreviewImage,
                    widthCells = 0,
                    heightCells = 0,
                    isShortcut = true,
                    className = "",
                    providerInfo = null,
                    activityInfo = info.activityInfo
                )
                appWidgets.add(widget)
            }

            appWidgets = appWidgets.sortedWith(
                compareBy({ it.appTitle },
                    { it.appPackageName },
                    { it.widgetTitle })
            ).toMutableList() as ArrayList<AppWidget>
            splitWidgetsByApps(appWidgets)
        }
    }

    private fun splitWidgetsByApps(appWidgets: ArrayList<AppWidget>) {
        var currentAppPackageName = ""
        val widgetListItems = ArrayList<WidgetsListItem>()
        var currentAppWidgets = ArrayList<AppWidget>()
        appWidgets.forEach { appWidget ->
            if (appWidget.appPackageName != currentAppPackageName) {
                if (widgetListItems.isNotEmpty()) {
                    widgetListItems.add(WidgetsListItemsHolder(currentAppWidgets))
                    currentAppWidgets = ArrayList()
                }

                widgetListItems.add(WidgetsListSection(appWidget.appTitle, appWidget.appIcon))
            }

            currentAppWidgets.add(appWidget)
            currentAppPackageName = appWidget.appPackageName
        }

        if (widgetListItems.isNotEmpty()) {
            widgetListItems.add(WidgetsListItemsHolder(currentAppWidgets))
        }

        setupAdapter(widgetListItems)
    }

    private fun setupAdapter(widgetsListItems: ArrayList<WidgetsListItem>) {
        activity?.runOnUiThread {
            val currAdapter = binding.widgetsList.adapter
            if (currAdapter == null) {
                WidgetsAdapter(activity!!, widgetsListItems, this) {
                    context.toast(R.string.touch_hold_widget)
                    ignoreTouches = false
                    touchDownY = -1
                }.apply {
                    binding.widgetsList.adapter = this
                }
            } else {
                (currAdapter as WidgetsAdapter).updateItems(widgetsListItems)
            }
        }
    }

    fun setupViews() {
        if (activity == null) {
            return
        }

        binding.widgetsFastscroller.updateColors(context.getProperPrimaryColor())

        var bottomListPadding = 0
        var leftListPadding = 0
        var rightListPadding = 0

        if (activity!!.navigationBarOnBottom) {
            bottomListPadding = activity!!.navigationBarHeight
            leftListPadding = 0
            rightListPadding = 0
        } else if (activity!!.navigationBarOnSide) {
            bottomListPadding = 0

            val display = if (isRPlus()) {
                display!!
            } else {
                (activity!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }

            if (display.rotation == Surface.ROTATION_90) {
                rightListPadding = activity!!.navigationBarWidth
            } else if (display.rotation == Surface.ROTATION_270) {
                leftListPadding = activity!!.navigationBarWidth
            }
        }

        binding.widgetsList.setPadding(0, 0, 0, bottomListPadding)
        binding.widgetsFastscroller.setPadding(leftListPadding, 0, rightListPadding, 0)
        (binding.widgetsList.adapter as? WidgetsAdapter)?.updateTextColor(context.getProperTextColor())
        setupDrawerBackground()
    }

    private fun getAppMetadataFromPackage(packageName: String): WidgetsListSection? {
        try {
            val appInfo = activity!!.packageManager.getApplicationInfo(packageName, 0)
            val appTitle = activity!!.packageManager.getApplicationLabel(appInfo).toString()

            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activityList = launcher.getActivityList(packageName, Process.myUserHandle())
            var appIcon = activityList.firstOrNull()?.getBadgedIcon(0)

            if (appIcon == null) {
                appIcon = context.packageManager.getApplicationIcon(packageName)
            }

            if (appTitle.isNotEmpty()) {
                return WidgetsListSection(appTitle, appIcon)
            }
        } catch (ignored: Exception) {
        } catch (error: Error) {
        }

        return null
    }

    override fun onWidgetLongPressed(appWidget: AppWidget) {
        if (appWidget.heightCells > context.config.homeRowCount - 1 || appWidget.widthCells > context.config.homeColumnCount) {
            context.showErrorToast(context.getString(R.string.widget_too_big))
            return
        }

        val type = if (appWidget.isShortcut) {
            ITEM_TYPE_SHORTCUT
        } else {
            ITEM_TYPE_WIDGET
        }

        val gridItem = HomeScreenGridItem(
            id = null,
            left = -1,
            top = -1,
            right = -1,
            bottom = -1,
            page = 0,
            packageName = appWidget.appPackageName,
            activityName = "",
            title = "",
            type = type,
            className = appWidget.className,
            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
            drawable = appWidget.widgetPreviewImage,
            providerInfo = appWidget.providerInfo,
            activityInfo = appWidget.activityInfo,
            widthCells = appWidget.widthCells,
            heightCells = appWidget.heightCells
        )

        activity?.widgetLongPressedOnList(gridItem)
        ignoreTouches = true
    }
}
