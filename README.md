# 📡 Camera2 RTSP Server + Web Control

Sistema profissional de streaming com controle remoto para Samsung Galaxy Note10+

![Status](https://img.shields.io/badge/status-active-success.svg)
![Android](https://img.shields.io/badge/Android-10%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)
![License](https://img.shields.io/badge/license-MIT-blue.svg)

## 🎯 Visão Geral

Este projeto transforma seu **Samsung Galaxy Note10+** em uma câmera profissional controlada remotamente, transmitindo vídeo via **RTSP** com controle total dos parâmetros da **Camera2 API**.

### ✨ Recursos Principais

- **🎥 Servidor RTSP (Porta 8554)**: Stream H.264 compatível com VLC, OBS, FFmpeg
- **🌐 Painel Web (Porta 8080)**: Interface de controle via navegador
- **🔧 Controle Total Camera2 API**:
  - ⚙️ Ajuste de ISO (50-3200)
  - 📸 Controle de exposição (1/8000s a 30s)
  - 🎯 Foco manual (0-10)
  - 🌡️ Balanço de branco (Auto, Daylight, Cloudy, Tungsten)
  - 📷 Troca entre 4 lentes (Wide, Ultra Wide, Telephoto, Frontal)
- **⚡ Baixa Latência**: 200-500ms via WiFi
- **🔄 Controle em Tempo Real**: Mudanças instantâneas no stream

---

## 📚 Arquitetura do Sistema

```
┌──────────────────────────────────────────────────────────┐
│                   Samsung Note10+                        │
│  ┌────────────────────────────────────────────────────┐  │
│  │  MainActivity.kt                                   │  │
│  │  ┌──────────────────┐  ┌──────────────────┐       │  │
│  │  │ Camera2Controller│  │  RtspServer      │       │  │
│  │  │  - ISO           │  │  Porta: 8554     │       │  │
│  │  │  - Exposição     │  │  URL: /live      │       │  │
│  │  │  - Foco          │  │                  │       │  │
│  │  │  - 4 Lentes      │  └──────────────────┘       │  │
│  │  └──────────────────┘           │                 │  │
│  │         │                        │                 │  │
│  │         │                  Stream H.264            │  │
│  │         │                        ↓                 │  │
│  │  ┌──────────────────────────────────────────┐     │  │
│  │  │     WebControlServer (NanoHTTPD)         │     │  │
│  │  │     Porta: 8080                          │     │  │
│  │  │     - GET  /      → Painel HTML         │     │  │
│  │  │     - POST /api/control → Controles     │     │  │
│  │  │     - GET  /api/status → Status         │     │  │
│  │  └──────────────────────────────────────────┘     │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                          │
                    WiFi Local
                          │
                          ↓
┌──────────────────────────────────────────────────────────┐
│                   Windows 10 PC                          │
│  ┌────────────────────┐    ┌──────────────────────┐     │
│  │  Navegador Chrome  │    │  VLC / OBS Studio    │     │
│  │  http://IP:8080    │    │  rtsp://IP:8554/live │     │
│  │                    │    │                      │     │
│  │  Controlar:        │    │  • Ver stream        │     │
│  │  • ISO             │    │  • Baixa latência    │     │
│  │  • Exposição       │    │  • 1280x720 @ 30fps  │     │
│  │  • Foco            │    │  • Controles reais   │     │
│  │  • Trocar lentes   │    │                      │     │
│  └────────────────────┘    └──────────────────────┘     │
└──────────────────────────────────────────────────────────┘
```

---

## 🛠️ Instalação Completa

### 📋 Pré-requisitos

**Hardware:**
- Samsung Galaxy Note10+ (Android 10+)
- PC Windows 10/11
- Roteador WiFi (mesma rede para ambos)

**Software:**
- [Android Studio](https://developer.android.com/studio) (última versão)
- [VLC Media Player](https://www.videolan.org/) ou [OBS Studio](https://obsproject.com/)
- Git (opcional)

---

### 📥 Passo 1: Clonar o Repositório

```bash
git clone https://github.com/luanscps/camera2api-brSS.git
cd camera2api-brSS
```

Ou faça download do ZIP e extraia.

---

### ⚙️ Passo 2: Configurar Projeto no Android Studio

1. **Abra o Android Studio**
2. **File → Open** e selecione a pasta `camera2api-brSS`
3. **Aguarde sincronização do Gradle** (primeira vez pode demorar)
4. Verifique que as dependências foram baixadas corretamente

#### 🔍 Verificar Dependências

O arquivo `app/build.gradle` já contém todas as dependências necessárias:

```gradle
dependencies {
    // Android Core
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    
    // NanoHTTPD - Servidor HTTP leve
    implementation 'org.nanohttpd:nanohttpd:2.3.1'
    
    // Libstreaming - Servidor RTSP
    implementation 'com.github.pedroSG94:rtmp-rtsp-stream-client-java:2.2.7'
    
    // Gson - Comunicação JSON
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Coroutines - Operações assíncronas
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

---

### 📱 Passo 3: Compilar e Instalar no Note10+

1. **Conecte o Note10+ ao PC via USB**
2. **Ative depuração USB** no celular:
   - Configurações → Sobre o telefone
   - Toque 7x em "Número da versão"
   - Volte → Opções do desenvolvedor
   - Ative "Depuração USB"
3. **No Android Studio, clique em Run ▶️**
4. **Selecione o Note10+** na lista de dispositivos
5. **Aguarde instalação** (primeira vez demora mais)

---

### 🔑 Passo 4: Conceder Permissões

Quando o app abrir:

1. **Permitir acesso à câmera**
2. **Permitir acesso ao microfone**
3. App iniciará automaticamente após permissões

---

### 🌐 Passo 5: Obter Endereço IP do Note10+

**Método 1 - Logcat (Android Studio):**

1. Abra o Logcat (View → Tool Windows → Logcat)
2. Procure por linhas:
   ```
   I/Server: RTSP URL: rtsp://192.168.x.x:8554/live
   I/Server: Web Control: http://192.168.x.x:8080
   ```
3. **Anote esses endereços!**

**Método 2 - Configurações WiFi:**

1. Note10+ → Configurações → WiFi
2. Toque na rede conectada
3. Veja o IP (ex: 192.168.1.100)

---

## 🚀 Como Usar

### 1️⃣ Acessar Painel de Controle (PC)

1. **Abra o navegador** (Chrome, Edge, Firefox)
2. **Digite o endereço**: `http://IP_DO_NOTE10:8080`
   - Exemplo: `http://192.168.1.100:8080`
3. **Painel web carregará** com todos os controles

#### 🎛️ Controles Disponíveis:

**Seleção de Câmera:**
- **Wide (Principal)** - Câmera principal 12MP
- **Ultra Wide** - Grande angular 16MP
- **Telephoto** - Zoom 2x 12MP
- **Frontal** - Câmera frontal 10MP

**ISO (Sensibilidade):**
- Range: 50 a 3200
- Padrão: 400
- Menor ISO = menos ruído, precisa mais luz
- Maior ISO = mais ruído, funciona em ambientes escuros

**Exposição (Velocidade do Obturador):**
- Range: 1/8000s a 30s
- Padrão: 1/60s
- Mais rápido = congela movimento, precisa mais luz
- Mais lento = motion blur, funciona com pouca luz

**Foco:**
- 0 = Autofoco (padrão)
- 1-10 = Foco manual (distância em metros)

**Balanço de Branco:**
- **Auto** - Ajuste automático
- **Luz do Dia** - 5200K (sol)
- **Nublado** - 6000K (céu nublado)
- **Tungstênio** - 3200K (lâmpadas incandescentes)

---

### 2️⃣ Ver Stream no VLC Player

1. **Abra o VLC**
2. **Mídia → Abrir Fluxo de Rede** (Ctrl+N)
3. **Cole o endereço RTSP**:
   ```
   rtsp://IP_DO_NOTE10:8554/live
   ```
   Exemplo: `rtsp://192.168.1.100:8554/live`
4. **Clique em Reproduzir**
5. **Stream iniciará** (pode levar 2-5 segundos)

#### ⚙️ Configurações Recomendadas VLC:

- Ferramentas → Preferências → Entrada/Codecs
- Cache de rede: **300ms**
- Usar decodificação de hardware: **Ativado**

---

### 3️⃣ Ver Stream no OBS Studio

1. **Abra o OBS**
2. **Fontes → Adicionar (+) → Media Source**
3. **Nome**: "Camera Note10+" (ou qualquer nome)
4. **Desmarque** "Local File"
5. **Input**: Cole o endereço RTSP
   ```
   rtsp://IP_DO_NOTE10:8554/live
   ```
6. **Marque**:
   - ✅ Use hardware decoding when available
   - ✅ Restart playback when source becomes active
7. **OK**

#### 🎬 Uso em Transmissões:

- Adicione o Note10+ como fonte
- Combine com outras cenas
- Use para IRL streaming, vlogging, etc.
- Controle câmera remotamente enquanto transmite

---

## 🎮 Fluxo de Uso Completo

**Cenário: Produção de Vídeo com Controle Remoto**

1. **Posicione o Note10+** no tripé/suporte
2. **No PC, abra navegador** → `http://IP:8080`
3. **No PC, abra VLC/OBS** → `rtsp://IP:8554/live`
4. **Ajuste ISO** no painel web → Veja mudança instantânea no stream
5. **Mude exposição** → Stream atualiza em tempo real
6. **Troque de lente** → Stream muda para nova câmera
7. **Ajuste foco manualmente** → Controle preciso de profundidade

**Latência esperada**: 200-500ms (normal para WiFi)

---

## 📊 Especificações Técnicas

| Categoria | Especificação |
|-----------|---------------|
| **Resolução** | 1280x720 (HD Ready) |
| **Frame Rate** | 30 FPS (fixo) |
| **Bitrate** | 1.2 Mbps |
| **Codec Vídeo** | H.264 (AVC) |
| **Codec Áudio** | AAC (se habilitado) |
| **Protocolo Stream** | RTSP (Real-Time Streaming Protocol) |
| **Protocolo Controle** | HTTP REST API |
| **Latência** | 200-500ms (WiFi local) |
| **Porta RTSP** | 8554 (padrão RTSP) |
| **Porta HTTP** | 8080 (padrão HTTP alternativo) |
| **Consumo Bateria** | ~15-20% por hora (streaming contínuo) |
| **Compatibilidade** | Android 10+ (API 29+) |

---

## 📡 API REST - Documentação

### Endpoints

#### `GET /`
Retorna o painel de controle HTML

**Response**: HTML page

---

#### `GET /api/status`
Retorna o status atual da câmera

**Response**:
```json
{
  "camera": "0",
  "iso": 400,
  "exposure": 16666667
}
```

**Campos**:
- `camera`: ID da câmera ativa ("0" = Wide, "1" = Frontal, "2" = Ultra Wide, "3" = Telephoto)
- `iso`: Sensibilidade ISO atual
- `exposure`: Tempo de exposição em nanosegundos

---

#### `POST /api/control`
Envia comandos de controle

**Request Body**:
```json
{
  "iso": 800,
  "exposure": 33333334,
  "focus": 5.0,
  "whiteBalance": "daylight",
  "camera": "2"
}
```

**Parâmetros** (todos opcionais):
- `iso` (number): 50-3200
- `exposure` (number): Tempo em nanosegundos
- `focus` (number): 0 (auto) ou 1-10 (manual)
- `whiteBalance` (string): "auto", "daylight", "cloudy", "tungsten"
- `camera` (string): "0", "1", "2", "3"

**Response**:
```json
{"status": "ok"}
```

---

### Exemplos de Uso com cURL

**Obter status:**
```bash
curl http://192.168.1.100:8080/api/status
```

**Alterar ISO:**
```bash
curl -X POST http://192.168.1.100:8080/api/control \
  -H "Content-Type: application/json" \
  -d '{"iso": 800}'
```

**Trocar para Ultra Wide:**
```bash
curl -X POST http://192.168.1.100:8080/api/control \
  -H "Content-Type: application/json" \
  -d '{"camera": "2"}'
```

**Ajustar múltiplos parâmetros:**
```bash
curl -X POST http://192.168.1.100:8080/api/control \
  -H "Content-Type: application/json" \
  -d '{"iso": 1600, "exposure": 8333333, "whiteBalance": "tungsten"}'
```

---

## 🐞 Troubleshooting

### ❌ Stream não aparece no VLC

**Possíveis causas:**

1. **Dispositivos em redes diferentes**
   - ✅ Solução: Conecte Note10+ e PC na mesma WiFi
   - Teste: `ping IP_DO_NOTE10` no CMD

2. **Firewall bloqueando porta 8554**
   - ✅ Solução (Windows):
     ```
     Firewall do Windows → Permitir app
     → Permitir porta 8554 (TCP/UDP)
     ```

3. **App não está rodando**
   - ✅ Solução: Verifique que app está aberto no Note10+
   - Veja logs no Logcat

4. **IP incorreto**
   - ✅ Solução: Confirme IP no Logcat ou configurações WiFi

---

### ❌ Painel web não carrega

**Possíveis causas:**

1. **Firewall bloqueando porta 8080**
   - ✅ Solução: Libere porta 8080 no firewall

2. **Navegador em modo offline**
   - ✅ Solução: Verifique conexão de internet

3. **Typo no endereço**
   - ✅ Solução: Confirme `http://` no início
   - Exemplo correto: `http://192.168.1.100:8080`

---

### ❌ Controles não respondem

**Possíveis causas:**

1. **Permissões negadas**
   - ✅ Solução: Configurações → Apps → Camera2 RTSP → Permissões
   - Ative Câmera e Microfone

2. **Erro na Camera2 API**
   - ✅ Solução: Reinicie o app
   - Veja logs de erro no Logcat

3. **Requests não chegando**
   - ✅ Solução: Abra Network Inspector no navegador (F12)
   - Veja se POST /api/control retorna 200 OK

---

### ❌ Latência muito alta (>1 segundo)

**Possíveis causas:**

1. **WiFi congestionado**
   - ✅ Solução: Use banda 5GHz em vez de 2.4GHz
   - Aproxime dispositivos do roteador

2. **Bitrate muito alto**
   - ✅ Solução: Reduza resolução ou FPS (requer modificar código)

3. **CPU sobrecarregada**
   - ✅ Solução: Feche outros apps no Note10+

---

### ⚠️ App crasha ao abrir

**Possíveis causas:**

1. **Dependências não sincronizadas**
   - ✅ Solução: File → Invalidate Caches / Restart
   - Build → Clean Project → Rebuild Project

2. **Versão Android incompatível**
   - ✅ Solução: Verifique Android 10+ (API 29+)

3. **Erro de compilação**
   - ✅ Solução: Veja stack trace no Logcat
   - Procure por `Fatal Exception`

---

## 📁 Estrutura do Projeto

```
camera2api-brSS/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml          # Permissões e configuração
│   │       └── java/com/camera2rtsp/
│   │           ├── MainActivity.kt          # Activity principal
│   │           ├── Camera2Controller.kt     # Controle Camera2 API
│   │           ├── WebControlServer.kt      # Servidor HTTP + Painel Web
│   │           └── RtspServer.kt            # Servidor RTSP
│   └── build.gradle                         # Dependências do app
├── settings.gradle                          # Repositórios Maven
├── .gitignore                               # Arquivos ignorados
└── README.md                                # Este arquivo
```

---

## 🔧 Arquivos Principais

### `MainActivity.kt`
**Responsabilidade**: Gerenciamento geral do app

- Solicita permissões de câmera e microfone
- Inicializa `Camera2Controller`, `RtspServer`, `WebControlServer`
- Obtém e exibe endereço IP local
- Gerencia ciclo de vida da Activity

### `Camera2Controller.kt`
**Responsabilidade**: Interface com Camera2 API

- Controla abertura/fechamento de câmera
- Gerencia CaptureSession para streaming
- Aplica configurações manuais (ISO, exposição, foco, WB)
- Troca entre diferentes lentes
- Atualiza CaptureRequest em tempo real

### `WebControlServer.kt`
**Responsabilidade**: Servidor HTTP e API REST

- Serve painel de controle HTML na raiz (`/`)
- Endpoint `/api/status` para obter estado atual
- Endpoint `/api/control` para enviar comandos
- Parse JSON e aplicação de parâmetros
- NanoHTTPD rodando na porta 8080

### `RtspServer.kt`
**Responsabilidade**: Streaming RTSP

- Inicializa `RtspServerCamera2` (biblioteca pedroSG94)
- Configura parâmetros de vídeo (resolução, FPS, bitrate)
- Inicia/para stream H.264
- Fornece endpoint RTSP na porta 8554

---

## 🎓 Conceitos Técnicos

### Camera2 API

A **Camera2 API** é a interface moderna do Android para controle avançado de câmera, substituindo a antiga Camera API. Oferece:

- **Controle manual total**: ISO, exposição, foco, balanço de branco
- **RAW capture**: Fotos em formato RAW (DNG)
- **Burst mode**: Múltiplas capturas rápidas
- **Multi-câmera**: Acesso simultâneo a várias lentes

**Neste projeto**, usamos Camera2 para:
1. Abrir câmera com `CameraManager.openCamera()`
2. Criar `CameraCaptureSession` para streaming contínuo
3. Configurar `CaptureRequest` com parâmetros manuais
4. Aplicar `setRepeatingRequest()` para atualização em tempo real

### RTSP (Real-Time Streaming Protocol)

**RTSP** é um protocolo de controle para streaming de mídia em tempo real. Funciona sobre TCP/UDP.

**Características**:
- Baixa latência (200-500ms)
- Compatível com VLC, OBS, FFmpeg
- Suporta múltiplos clientes simultâneos
- Padrão da indústria para câmeras IP

**Neste projeto**, usamos biblioteca [rtmp-rtsp-stream-client-java](https://github.com/pedroSG94/rtmp-rtsp-stream-client-java) que:
- Codifica vídeo em H.264 via MediaCodec
- Empacota em RTP/RTSP
- Serve na porta 8554

### NanoHTTPD

**NanoHTTPD** é um servidor HTTP minimalista em Java/Kotlin.

**Vantagens**:
- Leve (~100KB)
- Fácil integração
- Não requer root
- Ideal para apps Android

**Neste projeto**, usamos para:
- Servir painel HTML interativo
- API REST para controle remoto
- Comunicação JSON bidirecional

---

## 🚀 Melhorias Futuras

### Planejadas

- [ ] **Áudio no stream RTSP** (atualmente só vídeo)
- [ ] **Múltiplas resoluções** (seletor 720p/1080p/4K)
- [ ] **Gravação local** em MP4
- [ ] **Autenticação** no painel web (login/senha)
- [ ] **HTTPS** para controle seguro
- [ ] **Suporte landscape** no painel web
- [ ] **Presets** salvos (ISO/Exp combos)
- [ ] **Histograma** em tempo real
- [ ] **Zebras** para exposição
- [ ] **Focus peaking**
- [ ] **TimeLapse** remoto

### Contribuições

Contribuições são bem-vindas! Por favor:

1. Fork o projeto
2. Crie branch (`git checkout -b feature/MinhaFeature`)
3. Commit mudanças (`git commit -m 'Add: MinhaFeature'`)
4. Push para branch (`git push origin feature/MinhaFeature`)
5. Abra Pull Request

---

## 📄 Licença

MIT License

Copyright (c) 2026 Luan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## 🔗 Referências

### Documentação Oficial

- [Android Camera2 API](https://developer.android.com/training/camera2)
- [Camera2 API Reference](https://developer.android.com/reference/android/hardware/camera2/package-summary)
- [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)

### Bibliotecas Utilizadas

- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) - Servidor HTTP
- [rtmp-rtsp-stream-client-java](https://github.com/pedroSG94/rtmp-rtsp-stream-client-java) - RTSP Streaming
- [Gson](https://github.com/google/gson) - JSON parsing

### Players Compatíveis

- [VLC Media Player](https://www.videolan.org/) - Player universal
- [OBS Studio](https://obsproject.com/) - Software de transmissão
- [FFmpeg](https://ffmpeg.org/) - Ferramenta de linha de comando

### Protocolos

- [RTSP RFC 2326](https://tools.ietf.org/html/rfc2326)
- [RTP RFC 3550](https://tools.ietf.org/html/rfc3550)
- [H.264 Specification](https://www.itu.int/rec/T-REC-H.264)

---

## 👤 Autor

**Luan Santos**

- GitHub: [@luanscps](https://github.com/luanscps)
- Email: luanscps@gmail.com
- Localização: Brasil

---

## 🙏 Agradecimentos

- **Pedro SG94** - Biblioteca RTSP/RTMP
- **NanoHTTPD Team** - Servidor HTTP leve
- **Google Android Team** - Camera2 API
- **Comunidade Android** - Suporte e documentação

---

## 📧 Suporte

Encontrou um bug? Tem uma sugestão?

- **Issues**: [github.com/luanscps/camera2api-brSS/issues](https://github.com/luanscps/camera2api-brSS/issues)
- **Email**: luanscps@gmail.com

---

**⭐ Se este projeto foi útil, considere dar uma estrela no GitHub!**

---

*Desenvolvido com ❤️ para Samsung Galaxy Note10+ - Transforme seu smartphone em uma câmera profissional controlada remotamente*
