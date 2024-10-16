package org.fossify.home.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.viewbinding.ViewBinding
import org.fossify.home.activities.MainActivity

abstract class MyFragment<BINDING : ViewBinding>(
    context: Context,
    attributeSet: AttributeSet
) : RelativeLayout(context, attributeSet) {
    protected var activity: MainActivity? = null
    protected lateinit var binding: BINDING

    abstract fun setupFragment(activity: MainActivity)
}
