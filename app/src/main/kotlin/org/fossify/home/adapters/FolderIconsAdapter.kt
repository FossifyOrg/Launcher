package org.fossify.home.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.iterator
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.views.MyRecyclerView
import org.fossify.home.R
import org.fossify.home.activities.MainActivity
import org.fossify.home.databinding.ItemLauncherLabelBinding
import org.fossify.home.extensions.handleGridItemPopupMenu
import org.fossify.home.interfaces.ItemMenuListenerAdapter
import org.fossify.home.models.HomeScreenGridItem

class FolderIconsAdapter(
    activity: BaseSimpleActivity, var items: MutableList<HomeScreenGridItem>, private val iconPadding: Int,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    override fun getActionMenuId() = 0

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = itemCount

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.id?.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun prepareActionMode(menu: Menu) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemLauncherLabelBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        setupView(holder.itemView, item)
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    private fun removeItem(item: HomeScreenGridItem) {
        val position = items.indexOfFirst { it.id == item.id }
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    private fun setupView(view: View, item: HomeScreenGridItem) {
        ItemLauncherLabelBinding.bind(view).apply {
            launcherLabel.text = item.title
            launcherLabel.setTextColor(textColor)
            launcherIcon.setPadding(iconPadding, iconPadding, iconPadding, 0)
            launcherIcon.setImageDrawable(item.drawable)

            val mainListener = (activity as? MainActivity)?.menuListener

            root.setOnClickListener { itemClick(item) }
            root.setOnLongClickListener {
                popupAnchor.y = launcherIcon.y
                activity.handleGridItemPopupMenu(popupAnchor, item, false, object : ItemMenuListenerAdapter() {
                    override fun appInfo(gridItem: HomeScreenGridItem) {
                        mainListener?.appInfo(gridItem)
                    }

                    override fun remove(gridItem: HomeScreenGridItem) {
                        mainListener?.remove(gridItem)
                        removeItem(gridItem)
                    }

                    override fun uninstall(gridItem: HomeScreenGridItem) {
                        mainListener?.uninstall(gridItem)
                    }

                    override fun rename(gridItem: HomeScreenGridItem) {
                        mainListener?.rename(gridItem)
                    }

                    override fun beforeShow(menu: Menu) {
                        var visibleMenuItems = 0
                        for (menuItem in menu.iterator()) {
                            if (menuItem.isVisible) {
                                visibleMenuItems++
                            }
                        }
                        val yOffset = resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * (visibleMenuItems - 1)
                        popupAnchor.y -= yOffset
                    }
                })
                true
            }
        }
    }

    fun updateItems(items: List<HomeScreenGridItem>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }
}
