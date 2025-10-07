package com.example.snakegame

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)
        val btnUp: ImageButton = findViewById(R.id.btnUp)
        val btnLeft: ImageButton = findViewById(R.id.btnLeft)
        val btnDown: ImageButton = findViewById(R.id.btnDown)
        val btnRight: ImageButton = findViewById(R.id.btnRight)
        val btnLogin: Button = findViewById(R.id.btnLogin)
        val btnLeaderboard: Button = findViewById(R.id.btnLeaderboard)

        btnUp.setOnClickListener { gameView.setDirection(GameView.Dir.UP) }
        btnLeft.setOnClickListener { gameView.setDirection(GameView.Dir.LEFT) }
        btnDown.setOnClickListener { gameView.setDirection(GameView.Dir.DOWN) }
        btnRight.setOnClickListener { gameView.setDirection(GameView.Dir.RIGHT) }

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnLeaderboard.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }

        // Optional: show game over dialog by polling gameView state -
        // Instead, we can detect when game stops via a small watcher:
        // Poll every 200ms to detect stopped state and show Game Over (simple approach).
        Thread {
            var shown = false
            while (!isFinishing) {
                runOnUiThread {
                    if (!gameView.isShown) return@runOnUiThread
                }
                try { Thread.sleep(200) } catch(_: Exception) {}
                runOnUiThread {
                    // use reflection? simpler: check running via view (we didn't expose running),
                    // Instead, show a dialog when the view is not running AND not shown before by checking last draw:
                    // We'll show a dialog when the view's running is false by checking a small property is tricky.
                    // To keep simple, you can add a public 'isRunning' property to GameView if desired.
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        gameView.resumeGame()
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseGame()
    }
}