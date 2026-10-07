package com.storepos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.storepos.app.notifications.StorePosAlertWorker
import com.storepos.app.ui.StorePosApp
import com.storepos.app.ui.theme.StorePosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StorePosAlertWorker.schedule(this)
        setContent {
            StorePosTheme {
                StorePosApp()
            }
        }
    }
}
