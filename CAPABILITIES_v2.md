# Sistema de Detecção Dinâmica de Capabilities (v2)

## Visão Geral

A branch **v2** implementa um sistema inteligente que detecta automaticamente as capacidades de cada lente da câmera usando `CameraCharacteristics` e `INFO_SUPPORTED_HARDWARE_LEVEL`, mostrando apenas os controles disponíveis na interface web.

## Como Funciona

### 1. Backend: Detecção de Capacidades

#### `Camera2Controller.discoverAllCameras(context)`

Este método percorre todas as câmeras disponíveis no dispositivo e extrai:

```kotlin
for (id in manager.cameraIdList) {
    val chars = manager.getCameraCharacteristics(id)
    
    // Hardware Level (LEGACY, LIMITED, FULL, LEVEL_3)
    val hwLevel = chars.get(INFO_SUPPORTED_HARDWARE_LEVEL)
    
    // Capabilities flags
    val caps = chars.get(REQUEST_AVAILABLE_CAPABILITIES)
    val manualSensor = caps.contains(MANUAL_SENSOR)
    val manualPost = caps.contains(MANUAL_POST_PROCESSING)
    
    // Ranges dinâmicos
    val isoRange = chars.get(SENSOR_INFO_SENSITIVITY_RANGE)
    val expRange = chars.get(SENSOR_INFO_EXPOSURE_TIME_RANGE)
    val focusRange = chars.get(LENS_INFO_MINIMUM_FOCUS_DISTANCE)
    val zoomRange = chars.get(SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
    
    // Modos disponíveis
    val afModes = chars.get(CONTROL_AF_AVAILABLE_MODES)
    val awbModes = chars.get(CONTROL_AWB_AVAILABLE_MODES)
    
    // Hardware físico
    val hasFlash = chars.get(FLASH_INFO_AVAILABLE)
    val hasOIS = chars.get(LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
}
```

#### `CameraCapabilities` Data Class

Armazena todas as informações detectadas:

```kotlin
data class CameraCapabilities(
    val cameraId: String,
    val hardwareLevel: String,  // LEGACY, LIMITED, FULL, LEVEL_3
    val facing: String,         // BACK, FRONT, EXTERNAL
    val name: String,           // Wide, Ultra Wide, Telephoto, Frontal
    
    // Flags de capacidades
    val supportsManualSensor: Boolean,
    val supportsManualPostProcessing: Boolean,
    val supportsRaw: Boolean,
    val supportsBurstCapture: Boolean,
    val supportsDepthOutput: Boolean,
    val supportsLogicalMultiCamera: Boolean,
    
    // Ranges disponíveis
    val isoRange: Pair<Int, Int>?,
    val exposureTimeRange: Pair<Long, Long>?,
    val evRange: Pair<Int, Int>?,
    val focusDistanceRange: Pair<Float, Float>?,
    val zoomRange: Pair<Float, Float>?,
    val fpsRanges: List<Pair<Int, Int>>,
    
    // Formatos e resoluções
    val availableResolutions: List<String>,
    val supportedAFModes: List<String>,
    val supportedAEModes: List<String>,
    val supportedAWBModes: List<String>,
    
    // Hardware físico
    val hasFlash: Boolean,
    val hasOIS: Boolean,
    val focalLengths: List<Float>,
    val apertures: List<Float>
)
```

### 2. API Endpoint: `/api/capabilities`

O `WebControlServer` expõe um novo endpoint que retorna as capabilities em JSON:

```kotlin
private fun serveCapabilities(): Response {
    val capabilities = cameraController.discoverAllCameras(context)
    val json = gson.toJson(capabilities)
    val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
    resp.addHeader("Access-Control-Allow-Origin", "*")
    return resp
}
```

**Exemplo de resposta:**

