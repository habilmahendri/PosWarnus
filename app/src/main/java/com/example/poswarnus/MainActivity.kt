package com.example.poswarnus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.poswarnus.ui.navigation.PosNavGraph
import com.example.poswarnus.ui.theme.POSWarNusTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            POSWarNusTheme {
                // Paints the window background in the brand's background color regardless of
                // the Android theme XML (which stays Theme.Material.Light for the system chrome).
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PosNavGraph()
                }
            }
        }
    }
}
