package com.example.snakegame

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeaderboardActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)
        listView = findViewById(R.id.listView)

        loadTopScores()
    }

    private fun loadTopScores() {
        db.collection("scores")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { snap ->
                val items = snap.documents.map { doc ->
                    val username = doc.getString("username") ?: "unknown"
                    val score = doc.getLong("score") ?: 0L
                    "$username — $score"
                }
                listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
            }
            .addOnFailureListener {
                // show empty or toast
            }
    }
}