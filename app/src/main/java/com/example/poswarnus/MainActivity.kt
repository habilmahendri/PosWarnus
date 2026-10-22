package com.example.poswarnus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                PosNavGraph()
            }
        }
    }
}
