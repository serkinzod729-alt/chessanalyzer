package com.erkinzod.chessanalyzer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
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
    private val highlightBg = Color.parseColor("#5A5754")

    private lateinit var statusText: TextView
    private lateinit var chessBoardView: ChessBoardView
    private lateinit var evalBarView: EvalBarView

    private lateinit var explanationCard: LinearLayout
    private lateinit var explanationIcon: TextView
    private lateinit var explanationLabel: TextView

    private lateinit var moveStripScroll: HorizontalScrollView
    private lateinit var moveStripContainer: LinearLayout
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button

    private var whiteName: String = "White"
    private var blackName: String = "Black"
    private var gameId: String = ""

    private var fens: List<String> = emptyList()
    private var centipawns: List<Int> = emptyList()
    private var results: List<Pair<String, MoveQuality>> = emptyList()
    private var moveTokenViews: MutableList<TextView> = mutableListOf()
    private var currentIndex: Int = -1

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
        headerText.textSize = 18f
        headerText.setTextColor(textLight)
        headerText.gravity = Gravity.CENTER
        headerText.setPadding(0, 0, 0, 16)

        statusText = TextView(this)
        statusText.textSize = 15f
        statusText.setTextColor(textMuted)
        statusText.text = "Анализ партии, подождите..."
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 0, 0, 16)

        val boardRow = LinearLayout(this)
        boardRow.orientation = LinearLayout.HORIZONTAL
        boardRow.gravity = Gravity.CENTER_VERTICAL
        boardRow.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val density = resources.displayMetrics.density
        val evalBarWidth = (24 * density).toInt()

        evalBarView = EvalBarView(this)
        val evalParams = LinearLayout.LayoutParams(evalBarWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        evalParams.marginEnd = (6 * density).toInt()
        evalBarView.layoutParams = evalParams

        chessBoardView = ChessBoardView(this)
        chessBoardView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        boardRow.addView(evalBarView)
        boardRow.addView(chessBoardView)

        explanationCard = LinearLayout(this)
        explanationCard.orientation = LinearLayout.HORIZONTAL
        explanationCard.gravity = Gravity.CENTER_VERTICAL
        val explBg = GradientDrawable()
        explBg.cornerRadius = 16f
        explBg.setColor(cardBg)
        explanationCard.background = explBg
        explanationCard.setPadding(24, 24, 24, 24)
        val explParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        explParams.topMargin = 24
        explanationCard.layoutParams = explParams

        explanationIcon = TextView(this)
        val iconBg = GradientDrawable()
        iconBg.shape = GradientDrawable.OVAL
        iconBg.setColor(textMuted)
        explanationIcon.background = iconBg
        explanationIcon.gravity = Gravity.CENTER
        explanationIcon.setTextColor(Color.WHITE)
        explanationIcon.textSize = 14f
        val iconParams = LinearLayout.LayoutParams(64, 64)
        iconParams.marginEnd = 20
        explanationIcon.layoutParams = iconParams

        explanationLabel = TextView(this)
        explanationLabel.text = "Начальная позиция"
        explanationLabel.textSize = 16f
        explanationLabel.setTextColor(textLight)

        explanationCard.addView(explanationIcon)
        explanationCard.addView(explanationLabel)

        moveStripContainer = LinearLayout(this)
        moveStripContainer.orientation = LinearLayout.HORIZONTAL
        moveStripContainer.gravity = Gravity.CENTER_VERTICAL

        moveStripScroll = HorizontalScrollView(this)
        val stripParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        stripParams.topMargin = 24
        moveStripScroll.layoutParams = stripParams
        moveStripScroll.addView(moveStripContainer)

        val navRow = LinearLayout(this)
        navRow.orientation = LinearLayout.HORIZONTAL
        navRow.gravity = Gravity.CENTER
        val navParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        navParams.topMargin = 16
        navRow.layoutParams = navParams

        prevButton = Button(this)
        prevButton.text = "\u2190"
        prevButton.setOnClickListener { if (currentIndex > -1) selectMove(currentIndex - 1) }

        nextButton = Button(this)
        nextButton.text = "\u2192"
        nextButton.setOnClickListener { if (currentIndex < results.size - 1) selectMove(currentIndex + 1) }

        navRow.addView(prevButton)
        navRow.addView(nextButton)

        rootLayout.addView(headerText)
        rootLayout.addView(statusText)
        rootLayout.addView(boardRow)
        rootLayout.addView(explanationCard)
        rootLayout.addView(moveStripScroll)
        rootLayout.addView(navRow)

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

                val computed = mutableListOf<Pair<String, MoveQuality>>()
                for (i in sanTokens.indices) {
                    val moverIsWhite = (i % 2 == 0)
                    val wpBefore = centipawnsToWinPercent(cpList[i])
                    val wpAfter = centipawnsToWinPercent(cpList[i + 1])
                    val moverBefore = if (moverIsWhite) wpBefore else 100.0 - wpBefore
                    val moverAfter = if (moverIsWhite) wpAfter else 100.0 - wpAfter
                    val drop = moverBefore - moverAfter
                    computed.add(Pair(sanTokens[i], classifyDrop(drop)))
                }
                results = computed

                runOnUiThread {
                    statusText.text = ""
                    buildMoveStrip()
                    selectMove(results.size - 1)
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка анализа: ${e.message}" }
            }
        }.start()
    }

    private fun buildMoveStrip() {
        moveStripContainer.removeAllViews()
        moveTokenViews.clear()

        for ((index, pair) in results.withIndex()) {
            val (san, _) = pair
            if (index % 2 == 0) {
                val numberLabel = TextView(this)
                numberLabel.text = "${index / 2 + 1}."
                numberLabel.setTextColor(textMuted)
                numberLabel.textSize = 15f
                numberLabel.setPadding(8, 8, 4, 8)
                moveStripContainer.addView(numberLabel)
            }

            val token = TextView(this)
            token.text = san
            token.setTextColor(textLight)
            token.textSize = 15f
            token.setPadding(16, 8, 16, 8)
            token.setOnClickListener { selectMove(index) }
            moveStripContainer.addView(token)
            moveTokenViews.add(token)
        }
    }

    private fun selectMove(index: Int) {
        currentIndex = index
        val fenIndex = index + 1

        if (fenIndex in fens.indices) {
            chessBoardView.fen = fens[fenIndex]
        }
        if (fenIndex in centipawns.indices) {
            evalBarView.whiteWinPercent = centipawnsToWinPercent(centipawns[fenIndex])
        }

        if (index == -1) {
            explanationLabel.text = "Начальная позиция"
            (explanationIcon.background as GradientDrawable).setColor(textMuted)
            explanationIcon.text = ""
        } else if (index in results.indices) {
            val (san, quality) = results[index]
            val moveNumber = (index / 2) + 1
            val side = if (index % 2 == 0) "$moveNumber." else "$moveNumber..."
            explanationLabel.text = "$side $san — ${quality.label}"
            (explanationIcon.background as GradientDrawable).setColor(Color.parseColor(quality.colorHex))
            explanationIcon.text = quality.symbol
        }

        for ((i, token) in moveTokenViews.withIndex()) {
            token.setBackgroundColor(if (i == index) highlightBg else Color.TRANSPARENT)
        }

        if (index in moveTokenViews.indices) {
            val token = moveTokenViews[index]
            moveStripScroll.post { moveStripScroll.smoothScrollTo(token.left, 0) }
        }

        prevButton.isEnabled = index > -1
        nextButton.isEnabled = index < results.size - 1
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
}
