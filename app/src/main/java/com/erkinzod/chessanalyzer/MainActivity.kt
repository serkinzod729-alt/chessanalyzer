package com.erkinzod.chessanalyzer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : Activity() {

    private val clientId = "com.erkinzod.chessanalyzer"
    private val redirectUri = "com.erkinzod.chessanalyzer://callback"
    private val prefs by lazy { getSharedPreferences("auth", MODE_PRIVATE) }

    private val darkBg = Color.parseColor("#302E2B")
    private val cardBg = Color.parseColor("#3C3A37")
    private val accentGreen = Color.parseColor("#81B64C")
    private val textLight = Color.parseColor("#EDEDED")
    private val textMuted = Color.parseColor("#B0AEAB")
    private val winColor = Color.parseColor("#81B64C")
    private val lossColor = Color.parseColor("#FA412D")
    private val drawColor = Color.parseColor("#B0AEAB")

    private lateinit var statusText: TextView
    private lateinit var loginButton: Button
    private lateinit var gamesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER_HORIZONTAL
        layout.setBackgroundColor(darkBg)
        layout.setPadding(48, 96, 48, 48)

        val titleText = TextView(this)
        titleText.text = "Chess Analyzer"
        titleText.textSize = 26f
        titleText.setTextColor(textLight)
        titleText.setPadding(0, 0, 0, 32)

        statusText = TextView(this)
        statusText.textSize = 16f
        statusText.setTextColor(textMuted)
        statusText.text = "Не авторизован"
        statusText.setPadding(0, 0, 0, 24)

        loginButton = Button(this)
        loginButton.text = "Войти через Lichess"
        loginButton.setTextColor(Color.WHITE)
        val buttonBg = GradientDrawable()
        buttonBg.cornerRadius = 16f
        buttonBg.setColor(accentGreen)
        loginButton.background = buttonBg
        loginButton.setOnClickListener { startLogin() }

        gamesContainer = LinearLayout(this)
        gamesContainer.orientation = LinearLayout.VERTICAL
        val gamesParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        gamesParams.topMargin = 32
        gamesContainer.layoutParams = gamesParams

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(loginButton)
        layout.addView(gamesContainer)

        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(darkBg)
        scrollView.addView(layout)
        setContentView(scrollView)

        val existingToken = prefs.getString("access_token", null)
        if (existingToken != null) {
            fetchAccount(existingToken)
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "com.erkinzod.chessanalyzer" && data.host == "callback") {
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            val savedState = prefs.getString("oauth_state", null)
            if (code != null && state != null && state == savedState) {
                statusText.text = "Обмениваем код на токен..."
                exchangeCodeForToken(code)
            } else {
                statusText.text = "Ошибка авторизации (state mismatch)"
            }
        }
    }

    private fun startLogin() {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateCodeVerifier().take(16)

        prefs.edit()
            .putString("code_verifier", verifier)
            .putString("oauth_state", state)
            .apply()

        val authUrl = Uri.parse("https://lichess.org/oauth").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .build()

        startActivity(Intent(Intent.ACTION_VIEW, authUrl))
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun exchangeCodeForToken(code: String) {
        Thread {
            try {
                val verifier = prefs.getString("code_verifier", "") ?: ""
                val url = URL("https://lichess.org/api/token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val body = "grant_type=authorization_code" +
                        "&code=${Uri.encode(code)}" +
                        "&code_verifier=${Uri.encode(verifier)}" +
                        "&redirect_uri=${Uri.encode(redirectUri)}" +
                        "&client_id=${Uri.encode(clientId)}"

                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val response = BufferedReader(InputStreamReader(stream)).readText()

                if (responseCode in 200..299) {
                    val json = JSONObject(response)
                    val accessToken = json.getString("access_token")
                    prefs.edit().putString("access_token", accessToken).apply()
                    runOnUiThread { fetchAccount(accessToken) }
                } else {
                    runOnUiThread { statusText.text = "Ошибка обмена токена: $responseCode\n$response" }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка: ${e.message}" }
            }
        }.start()
    }

    private fun fetchAccount(token: String) {
        Thread {
            try {
                val url = URL("https://lichess.org/api/account")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val response = BufferedReader(InputStreamReader(stream)).readText()

                if (responseCode in 200..299) {
                    val json = JSONObject(response)
                    val username = json.getString("username")
                    runOnUiThread {
                        statusText.text = "Привет, $username!"
                        loginButton.text = "Перелогиниться"
                    }
                    fetchGames(username)
                } else {
                    runOnUiThread { statusText.text = "Токен недействителен, войдите снова" }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка: ${e.message}" }
            }
        }.start()
    }

    private fun fetchGames(username: String) {
        Thread {
            try {
                val token = prefs.getString("access_token", "") ?: ""
                val url = URL("https://lichess.org/api/games/user/$username?max=20&sort=dateDesc")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/x-ndjson")

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    runOnUiThread { statusText.text = "Ошибка загрузки партий: $responseCode" }
                    return@Thread
                }

                val lines = BufferedReader(InputStreamReader(conn.inputStream)).readLines()
                val games = lines.filter { it.isNotBlank() }.map { JSONObject(it) }

                runOnUiThread { renderGamesList(games, username) }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка загрузки партий: ${e.message}" }
            }
        }.start()
    }

    private fun renderGamesList(games: List<JSONObject>, myUsername: String) {
        gamesContainer.removeAllViews()
        for (game in games) {
            val players = game.getJSONObject("players")
            val white = players.getJSONObject("white").optJSONObject("user")?.optString("name") ?: "?"
            val black = players.getJSONObject("black").optJSONObject("user")?.optString("name") ?: "?"
            val winner = game.optString("winner", "draw")

            val resultText: String
            val resultColor: Int
            when {
                winner == "draw" -> {
                    resultText = "Ничья"
                    resultColor = drawColor
                }
                (winner == "white" && white.equals(myUsername, ignoreCase = true)) ||
                (winner == "black" && black.equals(myUsername, ignoreCase = true)) -> {
                    resultText = "Победа"
                    resultColor = winColor
                }
                else -> {
                    resultText = "Поражение"
                    resultColor = lossColor
                }
            }

            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            val cardBgDrawable = GradientDrawable()
            cardBgDrawable.cornerRadius = 16f
            cardBgDrawable.setColor(cardBg)
            card.background = cardBgDrawable
            card.setPadding(24, 20, 24, 20)

            val cardParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cardParams.bottomMargin = 12
            card.layoutParams = cardParams

            val namesText = TextView(this)
            namesText.text = "$white vs $black"
            namesText.textSize = 16f
            namesText.setTextColor(textLight)

            val resultTextView = TextView(this)
            resultTextView.text = resultText
            resultTextView.textSize = 14f
            resultTextView.setTextColor(resultColor)
            resultTextView.setPadding(0, 8, 0, 0)

            card.addView(namesText)
            card.addView(resultTextView)

            val gameId = game.optString("id", "")
            val analysisPrefs = getSharedPreferences("analysis_cache", MODE_PRIVATE)
            if (gameId.isNotBlank() && analysisPrefs.contains(gameId)) {
                val analyzedBadge = TextView(this)
                analyzedBadge.text = "✓ Проанализировано"
                analyzedBadge.textSize = 12f
                analyzedBadge.setTextColor(accentGreen)
                analyzedBadge.setPadding(0, 8, 0, 0)
                card.addView(analyzedBadge)
            }

            card.setOnClickListener {
                val moves = game.optString("moves", "")
                val gameId = game.optString("id", "")
                val intent = Intent(this, AnalysisActivity::class.java)
                intent.putExtra("moves", moves)
                intent.putExtra("white", white)
                intent.putExtra("black", black)
                intent.putExtra("gameId", gameId)
                startActivity(intent)
            }
            gamesContainer.addView(card)
        }
    }
}
