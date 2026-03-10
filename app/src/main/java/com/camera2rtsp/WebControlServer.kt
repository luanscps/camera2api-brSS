package com.camera2rtsp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD

class WebControlServer(
    port: Int,
    private val cameraController: Camera2Controller
) : NanoHTTPD(port) {

    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/"             -> serveControlPanel()
            uri == "/api/status"   -> serveStatus()
            uri == "/api/control" && session.method == Method.POST -> handleControl(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    // ── /api/status ──────────────────────────────────────────────────────────
    private fun serveStatus(): Response {
        val c = cameraController
        val status = mapOf(
            "status"        to "active",
            "camera"        to c.currentCameraId,
            "exposureLevel" to c.exposureLevel,
            "whiteBalance"  to c.whiteBalanceMode,
            "autoFocus"     to c.autoFocus,
            "zoom"          to c.zoomLevel,
            "lantern"       to c.lanternEnabled,
            "ois"           to c.oisEnabled,
            "resolution"    to "${c.currentWidth}x${c.currentHeight}",
            "bitrate"       to c.currentBitrate,
            "fps"           to c.currentFps,
            "streaming"     to (c.server?.isStreaming ?: false)
        )
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(status))
    }

    // ── /api/control ─────────────────────────────────────────────────────────
    private fun handleControl(session: IHTTPSession): Response {
        val map = mutableMapOf<String, String>()
        return try {
            session.parseBody(map)
            val json = map["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "text/plain", "No data"
            )
            val params = gson.fromJson<Map<String, Any>>(json,
                object : TypeToken<Map<String, Any>>() {}.type)
            cameraController.updateSettings(params)
            newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                """{"status":"error","message":"${e.message}"}""")
        }
    }

    // ── HTML do painel ───────────────────────────────────────────────────────
    private fun serveControlPanel(): Response {
        val html = """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<title>Camera2 RTSP — Note10+</title>
<style>
:root{
  --bg:#0f172a;--surface:#1e293b;--border:#334155;
  --accent:#38bdf8;--accent2:#0ea5e9;--text:#f1f5f9;--muted:#94a3b8;
  --green:#10b981;--red:#ef4444;--yellow:#f59e0b;
}
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
  background:var(--bg);color:var(--text);padding:12px;min-height:100vh}
.container{max-width:820px;margin:0 auto}
h1{color:var(--accent);text-align:center;font-size:clamp(18px,5vw,26px);margin-bottom:4px}
.subtitle{text-align:center;color:var(--muted);font-size:13px;margin-bottom:16px}

/* Status bar -------------------------------------------------------- */
.statusbar{
  display:flex;flex-wrap:wrap;gap:8px;justify-content:center;
  margin-bottom:20px;padding:12px;background:var(--surface);
  border-radius:12px;border:1px solid var(--border)
}
.badge{
  display:flex;align-items:center;gap:5px;
  background:#0f172a;padding:5px 12px;border-radius:20px;
  font-size:12px;font-weight:600
}
.dot{width:8px;height:8px;border-radius:50%;background:var(--green)}
.dot.off{background:var(--red)}
#latency-badge{background:#0f172a}

/* Cards ------------------------------------------------------------- */
.card{
  background:var(--surface);padding:18px;margin-bottom:14px;
  border-radius:12px;border:1px solid var(--border)
}
.card h3{
  color:var(--accent);font-size:15px;margin-bottom:14px;
  display:flex;align-items:center;gap:6px
}
.val{
  display:inline-block;background:#0f172a;padding:3px 10px;
  border-radius:6px;font-weight:700;color:var(--accent);
  min-width:72px;text-align:center;font-size:13px
}

/* Botões ------------------------------------------------------------ */
.btngroup{display:flex;flex-wrap:wrap;gap:8px}
btn,button{
  background:var(--accent);color:#0f172a;border:none;
  padding:10px 16px;border-radius:8px;cursor:pointer;
  font-weight:700;font-size:13px;
  transition:background .15s,transform .1s,box-shadow .15s;
  touch-action:manipulation;-webkit-tap-highlight-color:transparent
}
button:hover{background:var(--accent2)}
button:active{transform:scale(.95)}
button.active{background:#10b981;color:#fff}
button.danger{background:#ef4444;color:#fff}
button.warn{background:#f59e0b;color:#0f172a}
button.feedback{
  animation:pulse .45s ease;
}
@keyframes pulse{
  0%  {box-shadow:0 0 0 0 rgba(56,189,248,.7)}
  70% {box-shadow:0 0 0 10px rgba(56,189,248,0)}
  100%{box-shadow:0 0 0 0 rgba(56,189,248,0)}
}

/* Slider ------------------------------------------------------------ */
input[type=range]{
  width:100%;height:6px;background:var(--border);
  border-radius:4px;outline:none;-webkit-appearance:none;
  margin:10px 0
}
input[type=range]::-webkit-slider-thumb{
  -webkit-appearance:none;width:22px;height:22px;
  background:var(--accent);cursor:pointer;border-radius:50%;
  box-shadow:0 0 4px rgba(0,0,0,.4)
}
input[type=range]::-moz-range-thumb{
  width:22px;height:22px;background:var(--accent);
  cursor:pointer;border-radius:50%;border:none
}
.range-labels{
  display:flex;justify-content:space-between;
  font-size:11px;color:var(--muted);margin-top:2px
}

/* Toggle switch ----------------------------------------------------- */
.toggle-row{
  display:flex;align-items:center;justify-content:space-between;
  margin:8px 0
}
.toggle-label{font-size:14px}
.switch{position:relative;display:inline-block;width:44px;height:24px}
.switch input{opacity:0;width:0;height:0}
.slider-sw{
  position:absolute;cursor:pointer;top:0;left:0;right:0;bottom:0;
  background:var(--border);border-radius:24px;transition:.3s
}
.slider-sw:before{
  content:"";position:absolute;height:18px;width:18px;
  left:3px;bottom:3px;background:#fff;
  border-radius:50%;transition:.3s
}
input:checked+.slider-sw{background:var(--green)}
input:checked+.slider-sw:before{transform:translateX(20px)}

/* Grid 2 colunas em tela maior -------------------------------------- */
@media(min-width:520px){
  .grid2{display:grid;grid-template-columns:1fr 1fr;gap:14px}
  .grid2 .card{margin-bottom:0}
}

/* Responsivo Note10+ (360dp) --------------------------------------- */
@media(max-width:380px){
  body{padding:8px}
  .card{padding:14px}
  button{padding:9px 12px;font-size:12px}
}

/* Toast feedback ---------------------------------------------------- */
#toast{
  position:fixed;bottom:20px;left:50%;transform:translateX(-50%) translateY(60px);
  background:#10b981;color:#fff;padding:10px 22px;border-radius:24px;
  font-size:13px;font-weight:600;pointer-events:none;
  transition:transform .3s,opacity .3s;opacity:0;z-index:999
}
#toast.show{transform:translateX(-50%) translateY(0);opacity:1}
</style>
</head>
<body>
<div class="container">
  <h1>📷 Camera2 RTSP Control</h1>
  <p class="subtitle">Samsung Galaxy Note10+ · SM-N975F</p>

  <!-- Status bar -->
  <div class="statusbar">
    <div class="badge"><span class="dot" id="dot-stream"></span><span id="lbl-stream">Carregando…</span></div>
    <div class="badge">📷 <span id="lbl-cam">—</span></div>
    <div class="badge">🎞️ <span id="lbl-res">—</span></div>
    <div class="badge" id="latency-badge">⏱️ <span id="lbl-latency">—</span></div>
    <div class="badge">📶 <span id="lbl-br">—</span> kbps</div>
  </div>

  <!-- Câmera -->
  <div class="card">
    <h3>🎥 Câmera</h3>
    <div class="btngroup">
      <button data-cam="0" onclick="switchCamera('0',this)">📷 Wide</button>
      <button data-cam="2" onclick="switchCamera('2',this)">🌊 Ultra Wide</button>
      <button data-cam="3" onclick="switchCamera('3',this)">🔭 Telephoto</button>
      <button data-cam="1" onclick="switchCamera('1',this)">🤳 Frontal</button>
    </div>
  </div>

  <!-- Resolução + Bitrate -->
  <div class="grid2">
    <div class="card">
      <h3>📐 Resolução</h3>
      <div class="btngroup">
        <button data-res="720p"  onclick="setResolution('720p',this)">HD 720p</button>
        <button data-res="1080p" onclick="setResolution('1080p',this)">FHD 1080p</button>
        <button data-res="4k"    onclick="setResolution('4k',this)">4K UHD</button>
      </div>
      <p style="font-size:11px;color:var(--muted);margin-top:8px">⚠️ 4K pode causar queda de fps</p>
    </div>
    <div class="card">
      <h3>📶 Bitrate <span class="val" id="br-value">2500</span> kbps</h3>
      <input type="range" id="bitrate" min="500" max="25000" value="2500" step="500"
             oninput="updateBitrate(this.value)">
      <div class="range-labels"><span>500k</span><span>25M</span></div>
    </div>
  </div>

  <!-- Zoom -->
  <div class="card">
    <h3>🔍 Zoom <span class="val" id="zoom-value">1.0×</span></h3>
    <input type="range" id="zoom" min="0" max="1" value="0" step="0.01"
           oninput="updateZoom(this.value)">
    <div class="range-labels"><span>1× (mínimo)</span><span>Máximo óptico</span></div>
  </div>

  <!-- ISO + Exposição -->
  <div class="grid2">
    <div class="card">
      <h3>⚡ ISO <span class="val" id="iso-value">400</span></h3>
      <input type="range" id="iso" min="50" max="3200" value="400" step="50"
             oninput="updateISO(this.value)">
      <div class="range-labels"><span>50</span><span>3200</span></div>
    </div>
    <div class="card">
      <h3>🕐 Exposição <span class="val" id="exp-value">1/60s</span></h3>
      <input type="range" id="exposure" min="0" max="100" value="50"
             oninput="updateExposure(this.value)">
      <div class="range-labels"><span>1/8000s</span><span>30s</span></div>
    </div>
  </div>

  <!-- Foco + Balanço de Branco -->
  <div class="grid2">
    <div class="card">
      <h3>🎯 Foco <span class="val" id="focus-value">Auto</span></h3>
      <input type="range" id="focus" min="0" max="10" value="0" step="0.5"
             oninput="updateFocus(this.value)">
      <div class="range-labels"><span>Auto</span><span>Manual</span></div>
    </div>
    <div class="card">
      <h3>☀️ Balanço de Branco</h3>
      <div class="btngroup">
        <button onclick="setWB('auto',this)">Auto</button>
        <button onclick="setWB('daylight',this)">☀️</button>
        <button onclick="setWB('cloudy',this)">☁️</button>
        <button onclick="setWB('tungsten',this)">💡</button>
      </div>
    </div>
  </div>

  <!-- Controles extras -->
  <div class="card">
    <h3>🛠️ Controles Extras</h3>
    <div class="toggle-row">
      <span class="toggle-label">🔦 Lanterna</span>
      <label class="switch">
        <input type="checkbox" id="toggle-lantern" onchange="toggleLantern(this)">
        <span class="slider-sw"></span>
      </label>
    </div>
    <div class="toggle-row">
      <span class="toggle-label">🎬 Estabilização OIS/EIS</span>
      <label class="switch">
        <input type="checkbox" id="toggle-ois" onchange="toggleOIS(this)">
        <span class="slider-sw"></span>
      </label>
    </div>
  </div>

  <div style="text-align:center;margin-top:20px;color:var(--muted);font-size:12px">
    Desenvolvido com Camera2 API · RootEncoder · NanoHTTPD
  </div>
</div>

<div id="toast">✓ Enviado</div>

<script>
// ── Utilitários ──────────────────────────────────────────────────────────────
function showToast(msg){
  const t=document.getElementById('toast');
  t.textContent=msg||'✓ Enviado';
  t.classList.add('show');
  clearTimeout(t._timer);
  t._timer=setTimeout(()=>t.classList.remove('show'),1500);
}

function feedback(btn){
  if(!btn) return;
  btn.classList.remove('feedback');
  void btn.offsetWidth;          // reflow
  btn.classList.add('feedback');
}

function sendControl(data, btn, msg){
  const start = Date.now();
  fetch('/api/control',{
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body:JSON.stringify(data)
  }).then(r=>r.json()).then(()=>{
    feedback(btn);
    showToast(msg||'✓ Enviado');
  }).catch(err=>{
    showToast('❌ Erro: '+err.message);
    console.error(err);
  });
}

// Marca botão ativo dentro de um data-* group
function markActive(sel, val){
  document.querySelectorAll('['+sel+']').forEach(b=>{
    b.classList.toggle('active', b.getAttribute(sel)===val);
  });
}

// ── Câmera ───────────────────────────────────────────────────────────────────
const CAM_NAMES = {'0':'Wide','1':'Frontal','2':'Ultra Wide','3':'Telephoto'};
function switchCamera(id,btn){
  markActive('data-cam', id);
  sendControl({camera:id}, btn, '📷 '+CAM_NAMES[id]);
}

// ── Resolução ────────────────────────────────────────────────────────────────
const RES_LABELS={'720p':'HD 720p','1080p':'FHD 1080p','4k':'4K UHD'};
function setResolution(res,btn){
  markActive('data-res', res);
  sendControl({resolution:res}, btn, '📐 '+RES_LABELS[res]);
}

// ── Bitrate ──────────────────────────────────────────────────────────────────
let brTimer;
function updateBitrate(v){
  document.getElementById('br-value').textContent=v;
  clearTimeout(brTimer);
  brTimer=setTimeout(()=>sendControl({bitrate:parseInt(v)}, null, '📶 '+v+'kbps'), 300);
}

// ── Zoom ─────────────────────────────────────────────────────────────────────
let zoomTimer;
function updateZoom(v){
  const pct=(parseFloat(v)*100).toFixed(0);
  document.getElementById('zoom-value').textContent=pct+'%';
  clearTimeout(zoomTimer);
  zoomTimer=setTimeout(()=>sendControl({zoom:parseFloat(v)}, null, '🔍 Zoom '+pct+'%'), 150);
}

// ── ISO ──────────────────────────────────────────────────────────────────────
let isoTimer;
function updateISO(v){
  document.getElementById('iso-value').textContent=v;
  clearTimeout(isoTimer);
  isoTimer=setTimeout(()=>sendControl({iso:parseInt(v)}, null, '⚡ ISO '+v), 300);
}

// ── Exposição ────────────────────────────────────────────────────────────────
let expTimer;
function updateExposure(v){
  const times=[1/8000,1/4000,1/2000,1/1000,1/500,1/250,1/125,1/60,
               1/30,1/15,1/8,1/4,1/2,1,2,4,8,15,30];
  const idx=Math.floor((v/100)*(times.length-1));
  const t=times[idx];
  const ns=Math.round(t*1e9);
  const disp=t<1?'1/'+Math.round(1/t)+'s':t+'s';
  document.getElementById('exp-value').textContent=disp;
  clearTimeout(expTimer);
  expTimer=setTimeout(()=>sendControl({exposure:ns}, null, '🕐 '+disp), 300);
}

// ── Foco ─────────────────────────────────────────────────────────────────────
let focusTimer;
function updateFocus(v){
  const disp=parseFloat(v)===0?'Auto':parseFloat(v).toFixed(1);
  document.getElementById('focus-value').textContent=disp;
  clearTimeout(focusTimer);
  focusTimer=setTimeout(()=>sendControl({focus:parseFloat(v)}, null, '🎯 Foco '+disp), 300);
}

// ── Balanço de branco ────────────────────────────────────────────────────────
function setWB(mode,btn){
  sendControl({whiteBalance:mode}, btn, '☀️ WB: '+mode);
}

// ── Lanterna ─────────────────────────────────────────────────────────────────
function toggleLantern(chk){
  sendControl({lantern:chk.checked}, null,
    chk.checked?'🔦 Lanterna ON':'🔦 Lanterna OFF');
}

// ── OIS/EIS ──────────────────────────────────────────────────────────────────
function toggleOIS(chk){
  sendControl({ois:chk.checked}, null,
    chk.checked?'🎬 OIS ON':'🎬 OIS OFF');
}

// ── Polling de status a cada 2 s ─────────────────────────────────────────────
let lastPollTime = Date.now();

async function pollStatus(){
  const t0 = Date.now();
  try {
    const r = await fetch('/api/status');
    const latency = Date.now() - t0;
    const d = await r.json();

    // Streaming dot
    const streaming = d.streaming;
    document.getElementById('dot-stream').className = 'dot' + (streaming?'':' off');
    document.getElementById('lbl-stream').textContent = streaming?'Streaming':'Parado';

    // Câmera
    document.getElementById('lbl-cam').textContent = CAM_NAMES[d.camera]||d.camera;

    // Resolução
    document.getElementById('lbl-res').textContent = d.resolution||'—';

    // Bitrate
    document.getElementById('lbl-br').textContent = d.bitrate||'—';

    // Latência de polling
    document.getElementById('lbl-latency').textContent = latency + 'ms';

    // Sync toggles
    if(document.getElementById('toggle-lantern'))
      document.getElementById('toggle-lantern').checked = !!d.lantern;
    if(document.getElementById('toggle-ois'))
      document.getElementById('toggle-ois').checked = !!d.ois;

  } catch(e) {
    document.getElementById('dot-stream').className = 'dot off';
    document.getElementById('lbl-stream').textContent = 'Sem conexão';
  }
}

pollStatus();
setInterval(pollStatus, 2000);
</script>
</body>
</html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
