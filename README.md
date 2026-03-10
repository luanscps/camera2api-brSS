# 📡 Camera2 API + RTSP Server - Streaming Profissional Android

> Sistema completo de streaming com Camera2 API + Servidor RTSP + Controle Web para Samsung Galaxy Note10+

## 🎯 Funcionalidades

- ✅ **Servidor RTSP** na porta 8554 - compatível com VLC, OBS, FFmpeg
- ✅ **Painel de Controle Web** na porta 8080 - acesso via navegador
- ✅ **Controle em Tempo Real** de ISO, exposição, foco e balanço de branco
- ✅ **4 Lentes do Note10+**: Wide, Ultra Wide, Telephoto, Frontal
- ✅ **API REST** para comunicação bidirecional

## 🏗️ Arquitetura do Sistema

```
┌────────────────────────────────────────┐
│        Samsung Galaxy Note10+          │
│  ┌──────────────────────────────────┐  │
│  │  Camera2Controller               │  │
│  │  - Controle manual ISO/Expo      │  │
│  │  - 4 câmeras disponíveis         │  │
│  └──────────────────────────────────┘  │
│              ↓                          │
│  ┌──────────────────────────────────┐  │
│  │  RtspServer (porta 8554)         │  │
│  │  Stream: rtsp://IP:8554/live     │  │
│  └──────────────────────────────────┘  │
│              ↓                          │
│  ┌──────────────────────────────────┐  │
│  │  WebControlServer (porta 8080)   │  │
│  │  Painel: http://IP:8080          │  │
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
              ↓ WiFi Local
┌────────────────────────────────────────┐
│           Windows 10 PC                │
│  ┌─────────────┐   ┌───────────────┐  │
│  │   Chrome    │   │   VLC / OBS   │  │
│  │  Controles  │   │    Stream     │  │
│  └─────────────┘   └───────────────┘  │
└────────────────────────────────────────┘
```

## 📦 Dependências (build.gradle)

```gradle
dependencies {
    // NanoHTTPD - Servidor HTTP leve
    implementation 'org.nanohttpd:nanohttpd:2.3.1'
    
    // Gson - Comunicação JSON
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Coroutines - Operações assíncronas
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // AndroidX Camera2
    implementation 'androidx.camera:camera-camera2:1.3.0'
    implementation 'androidx.camera:camera-lifecycle:1.3.0'
}
```

**settings.gradle:**

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

## 🚀 Como Usar

### 1️⃣ No Android (Note10+)

1. Clone o repositório
2. Abra no Android Studio
3. Compile e instale no dispositivo
4. Conceda permissões de câmera
5. Anote o IP exibido no Logcat

### 2️⃣ No PC (Windows)

**Painel de Controle:**
```
http://192.168.0.XXX:8080
```

**Stream no VLC:**
```
rtsp://192.168.0.XXX:8554/live
```

**Stream no OBS:**
- Adicionar → Media Source
- Desmarque "Local File"
- Input: `rtsp://192.168.0.XXX:8554/live`
- Marque "Use hardware decoding"

## 🎮 Controles Disponíveis

- **ISO**: 50 - 3200
- **Exposição**: 1/8000s - 30s
- **Foco**: Auto ou Manual (0-10)
- **Balanço de Branco**: Auto, Luz do Dia, Nublado, Tungstênio
- **Câmeras**: Wide, Ultra Wide, Telephoto, Frontal

## 📝 Permissões (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
```

## ⚠️ Requisitos

- Android 10+ (API 29+)
- Mesma rede WiFi (Note10+ e PC)
- Liberar portas 8080 e 8554 no firewall
- Resolução recomendada: 1280x720 @ 30fps
- Latência esperada: 200-500ms via WiFi

## 🛠️ Estrutura do Projeto

```
app/src/main/java/com/camera2rtsp/
├── MainActivity.kt          # Activity principal
├── Camera2Controller.kt     # Controle da Camera2 API
├── RtspServer.kt           # Servidor RTSP simplificado
└── WebControlServer.kt     # Servidor HTTP + painel web
```

## 🐛 Solução de Problemas

**Stream não aparece:**
- Verifique se ambos dispositivos estão na mesma rede
- Confirme o IP correto do Note10+
- Teste o servidor: acesse `http://IP:8080` primeiro

**Controles não respondem:**
- Recarregue a página do painel web
- Verifique permissões de câmera no Android

**Latência alta:**
- Reduza resolução para 640x480
- Use cabo USB + adb para debugging
- Prefira WiFi 5GHz se disponível

## 📄 Licença

MIT License - Livre para uso pessoal e comercial

## 👤 Autor

**Luan Santos**
- GitHub: [@luanscps](https://github.com/luanscps)
- Email: luanscps@gmail.com

---

⭐ **Se este projeto foi útil, deixe uma estrela!**