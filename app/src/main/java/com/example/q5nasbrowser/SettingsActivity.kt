package com.example.q5nasbrowser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val userEdit = findViewById<EditText>(R.id.userEdit)
        val passEdit = findViewById<EditText>(R.id.passEdit)
        val saveBtn = findViewById<Button>(R.id.saveBtn)

        val (u, p) = SmbConfig.load(this)
        userEdit.setText(u)
        passEdit.setText(p)

        saveBtn.setOnClickListener {
            SmbConfig.save(this, userEdit.text.toString().trim(), passEdit.text.toString())
            finish()
        }
    }
}
