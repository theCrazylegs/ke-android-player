package com.thecrazylegs.keplayer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.thecrazylegs.keplayer.data.storage.TokenStorage
import com.thecrazylegs.keplayer.navigation.NavGraph
import com.thecrazylegs.keplayer.ui.theme.KEAndroidPlayerTheme

class MainActivity : ComponentActivity() {
    /**
     * Kill process cleanly. On Android TV boxes, finishAndRemoveTask() alone
     * doesn't kill the process, leaving old ViewModel coroutines running
     * (causes status flip-flop). Kill the process to ensure a fresh start.
     */
    private fun finishAndKill() {
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home button pressed
        finishAndKill()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Back button pressed - same clean shutdown as Home.
        // Without this, the process survives and the server thinks
        // the player is still connected (no PLAYER_EMIT_LEAVE sent).
        super.onBackPressed()
        finishAndKill()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val tokenStorage = TokenStorage(applicationContext)

        setContent {
            KEAndroidPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        tokenStorage = tokenStorage
                    )
                }
            }
        }
    }
}
