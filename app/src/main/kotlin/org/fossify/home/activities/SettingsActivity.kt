package org.fossify.home.activities

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import org.fossify.home.BuildConfig
import org.fossify.home.R
import org.fossify.home.databinding.ActivitySettingsBinding
import org.fossify.home.extensions.config
import org.fossify.home.helpers.LayoutBackupHelper
import org.fossify.home.helpers.MAX_COLUMN_COUNT
import org.fossify.home.helpers.MAX_ROW_COUNT
import org.fossify.home.helpers.MIN_COLUMN_COUNT
import org.fossify.home.helpers.MIN_ROW_COUNT
import org.fossify.home.helpers.REQUEST_EXPORT_LAYOUT
import org.fossify.home.helpers.REQUEST_IMPORT_LAYOUT
import org.fossify.home.receivers.LockDeviceAdminReceiver
import java.io.IOException
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private var pendingExport: LayoutBackupHelper.ExportResult? = null
    private var importMode = LayoutBackupHelper.ImportMode.KEEP_EXISTING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.settingsNestedScrollview))
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)
        setupOptionsMenu()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)
        refreshMenuItems()

        setupCustomizeColors()
        setupUseEnglish()
        setupDoubleTapToLock()
        setupCloseAppDrawerOnOtherAppOpen()
        setupOpenKeyboardOnAppDrawer()
        setupDrawerColumnCount()
        setupDrawerSearchBar()
        setupShowDrawerAppLabels()
        setupHomeRowCount()
        setupHomeColumnCount()
        setupShowHomeAppLabels()
        setupExportLayout()
        setupImportLayout()
        setupLanguage()
        setupManageHiddenIcons()
        updateTextColors(binding.settingsHolder)

        arrayOf(
            binding.settingsColorCustomizationSectionLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsDrawerSettingsLabel,
            binding.settingsHomeScreenLabel,
            binding.settingsBackupLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupOptionsMenu() {
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.about -> launchAbout()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        binding.settingsToolbar.menu.apply {
            findItem(R.id.more_apps_from_us).isVisible =
                !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf(
            beVisible = (config.wasUseEnglishToggled || Locale.getDefault().language != "en")
                    && !isTiramisuPlus()
        )

        binding.settingsUseEnglish.isChecked = config.useEnglish
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupDoubleTapToLock() {
        val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        binding.settingsDoubleTapToLock.isChecked = devicePolicyManager.isAdminActive(
            ComponentName(this, LockDeviceAdminReceiver::class.java)
        )

        binding.settingsDoubleTapToLockHolder.setOnClickListener {
            val isLockDeviceAdminActive = devicePolicyManager.isAdminActive(
                ComponentName(this, LockDeviceAdminReceiver::class.java)
            )
            if (isLockDeviceAdminActive) {
                devicePolicyManager.removeActiveAdmin(
                    ComponentName(this, LockDeviceAdminReceiver::class.java)
                )
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this, LockDeviceAdminReceiver::class.java)
                )
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.lock_device_admin_hint)
                )
                startActivity(intent)
            }
        }
    }

    private fun setupOpenKeyboardOnAppDrawer() {
        binding.settingsOpenKeyboardOnAppDrawerHolder.beVisibleIf(config.showSearchBar)
        binding.settingsOpenKeyboardOnAppDrawer.isChecked = config.autoShowKeyboardInAppDrawer
        binding.settingsOpenKeyboardOnAppDrawerHolder.setOnClickListener {
            binding.settingsOpenKeyboardOnAppDrawer.toggle()
            config.autoShowKeyboardInAppDrawer = binding.settingsOpenKeyboardOnAppDrawer.isChecked
        }
    }

    private fun setupCloseAppDrawerOnOtherAppOpen() {
        binding.settingsCloseAppDrawerOnOtherApp.isChecked = config.closeAppDrawer
        binding.settingsCloseAppDrawerOnOtherAppHolder.setOnClickListener {
            binding.settingsCloseAppDrawerOnOtherApp.toggle()
            config.closeAppDrawer = binding.settingsCloseAppDrawerOnOtherApp.isChecked
        }
    }

    private fun setupDrawerColumnCount() {
        val currentColumnCount = config.drawerColumnCount
        binding.settingsDrawerColumnCount.text = currentColumnCount.toString()
        binding.settingsDrawerColumnCountHolder.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in 1..MAX_COLUMN_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.column_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    config.drawerColumnCount = newColumnCount
                    setupDrawerColumnCount()
                }
            }
        }
    }

    private fun setupDrawerSearchBar() {
        val showSearchBar = config.showSearchBar
        binding.settingsShowSearchBar.isChecked = showSearchBar
        binding.settingsDrawerSearchHolder.setOnClickListener {
            binding.settingsShowSearchBar.toggle()
            config.showSearchBar = binding.settingsShowSearchBar.isChecked
            binding.settingsOpenKeyboardOnAppDrawerHolder.beVisibleIf(config.showSearchBar)
        }
    }

    private fun setupShowDrawerAppLabels() {
        binding.settingsShowDrawerAppLabels.isChecked = config.showDrawerAppLabels
        binding.settingsShowDrawerAppLabelsHolder.setOnClickListener {
            binding.settingsShowDrawerAppLabels.toggle()
            config.showDrawerAppLabels = binding.settingsShowDrawerAppLabels.isChecked
        }
    }

    private fun setupHomeRowCount() {
        val currentRowCount = config.homeRowCount
        binding.settingsHomeScreenRowCount.text = currentRowCount.toString()
        binding.settingsHomeScreenRowCountHolder.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in MIN_ROW_COUNT..MAX_ROW_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.row_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentRowCount) {
                val newRowCount = it as Int
                if (currentRowCount != newRowCount) {
                    config.homeRowCount = newRowCount
                    setupHomeRowCount()
                }
            }
        }
    }

    private fun setupHomeColumnCount() {
        val currentColumnCount = config.homeColumnCount
        binding.settingsHomeScreenColumnCount.text = currentColumnCount.toString()
        binding.settingsHomeScreenColumnCountHolder.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in MIN_COLUMN_COUNT..MAX_COLUMN_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.column_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    config.homeColumnCount = newColumnCount
                    setupHomeColumnCount()
                }
            }
        }
    }

    private fun setupShowHomeAppLabels() {
        binding.settingsShowHomeAppLabels.isChecked = config.showHomeAppLabels
        binding.settingsShowHomeAppLabelsHolder.setOnClickListener {
            binding.settingsShowHomeAppLabels.toggle()
            config.showHomeAppLabels = binding.settingsShowHomeAppLabels.isChecked
        }
    }

    private fun setupExportLayout() {
        binding.settingsExportLayoutHolder.setOnClickListener {
            // reading the grid items hits the database, so it cannot happen on the main thread
            ensureBackgroundThread {
                val export = LayoutBackupHelper.exportLayout(this)
                runOnUiThread {
                    if (export.exportedCount == 0) {
                        toast(R.string.layout_export_no_items)
                        return@runOnUiThread
                    }

                    pendingExport = export
                    val filename = LayoutBackupHelper.buildBackupFilename()
                    try {
                        startActivityForResult(
                            LayoutBackupHelper.createExportIntent(filename),
                            REQUEST_EXPORT_LAYOUT
                        )
                    } catch (e: ActivityNotFoundException) {
                        pendingExport = null
                        showErrorToast(e)
                    }
                }
            }
        }
    }

    private fun setupImportLayout() {
        binding.settingsImportLayoutHolder.setOnClickListener {
            // the file may want cells this device already uses, so ask who wins before
            // picking it, while the choice can still be explained in full
            val items = LayoutBackupHelper.ImportMode.entries.map {
                RadioItem(id = it.ordinal, title = getString(it.labelId()))
            }

            RadioGroupDialog(
                activity = this,
                items = ArrayList(items),
                checkedItemId = importMode.ordinal,
                titleId = R.string.layout_import_mode_title,
                showOKButton = true
            ) {
                importMode = LayoutBackupHelper.ImportMode.entries[it as Int]
                try {
                    startActivityForResult(
                        LayoutBackupHelper.createImportIntent(),
                        REQUEST_IMPORT_LAYOUT
                    )
                } catch (e: ActivityNotFoundException) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun LayoutBackupHelper.ImportMode.labelId() = when (this) {
        LayoutBackupHelper.ImportMode.REPLACE -> R.string.layout_import_mode_replace
        LayoutBackupHelper.ImportMode.KEEP_EXISTING -> R.string.layout_import_mode_keep
        LayoutBackupHelper.ImportMode.DISPLACE_EXISTING -> R.string.layout_import_mode_displace
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        val uri = resultData?.data
        if (resultCode != RESULT_OK || uri == null) {
            if (requestCode == REQUEST_EXPORT_LAYOUT) {
                pendingExport = null
            }

            return
        }

        when (requestCode) {
            REQUEST_EXPORT_LAYOUT -> {
                val export = pendingExport ?: return
                pendingExport = null
                ensureBackgroundThread { writeLayoutBackup(uri, export) }
            }

            REQUEST_IMPORT_LAYOUT -> ensureBackgroundThread { readLayoutBackup(uri) }
        }
    }

    private fun writeLayoutBackup(uri: Uri, export: LayoutBackupHelper.ExportResult) {
        try {
            val output = contentResolver.openOutputStream(uri)
                ?: throw IOException("could not open $uri for writing")

            output.bufferedWriter().use { it.write(export.json) }
            runOnUiThread {
                toast(
                    getString(
                        R.string.layout_export_success,
                        export.exportedCount,
                        export.skippedCount
                    )
                )
            }
        } catch (e: IOException) {
            runOnUiThread { showErrorToast(e) }
        } catch (_: Exception) {
            // the document provider backing the picked uri lives in another process and is
            // free to send back arbitrary unchecked exceptions, so nothing may escape here
            runOnUiThread { showErrorToast(getString(R.string.layout_export_failed)) }
        }
    }

    private fun readLayoutBackup(uri: Uri) {
        // reading and parsing are reported separately, so that a provider that refuses to
        // hand over the file is not blamed on the file's contents
        val json = try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IOException("could not open $uri for reading")

            input.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            runOnUiThread { showErrorToast(e) }
            return
        } catch (_: Exception) {
            // the document provider runs in another process and is free to send back
            // arbitrary unchecked exceptions, so nothing may escape onto this thread
            runOnUiThread { showErrorToast(getString(R.string.layout_import_read_failed)) }
            return
        }

        try {
            val result = LayoutBackupHelper.importLayout(this, json, importMode)
            runOnUiThread {
                toast(
                    getString(
                        R.string.layout_import_success,
                        result.restoredCount,
                        result.skippedCount
                    )
                )
            }
        } catch (_: Exception) {
            // a malformed or truncated file (JSONException) or an unsupported version
            // (IllegalArgumentException)
            runOnUiThread { showErrorToast(getString(R.string.layout_import_invalid_file)) }
        }
    }

    @SuppressLint("NewApi")
    private fun setupLanguage() {
        binding.settingsLanguage.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        binding.settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupManageHiddenIcons() {
        binding.settingsManageHiddenIconsHolder.setOnClickListener {
            startActivity(Intent(this, HiddenIconsActivity::class.java))
        }
    }

    private fun launchAbout() {
        val licenses = 0L
        val faqItems = ArrayList<FAQItem>()

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }
}
