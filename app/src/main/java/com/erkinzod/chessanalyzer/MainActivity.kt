package com.erkinzod.chessanalyzer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.util.Base64
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
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
    private val redirectUri = "chessanalyzer://callback"
    private val prefs by lazy { getSharedPreferences("auth", MODE_PRIVATE) }

    private lateinit var statusText: TextView
    private lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(48, 48, 48, 48)

        statusText = TextView(this)
        statusText.textSize = 18f
        statusText.text = "Не авторизован"

        loginButton = Button(this)
        loginButton.text = "Войти через Lichess"
        loginButton.setOnClickListener { startLogin() }

        layout.addView(statusText)
        layout.addView(loginButton)
        setContentView(layout)

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
        if (data != null && data.scheme == "chessanalyzer" && data.host == "callback") {
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
                } else {
                    runOnUiThread { statusText.text = "Токен недействителен, войдите снова" }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка: ${e.message}" }
            }
        }.start()
    }
}
