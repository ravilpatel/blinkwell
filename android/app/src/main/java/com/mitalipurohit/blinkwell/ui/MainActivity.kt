package com.mitalipurohit.blinkwell.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mitalipurohit.blinkwell.ui.navigation.BlinkWellNavGraph
import com.mitalipurohit.blinkwell.ui.theme.BlinkWellTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))

        setContent {
            BlinkWellTheme {
                BlinkWellNavGraph()
            }
        }
    }
}
