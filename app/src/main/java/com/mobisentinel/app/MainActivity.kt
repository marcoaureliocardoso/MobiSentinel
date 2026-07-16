package com.mobisentinel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.mobisentinel.app.ui.theme.MobiSentinelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobiSentinelTheme {
                Text("MobiSentinel")
            }
        }
    }
}
