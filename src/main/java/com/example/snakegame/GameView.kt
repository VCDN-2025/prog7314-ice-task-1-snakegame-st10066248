package com.example.snakegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Dir { UP, DOWN, LEFT, RIGHT }

    private val paintSnake = Paint().apply { isAntiAlias = true }
    private val paintFood = Paint().apply { isAntiAlias = true }
    private val paintBg = Paint().apply { isAntiAlias = true }
    private val paintText = Paint().apply { isAntiAlias = true; textSize = 64f }

    private var cellSize = 0f
    private var cols = 20
    private var rows = 30

    // snake stored as list of pairs (col,row) - head at index 0
    private var snake = mutableListOf<Pair<Int, Int>>()
    private var direction = Dir.RIGHT
    private var nextDirection: Dir? = null
    private var food = Pair(5,5)

    private var running = false
    private var score = 0

    private var updateDelay = 140L // ms, speed - lower => faster
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (running) {
                step()
                invalidate()
                postDelayed(this, updateDelay)
            }
        }
    }

    private val gestureDetector = GestureDetectorCompat(context, SwipeListener())

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    init {
        paintBg.color = 0xFF0F1722.toInt() // dark-ish background
        paintSnake.color = 0xFFFFD54F.toInt() // gold
        paintFood.color = 0xFFE57373.toInt()
        paintText.color = 0xFFFFFFFF.toInt()
        paintText.textAlign = Paint.Align.LEFT
        resetGame()
    }

    private fun resetGame() {
        snake.clear()
        val startX = cols / 3
        val startY = rows / 2
        snake.add(Pair(startX, startY))
        snake.add(Pair(startX-1, startY))
        snake.add(Pair(startX-2, startY))
        direction = Dir.RIGHT
        nextDirection = null
        score = 0
        running = true
        placeFood()
        removeCallbacks(updateRunnable)
        postDelayed(updateRunnable, updateDelay)
    }

    fun pauseGame() {
        running = false
        removeCallbacks(updateRunnable)
    }

    fun resumeGame() {
        if (!running) {
            running = true
            postDelayed(updateRunnable, updateDelay)
        }
    }

    private fun placeFood() {
        val empty = mutableListOf<Pair<Int, Int>>()
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                val p = Pair(c,r)
                if (!snake.contains(p)) empty.add(p)
            }
        }
        if (empty.isEmpty()) {
            // Won — reset
            running = false
            onGameOver()
            return
        }
        food = empty.random()
    }

    private fun step() {
        // commit next direction if valid
        nextDirection?.let {
            if (!isOpposite(it, direction)) direction = it
        }
        nextDirection = null

        val head = snake.first()
        val newHead = when(direction) {
            Dir.RIGHT -> Pair(head.first + 1, head.second)
            Dir.LEFT -> Pair(head.first - 1, head.second)
            Dir.UP -> Pair(head.first, head.second - 1)
            Dir.DOWN -> Pair(head.first, head.second + 1)
        }

        // wall collision -> game over
        if (newHead.first !in 0 until cols || newHead.second !in 0 until rows) {
            running = false
            onGameOver()
            return
        }

        // self collision
        if (snake.contains(newHead)) {
            running = false
            onGameOver()
            return
        }

        snake.add(0, newHead)

        if (newHead == food) {
            score += 10
            placeFood()
            // increase speed slightly
            if (updateDelay > 60) updateDelay -= 2
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun isOpposite(a: Dir, b: Dir): Boolean {
        return (a == Dir.UP && b == Dir.DOWN)
                || (a == Dir.DOWN && b == Dir.UP)
                || (a == Dir.LEFT && b == Dir.RIGHT)
                || (a == Dir.RIGHT && b == Dir.LEFT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cellSize = (w.toFloat() / cols.toFloat())
        // adjust rows to fit height nicely
        rows = (h / cellSize).toInt()
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(), paintBg)

        // draw food
        drawCell(canvas, food.first, food.second, paintFood)

        // draw snake
        for ((i, p) in snake.withIndex()) {
            val paint = paintSnake
            // head slightly bigger
            drawCell(canvas, p.first, p.second, paint)
        }

        // score
        canvas.drawText("Score: $score", 16f, 72f, paintText)

        if (!running) {
            // overlay game over
            val midX = width/2f
            val midY = height/2f
            paintText.textAlign = Paint.Align.CENTER
            paintText.textSize = 84f
            canvas.drawText("GAME OVER", midX, midY - 40, paintText)
            paintText.textSize = 52f
            canvas.drawText("Score: $score", midX, midY + 30, paintText)
            paintText.textAlign = Paint.Align.LEFT
            paintText.textSize = 64f
        }
    }

    private fun drawCell(canvas: Canvas, c: Int, r: Int, paint: Paint) {
        val left = c * cellSize
        val top = r * cellSize
        val rect = RectF(left, top, left + cellSize, top + cellSize)
        canvas.drawRoundRect(rect, 8f, 8f, paint)
    }

    // Buttons will call this
    fun setDirection(d: Dir) {
        // avoid reversing instantly
        if (!isOpposite(d, direction)) {
            nextDirection = d
        }
    }

    // save score to Firestore (called when game over)
    private fun onGameOver() {
        // first redraw to show Game Over
        post { invalidate() }

        // save to firestore
        val user = auth.currentUser
        val username = user?.displayName ?: user?.email?.substringBefore("@") ?: "guest"
        val uid = user?.uid ?: "anon-${System.currentTimeMillis()}"

        val data = hashMapOf(
            "uid" to uid,
            "username" to username,
            "score" to score,
            "timestamp" to Timestamp.now()
        )

        db.collection("scores")
            .add(data)
            .addOnSuccessListener {
                // saved - we do not show UI from here in-case activity is gone
            }
            .addOnFailureListener {
                // ignore for now; optionally log
            }
    }

    // touch/swipe for direction
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    private inner class SwipeListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_MIN_DISTANCE = 100
        private val SWIPE_THRESHOLD_VELOCITY = 100

        override fun onDown(e: MotionEvent?): Boolean = true

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y

            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                if (kotlin.math.abs(dx) > SWIPE_MIN_DISTANCE && kotlin.math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    if (dx > 0) setDirection(Dir.RIGHT) else setDirection(Dir.LEFT)
                    return true
                }
            } else {
                if (kotlin.math.abs(dy) > SWIPE_MIN_DISTANCE && kotlin.math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
                    if (dy > 0) setDirection(Dir.DOWN) else setDirection(Dir.UP)
                    return true
                }
            }
            return false
        }
    }
}