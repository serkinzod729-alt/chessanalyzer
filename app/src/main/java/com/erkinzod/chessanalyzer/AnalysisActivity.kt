package com.erkinzod.chessanalyzer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.github.bhlangonijr.chesslib.Board
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

enum class MoveQuality(val label: String, val symbol: String, val colorHex: String) {
    BEST("Лучший", "\u2713", "#81b64c"),
    GOOD("Хороший", "\u2713", "#96af8b"),
    INACCURACY("Неточность", "?!", "#f7c631"),
    MISTAKE("Ошибка", "?", "#ffa459"),
    BLUNDER("Зевок", "??", "#fa412d")
}

class AnalysisActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var movesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val movesSan = intent.getStringExtra("moves") ?: ""
        val whiteName = intent.getStringExtra("white") ?: "White"
        val blackName = intent.getStringExtra("black") ?: "Black"

        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.setPadding(32, 48, 32, 48)

        statusText = TextView(this)
        statusText.textSize = 18f
        statusText.text = "$whiteName vs $blackName\nАнализ партии, подождите..."
        statusText.setPadding(0, 0, 0, 32)

        movesContainer = LinearLayout(this)
        movesContainer.orientation = LinearLayout.VERTICAL

        rootLayout.addView(statusText)
        rootLayout.addView(movesContainer)

        val scrollView = ScrollView(this)
        scrollView.addView(rootLayout)
        setContentView(scrollView)

        analyzeGame(movesSan)
    }

    private fun analyzeGame(movesSan: String) {
        Thread {
            try {
                val sanTokens = movesSan.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val board = Board()
                val fens = mutableListOf(board.fen)

                for (san in sanTokens) {
                    board.doMove(san)
                    fens.add(board.fen)
                }

                val centipawns = mutableListOf<Int>()
                for ((index, fen) in fens.withIndex()) {
                    val cp = evaluatePosition(fen)
                    centipawns.add(cp)
                    runOnUiThread {
                        statusText.text = "Анализ: ${index + 1}/${fens.size} позиций..."
                    }
                }

                val results = mutableListOf<Pair<String, MoveQuality>>()
                for (i in sanTokens.indices) {
                    val moverIsWhite = (i % 2 == 0)
                    val wpBefore = centipawnsToWinPercent(centipawns[i])
                    val wpAfter = centipawnsToWinPercent(centipawns[i + 1])
                    val moverBefore = if (moverIsWhite) wpBefore else 100.0 - wpBefore
                    val moverAfter = if (moverIsWhite) wpAfter else 100.0 - wpAfter
                    val drop = moverBefore - moverAfter
                    val quality = classifyDrop(drop)
                    results.add(Pair(sanTokens[i], quality))
                }

                runOnUiThread {
                    statusText.text = "$whiteName vs $blackName — анализ готов"
                    renderResults(results)
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка анализа: ${e.message}" }
            }
        }.start()
    }

    private fun evaluatePosition(fen: String): Int {
        return try {
            val url = URL("https://chess-api.com/v1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject()
            body.put("fen", fen)
            body.put("depth", 10)

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).readText()
            val json = JSONObject(response)

            if (!json.isNull("mate") && json.optInt("mate", 0) != 0) {
                val mate = json.getInt("mate")
                if (mate > 0) 100000 else -100000
            } else {
                json.optString("centipawns", "0").toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun centipawnsToWinPercent(cp: Int): Double {
        val clamped = cp.coerceIn(-100000, 100000)
        return 50 + 50 * (2.0 / (1.0 + Math.exp(-0.00368208 * clamped)) - 1.0)
    }

    private fun classifyDrop(drop: Double): MoveQuality {
        return when {
            drop <= 2.0 -> MoveQuality.BEST
            drop <= 6.0 -> MoveQuality.GOOD
            drop <= 12.0 -> MoveQuality.INACCURACY
            drop <= 20.0 -> MoveQuality.MISTAKE
            else -> MoveQuality.BLUNDER
        }
    }

    private fun renderResults(results: List<Pair<String, MoveQuality>>) {
        movesContainer.removeAllViews()
        for ((index, pair) in results.withIndex()) {
            val (san, quality) = pair
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, 16, 0, 16)

            val icon = TextView(this)
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(Color.parseColor(quality.colorHex))
            icon.background = bg
            icon.text = quality.symbol
            icon.setTextColor(Color.WHITE)
            icon.gravity = Gravity.CENTER
            icon.textSize = 14f
            val size = 64
            val iconParams = LinearLayout.LayoutParams(size, size)
            iconParams.marginEnd = 24
            icon.layoutParams = iconParams

            val label = TextView(this)
            val moveNumber = (index / 2) + 1
            val side = if (index % 2 == 0) "$moveNumber." else ""
            label.text = "$side $san — ${quality.label}"
            label.textSize = 16f

            row.addView(icon)
            row.addView(label)
            movesContainer.addView(row)
        }
    }
}
