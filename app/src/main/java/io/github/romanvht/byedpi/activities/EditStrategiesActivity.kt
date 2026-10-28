package io.github.romanvht.byedpi.activities

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.getPreferences

/**
 * Список стратегий построчно.
 *
 * Многострочное поле ввода на телевизоре не работает с пультом: курсор уходит
 * в конец и не двигается. Поэтому здесь каждая стратегия — отдельный пункт
 * списка: пульт ходит по строкам, ОК открывает правку одной строки,
 * меню позволяет добавить, удалить или переименовать по порядку.
 */
class EditStrategiesActivity : BaseActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: StrategyAdapter
    private val lines = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_strategies)
        setupToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.edit_strategies)

        lines.addAll(loadStrategies())
        adapter = StrategyAdapter()
        listView = findViewById(R.id.strategies_list)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            editLine(position)
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = save()
        })
    }

    private fun loadStrategies(): List<String> {
        val text = getPreferences().getString("byedpi_proxytest_commands", "").orEmpty()
        return text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
    }

    /**
     * Диалог правки одной строки.
     *
     * Ширина принудительно растягивается на весь экран: стандартный диалог
     * оставляет стратегию за пределами окна, и её не видно целиком.
     */
    private fun showStrategyDialog(
        titleRes: Int,
        initial: String,
        showDelete: Boolean,
        onSave: (String) -> Unit
    ) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_strategy, null)
        val input = view.findViewById<android.widget.EditText>(R.id.strategy_line)
        input.setText(initial)
        input.setSelection(input.text.length)
        input.requestFocus()

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) onSave(value)
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (showDelete) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                onSave("")
            }
        }

        val dialog = builder.create()
        dialog.show()

        // Максимальная ширина: на телевизоре диалог по умолчанию занимает
        // половину экрана, а команды стратегий длинные.
        val metrics = resources.displayMetrics
        dialog.window?.setLayout(
            (metrics.widthPixels * 0.96).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun editLine(position: Int) {
        showStrategyDialog(
            titleRes = R.string.edit_strategy_title,
            initial = lines.getOrElse(position) { "" },
            showDelete = true
        ) { value ->
            if (value.isEmpty()) {
                if (position < lines.size) lines.removeAt(position)
            } else if (position < lines.size) {
                lines[position] = value
            } else {
                lines.add(value)
            }
            adapter.notifyDataSetChanged()
            persist()
        }
    }

    private fun addLine() {
        showStrategyDialog(
            titleRes = R.string.add_strategy_title,
            initial = "",
            showDelete = false
        ) { value ->
            lines.add(value)
            adapter.notifyDataSetChanged()
            persist()
        }
    }

    private fun save() {
        persist()
        finish()
    }

    private fun persist() {
        val cleaned = lines.filter { it.isNotBlank() && !it.startsWith("#") }.joinToString("\n")
        getPreferences().edit {
            putString("byedpi_proxytest_commands", cleaned)
            putBoolean("byedpi_proxytest_usercommands", cleaned.isNotBlank())
        }
        Log.i(TAG, "Saved strategies: ${lines.size}")
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_strategies, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_add_strategy -> {
            addLine()
            true
        }
        android.R.id.home -> {
            save()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private inner class StrategyAdapter : BaseAdapter() {
        override fun getCount(): Int = lines.size
        override fun getItem(position: Int): Any = lines[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(R.layout.item_strategy_line, parent, false)
            val index = view.findViewById<TextView>(R.id.strategy_index)
            val text = view.findViewById<TextView>(R.id.strategy_command)
            index.text = getString(R.string.strategy_index, position + 1)
            text.text = lines[position]
            return view
        }
    }

    private companion object {
        private const val TAG = "EditStrategies"
    }
}
