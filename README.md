# 📡 Camera2 RTSP Server + Web Control

> Sistema profissional de streaming RTSP com Camera2 API do Android - Controle remoto via navegador web

## 🎯 Sobre o Projeto

Transforme seu **Samsung Galaxy Note10+** em um servidor RTSP profissional com controle total via web. Stream de vídeo de alta qualidade com ajustes manuais de ISO, exposição, foco e seleção entre as 4 câmeras do dispositivo.

### ✨ Destaques

- **📡 Servidor RTSP** na porta 8554 usando [RootEncoder](https://github.com/pedroSG94/RootEncoder)
- **🌐 Painel Web** na porta 8080 com interface moderna
- **🎥 4 Câmeras** - Wide, Ultra Wide, Telephoto e Frontal
- **⚙️ Controles Manuais** - ISO (50-3200), Exposição (1/8000s - 30s), Foco, Balanço de Branco
- **⚡ Baixa Latência** - Ideal para monitoramento em tempo real
- **📱 Android 7.0+** (API 24+)

---

## 📸 Screenshots

### Painel Web de Controle
![Web Control Panel](https://via.placeholder.com/800x500/1e293b/38bdf8?text=Painel+Web+de+Controle)

### App Android
![Android App](https://via.placeholder.com/400x800/0f172a/38bdf8?text=App+Android)

---

## 🛠️ Arquitetura

```
┌────────────────────────────────────────────────────┐
│          Samsung Galaxy Note10+ (Android)             │
│  ┌──────────────────────────────────────────┐  │
│  │        Camera2 API Controller            │  │
│  │  - 4 Lentes (Wide/UltraWide/Tele/Front)  │  │
│  │  - Controle Manual (ISO/Exp/Focus/WB)    │  │
│  └─────────────┬────────────────────────────┘  │
│           │                               │
│  ┌────────┴─────────┐      ┌────────┴────────┐  │
│  │  RTSP Server  │      │  HTTP Server  │  │
│  │  Porta: 8554  │      │  Porta: 8080  │  │
│  │  RootEncoder  │      │  NanoHTTPD    │  │
│  └──────┬────────┘      └──────┬───────┘  │
└─────────│───────────────────────│───────────┘
         │  Stream H.264         │  HTTP/JSON
         │  1280x720@30fps      │  REST API
         │                      │
    WiFi │ Local Network        │
         │                      │
         │                      │
┌────────┴─────────────────────┴─────────┐
│          Windows 10 PC (mesma rede WiFi)          │
│  ┌──────────────┐      ┌────────────────┐  │
│  │  VLC / OBS    │      │  Navegador Web  │  │
│  │  RTSP Client  │      │  Chrome/Edge    │  │
│  │  Ver Stream   │      │  Controlar App  │  │
│  └──────────────┘      └────────────────┘  │
└──────────────────────────────────────────────┘
```

---

## 🚀 Começando

### 📍 Pré-requisitos

- **Android Studio** Hedgehog (2023.1.1) ou superior
- **Android SDK** API 24+ (Android 7.0 Nougat ou superior)
- **Samsung Galaxy Note10+** ou dispositivo com múltiplas câmeras
- **Rede WiFi** local para comunicação PC ↔ Android

### 📦 Instalação

1. **Clone o repositório:**

```bash
git clone https://github.com/luanscps/camera2api-brSS.git
cd camera2api-brSS
```

2. **Abra no Android Studio:**
   - File → Open → Selecione a pasta do projeto

3. **Sync Gradle:**
   - Aguarde o Android Studio baixar as dependências

4. **Conecte o Note10+ via USB:**
   - Ative **Depuração USB** nas Opções do Desenvolvedor

5. **Build e Instale:**
   - Clique em **Run** (Shift+F10)
   - Ou use: `./gradlew installDebug`

---

## 🎮 Como Usar

### 1️⃣ No Android (Note10+)

1. Abra o app **Camera2 RTSP Server**
2. Conceda permissões de Câmera e Áudio
3. Anote os endereços exibidos:
   ```
   📡 RTSP Stream: rtsp://192.168.0.XXX:8554/live
   🌐 Painel Web: http://192.168.0.XXX:8080
   ```

### 2️⃣ No PC - Abrir Painel de Controle

1. Abra o navegador (Chrome/Edge/Firefox)
2. Digite: `http://192.168.0.XXX:8080`
3. Você verá o painel de controle:
   - Seleção de câmera
   - Ajuste de ISO
   - Controle de exposição
   - Ajuste de foco
   - Balanço de branco

### 3️⃣ No PC - Ver Stream

**VLC Player:**
```
1. Mídia → Abrir Fluxo de Rede
2. Cole: rtsp://192.168.0.XXX:8554/live
3. Clique em Reproduzir
```

**OBS Studio:**
```
1. Fontes → Adicionar → Media Source
2. Desmarque "Local File"
3. Input: rtsp://192.168.0.XXX:8554/live
4. Marque "Use hardware decoding"
5. OK
```

### 4️⃣ Controlar em Tempo Real

- Ajuste ISO no painel web → veja mudança instantânea no VLC/OBS
- Mude exposição → imagem fica mais clara/escura
- Troque de câmera → alterne entre Wide/UltraWide/Telephoto/Frontal
- Ajuste foco manual → controle preciso da nitidez

---

## 📚 Dependências

### Bibliotecas Principais

| Biblioteca | Versão | Propósito |
|-----------|--------|----------|
| [RootEncoder](https://github.com/pedroSG94/RootEncoder) | 2.4.5 | Streaming RTSP/RTMP profissional |
| [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) | 2.3.1 | Servidor HTTP leve |
| Gson | 2.10.1 | Serialização JSON |
| Kotlin Coroutines | 1.7.3 | Operações assíncronas |

### Permissões Android

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

---

## 🛡️ Configuração do Firewall

**Windows 10/11:**

```powershell
# Liberar porta RTSP (8554)
New-NetFirewallRule -DisplayName "RTSP Server" -Direction Inbound -Protocol TCP -LocalPort 8554 -Action Allow

# Liberar porta HTTP (8080)
New-NetFirewallRule -DisplayName "HTTP Control Panel" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
```

---

## ⚙️ Parâmetros de Streaming

| Parâmetro | Valor | Descrição |
|----------|-------|-------------|
| Resolução | 1280x720 | HD 720p |
| Frame Rate | 30 FPS | Fluidez ideal |
| Bitrate | 2500 kbps | Qualidade vs latência |
| Codec | H.264 | Compatibilidade universal |
| Protocolo | RTSP/RTP | Baixa latência |
| Porta RTSP | 8554 | Padrão RTSP |
| Porta HTTP | 8080 | Painel web |

---

## 📊 Performance

- **Latência:** 200-500ms (WiFi local)
- **Taxa de quadros:** 30 FPS estável
- **Consumo de rede:** ~2.5 Mbps
- **Uso de CPU (Note10+):** 15-25%
- **Uso de bateria:** Médio-alto (recomendado manter conectado)

---

## 🔧 Solução de Problemas

### Problema: Não consigo acessar o painel web

**Solução:**
- Verifique se PC e Note10+ estão na **mesma rede WiFi**
- Desative firewall temporariamente para testar
- Use `ipconfig` (Windows) para verificar subnet
- Tente `http://IP:8080` em vez de `https://`

### Problema: Stream não aparece no VLC

**Solução:**
- Aguarde 5-10 segundos após abrir o app Android
- Verifique se usou `rtsp://` no início da URL
- No VLC: Ferramentas → Preferências → Input/Codecs → Aumente o cache de rede
- Teste com: `ffplay rtsp://IP:8554/live`

### Problema: Latência muito alta (>1 segundo)

**Solução:**
- Use conexão WiFi 5GHz se disponível
- Reduza bitrate em `RtspServerPedro.kt` (linha 20): `2500` → `1500`
- No VLC: Ferramentas → Preferências → Input/Codecs → Reduza cache
- Feche outros apps no Note10+ que usem câmera

---

## 🔥 Próximos Passos

- [ ] Suporte a áudio AAC
- [ ] Autenticação HTTP Basic
- [ ] Gravação de stream em MP4
- [ ] Suporte a múltiplas resoluções
- [ ] Interface web responsiva para mobile
- [ ] Suporte a RTMPS (RTMP Secure)
- [ ] Configuração de bitrate dinâmica

---

## 📜 Licença

MIT License - veja [LICENSE](LICENSE) para detalhes.

---

## 👏 Créditos

- [RootEncoder](https://github.com/pedroSG94/RootEncoder) by Pedro Santos - Excelente biblioteca RTSP
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) - Servidor HTTP minimalista
- Camera2 API - API oficial de câmera do Android

---

## ❤️ Contribuindo

Contribuições são bem-vindas!

1. Fork o projeto
2. Crie uma branch: `git checkout -b feature/MinhaFeature`
3. Commit: `git commit -m 'Adiciona MinhaFeature'`
4. Push: `git push origin feature/MinhaFeature`
5. Abra um Pull Request

---

## 📧 Contato

**Luan Silva**
- GitHub: [@luanscps](https://github.com/luanscps)
- Email: luanscps@gmail.com

---

## ⭐ Mostre seu apoio

Se este projeto ajudou você, dê uma ⭐ no repositório!

---

<p align="center">
  <strong>Feito com ❤️ usando Camera2 API, Kotlin e muita cafeina ☕</strong>
</p>
