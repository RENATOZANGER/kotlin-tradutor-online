package com.example.tradutor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class STTManager(
    private val context: Context,
    private val statusTextView: TextView,
    private val recognizedTextView: TextView,
    private val toggleButton: Button,
    private val progressBar: ProgressBar,
    private val coroutineScope: CoroutineScope,
    private val geminiTranslator: GeminiTranslator,
    private val ttsManager: TTSManager,
    private val onModeChange: () -> TranslationMode
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    // Handler e Runnable para gerenciar o temporizador de silêncio
    private val handler = Handler(Looper.getMainLooper())
    private val pauseRunnable = Runnable {
        Log.d("STTManager", "Pausa detectada (${PAUSE_TIMEOUT_MS / 1000}s) -> Parando escuta.")
        speechRecognizer?.stopListening()
    }

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        } else {
            statusTextView.text = context.getString(R.string.recognition_unavailable)
            toggleButton.isEnabled = false
        }
    }

    /**
     * Inicia o temporizador para parar a escuta após um período de silêncio.
     * Chamada após o início da escuta e a cada resultado parcial.
     */
    private fun resetPauseTimer() {
        handler.removeCallbacks(pauseRunnable)
        handler.postDelayed(pauseRunnable, PAUSE_TIMEOUT_MS)
    }

    @SuppressLint("SetTextI18n")
    fun startListening() {
        if (ttsManager.isSpeaking) {
            Toast.makeText(context, "Aguarde o áudio atual terminar.", Toast.LENGTH_SHORT).show()
            return
        }
        if (speechRecognizer == null || isListening) return

        val selectedMode = onModeChange()
        val sttLangCodeHint = selectedMode.getLang1Config().sttCode

        recognizedTextView.text =
            "Aguardando fala em ${selectedMode.lang1Key} ou ${selectedMode.lang2Key}..."

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLangCodeHint)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            toggleButton.text = "Parar Escuta"
            statusTextView.text = "Ouvindo nos modos ${selectedMode.displayName}..."
            progressBar.visibility = android.view.View.VISIBLE

            resetPauseTimer()
        } catch (e: Exception) {
            statusTextView.text = "Erro ao iniciar a escuta: ${e.message}"
            isListening = false
        }
    }

    /**
     * Para a escuta do SpeechRecognizer e limpa o estado.
     */
    @SuppressLint("SetTextI18n")
    fun stopListening() {
        handler.removeCallbacks(pauseRunnable)

        speechRecognizer?.cancel()
        isListening = false
        val selectedMode = onModeChange()
        toggleButton.text = "Iniciar Escuta"
        progressBar.visibility = android.view.View.INVISIBLE
        statusTextView.text = "Modo selecionado: ${selectedMode.displayName}. Pressione para falar."
    }


    fun destroy() {
        speechRecognizer?.destroy()
        handler.removeCallbacks(pauseRunnable)
    }
    @SuppressLint("SetTextI18n")
    override fun onBeginningOfSpeech() {
        progressBar.isIndeterminate = false
        statusTextView.text = "Falando..."
        handler.removeCallbacks(pauseRunnable)
    }

    @SuppressLint("SetTextI18n")
    override fun onPartialResults(partialResults: Bundle?) {
        val txt =
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (txt != null) {
            recognizedTextView.text = "Parcial: $txt"
            resetPauseTimer()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResults(results: Bundle?) {
        stopListening()

        val txt = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        val selectedMode = onModeChange()

        if (txt != null && txt.isNotBlank()) {
            coroutineScope.launch {
                geminiTranslator.processTranslation(txt, selectedMode)
            }
        } else {
            statusTextView.text = "Não foi possível reconhecer a fala. Tente novamente."
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onError(error: Int) {
        stopListening()

        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de rede."
            SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala detectada ou reconhecida."
            SpeechRecognizer.ERROR_AUDIO -> "Erro de gravação de áudio."
            else -> "Erro STT desconhecido ($error)"
        }
        statusTextView.text = "Erro: $errorMsg"
        Log.e("TranslatorApp", "Erro STT: $errorMsg")
    }
    override fun onReadyForSpeech(params: Bundle?) {
    }
    override fun onRmsChanged(rmsdB: Float) {
        progressBar.isIndeterminate = false
        progressBar.progress = (rmsdB * 10).toInt()
    }

    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}