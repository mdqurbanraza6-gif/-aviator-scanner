package com.scanner.aviator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.scanner.aviator.data.AppDatabase
import com.scanner.aviator.data.RoundEntity
import com.scanner.aviator.databinding.ActivityMainBinding
import com.scanner.aviator.service.ScreenCaptureService
import com.scanner.aviator.utils.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val roundsList = mutableListOf<RoundEntity>()
    private lateinit var listAdapter: ArrayAdapter<String>
    private val displayStrings = mutableListOf<String>()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
            Toast.makeText(this, "Scanning started. Open Chrome with Aviator.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Screen capture permission declined.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayStrings)
        binding.lvHistory.adapter = listAdapter

        requestPermissionsIfNeeded()
        observeData()

        binding.btnStart.setOnClickListener {
            showStartConsentDialog()
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
            Toast.makeText(this, "Scanner stopped.", Toast.LENGTH_SHORT).show()
        }

        binding.btnExport.setOnClickListener {
            if (roundsList.isNotEmpty()) {
                CsvExporter.exportAndShare(this, roundsList)
            } else {
                Toast.makeText(this, "No history available to export.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClear.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).roundDao().clearAll()
            }
        }
    }

    private fun observeData() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        AppDatabase.getInstance(this).roundDao().getAllRoundsLive().observe(this) { rounds ->
            roundsList.clear()
            roundsList.addAll(rounds)

            displayStrings.clear()
            rounds.forEach {
                displayStrings.add("${it.multiplier}x  —  ${sdf.format(Date(it.timestamp))}")
            }
            listAdapter.notifyDataSetChanged()

            updateStatistics(rounds)
        }
    }

    private fun updateStatistics(rounds: List<RoundEntity>) {
        val total = rounds.size
        if (total == 0) {
            binding.tvStats.text = ">= 2x: 0 (0%)\n>= 5x: 0 (0%)\n>= 10x: 0 (0%)\n>= 20x: 0 (0%)\n>= 100x: 0 (0%)\nTotal Rounds: 0 / 5000"
            return
        }

        fun countGte(threshold: Double) = rounds.count { it.multiplier >= threshold }
        fun pct(count: Int) = String.format(Locale.US, "%.1f", (count.toDouble() / total) * 100)

        val c2 = countGte(2.0)
        val c5 = countGte(5.0)
        val c10 = countGte(10.0)
        val c20 = countGte(20.0)
        val c100 = countGte(100.0)

        binding.tvStats.text = """
            >= 2x: $c2 (${pct(c2)}%)
            >= 5x: $c5 (${pct(c5)}%)
            >= 10x: $c10 (${pct(c10)}%)
            >= 20x: $c20 (${pct(c20)}%)
            >= 100x: $c100 (${pct(c100)}%)
            Total Recorded: $total / 5000
        """.trimIndent()
    }

    private fun showStartConsentDialog() {
        AlertDialog.Builder(this)
            .setTitle("Transparent Screen Capture")
            .setMessage("This scanner records the multiplier pill strip visible in your browser. Past results do not predict future outcomes.\n\nGrant screen capture to begin.")
            .setPositiveButton("Proceed") { _, _ ->
                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
