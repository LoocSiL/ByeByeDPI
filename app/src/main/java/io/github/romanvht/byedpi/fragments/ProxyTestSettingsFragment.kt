package io.github.romanvht.byedpi.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.*
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.EditStrategiesActivity
import io.github.romanvht.byedpi.activities.FileActivity
import io.github.romanvht.byedpi.utility.*
import java.io.File

/**
 * Настройки теста стратегий.
 *
 * Помимо штатного поля с командами добавлены импорт и экспорт файла стратегий:
 * файл читается/пишется в папке приложения, доступной снаружи без root —
 * Android/data/io.github.romanvht.byedpi/files/strategies.txt
 */
class ProxyTestSettingsFragment : PreferenceFragmentCompat() {

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updatePreferences()
        }

    private val pickStrategies = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val path = data.getStringExtra(FileActivity.EXTRA_PATH) ?: return@registerForActivityResult
        importFrom(File(path))
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.proxy_test_settings, rootKey)

        setupNumberSummary("byedpi_proxytest_delay", R.string.proxytest_delay_desc)
        setupNumberSummary("byedpi_proxytest_requests", R.string.proxytest_requests_desc)
        setupNumberSummary("byedpi_proxytest_limit", R.string.proxytest_limit_desc)
        setupNumberSummary("byedpi_proxytest_timeout", R.string.proxytest_timeout_desc)

        setEditTestPreferenceListenerInt("byedpi_proxytest_delay", 0, 10)
        setEditTestPreferenceListenerInt("byedpi_proxytest_requests", 1, 20)
        setEditTestPreferenceListenerInt("byedpi_proxytest_timeout", 1, 15)
        setEditTestPreferenceListenerInt("byedpi_proxytest_limit", 1, 50)

        setEditTestPreferenceListenerDomain("byedpi_proxytest_sni")

        findPreference<Preference>("byedpi_proxytest_import_file")?.setOnPreferenceClickListener {
            startImport()
            true
        }

        findPreference<Preference>("byedpi_proxytest_edit_list")?.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), EditStrategiesActivity::class.java)
            )
            true
        }

        findPreference<Preference>("byedpi_proxytest_export_file")?.setOnPreferenceClickListener {
            exportToFile()
            true
        }

        updatePreferences()
    }

    override fun onResume() {
        super.onResume()
        updatePreferences()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updatePreferences() {
        val manageDomainLists = findPreferenceNotNull<Preference>("manage_domain_lists")
        val importFile = findPreference<Preference>("byedpi_proxytest_import_file")
        val exportFile = findPreference<Preference>("byedpi_proxytest_export_file")
        val editList = findPreference<Preference>("byedpi_proxytest_edit_list")
        val activeLists = DomainListUtils.getLists(requireContext()).filter { it.isActive }

        manageDomainLists.summary = if (activeLists.isEmpty()) {
            getString(R.string.domain_lists_summary)
        } else {
            activeLists.joinToString(", ") { it.name }
        }

        val current = currentStrategiesText()
        val count = current.lines().count { it.isNotBlank() && !it.startsWith("#") }
        editList?.summary = if (count > 0) {
            getString(R.string.edit_list_summary_count, count)
        } else {
            getString(R.string.edit_list_summary_empty)
        }
        exportFile?.isEnabled = count > 0

        val strategiesFile = File(requireContext().getExternalFilesDir(null), STRATEGIES_FILE)
        val hasFile = strategiesFile.exists() && strategiesFile.length() > 0
        importFile?.summary = if (hasFile) {
            getString(R.string.import_file_summary_loaded, strategiesFile.length())
        } else {
            getString(R.string.import_file_summary_empty)
        }
    }

    private fun startImport() {
        // Встроенный браузер файлов приложения: на телевизорах часто нет
        // системного выбора документов, поэтому ACTION_GET_CONTENT не годится.
        val intent = Intent(requireContext(), FileActivity::class.java).apply {
            putExtra(FileActivity.EXTRA_MODE, FileActivity.MODE_OPEN)
            putExtra(FileActivity.EXTRA_TYPE, FileActivity.TYPE_STRATEGIES)
            putExtra(FileActivity.EXTRA_EXTENSION, "txt")
        }
        pickStrategies.launch(intent)
    }

    private fun importFrom(file: File) {
        val context = requireContext()
        try {
            val text = file.readText().replace("\r\n", "\n")
            if (text.isBlank()) {
                Toast.makeText(context, R.string.import_file_empty, Toast.LENGTH_SHORT).show()
                return
            }
            val usable = text.lines()
                .map { it.trim() }
                .count { it.isNotEmpty() && !it.startsWith("#") }
            if (usable == 0) {
                Toast.makeText(context, R.string.import_file_empty, Toast.LENGTH_SHORT).show()
                return
            }
            val target = File(context.getExternalFilesDir(null), STRATEGIES_FILE)
            target.parentFile?.mkdirs()
            // В настройки пишется только рабочие строки: комментарии из файла
            // нужны при чтении, но в поле списка они мешают и съедают лимит теста.
            val cleaned = text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .joinToString("\n")
            target.writeText(text)
            sharedPreferences?.edit {
                putBoolean("byedpi_proxytest_usercommands", true)
                putString("byedpi_proxytest_commands", cleaned)
            }
            findPreference<SwitchPreference>("byedpi_proxytest_usercommands")?.isChecked = true
            Toast.makeText(context, getString(R.string.import_file_done, usable), Toast.LENGTH_SHORT).show()
            updatePreferences()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to import strategies", exception)
            Toast.makeText(context, R.string.import_file_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val saveStrategies = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val path = data.getStringExtra(FileActivity.EXTRA_PATH) ?: return@registerForActivityResult
        val uri = data.data
        val content = currentStrategiesText()
        if (content.isBlank()) return@registerForActivityResult
        val context = requireContext()
        try {
            val target = File(path)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            } else {
                target.writeText(content)
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.test_settings_export_file)
                .setMessage(getString(R.string.export_file_done, target.absolutePath))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            updatePreferences()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to export strategies", exception)
            Toast.makeText(context, R.string.export_file_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentStrategiesText(): String {
        val fromPrefs = sharedPreferences?.getString("byedpi_proxytest_commands", "").orEmpty()
        if (fromPrefs.isNotBlank()) return fromPrefs
        // Поле в настройках могло остаться пустым, хотя файл уже загружен
        val file = File(requireContext().getExternalFilesDir(null), STRATEGIES_FILE)
        return if (file.exists()) runCatching { file.readText() }.getOrDefault("") else ""
    }

    private fun exportToFile() {
        val context = requireContext()
        val content = currentStrategiesText()
        if (content.isBlank()) {
            Toast.makeText(context, R.string.export_file_empty, Toast.LENGTH_SHORT).show()
            return
        }
        // Сохраняем через встроенный браузер файлов: можно выбрать любую папку,
        // включая Documents, и файл будет доступен вне приватной папки приложения.
        val intent = Intent(context, FileActivity::class.java).apply {
            putExtra(FileActivity.EXTRA_MODE, FileActivity.MODE_CREATE)
            putExtra(FileActivity.EXTRA_TYPE, FileActivity.TYPE_STRATEGIES)
            putExtra(FileActivity.EXTRA_EXTENSION, "txt")
        }
        saveStrategies.launch(intent)
    }

    private fun setupNumberSummary(key: String, descriptionResId: Int) {
        val pref = findPreference<EditTextPreference>(key) ?: return

        pref.summaryProvider = Preference.SummaryProvider<EditTextPreference> { p ->
            val value = p.text ?: "—"
            val description = getString(descriptionResId)
            "$value - $description"
        }
    }

    private companion object {
        private const val TAG = "ProxyTestSettings"
        private const val STRATEGIES_FILE = "strategies.txt"
    }
}
