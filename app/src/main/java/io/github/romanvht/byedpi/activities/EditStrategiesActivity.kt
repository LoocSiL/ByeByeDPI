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
 * ОК на тексте вызывает клавиатуру, «Назад» её прячет.
 * Любой выход с экрана сохраняет изменения — отдельной кнопки сохранения нет.
 */
class EditStrategiesActivity : BaseActivity() {

    private lateinit var textView: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_strategies)
        setupToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.edit_strategies)

        textView = findViewById(R.id.strategies_text)
        textView.setText(getPreferences().getString("byedpi_proxytest_commands", "").orEmpty())

        // Фокус НЕ снимаем: clearFocus + requestFocus заставляли поле ставить
        // курсор в конец текста. Поле держит фокус всё время, меняется только
        // видимость клавиатуры — курсор при этом остаётся на месте.
        textView.requestFocus()
        textView.setSelection(0)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        textView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                showKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun inputMethodManager(): InputMethodManager =
        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    /** Показывает клавиатуру, не трогая курсор и фокус. */
    private fun showKeyboard() {
        inputMethodManager().showSoftInput(textView, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Прячет клавиатуру; фокус и позиция курсора сохраняются. */
    private fun hideKeyboard() {
        inputMethodManager().hideSoftInputFromWindow(textView.windowToken, 0)
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
