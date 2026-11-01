package com.alsafa.native8.sales

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

@Composable
fun SalesProductPicker(context: Context, choices: List<SaleChoice>, query: String,
    onQuery: (String) -> Unit, quantity: String, onQuantity: (String) -> Unit,
    onAdd: (SaleChoice) -> Unit) {
    var scanError by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val scanner = BarcodeScanning.getClient()
            try {
                scanner.process(InputImage.fromFilePath(context, uri))
                    .addOnSuccessListener { found ->
                        val value = found.firstOrNull()?.rawValue
                        if (value.isNullOrBlank()) scanError = "لم يتم العثور على باركود"
                        else onQuery(value)
                    }.addOnFailureListener { scanError = it.message ?: "فشل المسح" }
                    .addOnCompleteListener { scanner.close() }
            } catch (e: Exception) {
                scanner.close()
                scanError = e.message ?: "تعذر قراءة الصورة"
            }
        }
    }
    SalesInput("بحث بالاسم أو الباركود", query, onQuery)
    Button(onClick = { launcher.launch("image/*") }) { Text("مسح الباركود من صورة عبر ML Kit") }
    if (scanError.isNotBlank()) Text(scanError)
    SalesInput("الكمية", quantity, onQuantity)
    choices.forEach { item ->
        TextButton(onClick = { onAdd(item) }) {
            Text(item.title + " | المتوفر: " + item.available + " | السعر: " + item.price)
        }
    }
}
