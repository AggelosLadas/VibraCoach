package com.aggelosladas.vibracoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aggelosladas.vibracoach.presentation.DashboardScreen
import com.aggelosladas.vibracoach.presentation.DashboardViewModel
import com.aggelosladas.vibracoach.ui.theme.VibraCoachTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibraCoachTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    val dashboardViewModel: DashboardViewModel = viewModel(
                        factory = DashboardViewModel.provideFactory(applicationContext)
                    )
                    DashboardScreen(viewModel = dashboardViewModel)
                }
            }
        }
    }
}
