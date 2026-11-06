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
import androidx.lifecycle.ViewModel

@Composable
fun SalesFrame(title: String, body: @Composable ColumnScope.() -> Unit) {
    MaterialTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))
            body()
        }
    }
}
@Composable
fun SalesInput(label: String, value: String, change: (String)->Unit) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SalesDateInput(label:String,value:String,change:(String)->Unit) {
 val context=androidx.compose.ui.platform.LocalContext.current
 if(!com.alsafa.native8.stylekit.SafaDateTime.fullMode(context)){SalesInput(label,value,change);return}
 OutlinedButton(onClick={com.alsafa.native8.stylekit.SafaDateTime.chooseDate(context,value,change)},modifier=Modifier.fillMaxWidth()) {Text(label+": "+value.ifBlank{"كل التواريخ"})}
}
