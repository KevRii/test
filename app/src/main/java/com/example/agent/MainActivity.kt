package com.example.agent

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val document = File(filesDir, "document.txt")
        val editor = findViewById<EditText>(R.id.editor)
        editor.setText(runCatching { document.readText() }.getOrDefault(""))
        findViewById<Button>(R.id.save_button).setOnClickListener {
            val result = runCatching { document.writeText(editor.text.toString()) }
            val message = if (result.isSuccess) R.string.saved else R.string.save_failed
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
