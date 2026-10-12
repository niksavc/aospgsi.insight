package com.aospgsi.insight.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AospGsiInsightApp() {
  var selectedTab by remember { mutableStateOf("Library") }
  MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("AOSP GSI Insight", style = MaterialTheme.typography.headlineLarge)
        Text("APK inspection and patching workbench")
        Text("Prototype scaffold · no APK is modified by this screen.")
        Button(onClick = { selectedTab = "Library" }) { Text("Library") }
        Button(onClick = { selectedTab = "Patch workspace" }) { Text("Patch workspace") }
        Button(onClick = { selectedTab = "Backends" }) { Text("Execution backends") }
        Text("Current section: $selectedTab", style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}
