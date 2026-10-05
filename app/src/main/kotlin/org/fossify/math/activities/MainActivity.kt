package org.fossify.math.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.grantland.widget.AutofitHelper
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.databinding.BottomTablayoutItemBinding
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getBottomNavigationBackgroundColor
import org.fossify.commons.extensions.onTabSelectionChanged
import org.fossify.commons.extensions.updateBottomTabItemColors
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.math.BuildConfig
import org.fossify.math.R
import org.fossify.math.adapters.HistoryAdapter
import org.fossify.math.databases.CalculatorDatabase
import org.fossify.math.databinding.ActivityMainBinding
import org.fossify.math.views.SymbolPalette
import org.fossify.math.extensions.calculatorDB
import org.fossify.math.extensions.config
import org.fossify.math.helpers.CalculatorInput
import org.fossify.math.helpers.ExpressionDisplay
import org.fossify.math.helpers.ExpressionEvaluator
import org.fossify.math.helpers.ExpressionEvaluator.EvaluationException
import org.fossify.math.helpers.ExpressionEvaluator.Reason
import org.fossify.math.helpers.dpadCompactTopBar
import org.fossify.math.helpers.getDecimalSeparator
import org.fossify.math.helpers.dpadPopupMenu
import org.fossify.math.models.History
import org.fossify.math.pages.SettingsPage
import org.fossify.math.pages.UnitTypesPage

/**
 * Three tabs like in DumberKit Contacts, ←/→ switch them: the calculator, the unit converter and the settings.
 *
 * The calculator is typed on the keypad like Calc-U-Later: digits directly, '*' an operator (each further press the
 * next one: + − × ÷), '#' the decimal point, OK the result, Back deletes. Hold '*' for %, ^, √, parentheses and ±.
 * ↑/↓ go through the history. In the other tabs Back returns to the calculator.
 */
class MainActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMainBinding::inflate)
    private val historyAdapter = HistoryAdapter()
    private var input = CalculatorInput()
    private lateinit var expressionAutofit: AutofitHelper
    private lateinit var unitTypesPage: UnitTypesPage
    private lateinit var settingsPage: SettingsPage
    private var currentTab = TAB_CALCULATOR

    /** Error shown in place of the preview after OK, until the next key. */
    private var errorMessage: String? = null

    /** What was typed before ↑ started going through the history, back on ↓ past the newest entry. */
    private var draftBeforeHistory: CalculatorInput? = null

    // a long press of '*' or Back does something else than a short one, so both act on key up
    private var starHeld = false
    private var backHeld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupEdgeToEdge(padBottomSystem = listOf(binding.mainTabsHolder))
        unitTypesPage = UnitTypesPage(this, binding.unitTypesPage)
        settingsPage = SettingsPage(this, binding.settingsPage)
        setupTabs()
        showTab(savedInstanceState?.getInt(CURRENT_TAB) ?: TAB_CALCULATOR)

        if (savedInstanceState == null) {
            // a fresh start: the history and the calculation of the last time are gone (also when Android closed
            // the app in the background, nothing else tells that it was closed)
            config.dpadExpression = ""
            config.dpadExpressionIsResult = false
        }
        input = CalculatorInput(config.dpadExpression, config.dpadExpressionIsResult)
        binding.historyList.apply {
            adapter = historyAdapter
            // like a paper tape: a short history sits right above the expression
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            // the keys belong to the calculator, the list only shows the entry picked with ↑/↓
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        expressionAutofit = AutofitHelper.create(binding.expression).apply {
            setMinTextSize(TypedValue.COMPLEX_UNIT_SP, MIN_EXPRESSION_TEXT_SIZE)
        }
        if (savedInstanceState == null) {
            clearStoredHistory()
        } else {
            loadHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.mainAppbar, NavigationIcon.None)
        dpadCompactTopBar(binding.mainAppbar, null)
        updateTextColors(binding.mainHolder)
        val textColor = getProperTextColor()
        binding.expression.setTextColor(textColor)
        binding.preview.setTextColor(textColor)
        binding.mainDivider.setBackgroundColor(textColor)
        historyAdapter.notifyItemRangeChanged(0, historyAdapter.itemCount)
        unitTypesPage.refresh()
        settingsPage.refresh()
        setupTabColors()
        focusCurrentTab()

        if (config.preventPhoneFromSleeping) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        render()
    }

    override fun onPause() {
        super.onPause()
        if (config.preventPhoneFromSleeping) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // closed (Back on an empty calculator): nothing of it stays stored
            clearStoredHistory()
            config.dpadExpression = ""
            config.dpadExpressionIsResult = false
        }
        if (!isChangingConfigurations) {
            CalculatorDatabase.destroyInstance()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(CURRENT_TAB, currentTab)
    }

    private fun setupTabs() {
        // a horizontal scroll view makes itself focusable whatever the layout says
        binding.mainTabsHolder.isFocusable = false
        binding.mainTabsHolder.isFocusableInTouchMode = false
        TAB_ICONS.forEachIndexed { index, icon ->
            binding.mainTabsHolder.newTab().setCustomView(org.fossify.commons.R.layout.bottom_tablayout_item).apply tab@{
                BottomTablayoutItemBinding.bind(customView!!).apply {
                    tabItemIcon.setImageResource(icon)
                    // icons only, like in Contacts: more room for the calculator on a small screen
                    tabItemLabel.beGone()
                    tabItemIcon.contentDescription = getString(TAB_TITLES[index])
                    val iconSize = resources.getDimensionPixelSize(R.dimen.dpad_tab_icon_size)
                    tabItemIcon.layoutParams = tabItemIcon.layoutParams.apply {
                        width = iconSize
                        height = iconSize
                    }
                    (tabItemHolder.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = 0
                }
                binding.mainTabsHolder.addTab(this@tab, false)
                // tabs switch with left/right, they are never a D-pad stop themselves
                view.isFocusable = false
                view.isFocusableInTouchMode = false
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = { updateBottomTabItemColors(it.customView, false, TAB_ICONS[it.position]) },
            tabSelectedAction = {
                updateBottomTabItemColors(it.customView, true, TAB_ICONS[it.position])
                if (it.position != currentTab) {
                    showTab(it.position)
                }
            }
        )
    }

    private fun setupTabColors() {
        (0 until binding.mainTabsHolder.tabCount).forEach { index ->
            updateBottomTabItemColors(binding.mainTabsHolder.getTabAt(index)?.customView, index == currentTab, TAB_ICONS[index])
        }
        binding.mainTabsHolder.setBackgroundColor(getBottomNavigationBackgroundColor())
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        binding.mainHolder.beVisibleIf(tab == TAB_CALCULATOR)
        binding.unitTypesPage.root.beVisibleIf(tab == TAB_CONVERTER)
        binding.settingsPage.root.beVisibleIf(tab == TAB_SETTINGS)
        binding.mainToolbar.setTitle(TAB_TITLES[tab])
        binding.mainTabsHolder.getTabAt(tab)?.takeIf { !it.isSelected }?.select()
        if (tab != TAB_CALCULATOR) {
            stopBrowsingHistory()
        }
        focusCurrentTab()
    }

    /** ←/→: the tab next to this one (no going round, like in Contacts). */
    private fun switchTab(delta: Int) {
        val target = currentTab + delta
        if (target in TAB_ICONS.indices) {
            showTab(target)
        }
    }

    private fun focusCurrentTab() {
        binding.root.post {
            when (currentTab) {
                TAB_CONVERTER -> unitTypesPage.focus()
                TAB_SETTINGS -> settingsPage.focus()
                // the calculator takes all keys itself, nothing on it is focused
                else -> currentFocus?.clearFocus()
            }
        }
    }

    /** Keys of the converter and settings tabs: their rows move with ↑/↓ and open with OK as usual. */
    private fun dispatchOtherTabKey(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP && !event.isCanceled
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (isUp) showTab(TAB_CALCULATOR)
                true
            }

            KeyEvent.KEYCODE_MENU -> true
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun saveInput() {
        config.dpadExpression = input.expression
        config.dpadExpressionIsResult = input.isResult
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isFirstDown = isDown && event.repeatCount == 0
        // popups and dialogs open on key up: one opened on key down would get the key up and stop reacting to keys
        val isUp = event.action == KeyEvent.ACTION_UP && !event.isCanceled

        if (binding.symbolPalette.isShowing) {
            return binding.symbolPalette.onKey(event)
        }

        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (isFirstDown) switchTab(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
            return true
        }

        if (currentTab != TAB_CALCULATOR) {
            return dispatchOtherTabKey(event)
        }

        when (val keyCode = event.keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                if (isFirstDown) edit { input.digit(keyCode - KeyEvent.KEYCODE_0) }
            }

            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                if (isFirstDown) edit { input.digit(keyCode - KeyEvent.KEYCODE_NUMPAD_0) }
            }

            KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_NUMPAD_DOT,
            KeyEvent.KEYCODE_NUMPAD_COMMA -> {
                if (isFirstDown) edit { input.decimalPoint() }
            }

            KeyEvent.KEYCODE_STAR -> when {
                isFirstDown -> starHeld = false
                // the palette shows up while '*' is still held (it ignores that press letting go)
                isDown && !starHeld -> {
                    starHeld = true
                    showMoreFunctions()
                }
                isUp && !starHeld -> edit { input.rotateOperator() }
            }

            KeyEvent.KEYCODE_BACK -> when {
                isFirstDown -> backHeld = false
                isDown && !backHeld -> {
                    backHeld = true
                    edit { input.clear() }
                }
                isUp && !backHeld -> {
                    if (input.isEmpty && draftBeforeHistory == null) {
                        finish()
                    } else {
                        edit { input.backspace() }
                    }
                }
            }

            KeyEvent.KEYCODE_DEL -> {
                if (isDown) edit { input.backspace() }
            }

            // the green call key is big and near the digits, a handy "="
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_NUMPAD_EQUALS -> {
                if (isUp) calculate()
            }

            KeyEvent.KEYCODE_DPAD_UP -> if (isDown) moveInHistory(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isDown) moveInHistory(1)

            KeyEvent.KEYCODE_MENU -> if (isUp) showMenu()

            // a hardware keyboard (emulator)
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> if (isFirstDown) edit { input.typeOperator(ExpressionEvaluator.PLUS) }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> if (isFirstDown) edit { input.typeOperator(ExpressionEvaluator.MINUS) }
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> if (isFirstDown) edit { input.typeOperator(ExpressionEvaluator.TIMES) }
            KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_NUMPAD_DIVIDE -> if (isFirstDown) edit { input.typeOperator(ExpressionEvaluator.DIVIDE) }

            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    /** A change of the expression by a key: leaves the history, clears an error, buzzes if wanted, redraws. */
    private fun edit(change: () -> Unit) {
        change()
        stopBrowsingHistory()
        errorMessage = null
        if (config.vibrateOnButtonPress) {
            binding.root.performHapticFeedback()
        }
        render()
    }

    private fun render() {
        val primary = getProperPrimaryColor()
        // kept right away: the app can be closed without a pause (killed, updated) and opens with it again
        saveInput()
        // a single number (a result) gets smaller rather than broken in two, a long calculation may take two lines
        expressionAutofit.setMaxLines(if (input.hasOperation()) 2 else 1)
        if (input.isEmpty) {
            binding.expression.text = "0"
            binding.expression.alpha = EMPTY_ALPHA
        } else {
            binding.expression.text = ExpressionDisplay.format(input.expression, primary)
            binding.expression.alpha = 1f
        }

        // below the expression only an error after OK, the result itself replaces the expression
        val error = errorMessage
        binding.preview.beVisibleIf(error != null)
        binding.preview.text = error ?: ""
        binding.preview.setTextColor(ContextCompat.getColor(this, R.color.dpad_cancel_red))
    }

    private fun calculate() {
        stopBrowsingHistory()
        try {
            val entry = input.evaluate() ?: return
            errorMessage = null
            if (config.vibrateOnButtonPress) {
                binding.root.performHapticFeedback()
            }
            addToHistory(entry.first, entry.second)
        } catch (e: EvaluationException) {
            errorMessage = getString(
                when (e.reason) {
                    Reason.DIVISION_BY_ZERO -> R.string.formula_divide_by_zero_error
                    Reason.TOO_BIG -> R.string.dpad_error_too_big
                    Reason.INVALID -> R.string.dpad_error_invalid
                }
            )
        }
        render()
    }

    /** ↑ older, ↓ newer: the entry's calculation is put in to be changed or computed again with OK. */
    private fun moveInHistory(step: Int) {
        val count = historyAdapter.itemCount
        if (count == 0) {
            return
        }

        val current = historyAdapter.selectedPosition
        val target = when {
            current == RecyclerView.NO_POSITION && step < 0 -> count - 1
            current == RecyclerView.NO_POSITION -> return
            else -> current + step
        }

        when {
            target < 0 -> return
            target >= count -> {
                // past the newest one: back to what was typed before
                input = draftBeforeHistory ?: CalculatorInput()
                draftBeforeHistory = null
                historyAdapter.selectedPosition = RecyclerView.NO_POSITION
            }

            else -> {
                if (draftBeforeHistory == null) {
                    draftBeforeHistory = CalculatorInput(input.expression, input.isResult)
                }
                historyAdapter.selectedPosition = target
                binding.historyList.smoothScrollToPosition(target)
                input.load(historyAdapter.getItem(target)!!.formula)
            }
        }
        errorMessage = null
        render()
    }

    private fun stopBrowsingHistory() {
        draftBeforeHistory = null
        historyAdapter.selectedPosition = RecyclerView.NO_POSITION
    }

    private fun loadHistory(scrollToEnd: Boolean = true) {
        ensureBackgroundThread {
            // the newest come first from the database, the screen shows them like a paper tape: newest at the bottom
            val items = calculatorDB.getHistory().reversed()
            Handler(Looper.getMainLooper()).post {
                if (isDestroyed) {
                    return@post
                }
                historyAdapter.setItems(items)
                binding.historyList.beVisibleIf(items.isNotEmpty())
                binding.historyPlaceholder.beVisibleIf(items.isEmpty())
                if (scrollToEnd && items.isNotEmpty()) {
                    binding.historyList.scrollToPosition(items.size - 1)
                }
            }
        }
    }

    private fun addToHistory(formula: String, result: String) {
        // the same calculation again (OK on an entry picked with ↑) isn't listed twice in a row
        val newest = historyAdapter.getItem(historyAdapter.itemCount - 1)
        if (newest?.formula == formula && newest.result == result) {
            return
        }

        ensureBackgroundThread {
            calculatorDB.insertOrUpdate(History(id = null, formula = formula, result = result, timestamp = System.currentTimeMillis()))
            loadHistory()
        }
    }

    /** The history is kept only while the app is open. */
    private fun clearStoredHistory() {
        val context = applicationContext
        ensureBackgroundThread {
            context.calculatorDB.deleteHistory()
            if (!isDestroyed) {
                loadHistory()
            }
        }
    }

    private fun clearHistory() {
        ConfirmationDialog(this, getString(R.string.dpad_clear_history_question)) {
            ensureBackgroundThread {
                calculatorDB.deleteHistory()
                loadHistory()
                runOnUiThread { toast(R.string.history_cleared) }
            }
        }
    }

    /** Hold '*': all operators and functions as square tiles laid out like the keypad, picked by digit or OK. */
    private fun showMoreFunctions() {
        val symbols = listOf(
            SymbolPalette.Symbol("+", 1) { input.typeOperator(ExpressionEvaluator.PLUS) },
            SymbolPalette.Symbol("−", 2) { input.typeOperator(ExpressionEvaluator.MINUS) },
            SymbolPalette.Symbol("×", 3) { input.typeOperator(ExpressionEvaluator.TIMES) },
            SymbolPalette.Symbol("÷", 4) { input.typeOperator(ExpressionEvaluator.DIVIDE) },
            SymbolPalette.Symbol("^", 5) { input.power() },
            SymbolPalette.Symbol("√", 6) { input.root() },
            SymbolPalette.Symbol("(", 7) { input.openParenthesis() },
            SymbolPalette.Symbol(")", 8) { input.closeParenthesis() },
            SymbolPalette.Symbol("%", 9) { input.percent() },
            SymbolPalette.Symbol("±", 0) { input.toggleSign() }
        )
        if (currentTab != TAB_CALCULATOR) {
            showTab(TAB_CALCULATOR)
        }
        binding.symbolPalette.show(symbols) { edit { it.action() } }
    }

    /** The result shown after OK, or what the calculation typed so far gives. */
    private fun currentValue(): String? {
        val value = when {
            input.isResult -> input.expression
            else -> input.preview()?.let { CalculatorInput.toExpressionText(it) }
                ?: input.expression.takeIf { it.isNotEmpty() && !input.hasOperation() }
        } ?: return null
        // as it's pasted elsewhere (an SMS…): the phone's decimal separator, no grouping spaces, a plain minus
        return value.removeSuffix(".").replace(ExpressionEvaluator.MINUS, '-').replace(".", getDecimalSeparator())
    }

    private fun copyResult() {
        currentValue()?.let { copyToClipboard(it) }
    }

    /** "Použiť v kalkulačke" in a converter: the converted value comes here like a result, ready to compute with. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val value = intent.getStringExtra(EXTRA_VALUE) ?: return
        stopBrowsingHistory()
        errorMessage = null
        input = CalculatorInput(value, evaluated = true)
        showTab(TAB_CALCULATOR)
        render()
    }

    private fun showMenu() {
        val items = buildList {
            add(R.string.dpad_more_functions to ::showMoreFunctions)
            if (currentValue() != null) {
                add(R.string.dpad_copy_result to ::copyResult)
            }
            if (historyAdapter.itemCount > 0) {
                add(R.string.dpad_clear_history to ::clearHistory)
            }
        }

        dpadPopupMenu(binding.mainToolbar).apply {
            items.forEachIndexed { index, (title, _) -> menu.add(0, index, index, title) }
            setOnMenuItemClickListener {
                items[it.itemId].second()
                true
            }
            show()
        }
    }

    companion object {
        const val EXTRA_VALUE = "dpad_value"
        private const val TAB_CALCULATOR = 0
        private const val TAB_CONVERTER = 1
        private const val TAB_SETTINGS = 2
        private const val CURRENT_TAB = "current_tab"
        private val TAB_ICONS = listOf(
            R.drawable.ic_calculator_vector,
            R.drawable.ic_square_foot_vector,
            org.fossify.commons.R.drawable.ic_settings_cog_vector
        )
        private val TAB_TITLES = listOf(R.string.app_launcher_name, R.string.unit_converter, org.fossify.commons.R.string.settings)

        private const val MIN_EXPRESSION_TEXT_SIZE = 20f
        private const val EMPTY_ALPHA = 0.4f
    }
}