```json
[
  {
    "camera_id": "0",
    "hardware_level": "FULL",
    "facing": "BACK",
    "name": "Wide",
    "supports_manual_sensor": true,
    "iso_range": [50, 3200],
    "exposure_time_range": [100000, 100000000],
    "ev_range": [-8, 8],
    "focus_distance_range": [0.0, 10.0],
    "zoom_range": [1.0, 8.0],
    "supported_af_modes": ["off", "auto", "continuous-video"],
    "supported_awb_modes": ["auto", "daylight", "cloudy", "tungsten"],
    "has_flash": true,
    "has_ois": true
  },
  {
    "camera_id": "1",
    "hardware_level": "LIMITED",
    "facing": "FRONT",
    "name": "Frontal",
    "supports_manual_sensor": false,
    "zoom_range": null,
    "focus_distance_range": null,
    "has_flash": false,
    "has_ois": false
  }
]
```

### 3. Frontend: Interface Dinâmica

#### `initCapabilities()` no HTML

Ao carregar a página, o JavaScript:

1. **Busca as capabilities** via `fetch('/api/capabilities')`
2. **Constrói os botões** de câmera dinamicamente
3. **Detecta resoluções** disponíveis
4. **Inicializa a UI** para a câmera 0

```javascript
async function initCapabilities() {
  const r = await fetch('/api/capabilities');
  _caps = await r.json();
  
  // Cria botões de câmera
  _caps.forEach(cap => {
    const btn = document.createElement('button');
    btn.setAttribute('data-cam', cap.camera_id);
    btn.textContent = cap.name;
    btn.onclick = () => switchCamera(cap.camera_id, btn);
    camGroup.appendChild(btn);
  });
  
  updateUIForCamera('0');
}
```

#### `updateUIForCamera(camId)`

Quando o usuário troca de câmera, esta função:

1. **Busca as capabilities** da câmera selecionada
2. **Mostra/esconde cards** baseado no que está disponível
3. **Habilita/desabilita controles** (sliders, toggles)
4. **Reconstrói botões** de foco/white balance dinamicamente

**Exemplo - Zoom:**

```javascript
const cap = getCameraCapabilities(camId);

if (cap.zoom_range && cap.zoom_range[1] > 1.0) {
  // Câmera suporta zoom
  document.getElementById('card-zoom').classList.remove('hidden');
  document.getElementById('zoom').disabled = false;
} else {
  // Câmera NÃO suporta zoom (ex: câmera frontal)
  document.getElementById('card-zoom').classList.add('hidden');
  document.getElementById('zoom').disabled = true;
}
```

**Exemplo - Foco Manual:**

```javascript
if (cap.focus_distance_range && cap.focus_distance_range[1] > 0) {
  // Câmera suporta foco manual
  focusCard.classList.remove('hidden');
  
  // Reconstrói botões de modo de foco baseado em supported_af_modes
  cap.supported_af_modes.forEach(mode => {
    if (mode === 'auto' || mode === 'continuous-video') {
      const btn = document.createElement('button');
      btn.textContent = mode === 'auto' ? '♻️ Auto' : '🎬 Contínuo';
      btn.onclick = () => setFocusMode(mode);
      focusModeGroup.appendChild(btn);
    }
  });
} else {
  // Câmera NÃO suporta foco manual (lente fixa)
  focusCard.classList.add('hidden');
}
```

**Exemplo - ISO/Manual Sensor:**

```javascript
if (cap.supports_manual_sensor && cap.iso_range) {
  // Câmera suporta controle manual de ISO
  isoCard.classList.remove('hidden');
  isoSlider.disabled = false;
  toggleManual.disabled = false;
} else {
  // Câmera NÃO suporta manual sensor
  isoCard.classList.add('hidden');
  isoSlider.disabled = true;
}
```

**Exemplo - White Balance Dinâmico:**

```javascript
const wbGroup = document.getElementById('btngroup-wb');
wbGroup.innerHTML = '';  // Limpa botões anteriores

cap.supported_awb_modes.forEach(mode => {
  const btn = document.createElement('button');
  btn.setAttribute('data-wb', mode);
  btn.textContent = icons[mode] || mode;
  btn.onclick = () => setWB(mode, btn);
  wbGroup.appendChild(btn);
});
```

**Exemplo - Flash/OIS:**

