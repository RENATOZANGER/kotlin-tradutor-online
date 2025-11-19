package com.example.tradutor

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TTSManager(private val context: Context) {

    @Volatile
    var isSpeaking = false
        private set

    /**
     * Sintetiza texto em áudio MP3 usando a API Google Cloud Text-to-Speech (com autenticação por token).
     * Requer o arquivo 'service_account.json' nos 'assets'.
     */
    suspend fun synthesizeNaturalVoice(
        text: String,
        languageCode: String,
        voiceName: String
    ): ByteArray {
        val inputStream = context.assets.open("service_account.json")

        // 1. Geração de Credenciais e Token
        val credentials = GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

        credentials.refreshIfExpired()
        val token = credentials.accessToken?.tokenValue
            ?: throw Exception("Token TTS inválido.")

        // 2. Construção do Payload
        val escapedText = JSONObject.quote(text)
        val payload = """
        {
            "input": { "text": $escapedText },
            "voice": {
                "languageCode": "$languageCode",
                "name": "$voiceName"
            },
            "audioConfig": { "audioEncoding": "MP3" }
        }
        """.trimIndent()

        // 3. Chamada de API
        val url = URL("https://texttospeech.googleapis.com/v1/text:synthesize")
        val conn = withContext(Dispatchers.IO) { url.openConnection() } as HttpURLConnection

        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true

        conn.outputStream.use { os ->
            os.write(payload.toByteArray())
        }

        val response = try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.readText()
                Log.e("TTS", "Erro TTS HTTP ${conn.responseCode}: $errorMsg")
                throw Exception("Falha na API TTS: ${conn.responseCode} - $errorMsg")
            }
        } catch (e: Exception) {
            Log.e("TTS", "Falha ao ler resposta TTS", e)
            throw e
        }

        val base64Audio = JSONObject(response).getString("audioContent")

        return Base64.decode(base64Audio, Base64.DEFAULT)
    }

    /**
     * Salva os bytes de áudio em um arquivo temporário e os reproduz.
     */
    fun playAudio(bytes: ByteArray) {
        val tempFile = File.createTempFile("tts", "mp3", context.cacheDir)
        tempFile.writeBytes(bytes)

        val player = MediaPlayer()
        player.setDataSource(tempFile.absolutePath)
        player.prepare()
        player.setOnCompletionListener {
            it.release()
            isSpeaking = false
        }
        player.start()
        isSpeaking = true
    }
}