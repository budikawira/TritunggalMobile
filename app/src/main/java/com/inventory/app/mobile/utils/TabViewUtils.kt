package com.inventory.app.mobile.utils

import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import com.inventory.app.mobile.R

// Two-line tab style (count large on top, label small below) shared by the Placement/Transfer
// Out/Transfer In scan-result screens. TabLayout's built-in text mode only supports one line
// with a single font size, so each tab gets a custom view (see item_tab_count.xml) instead.
object TabViewUtils {

    fun createCustomTabs(tabLayout: TabLayout, labels: List<String>, selectedPosition: Int) {
        val context = tabLayout.context
        labels.forEachIndexed { index, label ->
            val tab = tabLayout.getTabAt(index) ?: return@forEachIndexed
            val view = LayoutInflater.from(context).inflate(R.layout.item_tab_count, tabLayout, false)
            view.findViewById<TextView>(R.id.textTabLabel).text = label
            tab.customView = view
            setTabSelected(tab, index == selectedPosition)
        }
    }

    fun updateTabCount(tabLayout: TabLayout, position: Int, count: Int) {
        val tab = tabLayout.getTabAt(position) ?: return
        tab.customView?.findViewById<TextView>(R.id.textTabCount)?.text = count.toString()
    }

    fun setTabSelected(tab: TabLayout.Tab, selected: Boolean) {
        val view = tab.customView ?: return
        val color = ContextCompat.getColor(
            view.context,
            if (selected) R.color.primary else R.color.darkGrey
        )
        view.findViewById<TextView>(R.id.textTabCount)?.setTextColor(color)
        view.findViewById<TextView>(R.id.textTabLabel)?.setTextColor(color)
    }
}
