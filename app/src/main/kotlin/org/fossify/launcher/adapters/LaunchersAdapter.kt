package org.fossify.launcher.adapters

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.Transition
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.realScreenSize
import org.fossify.launcher.R
import org.fossify.launcher.activities.SimpleActivity
import org.fossify.launcher.databinding.ItemLauncherLabelBinding
import org.fossify.launcher.extensions.config
import org.fossify.launcher.interfaces.AllAppsListener
import org.fossify.launcher.models.AppLauncher
import org.fossify.launcher.models.HomeScreenGridItem

class LaunchersAdapter(
    val activity: SimpleActivity,
    launchers: ArrayList<AppLauncher>,
    val allAppsListener: AllAppsListener,
    val itemClick: (Any) -> Unit
) : RecyclerView.Adapter<LaunchersAdapter.ViewHolder>(), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textColor = activity.getProperTextColor()
    private var iconPadding = 0
    private var wereFreshIconsLoaded = false
    private var filterQuery: String? = null
    private var filteredLaunchers: List<AppLauncher> = launchers

    var launchers: ArrayList<AppLauncher> = launchers
        set(value) {
            field = value
            updateFilter()
        }

    init {
        calculateIconWidth()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLauncherLabelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(filteredLaunchers[position])
    }

    override fun getItemCount() = filteredLaunchers.size

    private fun calculateIconWidth() {
        val currentColumnCount = activity.config.drawerColumnCount

        val iconWidth = activity.realScreenSize.x / currentColumnCount
        iconPadding = (iconWidth * 0.1f).toInt()
    }

    fun hideIcon(item: HomeScreenGridItem) {
        val itemToRemove = launchers.firstOrNull { it.getLauncherIdentifier() == item.getItemIdentifier() }
        if (itemToRemove != null) {
            val position = launchers.indexOfFirst { it.getLauncherIdentifier() == item.getItemIdentifier() }
            val filteredPosition = filteredLaunchers.indexOfFirst { it.getLauncherIdentifier() == item.getItemIdentifier() }
            launchers.removeAt(position)
            updateFilter()
            notifyItemRemoved(filteredPosition)
        }
    }

    fun updateItems(newItems: ArrayList<AppLauncher>) {
        val oldSum = launchers.sumOf { it.getHashToCompare() }
        val newSum = newItems.sumOf { it.getHashToCompare() }
        if (oldSum != newSum || !wereFreshIconsLoaded) {
            launchers = newItems
            notifyDataSetChanged()
            wereFreshIconsLoaded = true
        }
    }

    fun updateSearchQuery(newQuery: String?) {
        if (filterQuery != newQuery) {
            filterQuery = newQuery
            updateFilter()
            notifyDataSetChanged()
        }
    }

    fun updateTextColor(newTextColor: Int) {
        if (newTextColor != textColor) {
            textColor = newTextColor
            notifyDataSetChanged()
        }
    }

    private fun updateFilter() {
        filteredLaunchers = launchers.filter { filterQuery == null || it.title.contains(filterQuery!!, ignoreCase = true) }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(launcher: AppLauncher): View {
            val binding = ItemLauncherLabelBinding.bind(itemView)
            itemView.apply {
                binding.launcherLabel.text = launcher.title
                binding.launcherLabel.setTextColor(textColor)
                binding.launcherIcon.setPadding(iconPadding, iconPadding, iconPadding, 0)

                // Once all images are loaded and crossfades are done, directly set drawables
                if (launcher.drawable != null && binding.launcherIcon.tag == true) {
                    binding.launcherIcon.setImageDrawable(launcher.drawable)
                } else {
                    val factory = DrawableCrossFadeFactory.Builder(150).setCrossFadeEnabled(true).build()
                    val placeholderDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.placeholder_drawable, launcher.thumbnailColor)

                    Glide.with(activity)
                        .load(launcher.drawable)
                        .placeholder(placeholderDrawable)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .transition(DrawableTransitionOptions.withCrossFade(factory))
                        .into(object : DrawableImageViewTarget(binding.launcherIcon) {
                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                super.onResourceReady(resource, transition)
                                // Set tag to true to mark that crossfade was already done on this view
                                view.tag = true
                            }
                        })
                }

                setOnClickListener { itemClick(launcher) }
                setOnLongClickListener { view ->
                    val location = IntArray(2)
                    getLocationOnScreen(location)
                    allAppsListener.onAppLauncherLongPressed((location[0] + width / 2).toFloat(), location[1].toFloat(), launcher)
                    true
                }
            }

            return itemView
        }
    }

    override fun onChange(position: Int) = filteredLaunchers.getOrNull(position)?.getBubbleText() ?: ""
}
