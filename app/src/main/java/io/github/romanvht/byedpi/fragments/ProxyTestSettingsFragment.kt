package io.github.romanvht.byedpi.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.*
import io.github.romanvht.byedpi.R
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
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            importFrom(uri)
        }
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
        val switchUserCommands = findPreferenceNotNull<SwitchPreference>("byedpi_proxytest_usercommands")
        val textUserCommands = findPreferenceNotNull<EditTextPreference>("byedpi_proxytest_commands")
        val manageDomainLists = findPreferenceNotNull<Preference>("manage_domain_lists")
        val importFile = findPreference<Preference>("byedpi_proxytest_import_file")
        val exportFile = findPreference<Preference>("byedpi_proxytest_export_file")
        val activeLists = DomainListUtils.getLists(requireContext()).filter { it.isActive }

        manageDomainLists.summary = if (activeLists.isEmpty()) {
            getString(R.string.domain_lists_summary)
        } else {
            activeLists.joinToString(", ") { it.name }
        }

        textUserCommands.isEnabled = switchUserCommands.isChecked

        val strategiesFile = File(requireContext().getExternalFilesDir(null), STRATEGIES_FILE)
        val hasFile = strategiesFile.exists() && strategiesFile.length() > 0
        importFile?.summary = if (hasFile) {
            getString(R.string.import_file_summary_loaded, strategiesFile.absolutePath)
        } else {
            getString(R.string.import_file_summary_empty)
        }
        exportFile?.isEnabled = switchUserCommands.isChecked
    }

    private fun startImport() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/plain"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            pickStrategies.launch(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to open document picker", exception)
            Toast.makeText(requireContext(), R.string.file_picker_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun importFrom(uri: Uri) {
        val context = requireContext()
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text.isNullOrBlank()) {
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
            target.writeText(text)
            // Продублировать в настройки, чтобы стратегии были видны и без файла
            sharedPreferences?.edit {
                putBoolean("byedpi_proxytest_usercommands", true)
                putString("byedpi_proxytest_commands", text)
            }
            Toast.makeText(context, getString(R.string.import_file_done, usable), Toast.LENGTH_SHORT).show()
            updatePreferences()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to import strategies", exception)
            Toast.makeText(context, R.string.import_file_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToFile() {
        val context = requireContext()
        val target = File(context.getExternalFilesDir(null), STRATEGIES_FILE)
        val content = sharedPreferences?.getString("byedpi_proxytest_commands", "").orEmpty()
        if (content.isBlank()) {
            Toast.makeText(context, R.string.export_file_empty, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            target.parentFile?.mkdirs()
            target.writeText(content)
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
