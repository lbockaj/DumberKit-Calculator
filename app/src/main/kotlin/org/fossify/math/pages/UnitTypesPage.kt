package org.fossify.math.pages

import android.content.Intent
import android.view.LayoutInflater
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.math.activities.SimpleActivity
import org.fossify.math.activities.UnitConverterActivity
import org.fossify.math.databinding.ItemUnitTypeBinding
import org.fossify.math.databinding.PageUnitTypesBinding
import org.fossify.math.helpers.applyDpadFocusHighlight
import org.fossify.math.helpers.converters.Converter

/** The "Prevodník" tab: length, area, volume… as a list, ↑/↓ and OK. */
class UnitTypesPage(private val activity: SimpleActivity, private val binding: PageUnitTypesBinding) {
    private val rows = ArrayList<ItemUnitTypeBinding>()

    init {
        Converter.ALL.forEachIndexed { index, converter ->
            val row = ItemUnitTypeBinding.inflate(LayoutInflater.from(activity), binding.unitTypesHolder, false)
            row.unitTypeIcon.setImageResource(converter.imageResId)
            row.unitTypeLabel.setText(converter.nameResId)
            row.root.applyDpadFocusHighlight()
            row.root.setOnClickListener { openConverter(index) }
            binding.unitTypesHolder.addView(row.root)
            rows.add(row)
        }
    }

    fun refresh() {
        activity.updateTextColors(binding.unitTypesHolder)
        rows.forEach { it.unitTypeIcon.applyColorFilter(activity.getProperPrimaryColor()) }
    }

    /** The first kind of units (length) is focused whenever the tab is shown, the list scrolled to the top. */
    fun focus() {
        val row = rows.firstOrNull() ?: return
        binding.unitTypesScrollview.scrollTo(0, 0)
        row.root.requestFocusFromTouch()
    }

    private fun openConverter(index: Int) {
        Intent(activity, UnitConverterActivity::class.java).apply {
            putExtra(UnitConverterActivity.EXTRA_CONVERTER_ID, index)
            activity.startActivity(this)
        }
    }
}
