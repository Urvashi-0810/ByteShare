package com.byteshare.android.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Android 15+ draws apps edge-to-edge (and from API 36 it can't be turned off), so the
 * status bar, navigation bar and keyboard overlap content unless we pad for them.
 */
object SystemBarInsets {

    /**
     * Pads the activity's root view so content sits between the system bars and above the keyboard.
     * If [bottomBar] is given, it fills the navigation-bar area itself (its background runs
     * behind the gesture bar) and the root only pads for the keyboard.
     */
    fun apply(activity: Activity, bottomBar: View? = null) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val baseRoot = Padding(root)
        val baseBar = bottomBar?.let { Padding(it) }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val bottom = if (bottomBar == null) maxOf(bars.bottom, ime.bottom) else ime.bottom
            view.updatePadding(
                left = baseRoot.left + bars.left,
                top = baseRoot.top + bars.top,
                right = baseRoot.right + bars.right,
                bottom = baseRoot.bottom + bottom
            )
            if (bottomBar != null && baseBar != null) {
                // With the keyboard open the bar sits on top of it, so no gesture-bar gap is needed
                bottomBar.updatePadding(bottom = baseBar.bottom + if (ime.bottom > 0) 0 else bars.bottom)
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }

    private class Padding(view: View) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
    }
}
