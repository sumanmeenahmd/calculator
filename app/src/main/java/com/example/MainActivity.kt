package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.theme.CalendarWebTheme
import com.example.webview.CalendarWebViewModel
import com.example.webview.CalendarWebViewScreen

class MainActivity : ComponentActivity() {
  private val webViewModel: CalendarWebViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CalendarWebTheme {
        CalendarWebViewScreen(viewModel = webViewModel)
      }
    }
  }
}
