package com.erkinzod.chessanalyzer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this)
        textView.text = "Chess Analyzer\n\nПривет, ЭРКИНЗОД!"
        textView.textSize = 22f
        textView.setPadding(48, 96, 48, 48)

        setContentView(textView)
    }
}
