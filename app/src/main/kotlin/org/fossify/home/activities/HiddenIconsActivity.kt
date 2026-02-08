package org.fossify.home.activities

import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserManager
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.home.adapters.HiddenIconsAdapter
import org.fossify.home.databinding.ActivityHiddenIconsBinding
import org.fossify.home.extensions.config
import org.fossify.home.extensions.getDrawableForPackageName
import org.fossify.home.extensions.hiddenIconsDB
import org.fossify.home.helpers.UNKNOWN_USER_SERIAL
import org.fossify.home.models.HiddenIcon

class HiddenIconsActivity : SimpleActivity(), RefreshRecyclerViewListener {
    private val binding by viewBinding(ActivityHiddenIconsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateIcons()

        setupEdgeToEdge(padBottomSystem = listOf(binding.manageHiddenIconsList))
        setupMaterialScrollListener(binding.manageHiddenIconsList, binding.manageHiddenIconsAppbar)

        val layoutManager = binding.manageHiddenIconsList.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = config.drawerColumnCount
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageHiddenIconsAppbar, NavigationIcon.Arrow)
    }

    private fun updateIcons() {
        ensureBackgroundThread {
            val hiddenIcons = hiddenIconsDB.getHiddenIcons().sortedWith(
                compareBy({
                    it.title.normalizeString().lowercase()
                }, {
                    it.packageName
                })
            ).toMutableList() as ArrayList<HiddenIcon>

            val hiddenIconsEmpty = hiddenIcons.isEmpty()
            runOnUiThread {
                binding.manageHiddenIconsPlaceholder.beVisibleIf(hiddenIconsEmpty)
            }

            if (hiddenIcons.isNotEmpty()) {
                val launcherApps =
                    applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
                val userManager = applicationContext.getSystemService(USER_SERVICE) as UserManager
                val userHandles = userManager.userProfiles
                for (userHandle in userHandles) {
                    val userSerial = userManager.getSerialNumberForUser(userHandle)
                    if (userSerial == UNKNOWN_USER_SERIAL) {
                        continue
                    }

                    val activityList = launcherApps.getActivityList(null, userHandle)
                    for (info in activityList) {
                        val packageName = info.applicationInfo.packageName
                        val activityName = info.name
                        hiddenIcons.firstOrNull {
                            it.getIconIdentifier() == "$packageName/$activityName/$userSerial"
                        }?.apply {
                            drawable = info.getBadgedIcon(resources.displayMetrics.densityDpi)
                                ?: getDrawableForPackageName(packageName, userSerial)
                        }
                    }
                }

                hiddenIcons.firstOrNull { it.packageName == applicationContext.packageName }?.apply {
                    drawable = getDrawableForPackageName(packageName)
                }
            }

            val iconsToRemove = hiddenIcons.filter { it.drawable == null }
            if (iconsToRemove.isNotEmpty()) {
                hiddenIconsDB.removeHiddenIcons(iconsToRemove)
                hiddenIcons.removeAll(iconsToRemove)
            }

            runOnUiThread {
                HiddenIconsAdapter(this, hiddenIcons, this, binding.manageHiddenIconsList) {
                }.apply {
                    binding.manageHiddenIconsList.adapter = this
                }
            }
        }
    }

    override fun refreshItems() {
        updateIcons()
    }
}
