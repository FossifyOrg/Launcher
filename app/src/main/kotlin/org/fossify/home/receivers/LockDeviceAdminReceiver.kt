package org.fossify.home.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import org.fossify.home.R

class LockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.lock_device_admin_warning)
    }
}
