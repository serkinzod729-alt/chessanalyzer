package com.erkinzod.chessanalyzer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.github.bhlangonijr.chesslib.Board
import org.json.JSONArray
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

    private val darkBg = Color.parseColor("#302E2B")
    private val cardBg = Color.parseColor("#3C3A37")
    private val textLight = Color.parseColor("#EDEDED")
    private val textMuted = Color.parseColor("#B0AEAB")

    private lateinit var statusText: TextView
    private lateinit var movesContainer: LinearLayout
    private lateinit var chessBoardView: ChessBoardView
    private lateinit var evalBarView: EvalBarView

    private var whiteName: String = "White"
    private var blackName: String = "Black"

    private var fens: List<String> = emptyList()
    private var centipawns: List<Int> = emptyList()
    private var gameId: String = ""
    private val analysisPrefs by lazy { getSharedPreferences("analysis_cache", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val movesSan = intent.getStringExtra("moves") ?: ""
        whiteName = intent.getStringExtra("white") ?: "White"
        blackName = intent.getStringExtra("black") ?: "Black"
        gameId = intent.getStringExtra("gameId") ?: ""

        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.setBackgroundColor(darkBg)
        rootLayout.setPadding(24, 48, 24, 48)

        val headerText = TextView(this)
        headerText.text = "$whiteName vs $blackName"
        headerText.textSize = 20f
        headerText.setTextColor(textLight)
        headerText.setPadding(0, 0, 0, 24)
        headerText.gravity = Gravity.CENTER

        statusText = TextView(this)
        statusText.textSize = 15f
        statusText.setTextColor(textMuted)
        statusText.text = "Анализ партии, подождите..."
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 0, 0, 24)

        val boardRow = LinearLayout(this)
        boardRow.orientation = LinearLayout.HORIZONTAL
        boardRow.gravity = Gravity.CENTER_VERTICAL
        val boardRowParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        boardRow.layoutParams = boardRowParams

        val density = resources.displayMetrics.density
        val evalBarWidth = (24 * density).toInt()

        evalBarView = EvalBarView(this)
        val evalParams = LinearLayout.LayoutParams(evalBarWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        evalParams.marginEnd = (6 * density).toInt()
        evalBarView.layoutParams = evalParams

        chessBoardView = ChessBoardView(this)
        val boardParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        chessBoardView.layoutParams = boardParams

        boardRow.addView(evalBarView)
        boardRow.addView(chessBoardView)

        movesContainer = LinearLayout(this)
        movesContainer.orientation = LinearLayout.VERTICAL
        movesContainer.setPadding(0, 32, 0, 0)

        rootLayout.addView(headerText)
        rootLayout.addView(statusText)
        rootLayout.addView(boardRow)
        rootLayout.addView(movesContainer)

        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(darkBg)
        scrollView.addView(rootLayout)
        setContentView(scrollView)

        analyzeGame(movesSan)
    }

    private fun loadCachedCentipawns(expectedSize: Int): List<Int>? {
        if (gameId.isBlank()) return null
        val stored = analysisPrefs.getString(gameId, null) ?: return null
        return try {
            val arr = JSONArray(stored)
            if (arr.length() != expectedSize) return null
            (0 until arr.length()).map { arr.getInt(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveCentipawnsToCache(cpList: List<Int>) {
        if (gameId.isBlank()) return
        val arr = JSONArray()
        for (cp in cpList) arr.put(cp)
        analysisPrefs.edit().putString(gameId, arr.toString()).apply()
    }

    private fun analyzeGame(movesSan: String) {
        Thread {
            try {
                val sanTokens = movesSan.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val board = Board()
                val fenList = mutableListOf(board.fen)

                for (san in sanTokens) {
                    board.doMove(san)
                    fenList.add(board.fen)
                }
                fens = fenList

                val cached = loadCachedCentipawns(fenList.size)
                val cpList: MutableList<Int>
                if (cached != null) {
                    cpList = cached.toMutableList()
                    runOnUiThread { statusText.text = "Загружено из кэша" }
                } else {
                    cpList = mutableListOf()
                    for ((index, fen) in fenList.withIndex()) {
                        val cp = evaluatePosition(fen)
                        cpList.add(cp)
                        runOnUiThread {
                            statusText.text = "Анализ: ${index + 1}/${fenList.size} позиций..."
                        }
                    }
                    saveCentipawnsToCache(cpList)
                }
                centipawns = cpList

                val results = mutableListOf<Pair<String, MoveQuality>>()
                for (i in sanTokens.indices) {
                    val moverIsWhite = (i % 2 == 0)
                    val wpBefore = centipawnsToWinPercent(cpList[i])
                    val wpAfter = centipawnsToWinPercent(cpList[i + 1])
                    val moverBefore = if (moverIsWhite) wpBefore else 100.0 - wpBefore
                    val moverAfter = if (moverIsWhite) wpAfter else 100.0 - wpAfter
                    val drop = moverBefore - moverAfter
                    results.add(Pair(sanTokens[i], classifyDrop(drop)))
                }

                runOnUiThread {
                    statusText.text = "Анализ готов"
                    if (fenList.isNotEmpty()) {
                        chessBoardView.fen = fenList.last()
                        evalBarView.whiteWinPercent = centipawnsToWinPercent(cpList.last())
                    }
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

            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            val cardBgDrawable = GradientDrawable()
            cardBgDrawable.cornerRadius = 16f
            cardBgDrawable.setColor(cardBg)
            card.background = cardBgDrawable
            card.setPadding(24, 20, 24, 20)

            val cardParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cardParams.bottomMargin = 12
            card.layoutParams = cardParams

            val icon = TextView(this)
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(Color.parseColor(quality.colorHex))
            icon.background = bg
            icon.text = quality.symbol
            icon.setTextColor(Color.WHITE)
            icon.gravity = Gravity.CENTER
            icon.textSize = 14f
            val iconParams = LinearLayout.LayoutParams(64, 64)
            iconParams.marginEnd = 24
            icon.layoutParams = iconParams

            val label = TextView(this)
            val moveNumber = (index / 2) + 1
            val side = if (index % 2 == 0) "$moveNumber." else ""
            label.text = "$side $san — ${quality.label}"
            label.textSize = 16f
            label.setTextColor(textLight)

            card.addView(icon)
            card.addView(label)

            card.setOnClickListener {
                if (index + 1 < fens.size) {
                    chessBoardView.fen = fens[index + 1]
                    evalBarView.whiteWinPercent = centipawnsToWinPercent(centipawns[index + 1])
                }
            }

            movesContainer.addView(card)
        }
    }
}
