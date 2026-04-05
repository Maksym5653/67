package com.mwai.overlay

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class MatrixRainView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val chars = "0123456789ABCDEF<>{}[]|/\\!@#\$%^&*MWAI".toCharArray()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBright = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintFade = Paint(Paint.ANTI_ALIAS_FLAG)

    private var columns = 0
    private var drops = IntArray(0)
    private var speeds = FloatArray(0)
    private var dropChars = Array(0) { Array(0) { ' ' } }
    private var rows = 0
    private val cellSize = 28f
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    init {
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = cellSize * 0.75f
        paintBright.typeface = Typeface.MONOSPACE
        paintBright.textSize = cellSize * 0.75f
        paintBright.color = Color.WHITE
        paintFade.typeface = Typeface.MONOSPACE
        paintFade.textSize = cellSize * 0.75f
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        columns = (w / cellSize).toInt() + 1
        rows = (h / cellSize).toInt() + 2
        drops = IntArray(columns) { Random.nextInt(rows) }
        speeds = FloatArray(columns) { 0.3f + Random.nextFloat() * 0.7f }
        dropChars = Array(columns) { Array(rows) { chars.random() } }
        startRain()
    }

    private val rainRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            // Update chars randomly
            for (col in 0 until columns) {
                val row = drops[col].toInt()
                if (row in 0 until rows) dropChars[col][row] = chars.random()
                if (Random.nextFloat() > 0.95f)
                    dropChars[col][Random.nextInt(rows)] = chars.random()
            }
            // Advance drops
            for (col in 0 until columns) {
                drops[col] = (drops[col] + speeds[col]).toInt()
                if (drops[col] > rows + Random.nextInt(5))
                    drops[col] = -Random.nextInt(rows / 2)
            }
            invalidate()
            handler.postDelayed(this, 60)
        }
    }

    fun startRain() {
        running = true
        handler.post(rainRunnable)
    }

    fun stopRain() {
        running = false
        handler.removeCallbacks(rainRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRain()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        for (col in 0 until columns) {
            val dropRow = drops[col].toInt()
            for (row in 0 until rows) {
                val x = col * cellSize
                val y = (row + 1) * cellSize
                val char = if (row < dropChars[col].size) dropChars[col][row].toString() else "0"

                when {
                    row == dropRow -> {
                        // Bright head
                        paintBright.alpha = 255
                        canvas.drawText(char, x, y, paintBright)
                    }
                    row in (dropRow - 1)..(dropRow) -> {
                        paintBright.alpha = 180
                        canvas.drawText(char, x, y, paintBright)
                    }
                    row < dropRow && row > dropRow - 12 -> {
                        // Green trail — fades with distance
                        val dist = dropRow - row
                        val alpha = ((1f - dist / 12f) * 200).toInt().coerceIn(20, 200)
                        val green = (100 + (1f - dist / 12f) * 155).toInt().coerceIn(80, 255)
                        paint.color = Color.rgb(0, green, 0)
                        paint.alpha = alpha
                        canvas.drawText(char, x, y, paint)
                    }
                    else -> {
                        // Dark background chars
                        if (Random.nextFloat() > 0.97f) {
                            paintFade.color = Color.rgb(0, 50, 0)
                            paintFade.alpha = 60
                            canvas.drawText(char, x, y, paintFade)
                        }
                    }
                }
            }
        }
    }
}
