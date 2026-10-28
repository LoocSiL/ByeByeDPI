package io.github.romanvht.byedpi.activities

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.edit
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.getPreferences

/**
 * Полноэкранный просмотр и правка списка стратегий.
 *
 * Клавиатура спрятана по умолчанию: пульт листает текст и ставит курсор.
 * ОК на тексте вызывает клавиатуру ровно в позиции курсора, «Назад» её прячет.
 * Любой выход с экрана сохраняет изменения — отдельной кнопки сохранения нет.
 */
class EditStrategiesActivity : BaseActivity() {

    private lateinit var textView: EditText

    /** Позиция курсора, зафиксированная перед показом клавиатуры. */
    private var savedSelection = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_strategies)
        setupToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.edit_strategies)

        textView = findViewById(R.id.strategies_text)
        textView.setText(getPreferences().getString("byedpi_proxytest_commands", "").orEmpty())
        textView.setSelection(0)
        textView.clearFocus()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        textView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                // Запоминаем позицию до запроса фокуса: при получении фокуса
                // поле само ставит курсор в конец, и он «улетал».
                savedSelection = textView.selectionStart.coerceIn(0, textView.text?.length ?: 0)
                showKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun inputMethodManager(): InputMethodManager =
        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun showKeyboard() {
        // Сначала фокус и восстановление позиции, только потом показ клавиатуры.
        textView.requestFocus()
        val length = textView.text?.length ?: 0
        val pos = savedSelection.coerceIn(0, length)
        textView.setSelection(pos)
        inputMethodManager().showSoftInput(textView, InputMethodManager.SHOW_IMPLICIT)
        // Некоторые прошивки сдвигают курсор при появлении IME — ставим ещё раз.
        textView.post { textView.setSelection(pos.coerceIn(0, textView.text?.length ?: 0)) }
    }

    private fun hideKeyboard() {
        savedSelection = textView.selectionStart.coerceIn(0, textView.text?.length ?: 0)
        inputMethodManager().hideSoftInputFromWindow(textView.windowToken, 0)
        textView.clearFocus()
    }

    /** «Назад» убирает клавиатуру, если она открыта; иначе сохраняет и закрывает экран. */
    private fun handleBack() {
        if (inputMethodManager().isAcceptingText) {
            hideKeyboard()
        } else {
            save()
        }
    }

    /** Сохранение при любом способе выхода: системная кнопка «назад» тоже сюда попадает. */
    override fun onPause() {
        super.onPause()
        persist()
    }

    private fun save() {
        persist()
        finish()
    }

    private fun persist() {
        val text = textView.text?.toString().orEmpty()
        val cleaned = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString("\n")
        getPreferences().edit {
            putString("byedpi_proxytest_commands", cleaned)
            putBoolean("byedpi_proxytest_usercommands", cleaned.isNotBlank())
        }
        Log.i(TAG, "Saved strategies: ${cleaned.lines().count { it.isNotBlank() }}")
    }

    private companion object {
        private const val TAG = "EditStrategies"
    }
}
