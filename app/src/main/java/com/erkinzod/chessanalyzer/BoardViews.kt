package com.erkinzod.chessanalyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ChessBoardView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var fen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
        set(value) {
            field = value
            invalidate()
        }

    private val lightPaint = Paint().apply { color = Color.parseColor("#EBECD0") }
    private val darkPaint = Paint().apply { color = Color.parseColor("#779556") }
    private val whitePiecePaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val blackPiecePaint = Paint().apply {
        color = Color.parseColor("#101010")
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = Math.min(width, height)
        val squareSize = size / 8f
        whitePiecePaint.textSize = squareSize * 0.75f
        blackPiecePaint.textSize = squareSize * 0.75f

        // Проход 1: фон всех 64 клеток
        for (rankIndex in 0 until 8) {
            for (fileIndex in 0 until 8) {
                val x0 = fileIndex * squareSize
                val y0 = rankIndex * squareSize
                val isLight = (rankIndex + fileIndex) % 2 == 0
                canvas.drawRect(x0, y0, x0 + squareSize, y0 + squareSize, if (isLight) lightPaint else darkPaint)
            }
        }

        // Проход 2: фигуры поверх фона
        val boardPart = fen.trim().split(" ").firstOrNull() ?: return
        val ranks = boardPart.split("/")
        if (ranks.size != 8) return

        for (rankIndex in 0 until 8) {
            var file = 0
            for (ch in ranks[rankIndex]) {
                if (ch.isDigit()) {
                    file += ch.toString().toInt()
                } else {
                    val symbol = pieceSymbol(ch)
                    if (symbol != null) {
                        val paint = if (ch.isUpperCase()) whitePiecePaint else blackPiecePaint
                        val cx = file * squareSize + squareSize / 2f
                        val cy = rankIndex * squareSize + squareSize / 2f - (paint.ascent() + paint.descent()) / 2f
                        canvas.drawText(symbol, cx, cy, paint)
                    }
                    file += 1
                }
            }
        }
    }

    private fun pieceSymbol(c: Char): String? {
        return when (c) {
            'P' -> "\u2659"
            'N' -> "\u2658"
            'B' -> "\u2657"
            'R' -> "\u2656"
            'Q' -> "\u2655"
            'K' -> "\u2654"
            'p' -> "\u265F"
            'n' -> "\u265E"
            'b' -> "\u265D"
            'r' -> "\u265C"
            'q' -> "\u265B"
            'k' -> "\u265A"
            else -> null
        }
    }
}

class EvalBarView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var whiteWinPercent: Double = 50.0
        set(value) {
            field = value
            invalidate()
        }

    private val whitePaint = Paint().apply { color = Color.parseColor("#EBECD0") }
    private val blackPaint = Paint().apply { color = Color.parseColor("#302E2B") }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val whiteHeight = (height * (whiteWinPercent / 100.0)).toFloat()
        canvas.drawRect(0f, height - whiteHeight, width.toFloat(), height.toFloat(), whitePaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height - whiteHeight, blackPaint)
    }
}
