package org.fossify.math.pages

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import org.fossify.commons.activities.CustomizationActivity
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.APP_ICON_IDS
import org.fossify.commons.helpers.APP_LAUNCHER_NAME
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.math.activities.DpadAboutActivity
import org.fossify.math.activities.SimpleActivity
import org.fossify.math.databinding.PageSettingsBinding
import org.fossify.math.extensions.config
import org.fossify.math.extensions.launchChangeAppLanguageIntent
import org.fossify.math.helpers.applyDpadFocusHighlight
import org.fossify.math.helpers.applyTouchInputSetting
import java.util.Locale
import kotlin.system.exitProcess

/** The "Nastavenia" tab: Fossify's settings as plain rows for the keys (the original screen was Compose, for touch). */
class SettingsPage(private val activity: SimpleActivity, private val binding: PageSettingsBinding) {
    private val config get() = activity.config
    private val rows = ArrayList<View>()

    init {
        // the rows become clickable when their actions are set, only then they can be found
        refresh()
        fun collect(view: View) {
            if (view.isClickable && view.id != View.NO_ID && activity.resources.getResourceEntryName(view.id).endsWith("_holder")) {
                rows.add(view)
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collect(view.getChildAt(i))
                }
            }
        }
        collect(binding.settingsHolder)
        rows.forEach {
            it.isFocusable = true
            it.applyDpadFocusHighlight()
        }
    }

    /** Values and colors again, they can change in the screens opened from here. */
    fun refresh() {
        setupCustomizeColors()
        setupTouchInput()
        setupUseEnglish()
        setupLanguage()
        setupShowKeyHints()
        setupVibrate()
        setupPreventPhoneFromSleeping()
        setupAbout()
        activity.updateTextColors(binding.settingsHolder)
        arrayOf(binding.settingsColorCustomizationSectionLabel, binding.settingsGeneralSettingsLabel).forEach {
            it.setTextColor(activity.getProperPrimaryColor())
        }
    }

    /** The focused row, or the first one when the tab is opened. */
    fun focus() {
        val focused = activity.currentFocus
        if (focused == null || focused !in rows || !focused.isShown) {
            binding.settingsNestedScrollview.scrollTo(0, 0)
            rows.firstOrNull { it.isShown }?.requestFocusFromTouch()
        }
    }

    /**
     * Commons' own way in locks the customization behind a "get the Pro version" dialog for apps with a package name
     * not its own; this app has its own name on purpose, so the screen is opened directly. The light, dark and
     * system themes are picked there.
     */
    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            Intent(activity.applicationContext, CustomizationActivity::class.java).apply {
                putExtra(APP_ICON_IDS, activity.getAppIconIDs())
                putExtra(APP_LAUNCHER_NAME, activity.getAppLauncherName())
                activity.startActivity(this)
            }
        }
    }

    private fun setupTouchInput() {
        binding.settingsTouchInput.isChecked = config.dpadTouchEnabled
        binding.settingsTouchInputHolder.setOnClickListener {
            binding.settingsTouchInput.toggle()
            config.dpadTouchEnabled = binding.settingsTouchInput.isChecked
            activity.applyTouchInputSetting()
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        binding.settingsUseEnglish.isChecked = config.useEnglish
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        binding.settingsLanguage.text = Locale.getDefault().displayLanguage
        if (isTiramisuPlus()) {
            binding.settingsLanguageHolder.beVisible()
            binding.settingsLanguageHolder.setOnClickListener {
                activity.launchChangeAppLanguageIntent()
            }
        } else {
            binding.settingsLanguageHolder.beGone()
        }
    }

    private fun setupShowKeyHints() {
        binding.settingsShowKeyHints.isChecked = config.dpadShowKeyHints
        binding.settingsShowKeyHintsHolder.setOnClickListener {
            binding.settingsShowKeyHints.toggle()
            config.dpadShowKeyHints = binding.settingsShowKeyHints.isChecked
        }
    }

    private fun setupVibrate() {
        binding.settingsVibrate.isChecked = config.vibrateOnButtonPress
        binding.settingsVibrateHolder.setOnClickListener {
            binding.settingsVibrate.toggle()
            config.vibrateOnButtonPress = binding.settingsVibrate.isChecked
        }
    }

    private fun setupPreventPhoneFromSleeping() {
        binding.settingsPreventPhoneFromSleeping.isChecked = config.preventPhoneFromSleeping
        binding.settingsPreventPhoneFromSleepingHolder.setOnClickListener {
            binding.settingsPreventPhoneFromSleeping.toggle()
            config.preventPhoneFromSleeping = binding.settingsPreventPhoneFromSleeping.isChecked
        }
    }

    private fun setupAbout() {
        binding.settingsAboutHolder.setOnClickListener {
            activity.startActivity(Intent(activity, DpadAboutActivity::class.java))
        }
    }
}
