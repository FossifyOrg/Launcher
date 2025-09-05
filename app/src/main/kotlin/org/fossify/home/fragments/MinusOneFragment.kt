package org.fossify.home.fragments

import android.content.Context
import android.util.AttributeSet
import org.fossify.home.activities.MainActivity
import org.fossify.home.databinding.MinusOneScreenBinding
import org.fossify.home.interfaces.MinusOneFragmentListener

class MinusOneFragment(context: Context, attributeSet: AttributeSet) :
    MyFragment<MinusOneScreenBinding>(context, attributeSet) {

    var listener: MinusOneFragmentListener? = null

    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        binding = MinusOneScreenBinding.bind(this)
    }

    fun refresh() {
        listener?.onRefreshRequested()
    }

    fun hide() {
        listener?.onHideRequested()
    }
}
