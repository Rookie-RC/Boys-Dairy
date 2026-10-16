package com.rookierc.boysdairy

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.rookierc.boysdairy.ui.BoysDairyRoot

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block screenshots / screen recording and hide the content in the recents screen.
        // Disabled in debug builds so the screen can be mirrored while developing.
        if (BuildConfig.SECURE_SCREEN) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        enableEdgeToEdge()

        setContent {
            BoysDairyRoot()
        }
    }
}
