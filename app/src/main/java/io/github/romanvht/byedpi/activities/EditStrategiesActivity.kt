package io.github.romanvht.byedpi.activities

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.edit
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.getPreferences

/**
 * Полноэкранный просмотр и правка списка стратегий.
 *
 * Клавиатура спрятана по умолчанию: пульт листает текст и ставит курсор.
 * Клавиатура вызывается центральной кнопкой пульта на нужном месте и убирается
 * кнопкой «Назад». Сохранение — в меню.
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
        textView.setSelection(0)
        textView.clearFocus()

        // Центральная кнопка пульта на поле вызывает клавиатуру. Сама клавиатура
        // не открывается сразу, чтобы не перекрывать текст и не ломать навигацию.
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

    private fun showKeyboard() {
        textView.requestFocus()
        textView.setSelection(textView.selectionStart.coerceAtLeast(0))
        inputMethodManager().showSoftInput(textView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_edit_strategies, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_save_strategies -> {
            save()
            true
        }
        R.id.action_keyboard -> {
            if (inputMethodManager().isAcceptingText) hideKeyboard() else showKeyboard()
            true
        }
        android.R.id.home -> {
            handleBack()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun save() {
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
        setResult(RESULT_OK)
        finish()
    }

    private companion object {
        private const val TAG = "EditStrategies"
    }
}
