package com.camera2rtsp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pedro.rtspserver.RtspServerCamera2
import fi.iki.elonen.NanoHTTPD

class WebControlServer(
    port: Int,
    private val cameraController: Camera2Controller,
    private val context: Context
) : NanoHTTPD(port) {

    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/"                  -> serveControlPanel()
            uri == "/status"            -> serveStatus()
            uri == "/api/status"        -> serveStatus()
            uri == "/api/capabilities" -> serveCapabilities()
            uri == "/api/control" && session.method == Method.POST -> handleControl(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun serveCapabilities(): Response {
        val capabilities = cameraController.discoverAllCameras(context)
        val json = gson.toJson(capabilities)
        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    private fun serveStatus(): Response {
        val c = cameraController
        val srv: RtspServerCamera2? = c.server

        val focusMode = if (c.autoFocus) "continuous-video" else "off"
        val focusDist = String.format("%.2f", c.focusDistance)

        // Número de clientes RTSP conectados via RtspServer.getNumClients()
        val numClients = try {
            val streamClient = srv?.getStreamClient()
            // RtspServerStreamClient não expõe contagem direta;
            // usamos reflexão apenas se a lib não expuser método público.
            // Na versão 1.3.6 o RtspServer é acessível via campo interno;
            // a forma segura é via isStreaming como proxy simples.
            if (srv?.isStreaming == true) 1 else 0
        } catch (_: Exception) { 0 }

        val curvals = mapOf(
            "video_size"      to "${c.currentWidth}x${c.currentHeight}",
            "ffc"             to if (c.currentCameraId == "1") "on" else "off",
            "camera_id"       to c.currentCameraId,
            "zoom"            to "${(c.zoomLevel * 100).toInt() + 100}",
            "focusmode"       to focusMode,
            "focus_distance"  to focusDist,
            "focal_length"    to "4.30",
            "aperture"        to "1.50",
            "whitebalance"    to c.whiteBalanceMode,
            "torch"           to if (c.lanternEnabled) "on" else "off",
            "iso"             to c.isoValue.toString(),
            "exposure_ns"     to c.exposureNs.toString(),
            "frame_duration"  to c.frameDurationNs.toString(),
            "manual_sensor"   to if (c.manualSensor) "on" else "off",
            "bitrate_kbps"    to c.currentBitrate.toString(),
            "fps"             to c.currentFps.toString(),
            "ois"             to if (c.oisEnabled) "on" else "off"
        )

        val avail = mapOf(
            "focusmode"      to listOf("off", "auto", "continuous-video", "continuous-picture"),
            "whitebalance"   to listOf("auto", "incandescent", "fluorescent", "daylight", "cloudy"),
            "video_size"     to listOf("3840x2160", "1920x1080", "1280x720", "960x540"),
            "torch"          to listOf("on", "off"),
            "manual_sensor"  to listOf("on", "off"),
            "focus_distance" to (0..100).map { String.format("%.2f", it * 0.1) },
            "iso"            to listOf("50","100","200","400","800","1600","3200"),
            "zoom"           to (100..800 step 7).map { it.toString() },
            "camera_id"      to listOf("0", "1", "2", "3")
        )

        val status = mapOf(
            "video_connections" to numClients,
            "audio_connections" to 0,
            "streaming"         to (srv?.isStreaming ?: false),
            "curvals"           to curvals,
            "avail"             to avail
        )

        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(status))
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    private fun handleControl(session: IHTTPSession): Response {
        val map = mutableMapOf<String, String>()
        return try {
            session.parseBody(map)
            val json = map["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "text/plain", "No data"
            )
            val params = gson.fromJson<Map<String, Any>>(
                json, object : TypeToken<Map<String, Any>>() {}.type
            )
            cameraController.updateSettings(params)
            val resp = newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
            resp.addHeader("Access-Control-Allow-Origin", "*")
            resp
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "application/json",
                """{"status":"error","message":"${e.message}"}"""
            )
        }
    }

    private fun serveControlPanel(): Response {
        val html = """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>Camera2 RTSP Control</title>
<style>
:root{
  --bg:#0f172a;--surface:#1e293b;--surface2:#263348;--border:#334155;
  --accent:#38bdf8;--accent2:#0ea5e9;--text:#f1f5f9;--muted:#94a3b8;
  --green:#10b981;--red:#ef4444;--yellow:#f59e0b;--orange:#f97316;
}
*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html{font-size:14px}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
  background:var(--bg);color:var(--text);padding:10px;min-height:100vh;
  overscroll-behavior:none}
.container{max-width:900px;margin:0 auto}
.header{text-align:center;margin-bottom:14px}
.header h1{color:var(--accent);font-size:clamp(16px,5vw,24px);font-weight:800}
.header p{color:var(--muted);font-size:11px;margin-top:3px}
.statusbar{display:flex;flex-wrap:wrap;gap:6px;justify-content:center;
  padding:10px;background:var(--surface);border-radius:14px;
  border:1px solid var(--border);margin-bottom:14px}
.badge{display:flex;align-items:center;gap:5px;background:var(--bg);
  padding:5px 11px;border-radius:20px;font-size:11px;font-weight:600;
  white-space:nowrap}
.dot{width:8px;height:8px;border-radius:50%;background:var(--green);
  box-shadow:0 0 6px var(--green);transition:background .3s}
.dot.off{background:var(--red);box-shadow:0 0 6px var(--red)}
.lat-ok{color:var(--green)}.lat-warn{color:var(--yellow)}.lat-bad{color:var(--red)}
.card{background:var(--surface);padding:14px;margin-bottom:12px;
  border-radius:14px;border:1px solid var(--border)}
.card.hidden{display:none}
.card h3{color:var(--accent);font-size:13px;font-weight:700;margin-bottom:11px;
  display:flex;align-items:center;gap:6px}
.val{display:inline-block;background:var(--bg);padding:2px 10px;
  border-radius:6px;font-weight:700;color:var(--accent);
  min-width:72px;text-align:center;font-size:12px}
.info-row{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:6px}
.info-pill{background:var(--bg);padding:4px 10px;border-radius:8px;
  font-size:11px;color:var(--muted)}
.info-pill span{color:var(--text);font-weight:600}
.btngroup{display:flex;flex-wrap:wrap;gap:7px}
button{background:var(--surface2);color:var(--text);border:1px solid var(--border);
  padding:9px 13px;border-radius:10px;cursor:pointer;
  font-weight:600;font-size:12px;line-height:1.2;
  transition:background .15s,transform .1s,box-shadow .15s;
  touch-action:manipulation;user-select:none;min-height:40px}
button:hover{background:var(--accent);color:#0f172a;border-color:var(--accent)}
button:active{transform:scale(.93)}
button.active{background:var(--green);color:#fff;border-color:var(--green)}
button.disabled{opacity:0.3;cursor:not-allowed;pointer-events:none}
@keyframes pulse-ok{
  0%{box-shadow:0 0 0 0 rgba(16,185,129,.8)}
  70%{box-shadow:0 0 0 12px rgba(16,185,129,0)}
  100%{box-shadow:0 0 0 0 rgba(16,185,129,0)}}
@keyframes pulse-err{
  0%{box-shadow:0 0 0 0 rgba(239,68,68,.8)}
  70%{box-shadow:0 0 0 12px rgba(239,68,68,0)}
  100%{box-shadow:0 0 0 0 rgba(239,68,68,0)}}
button.fb-ok{animation:pulse-ok .45s ease}
button.fb-err{animation:pulse-err .45s ease}
.slider-wrap{padding:4px 0}
input[type=range]{width:100%;height:5px;background:var(--border);
  border-radius:4px;outline:none;-webkit-appearance:none;margin:10px 0;cursor:pointer}
input[type=range]::-webkit-slider-thumb{-webkit-appearance:none;
  width:24px;height:24px;background:var(--accent);
  cursor:grab;border-radius:50%;border:3px solid var(--bg);
  box-shadow:0 2px 6px rgba(0,0,0,.5)}
input[type=range]:disabled{opacity:0.3;cursor:not-allowed}
input[type=range]:disabled::-webkit-slider-thumb{cursor:not-allowed}
.rlabels{display:flex;justify-content:space-between;
  font-size:10px;color:var(--muted);margin-top:2px}
.toggle-row{display:flex;align-items:center;justify-content:space-between;
  padding:9px 0;border-bottom:1px solid var(--border)}
.toggle-row:last-child{border-bottom:none}
.toggle-label{font-size:13px;display:flex;align-items:center;gap:7px}
.switch{position:relative;display:inline-block;width:46px;height:26px;flex-shrink:0}
.switch input{opacity:0;width:0;height:0}
.sw{position:absolute;cursor:pointer;inset:0;
  background:var(--border);border-radius:26px;transition:.3s}
.sw:before{content:"";position:absolute;height:20px;width:20px;
  left:3px;bottom:3px;background:#fff;border-radius:50%;transition:.3s;
  box-shadow:0 1px 3px rgba(0,0,0,.4)}
input:checked+.sw{background:var(--green)}
input:checked+.sw:before{transform:translateX(20px)}
input:disabled+.sw{opacity:0.3;cursor:not-allowed}
@media(min-width:480px){
  .grid2{display:grid;grid-template-columns:1fr 1fr;gap:12px}
  .grid2 .card{margin-bottom:0}}
#toast{position:fixed;bottom:22px;left:50%;
  transform:translateX(-50%) translateY(80px);
  padding:10px 22px;border-radius:24px;
  font-size:13px;font-weight:700;
  pointer-events:none;transition:transform .25s cubic-bezier(.34,1.56,.64,1),opacity .25s;
  opacity:0;z-index:999;white-space:nowrap}
#toast.show{transform:translateX(-50%) translateY(0);opacity:1}
#toast.ok{background:var(--green);color:#fff}
#toast.err{background:var(--red);color:#fff}
.preset-quality{display:flex;gap:6px;margin-bottom:10px}
.pq-btn{flex:1;padding:8px 4px;font-size:11px;text-align:center}
.fps-row{display:flex;gap:6px;margin-top:8px}
.fps-row button{flex:1;font-size:11px;padding:7px 4px}
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <h1>📷 Camera2 RTSP Control</h1>
    <p>Samsung Galaxy Note10+ · SM-N975F · Android 12</p>
  </div>
  <div class="statusbar">
    <div class="badge"><span class="dot off" id="dot-stream"></span><span id="lbl-stream">Conectando…</span></div>
    <div class="badge">📷 <span id="lbl-cam">—</span></div>
    <div class="badge">🏞️ <span id="lbl-res">—</span></div>
    <div class="badge">📶 <span id="lbl-br">—</span> kbps</div>
    <div class="badge">🔗 <span id="lbl-clients">0</span> cliente(s)</div>
    <div class="badge">⏱️ <span id="lbl-lat" class="lat-ok">—</span></div>
  </div>
  <div class="card">
    <h3>📊 Estado da câmera</h3>
    <div class="info-row">
      <div class="info-pill">Foco: <span id="info-focusmode">—</span></div>
      <div class="info-pill">Dist: <span id="info-focusdist">—</span></div>
      <div class="info-pill">ISO: <span id="info-iso">—</span></div>
      <div class="info-pill">Exp: <span id="info-exp">—</span></div>
      <div class="info-pill">Focal: <span id="info-focal">—</span> mm</div>
      <div class="info-pill">f/<span id="info-ap">—</span></div>
      <div class="info-pill">WB: <span id="info-wb">—</span></div>
      <div class="info-pill">OIS: <span id="info-ois">—</span></div>
      <div class="info-pill">FPS: <span id="info-fps">—</span></div>
    </div>
  </div>
  <div class="card" id="card-camera">
    <h3>🎥 Câmera</h3>
    <div class="btngroup" id="btngroup-camera"></div>
  </div>
  <div class="grid2">
    <div class="card" id="card-resolution">
      <h3>📏 Resolução &amp; Qualidade</h3>
      <div class="btngroup preset-quality" id="btngroup-resolution"></div>
      <div class="fps-row" id="btngroup-fps"></div>
    </div>
    <div class="card" id="card-bitrate">
      <h3>📶 Bitrate <span class="val" id="br-value">4000</span> kbps</h3>
      <div class="slider-wrap">
        <input type="range" id="bitrate" min="500" max="25000" value="4000" step="500"
               oninput="updateBitrate(this.value)">
        <div class="rlabels"><span>500k</span><span>12M</span><span>25M</span></div>
      </div>
      <div class="btngroup" style="margin-top:8px">
        <button onclick="setBitratePreset(2000)">2M</button>
        <button onclick="setBitratePreset(4000)">4M</button>
        <button onclick="setBitratePreset(8000)">8M</button>
        <button onclick="setBitratePreset(20000)">20M</button>
      </div>
    </div>
  </div>
  <div class="card" id="card-zoom">
    <h3>🔍 Zoom <span class="val" id="zoom-val">1×</span></h3>
    <div class="slider-wrap">
      <input type="range" id="zoom" min="0" max="1" value="0" step="0.01"
             oninput="updateZoom(this.value)">
      <div class="rlabels"><span>1× (sem zoom)</span><span>Máximo</span></div>
    </div>
    <div class="btngroup" style="margin-top:8px">
      <button onclick="setZoomPreset(0)">1×</button>
      <button onclick="setZoomPreset(0.25)">~2×</button>
      <button onclick="setZoomPreset(0.5)">~4×</button>
      <button onclick="setZoomPreset(1.0)">Máx</button>
    </div>
  </div>
  <div class="card" id="card-focus">
    <h3>🎯 Foco <span class="val" id="focus-val">Auto</span></h3>
    <div class="slider-wrap">
      <input type="range" id="focus" min="0" max="10" value="0" step="0.1"
             oninput="updateFocus(this.value)">
      <div class="rlabels"><span>Auto / ∞</span><span>Macro (10D)</span></div>
    </div>
    <div class="btngroup" style="margin-top:8px" id="btngroup-focusmode"></div>
  </div>
  <div class="grid2">
    <div class="card" id="card-iso">
      <h3>🌡️ ISO <span class="val" id="iso-val">50</span></h3>
      <div class="slider-wrap">
        <input type="range" id="iso" min="0" max="100" value="0" step="1"
               oninput="updateISO(this.value)">
        <div class="rlabels"><span>50</span><span>1600</span><span>3200</span></div>
      </div>
      <div class="toggle-row" style="margin-top:6px">
        <span class="toggle-label">🔧 Sensor Manual</span>
        <label class="switch">
          <input type="checkbox" id="toggle-manual" onchange="toggleManual(this)">
          <span class="sw"></span>
        </label>
      </div>
    </div>
    <div class="card" id="card-ev">
      <h3>🕐 Exposure (EV) <span class="val" id="ev-val">±0</span></h3>
      <div class="slider-wrap">
        <input type="range" id="ev" min="-8" max="8" value="0" step="1"
               oninput="updateEV(this.value)">
        <div class="rlabels"><span>-8</span><span>0</span><span>+8</span></div>
      </div>
      <div class="btngroup" style="margin-top:8px">
        <button onclick="setEVPreset(-4)">-4</button>
        <button onclick="setEVPreset(0)">±0</button>
        <button onclick="setEVPreset(4)">+4</button>
      </div>
    </div>
  </div>
  <div class="card" id="card-wb">
    <h3>☀️ Balanço de Branco</h3>
    <div class="btngroup" id="btngroup-wb"></div>
  </div>
  <div class="card" id="card-extras">
    <h3>🛠️ Controles Extras</h3>
    <div id="extras-content"></div>
  </div>
  <p style="text-align:center;margin-top:16px;color:var(--muted);font-size:10px;padding-bottom:20px">
    Camera2 API · RootEncoder · NanoHTTPD · v2.1
  </p>
</div>
<div id="toast" class="ok">✓ Enviado</div>
<script>
const ISO_LIST=[50,81,112,143,174,205,236,267,298,329,360,391,422,453,484,
  515,546,577,608,639,670,701,732,763,794,825,856,887,918,949,980,1011,1042,
  1073,1104,1135,1166,1197,1228,1259,1290,1321,1352,1383,1414,1445,1476,1507,
  1538,1569,1600,1631,1662,1693,1724,1755,1786,1817,1848,1879,1910,1941,1972,
  2003,2034,2065,2096,2127,2158,2189,2220,2251,2282,2313,2344,2375,2406,2437,
  2468,2499,2530,2561,2592,2623,2654,2685,2716,2747,2778,2809,2840,2871,2902,
  2933,2964,2995,3026,3057,3088,3119,3150,3200];
const CAM_NAMES={'0':'Wide','1':'Frontal','2':'Ultra Wide','3':'Telephoto'};

let _caps = null;
let _currentCamId = "0";
let _toastTimer;

function showToast(msg,isError){
  const t=document.getElementById('toast');
  t.textContent=msg;t.className=isError?'err':'ok';t.classList.add('show');
  clearTimeout(_toastTimer);_toastTimer=setTimeout(()=>t.classList.remove('show'),1800);
}

function feedback(btn,ok){
  if(!btn)return;
  const cls=ok!==false?'fb-ok':'fb-err';
  btn.classList.remove('fb-ok','fb-err');void btn.offsetWidth;
  btn.classList.add(cls);setTimeout(()=>btn.classList.remove(cls),500);
}

function markActive(attr,val){
  document.querySelectorAll('['+attr+']').forEach(b=>{
    b.classList.toggle('active',b.getAttribute(attr)===String(val));
  });
}

function sendControl(data,btn,msg){
  fetch('/api/control',{
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body:JSON.stringify(data)
  }).then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.json();})
  .then(()=>{feedback(btn,true);showToast(msg||'✓ OK',false);})
  .catch(err=>{feedback(btn,false);showToast('❌ '+err.message,true);});
}

function getCameraCapabilities(camId) {
  if (!_caps) return null;
  return _caps.find(c => c.camera_id === camId);
}

function updateUIForCamera(camId) {
  _currentCamId = camId;
  const cap = getCameraCapabilities(camId);
  if (!cap) return;

  // Zoom
  const zoomCard = document.getElementById('card-zoom');
  const zoomSlider = document.getElementById('zoom');
  if (cap.zoom_range && cap.zoom_range[1] > 1.0) {
    zoomCard.classList.remove('hidden');
    zoomSlider.disabled = false;
  } else {
    zoomCard.classList.add('hidden');
    zoomSlider.disabled = true;
  }

  // Foco manual
  const focusCard = document.getElementById('card-focus');
  const focusSlider = document.getElementById('focus');
  const focusModeGroup = document.getElementById('btngroup-focusmode');
  if (cap.focus_distance_range && cap.focus_distance_range[1] > 0) {
    focusCard.classList.remove('hidden');
    focusSlider.disabled = false;
    // Rebuild focus mode buttons
    focusModeGroup.innerHTML = '';
    cap.supported_af_modes.forEach(mode => {
      const btn = document.createElement('button');
      if (mode === 'auto' || mode === 'continuous-video') {
        btn.textContent = mode === 'auto' ? '♻️ Auto' : '🎬 Contínuo';
        btn.onclick = () => setFocusMode(mode);
        focusModeGroup.appendChild(btn);
      } else if (mode === 'off') {
        btn.textContent = '🔒 Travar';
        btn.onclick = () => setFocusMode('off');
        focusModeGroup.appendChild(btn);
      }
    });
  } else {
    focusCard.classList.add('hidden');
    focusSlider.disabled = true;
  }

  // ISO / Manual Sensor
  const isoCard = document.getElementById('card-iso');
  const isoSlider = document.getElementById('iso');
  const toggleManual = document.getElementById('toggle-manual');
  if (cap.supports_manual_sensor && cap.iso_range) {
    isoCard.classList.remove('hidden');
    isoSlider.disabled = false;
    toggleManual.disabled = false;
  } else {
    isoCard.classList.add('hidden');
    isoSlider.disabled = true;
    toggleManual.disabled = true;
  }

  // EV
  const evCard = document.getElementById('card-ev');
  const evSlider = document.getElementById('ev');
  if (cap.ev_range) {
    evCard.classList.remove('hidden');
    evSlider.disabled = false;
    evSlider.min = cap.ev_range[0];
    evSlider.max = cap.ev_range[1];
    const labels = evCard.querySelector('.rlabels');
    labels.innerHTML = `<span>${cap.ev_range[0]}</span><span>0</span><span>+${cap.ev_range[1]}</span>`;
  } else {
    evCard.classList.add('hidden');
    evSlider.disabled = true;
  }

  // White Balance
  const wbGroup = document.getElementById('btngroup-wb');
  wbGroup.innerHTML = '';
  cap.supported_awb_modes.forEach(mode => {
    const btn = document.createElement('button');
    btn.setAttribute('data-wb', mode);
    const icons = {
      'auto': '🔄 Auto',
      'daylight': '☀️ Dia',
      'cloudy': '☁️ Nublado',
      'tungsten': '💡 Tungstênio',
      'incandescent': '💡 Tungstênio',
      'fluorescent': '🔦 Fluorescente'
    };
    btn.textContent = icons[mode] || mode;
    btn.onclick = () => setWB(mode, btn);
    wbGroup.appendChild(btn);
  });

  // Extras: Flash / OIS
  const extrasContent = document.getElementById('extras-content');
  extrasContent.innerHTML = '';
  
  if (cap.has_flash) {
    const row = document.createElement('div');
    row.className = 'toggle-row';
    row.innerHTML = `
      <span class="toggle-label">🔦 Lanterna (Torch)</span>
      <label class="switch">
        <input type="checkbox" id="toggle-lantern" onchange="toggleLantern(this)">
        <span class="sw"></span>
      </label>
    `;
    extrasContent.appendChild(row);
  }

  if (cap.has_ois) {
    const row = document.createElement('div');
    row.className = 'toggle-row';
    row.innerHTML = `
      <span class="toggle-label">🎬 Estabilização OIS</span>
      <label class="switch">
        <input type="checkbox" id="toggle-ois" onchange="toggleOIS(this)">
        <span class="sw"></span>
      </label>
    `;
    extrasContent.appendChild(row);
  }

  if (!cap.has_flash && !cap.has_ois) {
    document.getElementById('card-extras').classList.add('hidden');
  } else {
    document.getElementById('card-extras').classList.remove('hidden');
  }
}

function switchCamera(id,btn){markActive('data-cam',id);updateUIForCamera(id);sendControl({camera:id},btn,'📷 '+CAM_NAMES[id]);}
function setResolution(res,btn){markActive('data-res',res);sendControl({resolution:res},btn,'📏 '+res);}
function setFPS(fps,btn){markActive('data-fps',fps);sendControl({fps:fps},btn,'🎬 '+fps+' fps');}
let _brT;
function updateBitrate(v){
  document.getElementById('br-value').textContent=v;
  clearTimeout(_brT);_brT=setTimeout(()=>sendControl({bitrate:+v},null,'📶 '+v+'kbps'),400);
}
function setBitratePreset(v){
  document.getElementById('bitrate').value=v;
  document.getElementById('br-value').textContent=v;
  sendControl({bitrate:v},null,'📶 '+v+'kbps');
}
let _zT;
function updateZoom(v){
  const pct=parseFloat(v);
  const mult=(1+pct*7).toFixed(1);
  document.getElementById('zoom-val').textContent=mult+'×';
  clearTimeout(_zT);_zT=setTimeout(()=>sendControl({zoom:pct},null,'🔍 '+mult+'×'),150);
}
function setZoomPreset(v){document.getElementById('zoom').value=v;updateZoom(v);}
let _fT;
function updateFocus(v){
  const f=parseFloat(v);
  document.getElementById('focus-val').textContent=f===0?'Auto':f.toFixed(1)+'D';
  clearTimeout(_fT);_fT=setTimeout(()=>sendControl({focus:f/10},null,'🎯 '+(f===0?'Auto':f.toFixed(1)+'D')),200);
}
function setFocusAuto(){document.getElementById('focus').value=0;updateFocus(0);}
function setFocusMode(mode){sendControl({focusmode:mode},null,'🎯 '+mode);}
let _iT;
function updateISO(v){
  const iso=ISO_LIST[Math.min(+v,ISO_LIST.length-1)];
  document.getElementById('iso-val').textContent=iso;
  clearTimeout(_iT);_iT=setTimeout(()=>sendControl({iso:iso},null,'🌡️ ISO '+iso),350);
}
let _eT;
function updateEV(v){
  const n=+v;
  document.getElementById('ev-val').textContent=(n>0?'+':'')+n;
  clearTimeout(_eT);_eT=setTimeout(()=>sendControl({exposure:n},null,'🕐 EV '+(n>0?'+':'')+n),300);
}
function setEVPreset(v){document.getElementById('ev').value=v;updateEV(v);}
function setWB(mode,btn){markActive('data-wb',mode);sendControl({whiteBalance:mode},btn,'☀️ '+mode);}
function toggleManual(chk){sendControl({manualSensor:chk.checked},null,chk.checked?'🔧 Manual ON':'🔧 Auto');}
function toggleLantern(chk){sendControl({lantern:chk.checked},null,chk.checked?'🔦 ON':'🔦 OFF');}
function toggleOIS(chk){sendControl({ois:chk.checked},null,chk.checked?'🎬 OIS ON':'🎬 OIS OFF');}
function nsToShutter(ns){
  if(ns<=0)return '—';
  const s=ns/1e9;
  return s>=1?s.toFixed(1)+'s':'1/'+Math.round(1/s)+'s';
}
function latClass(ms){return ms<100?'lat-ok':ms<300?'lat-warn':'lat-bad';}
let _pollFail=0;

async function pollStatus(){
  const t0=Date.now();
  try{
    const r=await fetch('/api/status');
    if(!r.ok)throw new Error('HTTP '+r.status);
    const d=await r.json();
    const lat=Date.now()-t0;
    const c=d.curvals||{};
    _pollFail=0;
    const streaming=d.streaming||false;
    document.getElementById('dot-stream').className='dot'+(streaming?'':' off');
    document.getElementById('lbl-stream').textContent=streaming?'▶ Streaming':'⏹ Parado';
    document.getElementById('lbl-cam').textContent=CAM_NAMES[c.camera_id]||c.camera_id||'—';
    document.getElementById('lbl-res').textContent=c.video_size||'—';
    document.getElementById('lbl-br').textContent=c.bitrate_kbps||'—';
    document.getElementById('lbl-clients').textContent=d.video_connections||0;
    const latEl=document.getElementById('lbl-lat');
    latEl.textContent=lat+'ms';latEl.className=latClass(lat);
    document.getElementById('info-focusmode').textContent=c.focusmode||'—';
    document.getElementById('info-focusdist').textContent=(c.focus_distance||'0.00')+'D';
    document.getElementById('info-iso').textContent=c.iso||'—';
    document.getElementById('info-exp').textContent=c.exposure_ns?nsToShutter(+c.exposure_ns):'—';
    document.getElementById('info-focal').textContent=c.focal_length||'—';
    document.getElementById('info-ap').textContent=c.aperture||'—';
    document.getElementById('info-wb').textContent=c.whitebalance||'—';
    document.getElementById('info-ois').textContent=c.ois||'—';
    document.getElementById('info-fps').textContent=(c.fps||'—')+'fps';
    syncChk('toggle-lantern',c.torch==='on');
    syncChk('toggle-ois',c.ois==='on');
    syncChk('toggle-manual',c.manual_sensor==='on');
    if(c.camera_id)markActive('data-cam',c.camera_id);
    if(c.whitebalance)markActive('data-wb',c.whitebalance);
    if(c.fps)markActive('data-fps',+c.fps);
    syncSlider('bitrate',c.bitrate_kbps,500,25000);
    if(c.bitrate_kbps)document.getElementById('br-value').textContent=c.bitrate_kbps;
  }catch(e){
    _pollFail++;
    if(_pollFail>=3){
      document.getElementById('dot-stream').className='dot off';
      document.getElementById('lbl-stream').textContent='Sem conexão';
    }
  }
}

function syncChk(id,val){
  const el=document.getElementById(id);
  if(el&&el.checked!==val)el.checked=val;
}

function syncSlider(id,val,min,max){
  if(!val)return;
  const el=document.getElementById(id);
  if(!el)return;
  const v=Math.max(min,Math.min(max,+val));
  if(document.activeElement!==el)el.value=v;
}

async function initCapabilities() {
  try {
    const r = await fetch('/api/capabilities');
    if (!r.ok) throw new Error('HTTP ' + r.status);
    _caps = await r.json();
    console.log('Capabilities loaded:', _caps);

    // Build camera buttons
    const camGroup = document.getElementById('btngroup-camera');
    _caps.forEach(cap => {
      const btn = document.createElement('button');
      btn.setAttribute('data-cam', cap.camera_id);
      const icons = {'0': '📷', '1': '🤳', '2': '🌊', '3': '🔭'};
      btn.textContent = (icons[cap.camera_id] || '📷') + ' ' + cap.name;
      btn.onclick = () => switchCamera(cap.camera_id, btn);
      camGroup.appendChild(btn);
    });

    // Build resolution buttons dynamically
    const resGroup = document.getElementById('btngroup-resolution');
    const firstCam = _caps[0];
    if (firstCam && firstCam.available_resolutions) {
      const resolutions = firstCam.available_resolutions;
      const presets = [
        { res: '720p', width: 1280, height: 720, bitrate: 4000, label: 'HD<br><small>720p · 4M</small>' },
        { res: '1080p', width: 1920, height: 1080, bitrate: 8000, label: 'FHD<br><small>1080p · 8M</small>' },
        { res: '4k', width: 3840, height: 2160, bitrate: 20000, label: '4K<br><small>2160p · 20M</small>' }
      ];
      presets.forEach(p => {
        if (resolutions.includes(`${p.width}x${p.height}`)) {
          const btn = document.createElement('button');
          btn.className = 'pq-btn';
          btn.setAttribute('data-res', p.res);
          btn.innerHTML = p.label;
          btn.onclick = () => setResolution(p.res, btn);
          resGroup.appendChild(btn);
        }
      });
    }

    // Build FPS buttons
    const fpsGroup = document.getElementById('btngroup-fps');
    const fpsOptions = [15, 24, 30];
    fpsOptions.forEach(fps => {
      const btn = document.createElement('button');
      btn.setAttribute('data-fps', fps);
      btn.textContent = fps + ' fps';
      if (fps === 30) btn.classList.add('active');
      btn.onclick = () => setFPS(fps, btn);
      fpsGroup.appendChild(btn);
    });

    // Initialize UI for camera 0
    updateUIForCamera('0');
  } catch (err) {
    console.error('Failed to load capabilities:', err);
  }
}

initCapabilities();
pollStatus();
setInterval(pollStatus,2000);
</script>
</body>
</html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
