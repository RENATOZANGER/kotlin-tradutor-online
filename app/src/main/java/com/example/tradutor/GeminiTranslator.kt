package com.example.tradutor

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.ai.client.generativeai.type.GoogleGenerativeAIException

class GeminiTranslator(
    private val context: Context,
    val ttsManager: TTSManager,
    private val statusTextView: TextView,
    private val recognizedTextView: TextView,
    private val translatedTextView: TextView,
    private val progressBar: ProgressBar
) {
    private val geminiModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = getGeminiApiKey()
        )
    }

    @Volatile
    private var hasTranslated = false

    private fun getAcceptedNames(key: String): Set<String> {
        return when (key) {
            "Inglês" -> setOf("Inglês", "English")
            "Português" -> setOf("Português", "Portuguese")
            "Espanhol" -> setOf("Espanhol", "Spanish")
            else -> setOf(key)
        }
    }

    /**
     * Processa o texto reconhecido, chama o Gemini para tradução e o TTS para reprodução.
     */
    suspend fun processTranslation(text: String, selectedMode: TranslationMode) {
        val cleanText = text.trim()
        if (cleanText.length < 2) {
            recognizedTextView.setText(R.string.recognized_text_placeholder)
            return
        }

        if (hasTranslated) return
        hasTranslated = true

        if (!isNetworkAvailable(context)) {
            recognizedTextView.setText(R.string.sem_conexao)
            translatedTextView.text = context.getString(R.string.error_network_needed)
            progressBar.visibility = View.INVISIBLE
            return
        }

        recognizedTextView.text = "Reconhecido: $cleanText"
        statusTextView.text = "Detectando idioma e traduzindo (${selectedMode.displayName})..."
        progressBar.visibility = View.VISIBLE

        try {
            val prompt = generateTranslationPrompt(cleanText, selectedMode)
            val response = withContext(Dispatchers.IO) {
                geminiModel.generateContent(prompt)
            }

            val (translationText, finalSourceKey, finalTargetKey) = parseAndExtractTranslation(
                response.text,
                selectedMode
            )

            if (translationText.isBlank() || translationText.contains("Erro ao processar")) {
                translatedTextView.text = translationText
                statusTextView.text = "Erro: Tradução não processável."
                return
            }
            val sourceConfig = allLanguages.getValue(finalSourceKey)
            val targetConfig = allLanguages.getValue(finalTargetKey)

            recognizedTextView.text =
                "Reconhecido (Origem: ${sourceConfig.displayName}): $cleanText"
            translatedTextView.text = translationText
            statusTextView.text =
                "Tradução concluída: ${sourceConfig.displayName} → ${targetConfig.displayName}"
            val audioBytes = withContext(Dispatchers.IO) {
                ttsManager.synthesizeNaturalVoice(
                    translationText,
                    targetConfig.ttsLangCode,
                    targetConfig.ttsVoiceName
                )
            }
            ttsManager.playAudio(audioBytes)

        } catch (e: GoogleGenerativeAIException) {
            val displayMessage = if (e.message?.contains("RESOURCE_EXHAUSTED") == true ||
                e.message?.contains("429") == true) {
                "Ops! Excesso de uso. Por favor, aguarde um minuto e tente novamente."
            } else {
                "Erro na tradução/detecção: ${e.message}"
            }
            translatedTextView.text = displayMessage
            statusTextView.text = "Falha de API."
            Log.e("TranslatorApp", "Erro Gemini: ${e.message}")

        } catch (e: Exception) {
            translatedTextView.text = "Erro na tradução/detecção: ${e.message}"
            statusTextView.text = "Falha desconhecida."
            Log.e("TranslatorApp", "Erro na tradução: ${e.message}")
        } finally {
            progressBar.visibility = View.INVISIBLE
            hasTranslated = false
        }
    }
    private fun generateTranslationPrompt(text: String, mode: TranslationMode): String {
        return if (mode == TranslationMode.AUTO_PT) {
            val allKeys = allLanguages.keys.joinToString(", ")
            """
                Você é um tradutor inteligente. O texto de entrada pode ser em qualquer um dos seguintes idiomas: $allKeys.

                1. Detecte o idioma de origem (Source Language) do texto. Use o nome do idioma exato (ex: Português, Inglês ou Espanhol).
                2. Determine o idioma de destino (Target Language) com a seguinte regra:
                   - SE o idioma de origem for 'Português', o destino deve ser 'Inglês'.
                   - CASO CONTRÁRIO, o destino DEVE ser 'Português'.
                3. Traduza o texto.

                RESPOSTA OBRIGATORIAMENTE EM FORMATO JSON:

                {
                  "source_language_name": "Nome do Idioma de Origem (e.g., Português)",
                  "target_language_name": "Nome do Idioma de Destino (e.g., Espanhol)",
                  "translated_text": "A tradução aqui."
                }

                Texto de Entrada: "$text"
            """.trimIndent()
        } else {
            val lang1Key = mode.lang1Key
            val lang2Key = mode.lang2Key
            """
                Você é um tradutor bidirecional. O texto de entrada está em $lang1Key ou $lang2Key.

                1. Detecte o idioma de origem (Source Language) do texto. Use o nome do idioma exato do par (ex: Português, Inglês ou Espanhol).
                2. Determine o idioma de destino (Target Language) que é o outro idioma do par. Use o nome do idioma exato do par.
                3. Traduza o texto.

                RESPOSTA OBRIGATORIAMENTE EM FORMATO JSON:

                {
                  "source_language_name": "Nome do Idioma de Origem (e.g., Português)",
                  "target_language_name": "Nome do Idioma de Destino (e.g., Espanhol)",
                  "translated_text": "A tradução aqui."
                }

                Texto de Entrada: "$text"
            """.trimIndent()
        }
    }

    private fun parseAndExtractTranslation(
        responseText: String?,
        selectedMode: TranslationMode
    ): Triple<String, String, String> {
        var cleanResponse = responseText?.trim() ?: ""

        if (cleanResponse.isBlank()) {
            return Triple("Erro: Resposta vazia do tradutor.", selectedMode.lang1Key, selectedMode.lang2Key)
        }

        if (cleanResponse.startsWith("```json")) {
            cleanResponse = cleanResponse.removePrefix("```json").removeSuffix("```").trim()
        } else if (cleanResponse.startsWith("```")) {
            cleanResponse = cleanResponse.substringAfter("```").substringBeforeLast("```").trim()
        }
        val jsonResponse = try {
            JSONObject(cleanResponse)
        } catch (e: Exception) {
            Log.e("TranslatorApp", "Falha ao fazer parse do JSON: $cleanResponse", e)
            return Triple(
                "Erro ao processar tradução. Resposta bruta: $cleanResponse",
                selectedMode.lang1Key,
                selectedMode.lang2Key
            )
        }

        val sourceLangNameFromGemini = jsonResponse.optString("source_language_name", "")
        val targetLangNameFromGemini = jsonResponse.optString("target_language_name", "")
        val translatedText = jsonResponse.optString("translated_text", "")
        val recognizedSourceKey = allLanguages.keys.firstOrNull { key ->
            getAcceptedNames(key).any { sourceLangNameFromGemini.equals(it, ignoreCase = true) }
        } ?: selectedMode.lang1Key
        val recognizedTargetKey = allLanguages.keys.firstOrNull { key ->
            getAcceptedNames(key).any { targetLangNameFromGemini.equals(it, ignoreCase = true) }
        } ?: selectedMode.lang2Key

        var finalSourceKey: String = recognizedSourceKey
        var finalTargetKey: String = recognizedTargetKey

        if (selectedMode != TranslationMode.AUTO_PT) {
            val lang1Key = selectedMode.lang1Key
            val lang2Key = selectedMode.lang2Key

            when (recognizedSourceKey) {
                lang1Key -> {
                    finalSourceKey = lang1Key
                    finalTargetKey = lang2Key
                }
                lang2Key -> {
                    finalSourceKey = lang2Key
                    finalTargetKey = lang1Key
                }
                else -> {
                    Log.w(
                        "TranslatorApp",
                        "Falha na detecção precisa do idioma, assumindo $lang1Key como origem."
                    )
                    finalSourceKey = lang1Key
                    finalTargetKey = lang2Key
                }
            }
        }

        return Triple(translatedText, finalSourceKey, finalTargetKey)
    }
}