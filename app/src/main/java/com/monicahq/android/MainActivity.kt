package com.monicahq.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import com.monicahq.android.instance.instanceHost
import com.monicahq.android.ui.theme.MonicaTheme
import com.monicahq.android.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonicaTheme {
                // Nothing is wired yet: signing up, logging in and choosing the instance come in
                // the next features.
                WelcomeScreen(
                    instanceHost = instanceHost(DEFAULT_INSTANCE_URL),
                    onSignUp = {},
                    onLogIn = {},
                    onInstanceSettings = {},
                )
            }
        }
    }
}