```javascript
if (cap.has_flash) {
  // Adiciona toggle de lanterna
  const row = document.createElement('div');
  row.innerHTML = `
    <span>🔦 Lanterna</span>
    <input type="checkbox" id="toggle-lantern" onchange="toggleLantern(this)">
  `;
  extrasContent.appendChild(row);
}

if (cap.has_ois) {
  // Adiciona toggle de OIS
  const row = document.createElement('div');
  row.innerHTML = `
    <span>🎬 OIS</span>
    <input type="checkbox" id="toggle-ois" onchange="toggleOIS(this)">
  `;
  extrasContent.appendChild(row);
}
```

## Benefícios

### ✅ Precisão
- Cada câmera mostra **apenas os controles que ela realmente suporta**
- Evita erro de tentar usar recursos não disponíveis
- Compatível com qualquer dispositivo Android com Camera2 API

### ✅ Experiência do Usuário
- Interface **limpa e não-confusa**
- Não mostra controles desabilitados/inúteis
- Feedback visual claro do que está disponível

### ✅ Manutenibilidade
- Sistema **auto-adaptável** - funciona em qualquer dispositivo
- Não precisa hardcode de capabilities por modelo
- Adicionar novo controle = apenas adicionar lógica de detecção + UI

## Exemplo Prático: Galaxy Note10+

### Câmera Wide (ID 0) - FULL
✅ Zoom (1× - 8×)  
✅ Foco manual (0 - 10D)  
✅ ISO manual (50 - 3200)  
✅ EV (-8 a +8)  
✅ White Balance (auto, daylight, cloudy, tungsten, fluorescent)  
✅ Lanterna  
✅ OIS  

### Câmera Frontal (ID 1) - LIMITED
❌ Zoom (lente fixa)  
❌ Foco manual (foco fixo)  
⚠️ ISO limitado  
✅ EV (-4 a +4)  
✅ White Balance (auto, daylight, cloudy)  
❌ Lanterna  
❌ OIS  

### Câmera Ultra Wide (ID 2) - LIMITED
⚠️ Zoom mínimo (0.6× - 2×)  
❌ Foco manual (foco fixo)  
⚠️ ISO limitado  
✅ EV  
✅ White Balance  
❌ Lanterna  
❌ OIS  

### Câmera Telephoto (ID 3) - FULL
✅ Zoom (2× - 10×)  
✅ Foco manual  
✅ ISO manual  
✅ EV  
✅ White Balance  
❌ Lanterna (sem flash)  
✅ OIS  

## Implementação

### Arquivos Modificados

1. **`CameraCapabilities.kt`** - Data class com todas as capabilities
2. **`Camera2Controller.kt`** - Método `discoverAllCameras()` já implementado
3. **`WebControlServer.kt`** - Endpoint `/api/capabilities` + HTML dinâmico
4. **`StreamingService.kt`** - Passa `context` para WebControlServer

### Como Testar

1. Compile e instale o app na branch v2
2. Inicie o streaming
3. Acesse `http://<IP>:8080` no navegador
4. Troque entre câmeras (📷 Wide, 🤳 Frontal, 🌊 Ultra Wide, 🔭 Telephoto)
5. Observe que os controles aparecem/desaparecem baseado na câmera
6. Abra Developer Tools → Network → busque `/api/capabilities` para ver JSON bruto

### Debug

Para verificar as capabilities detectadas:

```bash
adb logcat | grep "Camera2Controller"
```

Ou no navegador:

```javascript
fetch('/api/capabilities').then(r => r.json()).then(console.log)
```

## Roadmap Futuro

- [ ] Adicionar cache de capabilities (evitar re-query a cada page load)
- [ ] Mostrar hardware level na UI (badge LEGACY/LIMITED/FULL/LEVEL_3)
- [ ] Slider de ISO dinâmico baseado no range real da câmera
- [ ] Auto-ajuste de bitrate/FPS baseado em resolução máxima
- [ ] Preset modes (Portrait, Night, Sport) quando disponíveis
- [ ] Detecção de câmeras lógicas (multi-camera fusion)

---

**Desenvolvido por:** Luan  
**Versão:** 2.1  
**Data:** Março 2026
