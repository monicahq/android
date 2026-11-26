package com.monicahq.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.monicahq.android.ui.theme.MonicaTheme
import com.monicahq.android.welcome.WelcomeRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonicaTheme {
                WelcomeRoute()
            }
        }
    }
}
