package org.fossify.math.views

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.ScrollView
import android.widget.TextView

/**
 * Root of Commons' plain message dialog. Commons shows a "fake version, get the original from its website" warning in
 * every app with a package name not its own (on the first start and then at random); this app has its own name on
 * purpose, so that one message is closed right away, like pressing Back. Every other message stays as it is.
 */
class DpadMessageScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val message = findViewById<TextView>(org.fossify.commons.R.id.message)?.text?.toString() ?: return
        if (!message.contains("fake version", true)) {
            return
        }

        rootView.alpha = 0f
        post {
            val now = SystemClock.uptimeMillis()
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
                rootView.dispatchKeyEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0))
            }
        }
    }
}
