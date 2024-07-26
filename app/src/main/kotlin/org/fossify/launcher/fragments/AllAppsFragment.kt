package org.fossify.launcher.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.launcher.activities.MainActivity
import org.fossify.launcher.adapters.LaunchersAdapter
import org.fossify.launcher.databinding.AllAppsFragmentBinding
import org.fossify.launcher.extensions.config
import org.fossify.launcher.extensions.launchApp
import org.fossify.launcher.helpers.ITEM_TYPE_ICON
import org.fossify.launcher.interfaces.AllAppsListener
import org.fossify.launcher.models.AppLauncher
import org.fossify.launcher.models.HomeScreenGridItem

class AllAppsFragment(context: Context, attributeSet: AttributeSet) : MyFragment<AllAppsFragmentBinding>(context, attributeSet), AllAppsListener {
    private var lastTouchCoords = Pair(0f, 0f)
    var touchDownY = -1
    var ignoreTouches = false
    var hasTopPadding = false

    private var launchers = emptyList<AppLauncher>()

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        this.binding = AllAppsFragmentBinding.bind(this)

        binding.allAppsGrid.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onResume() {
        if (binding.allAppsGrid.layoutManager == null || binding.allAppsGrid.adapter == null) {
            return
        }

        val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
        if (layoutManager.spanCount != context.config.drawerColumnCount) {
            onConfigurationChanged()
            // Force redraw due to changed item size
            (binding.allAppsGrid.adapter as LaunchersAdapter).notifyDataSetChanged()
        }
    }

    fun onConfigurationChanged() {
        binding.allAppsGrid.scrollToPosition(0)
        binding.allAppsFastscroller.resetManualScrolling()
        setupViews()

        val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context.config.drawerColumnCount
        setupAdapter(launchers)
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onInterceptTouchEvent(event)
        }

        var shouldIntercept = false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                if (ignoreTouches) {
                    // some devices ACTION_MOVE keeps triggering for the whole long press duration, but we are interested in real moves only, when coords change
                    if (lastTouchCoords.first != event.x || lastTouchCoords.second != event.y) {
                        touchDownY = -1
                        return true
                    }
                }

                // pull the whole fragment down if it is scrolled way to the top and the user pulls it even further
                if (touchDownY != -1) {
                    val distance = event.y.toInt() - touchDownY
                    shouldIntercept = distance > 0 && binding.allAppsGrid.computeVerticalScrollOffset() == 0
                    if (shouldIntercept) {
                        activity?.startHandlingTouches(touchDownY)
                        touchDownY = -1
                    }
                }
            }
        }

        lastTouchCoords = Pair(event.x, event.y)
        return shouldIntercept
    }

    fun gotLaunchers(appLaunchers: List<AppLauncher>) {
        launchers = appLaunchers.sortedWith(
            compareBy(
                { it.title.normalizeString().lowercase() },
                { it.packageName }
            )
        )

        setupAdapter(launchers)
    }

    private fun getAdapter() = binding.allAppsGrid.adapter as? LaunchersAdapter

    private fun setupAdapter(launchers: List<AppLauncher>) {
        activity?.runOnUiThread {
            val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.config.drawerColumnCount

            var adapter = getAdapter()
            if (adapter == null) {
                adapter = LaunchersAdapter(activity!!, this) {
                    activity?.launchApp((it as AppLauncher).packageName, it.activityName)
                    if (activity?.config?.closeAppDrawer == true) {
                        activity?.closeAppDrawer(delayed = true)
                    }
                    ignoreTouches = false
                    touchDownY = -1
                }.apply {
                    binding.allAppsGrid.adapter = this
                }
            }

            adapter.submitList(launchers.toMutableList())
        }
    }

    fun hideIcon(item: HomeScreenGridItem) {
        val itemToRemove = launchers.firstOrNull { it.getLauncherIdentifier() == item.getItemIdentifier() }
        if (itemToRemove != null) {
            val position = launchers.indexOfFirst { it.getLauncherIdentifier() == item.getItemIdentifier() }
            launchers = launchers.toMutableList().apply {
                removeAt(position)
            }

            getAdapter()?.submitList(launchers)
        }
    }

    fun setupViews(addTopPadding: Boolean = hasTopPadding) {
        if (activity == null) {
            return
        }

        binding.allAppsFastscroller.updateColors(context.getProperPrimaryColor())

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

        binding.allAppsGrid.setPadding(0, 0, resources.getDimension(org.fossify.commons.R.dimen.medium_margin).toInt(), bottomListPadding)
        binding.allAppsFastscroller.setPadding(leftListPadding, 0, rightListPadding, 0)

        hasTopPadding = addTopPadding
        val topPadding = if (addTopPadding) activity!!.statusBarHeight else 0
        setPadding(0, topPadding, 0, 0)
        background = ColorDrawable(context.getProperBackgroundColor())
        getAdapter()?.updateTextColor(context.getProperTextColor())

        binding.searchBar.beVisibleIf(context.config.showSearchBar)
        binding.searchBar.getToolbar().beGone()
        binding.searchBar.updateColors()
        binding.searchBar.setupMenu()

        binding.searchBar.onSearchTextChangedListener = { query ->
            val filtered = launchers.filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
            getAdapter()?.submitList(filtered) {
                showNoResultsPlaceholderIfNeeded()
            }
        }
    }

    private fun showNoResultsPlaceholderIfNeeded() {
        val itemCount = getAdapter()?.itemCount
        binding.noResultsPlaceholder.beVisibleIf(itemCount != null && itemCount == 0)
    }

    override fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher) {
        val gridItem = HomeScreenGridItem(
            id = null,
            left = -1,
            top = -1,
            right = -1,
            bottom = -1,
            page = 0,
            packageName = appLauncher.packageName,
            activityName = appLauncher.activityName,
            title = appLauncher.title,
            type = ITEM_TYPE_ICON,
            className = "",
            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
            drawable = appLauncher.drawable
        )

        activity?.showHomeIconMenu(x, y, gridItem, true)
        ignoreTouches = true

        binding.searchBar.closeSearch()
    }

    fun onBackPressed(): Boolean {
        if (binding.searchBar.isSearchOpen) {
            binding.searchBar.closeSearch()
            return true
        }

        return false
    }
}
