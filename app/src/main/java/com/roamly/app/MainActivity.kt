package com.roamly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.roamly.app.data.RoamlyRepository
import com.roamly.app.ui.RoamlyMainApp
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = RoamlyRepository.getInstance(applicationContext)

        setContent {
            RoamlyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RoamlyBackground
                ) {
                    RoamlyMainApp(repository = repository)
                }
            }
        }
    }
}
