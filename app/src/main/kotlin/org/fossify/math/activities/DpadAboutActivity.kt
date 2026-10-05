package org.fossify.math.activities

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import org.fossify.math.BuildConfig
import org.fossify.math.R
import org.fossify.math.databinding.ActivityDpadAboutBinding
import org.fossify.math.helpers.dpadCompactTopBar
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.views.MyTextView

/** App name, version, the keypad shortcuts of each screen and the license; the arrows scroll the page. */
class DpadAboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDpadAboutBinding::inflate)

    /** Screen title, then its (key, action) rows. */
    private val shortcuts = listOf(
        R.string.dpad_about_tabs to listOf(
            R.string.dpad_key_left_right to R.string.dpad_key_calc_left_action,
            R.string.dpad_key_back to R.string.dpad_key_tabs_back_action
        ),
        R.string.app_launcher_name to listOf(
            R.string.dpad_key_digits to R.string.dpad_key_calc_digits_action,
            R.string.dpad_key_star to R.string.dpad_key_calc_star_action,
            R.string.dpad_key_star_hold to R.string.dpad_key_calc_star_hold_action,
            R.string.dpad_key_pound to R.string.dpad_key_calc_pound_action,
            R.string.dpad_key_ok to R.string.dpad_key_calc_ok_action,
            R.string.dpad_key_back to R.string.dpad_key_calc_back_action,
            R.string.dpad_key_back_hold to R.string.dpad_key_calc_back_hold_action,
            R.string.dpad_key_up_down to R.string.dpad_key_calc_up_down_action,
            R.string.dpad_key_menu to R.string.dpad_key_calc_menu_action
        ),
        R.string.unit_converter to listOf(
            R.string.dpad_key_digits to R.string.dpad_key_converter_digits_action,
            R.string.dpad_key_pound to R.string.dpad_key_calc_pound_action,
            R.string.dpad_key_up_down to R.string.dpad_key_converter_up_down_action,
            R.string.dpad_key_left_right to R.string.dpad_key_converter_left_right_action,
            R.string.dpad_key_ok to R.string.dpad_key_converter_ok_action,
            R.string.dpad_key_star to R.string.dpad_key_converter_star_action,
            R.string.dpad_key_star_hold to R.string.dpad_key_converter_star_hold_action,
            R.string.dpad_key_back to R.string.dpad_key_converter_back_action,
            R.string.dpad_key_back_hold to R.string.dpad_key_converter_back_hold_action
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.aboutNestedScrollview))

        binding.aboutVersion.text = getString(R.string.dpad_about_version, BuildConfig.VERSION_NAME)
        binding.aboutIcon.setImageDrawable(packageManager.getApplicationIcon(applicationInfo))
        addShortcuts()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.None)
        dpadCompactTopBar(binding.aboutAppbar, binding.aboutNestedScrollview)
        updateTextColors(binding.aboutHolder)
        listOf(binding.aboutShortcutsLabel, binding.aboutLicenseLabel).forEach { it.setTextColor(getProperPrimaryColor()) }
        binding.aboutNestedScrollview.requestFocus()
    }

    private fun addShortcuts() {
        val keyWidth = resources.getDimensionPixelSize(R.dimen.dpad_about_key_width)
        val smallMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        val sectionMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
        shortcuts.forEach { (screen, keys) ->
            binding.aboutShortcuts.addView(MyTextView(this).apply {
                setText(screen)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, sectionMargin, 0, 0)
            })

            keys.forEach { (key, action) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, smallMargin, 0, 0)
                }

                row.addView(MyTextView(this).apply {
                    setText(key)
                    textSize = 15f
                    alpha = 0.8f
                    gravity = Gravity.START
                }, LinearLayout.LayoutParams(keyWidth, LinearLayout.LayoutParams.WRAP_CONTENT))

                row.addView(MyTextView(this).apply {
                    setText(action)
                    textSize = 15f
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                binding.aboutShortcuts.addView(row)
            }
        }
    }
}
