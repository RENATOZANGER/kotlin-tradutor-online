package com.example.tradutor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity() {

    // Componentes da UI
    private lateinit var statusTextView: TextView
    private lateinit var recognizedTextView: TextView
    private lateinit var translatedTextView: TextView
    private lateinit var toggleButton: Button
    private lateinit var exitButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerMode: Spinner

    // Lógica do App
    private var selectedMode: TranslationMode = TranslationMode.PT_ES
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var ttsManager: TTSManager
    private lateinit var geminiTranslator: GeminiTranslator
    private lateinit var sttManager: STTManager


    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Inicializa componentes de UI
        statusTextView = findViewById(R.id.statusTextView)
        recognizedTextView = findViewById(R.id.recognizedTextView)
        translatedTextView = findViewById(R.id.translatedTextView)
        toggleButton = findViewById(R.id.toggleButton)
        exitButton = findViewById(R.id.exitButton)
        progressBar = findViewById(R.id.progressBar)
        spinnerMode = findViewById(R.id.spinnerMode)

        // Habilita o scroll para os textos
        recognizedTextView.movementMethod = android.text.method.ScrollingMovementMethod()
        translatedTextView.movementMethod = android.text.method.ScrollingMovementMethod()

        // 2. Inicializa Classes de Lógica
        ttsManager = TTSManager(this)
        geminiTranslator = GeminiTranslator(
            this, ttsManager, statusTextView, recognizedTextView, translatedTextView, progressBar
        )
        sttManager = STTManager(
            this, statusTextView, recognizedTextView, toggleButton, progressBar,
            mainScope, geminiTranslator, ttsManager
        ) { selectedMode }

        // 3. Configura a UI
        setupModeSpinner()
        checkAudioPermission()

        toggleButton.setOnClickListener {
            if (sttManager.isListening) {
                sttManager.stopListening() // Agora renomeado
            } else {
                sttManager.startListening()
            }
        }

        exitButton.setOnClickListener {
            finish()
        }
    }

    private fun setupModeSpinner() {
        val modeNames = TranslationMode.entries.map { it.displayName }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modeNames
        )

        spinnerMode.adapter = adapter

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("SetTextI18n")
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedMode = TranslationMode.entries[position]
                statusTextView.text =
                    "Modo selecionado: ${selectedMode.displayName}. Pressione para falar."
                recognizedTextView.text = getString(R.string.recognized_text_placeholder)
                translatedTextView.text = getString(R.string.translated_text_placeholder)

                // Garante que a escuta pare ao mudar o modo
                sttManager.stopListening()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerMode.setSelection(TranslationMode.entries.indexOf(selectedMode))
    }


    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(toggleButton, "Permissão necessária.", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Tentar novamente") { checkAudioPermission() }
                    .show()
                toggleButton.isEnabled = false
            } else {
                toggleButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sttManager.destroy()
    }
}