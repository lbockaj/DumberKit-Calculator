package org.fossify.math.helpers

import android.annotation.SuppressLint
import android.content.Context
import org.fossify.commons.activities.CustomizationActivity
import org.fossify.commons.models.MyTheme

/*
 * Commons' predefined themes (light, dark, white…) each come with their own launcher icon color: picking one of them
 * turns the icon green, and a theme is only recognized again (instead of "Custom") while the icon has that color.
 * Here the icon keeps the color picked for it: every predefined theme takes over the icon's current color.
 */

/** Called before the screen sets up its themes, while Commons' own theme list is still empty. */
@SuppressLint("DiscouragedPrivateApi")
fun CustomizationActivity.keepIconColorWithThemes() {
    try {
        val themesField = CustomizationActivity::class.java.getDeclaredField("predefinedThemes").apply { isAccessible = true }
        val iconField = CustomizationActivity::class.java.getDeclaredField("curAppIconColor").apply { isAccessible = true }
        themesField.set(this, IconKeepingThemes(this) { iconField.getInt(this) })
    } catch (ignored: Exception) {
        // not reachable in this Commons version: themes change the icon color as before
    }
}

private class IconKeepingThemes(context: Context, private val currentIconColor: () -> Int) : LinkedHashMap<Int, MyTheme>() {
    private val iconColors = context.resources.getIntArray(org.fossify.commons.R.array.md_app_icon_colors)
    private val iconColorIds = context.resources.obtainTypedArray(org.fossify.commons.R.array.md_app_icon_colors).let { array ->
        IntArray(array.length()) { array.getResourceId(it, 0) }.also { array.recycle() }
    }

    /** The color resource of the icon's current color, if it's one of the selectable icon colors. */
    private fun iconColorId(): Int? = iconColorIds.getOrNull(iconColors.indexOf(currentIconColor()))?.takeIf { it != 0 }

    // "Custom" has no colors of its own (all 0), it's left alone
    private fun MyTheme.withIcon(id: Int) = if (appIconColorId == 0 || appIconColorId == id) this else copy(appIconColorId = id)

    // the icon color can change while the screen is open, so the themes follow it whenever they are looked at
    private fun followIcon() {
        val id = iconColorId() ?: return
        keys.toList().forEach { key -> super.get(key)?.let { super.put(key, it.withIcon(id)) } }
    }

    override fun put(key: Int, value: MyTheme): MyTheme? = super.put(key, iconColorId()?.let { value.withIcon(it) } ?: value)

    override fun get(key: Int): MyTheme? {
        followIcon()
        return super.get(key)
    }

    override val entries: MutableSet<MutableMap.MutableEntry<Int, MyTheme>>
        get() {
            followIcon()
            return super.entries
        }
}
