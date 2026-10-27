package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.StrategyResultAdapter
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.data.TestState
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TestActivity : BaseActivity() {

    private companion object {
        private const val TAG = "TestActivity"
    }

    private lateinit var strategiesRecyclerView: RecyclerView
    private lateinit var progressTextView: TextView
    private lateinit var disclaimerTextView: TextView
    private lateinit var startStopButton: MaterialButton
    private lateinit var strategyAdapter: StrategyResultAdapter
    private lateinit var cmdHistoryUtils: HistoryUtils

    private val strategies = mutableListOf<StrategyResult>()
    private var renderedRunId = -1L
    private val isTesting: Boolean get() = TestService.isRunning
    private val prefs by lazy { getPreferences() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceManager.refresh(this)
        setContentView(R.layout.activity_proxy_test)
        setupToolbar()

        cmdHistoryUtils = HistoryUtils(this)
        strategiesRecyclerView = findViewById(R.id.strategiesRecyclerView)
        startStopButton = findViewById(R.id.startStopButton)
        progressTextView = findViewById(R.id.progressTextView)
        disclaimerTextView = findViewById(R.id.disclaimerTextView)

        strategyAdapter = StrategyResultAdapter(this,
            onApply = { command ->
                addToHistory(command)
            }
        )

        strategiesRecyclerView.layoutManager = LinearLayoutManager(this)
        strategiesRecyclerView.adapter = strategyAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TestService.state.collect { state ->
                    renderState(state)
                    if (!state.isLoaded && !state.isRunning) TestService.loadResults(applicationContext)
                }
            }
        }

        startStopButton.setOnClickListener {
            startStopButton.isClickable = false

            if (isTesting) TestService.stop() else TestService.start(this)

            startStopButton.postDelayed({ startStopButton.isClickable = true }, 1000)
        }

        startStopButton.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                startStopButton.strokeWidth = 10
                startStopButton.strokeColor = ColorStateList.valueOf(android.graphics.Color.argb(100, 0, 0, 0))
            } else {
                startStopButton.strokeWidth = 0
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isTesting && appStatus.first == AppStatus.Running) {
                    val intent = Intent(this@TestActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                }
                finish()
            }
        })

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_test, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy_log -> {
                copyLog()
                true
            }
            R.id.action_export_results -> {
                exportResults()
                true
            }
            R.id.action_settings -> {
                if (!isTesting) {
                    val intent = Intent(this, TestSettingsActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.test_unavailable, Toast.LENGTH_SHORT).show()
                }
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun renderState(state: TestState) {
        startStopButton.text = getString(if (state.isRunning) R.string.test_stop else R.string.test_start)
        startStopButton.isEnabled = !state.isStopping
        if (state.isRunning) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        progressTextView.text = when {
            state.isRunning && state.currentStrategy > 0 -> getString(R.string.test_process, state.currentStrategy, state.strategies.size)
            state.isRunning -> ""
            state.failed || state.wasInterrupted -> getString(R.string.test_proxy_error)
            state.strategies.isNotEmpty() -> getString(R.string.test_complete)
            else -> ""
        }
        disclaimerTextView.visibility = if (state.wasInterrupted || (!state.isRunning && state.strategies.isEmpty())) View.VISIBLE else View.GONE
        disclaimerTextView.text = getString(if (state.wasInterrupted) R.string.test_crash else R.string.test_disclaimer)

        strategyAdapter.setTestingState(state.isRunning)
        val replace = renderedRunId != state.runId || strategies.map { it.command } != state.strategies.map { it.command }
        val reorder = replace || strategies.indices.any { strategies[it].isCompleted != state.strategies[it].isCompleted }
        if (replace) {
            strategies.clear()
            strategies.addAll(state.strategies.map { it.copy(siteResults = it.siteResults.toMutableList()) })
            renderedRunId = state.runId
        } else {
            strategies.forEachIndexed { index, strategy ->
                val value = state.strategies[index]
                if (strategy.copy(isExpanded = value.isExpanded) != value) {
                    strategy.successCount = value.successCount
                    strategy.totalRequests = value.totalRequests
                    strategy.currentProgress = value.currentProgress
                    strategy.isCompleted = value.isCompleted
                    strategy.siteResults.clear()
                    strategy.siteResults.addAll(value.siteResults)
                    if (!reorder) strategyAdapter.updateStrategy(strategy)
                }
            }
        }
        if (reorder) strategyAdapter.updateStrategies(strategies)
    }

    private fun addToHistory(command: String) {
        if (isTesting) return

        prefs.edit(commit = true) { putString("byedpi_cmd_args", command) }
        lifecycleScope.launch(Dispatchers.IO) {
            cmdHistoryUtils.addCommand(command)

            val mode = prefs.mode()
            if (mode == Mode.VPN && VpnService.prepare(this@TestActivity) != null) return@launch

            val toastText = withContext(Dispatchers.Main) {
                if (!isTesting && appStatus.first == AppStatus.Running) {
                    ServiceManager.restart(this@TestActivity, mode)
                    R.string.service_restart
                } else {
                    R.string.cmd_history_applied
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@TestActivity, toastText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyLog() {
        val completeStrategies = strategies.filter { it.isCompleted }

        if (completeStrategies.isEmpty()) {
            Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()

        completeStrategies.forEach { strategy ->
            sb.appendLine("${strategy.command}\n")

            strategy.siteResults.forEach { site ->
                sb.appendLine("${site.site} - ${site.successCount}/${site.totalCount}")
            }

            sb.appendLine("\n${strategy.successCount}/${strategy.totalRequests}")
            sb.appendLine("-------------")
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("proxy_test_log", sb.toString())
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    private val resultsRegister = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uri = data.data
        val path = data.getStringExtra(FileActivity.EXTRA_PATH)
        val file = if (path == null) null else File(path)
        if (uri == null && file == null) return@registerForActivityResult

        writeResults(uri, file)
    }

    private fun exportResults() {
        if (strategies.none { it.isCompleted }) {
            Toast.makeText(this, R.string.results_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, FileActivity::class.java)
        intent.putExtra(FileActivity.EXTRA_MODE, FileActivity.MODE_CREATE)
        intent.putExtra(FileActivity.EXTRA_TYPE, FileActivity.TYPE_RESULTS)
        resultsRegister.launch(intent)
    }

    /**
     * Полный отчёт по всем завершённым стратегиям: команда, общий счёт,
     * разбивка по каждому проверенному сайту и доля успеха.
     * Пишется и в выбранный файл, и во внешнюю папку приложения как страховка.
     */
    private fun writeResults(uri: Uri?, file: File?) {
        val payload = buildResultsJson()
        lifecycleScope.launch(Dispatchers.IO) {
            var written = false
            try {
                val stream = when {
                    uri != null -> contentResolver.openOutputStream(uri)
                    file != null -> file.outputStream()
                    else -> null
                }
                if (stream != null) {
                    stream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    written = true
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to write results", exception)
            }

            if (!written) {
                val fallback = File(getExternalFilesDir(null), "results_export.json")
                try {
                    fallback.writeText(payload)
                    written = true
                    Log.i(TAG, "Results saved to ${fallback.absolutePath}")
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to write results fallback", exception)
                }
            }

            val saved = written
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@TestActivity,
                    if (saved) R.string.results_saved else R.string.results_save_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildResultsJson(): String {
        val complete = strategies.filter { it.isCompleted }
        val root = JSONObject()
        root.put("app", "io.github.romanvht.byedpi")
        root.put("export", "proxy_test_results")
        root.put("timestamp", System.currentTimeMillis())
        val strategiesArray = JSONArray()
        complete.forEach { strategy ->
            val item = JSONObject()
            item.put("command", strategy.command)
            item.put("successCount", strategy.successCount)
            item.put("totalRequests", strategy.totalRequests)
            item.put("successPercentage", strategy.successPercentage)
            val sites = JSONArray()
            strategy.siteResults.forEach { site ->
                val siteObject = JSONObject()
                siteObject.put("site", site.site)
                siteObject.put("successCount", site.successCount)
                siteObject.put("totalCount", site.totalCount)
                siteObject.put("percentage", if (site.totalCount > 0) site.successCount * 100 / site.totalCount else 0)
                sites.put(siteObject)
            }
            item.put("sites", sites)
            strategiesArray.put(item)
        }
        root.put("strategies", strategiesArray)
        return root.toString(2)
    }
}
