package com.taha.newraapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.taha.newraapp.ui.navigation.NewRaNavHost
import com.taha.newraapp.ui.theme.TestRaTheme

import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TestRaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewRaNavHost()
                }
            }
        }
    }
}
