package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import io.github.romanvht.byedpi.R
import androidx.core.content.edit
import io.github.romanvht.byedpi.utility.getPreferences

/**
 * Полноэкранный редактор списка стратегий.
 *
 * Отдельная активность, а не диалог: диалог на телевизоре перекрывается экранной
 * клавиатурой, и пультом до текста не добраться. Здесь поле занимает весь экран,
 * клавиатура вызывается по кнопке «Правка», а кнопка сохранения — в меню.
 */
class EditStrategiesActivity : BaseActivity() {

    private lateinit var textView: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_edit_strategies)
        setupToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.edit_strategies)

        textView = findViewById(R.id.strategies_text)
        textView.setText(getPreferences().getString("byedpi_proxytest_commands", "").orEmpty())
        // Курсор в начало: иначе он оказывается в конце и пультом до верха не добраться
        textView.setSelection(0)
        textView.clearFocus()
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
        R.id.action_edit_strategies -> {
            textView.requestFocus()
            textView.setSelection(textView.text.length)
            true
        }
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun save() {
        val text = textView.text.toString()
        getPreferences().edit {
            putString("byedpi_proxytest_commands", text)
            putBoolean("byedpi_proxytest_usercommands", text.isNotBlank())
        }
        Toast.makeText(this, R.string.strategies_saved, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SAVED, true))
        finish()
    }

    companion object {
        const val EXTRA_SAVED = "saved"
    }
}
