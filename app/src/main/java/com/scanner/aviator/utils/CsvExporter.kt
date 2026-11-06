package com.scanner.aviator.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.scanner.aviator.data.RoundEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    fun exportAndShare(context: Context, rounds: List<RoundEntity>) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csvBuilder = java.lang.StringBuilder()
        csvBuilder.append("ID,Multiplier,Timestamp,Readable_Time\n")

        for (r in rounds) {
            csvBuilder.append("${r.id},${r.multiplier},${r.timestamp},\"${sdf.format(Date(r.timestamp))}\"\n")
        }

        val cachePath = File(context.cacheDir, "exports")
        cachePath.mkdirs()
        val file = File(cachePath, "aviator_rounds_${System.currentTimeMillis()}.csv")
        file.writeText(csvBuilder.toString())

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Rounds CSV"))
    }
}
