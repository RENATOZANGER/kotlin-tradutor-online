package com.example.tradutor

// --- CONSTANTES DE PERMISSÃO E TEMPO ---
const val REQUEST_RECORD_AUDIO_PERMISSION = 200
const val PAUSE_TIMEOUT_MS = 5000L

// --- CONSTANTES DE API ---
private const val GEMINI_API_KEY = "xxxx"
const val MODEL_NAME = "gemini-2.0-flash"

// --- ESTRUTURAS DE DADOS ---

// Estrutura para as configurações detalhadas de cada idioma
data class LanguageConfig(
    val displayName: String,
    val sttCode: String, // Código para Reconhecimento de Fala (e.g., "pt-BR")
    val ttsLangCode: String, // Código de Idioma para Google TTS (e.g., "pt-BR")
    val ttsVoiceName: String // Nome da Voz para Google TTS (e.g., "pt-BR-Wavenet-B")
)

// Mapeamento de todos os idiomas necessários. Chaves devem ser consistentes com o Gemini
val allLanguages = mapOf(
    "Português" to LanguageConfig("Português (Brasil)", "pt-BR", "pt-BR", "pt-BR-Wavenet-B"),
    "Espanhol" to LanguageConfig("Espanhol (Espanha)", "es-ES", "es-ES", "es-ES-Neural2-F"),
    "Inglês" to LanguageConfig("Inglês (EUA)", "en-US", "en-US", "en-US-Standard-C"),
    "Francês" to LanguageConfig("Francês (França)", "fr-FR", "fr-FR", "fr-FR-Wavenet-B"),
    "Alemão" to LanguageConfig("Alemão (Alemanha)", "de-DE", "de-DE", "de-DE-Wavenet-B"),
    "Italiano" to LanguageConfig("Italiano (Itália)", "it-IT", "it-IT", "it-IT-Wavenet-B"),
)

// Enum para os modos de tradução
enum class TranslationMode(val displayName: String, val lang1Key: String, val lang2Key: String) {
    PT_ES("Português ↔ Espanhol", "Português", "Espanhol"),
    EN_PT("Português ↔ Inglês", "Português", "Inglês"),
    ES_EN("Espanhol ↔ Inglês", "Espanhol", "Inglês"),
    AUTO_PT("Auto Detect ↔ Português", "Português", "Inglês");

    fun getLang1Config(): LanguageConfig = allLanguages.getValue(lang1Key)
}


fun getGeminiApiKey(): String = GEMINI_API_KEY