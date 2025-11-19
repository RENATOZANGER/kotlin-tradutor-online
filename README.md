# 🎤 Tradutor de Voz com Gemini + TTS

Aplicativo Android de tradução instantânea usando:
- **Google Gemini 2.0 Flash** → Tradução inteligente
- **Google TTS** → Síntese de voz natural
- Suporte a múltiplos idiomas e tradução bidirecional.

O objetivo é permitir conversas em tempo real entre dois idiomas, semelhante a um tradutor pessoal.

---

## 🚀 Funcionalidades
✔ Tradução automática via Gemini  
✔ Voz sintetizada com Google TTS  
✔ Detecção de idioma de entrada  
✔ Suporte a múltiplos modos de tradução (PT ↔ ES, PT ↔ EN, etc.)  
✔ Interface simples e rápida  
✔ Botão para iniciar/parar escuta  
✔ Botão de fechar o app

---


---

## Como criar a API Key do **Google Gemini**

1 Acesse o site oficial: 👉 https://aistudio.google.com/app/apikey

2 Clique em **"Create API Key"**

3 Escolha:
- **Project** → selecione seu projeto OU crie um novo
- **Key type** → API Key

4 Copie a chave gerada

5 Coloque a chave em GEMINI_API_KEY


## Como criar e exportar o Service Account
- Acessar o Google Cloud Console => https://console.cloud.google.com/
  - Selecionar seu projeto
  - Ativar a API necessária: Text-to-Speech API

- Criar Service Account(Menu → IAM & Admin → Service Accounts)
  - Permissões: adicionar a role: Agente de servico do cloud Speec-to-text
- Service Account → Keys → Add Key → Create new key → JSON 
- Baixe o arquivo e coloque em:  app/src/main/assets/service_account.json