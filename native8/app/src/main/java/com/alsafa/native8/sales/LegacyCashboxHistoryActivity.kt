package com.alsafa.native8.sales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.alsafa.native8.data.migration.LegacyCashboxHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Diagnostic read-only view of the imported legacy source.
 * Original source records are kept unchanged; unified balances project them without reposting.
 */
class LegacyCashboxHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var sections by mutableStateOf<List<LegacyCashboxHistory.Section>>(emptyList())
        var selected by mutableStateOf("")
        var entries by mutableStateOf<List<LegacyCashboxHistory.Entry>>(emptyList())
        var error by mutableStateOf("")
        val repository = LegacyCashboxHistory(applicationContext)
        lifecycleScope.launch {
            try { sections = withContext(Dispatchers.IO) { repository.sections() } }
            catch (e: Exception) { error = e.message ?: "تعذر قراءة الأرشيف" }
        }
        setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("العمليات التاريخية المستعادة — فحص", style = MaterialTheme.typography.titleLarge)
                Text("عرض المصدر الأصلي للعمليات المستعادة. تدخل الحركات المؤهلة في حساب الرصيد الموحد دون إعادة ترحيلها.")
                if (error.isNotEmpty()) Text(error)
                if (sections.isEmpty()) Text("لا توجد أقسام مستعادة في قاعدة الفحص، أو لم يكتمل تحميلها.")
                sections.forEach { section ->
                    OutlinedButton(onClick = {
                        selected = section.name
                        lifecycleScope.launch {
                            try {
                                entries = withContext(Dispatchers.IO) { repository.entries(section.name) }
                                error = ""
                            } catch (e: Exception) { error = e.message ?: "تعذر عرض السجلات" }
                        }
                    }) { Text("${section.name} (${section.count})") }
                }
                if (selected.isNotBlank()) Text("القسم: $selected — آخر 150 سجلاً")
                entries.forEach { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("السجل ${entry.ordinal} — ${entry.sourceId ?: "بلا معرف"}")
                            Text(entry.raw, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
