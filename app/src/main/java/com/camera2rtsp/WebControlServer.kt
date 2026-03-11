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
            uri == "/"           -> serveControlPanel()
            uri == "/status"     -> serveStatus()          // compatível IPWebcam
            uri == "/api/status" -> serveStatus()
            uri == "/api/control" && session.method == Method.POST -> handleControl(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  /status  —  estrutura inspirada no IPWebcam
    // ─────────────────────────────────────────────────────────────────────────
    private fun serveStatus(): Response {
        val c = cameraController
        val srv = c.server

        val focusMode = if (c.autoFocus) "continuous-video" else "off"
        val focusDist = String.format("%.2f", c.focusDistance)

        val curvals = mapOf(
            "video_size"      to "${c.currentWidth}x${c.currentHeight}",
            "ffc"             to if (c.currentCameraId == "1") "on" else "off",
            "camera_id"       to c.currentCameraId,
            "zoom"            to "${(c.zoomLevel * 100).toInt() + 100}",  // 100..800 como IPWebcam
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
            "video_connections" to (srv?.connectedClientsCount ?: 0),
            "audio_connections" to 0,
            "streaming"         to (srv?.isStreaming ?: false),
            "curvals"           to curvals,
            "avail"             to avail
        )

        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(status))
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  /api/control
    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    //  HTML WebGUI
    // ─────────────────────────────────────────────────────────────────────────
    private fun serveControlPanel(): Response {
        val html = """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>Camera2 RTSP Control</title>
<style>
:root{
  --bg:#0f172a;--surface:#1e293b;--border:#334155;
  --accent:#38bdf8;--accent2:#0ea5e9;--text:#f1f5f9;--muted:#94a3b8;
  --green:#10b981;--red:#ef4444;--yellow:#f59e0b;
}
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
  background:var(--bg);color:var(--text);padding:12px;min-height:100vh}
.container{max-width:860px;margin:0 auto}
h1{color:var(--accent);text-align:center;font-size:clamp(18px,5vw,26px);margin-bottom:4px}
.sub{text-align:center;color:var(--muted);font-size:12px;margin-bottom:16px}

.statusbar{display:flex;flex-wrap:wrap;gap:8px;justify-content:center;
  padding:12px;background:var(--surface);border-radius:12px;
  border:1px solid var(--border);margin-bottom:18px}
.badge{display:flex;align-items:center;gap:5px;background:#0f172a;
  padding:5px 12px;border-radius:20px;font-size:12px;font-weight:600}
.dot{width:8px;height:8px;border-radius:50%;background:var(--green)}
.dot.off{background:var(--red)}

.card{background:var(--surface);padding:16px;margin-bottom:14px;
  border-radius:12px;border:1px solid var(--border)}
.card h3{color:var(--accent);font-size:14px;margin-bottom:12px;
  display:flex;align-items:center;gap:6px}
.val{display:inline-block;background:#0f172a;padding:3px 10px;
  border-radius:6px;font-weight:700;color:var(--accent);
  min-width:80px;text-align:center;font-size:13px}
.info-row{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:10px}
.info-pill{background:#0f172a;padding:4px 10px;border-radius:8px;
  font-size:11px;color:var(--muted)}
.info-pill span{color:var(--text);font-weight:600}

.btngroup{display:flex;flex-wrap:wrap;gap:8px}
button{background:var(--accent);color:#0f172a;border:none;
  padding:9px 14px;border-radius:8px;cursor:pointer;
  font-weight:700;font-size:13px;
  transition:background .15s,transform .1s;
  touch-action:manipulation}
button:hover{background:var(--accent2)}
button:active{transform:scale(.95)}
button.active{background:var(--green);color:#fff}
button.danger{background:var(--red);color:#fff}
@keyframes pulse{
  0%{box-shadow:0 0 0 0 rgba(56,189,248,.7)}
  70%{box-shadow:0 0 0 10px rgba(56,189,248,0)}
  100%{box-shadow:0 0 0 0 rgba(56,189,248,0)}
}
button.feedback{animation:pulse .4s ease}

input[type=range]{width:100%;height:6px;background:var(--border);
  border-radius:4px;outline:none;-webkit-appearance:none;margin:10px 0}
input[type=range]::-webkit-slider-thumb{-webkit-appearance:none;
  width:22px;height:22px;background:var(--accent);
  cursor:pointer;border-radius:50%;box-shadow:0 0 4px rgba(0,0,0,.4)}
input[type=range]::-moz-range-thumb{width:22px;height:22px;
  background:var(--accent);cursor:pointer;border-radius:50%;border:none}
.rlabels{display:flex;justify-content:space-between;
  font-size:11px;color:var(--muted);margin-top:2px}

.toggle-row{display:flex;align-items:center;justify-content:space-between;margin:8px 0}
.switch{position:relative;display:inline-block;width:44px;height:24px}
.switch input{opacity:0;width:0;height:0}
.sw{position:absolute;cursor:pointer;top:0;left:0;right:0;bottom:0;
  background:var(--border);border-radius:24px;transition:.3s}
.sw:before{content:"";position:absolute;height:18px;width:18px;
  left:3px;bottom:3px;background:#fff;border-radius:50%;transition:.3s}
input:checked+.sw{background:var(--green)}
input:checked+.sw:before{transform:translateX(20px)}

@media(min-width:520px){
  .grid2{display:grid;grid-template-columns:1fr 1fr;gap:14px}
  .grid2 .card{margin-bottom:0}
}

#toast{position:fixed;bottom:20px;left:50%;
  transform:translateX(-50%) translateY(60px);
  background:var(--green);color:#fff;padding:10px 22px;
  border-radius:24px;font-size:13px;font-weight:600;
  pointer-events:none;transition:transform .3s,opacity .3s;
  opacity:0;z-index:999}
#toast.show{transform:translateX(-50%) translateY(0);opacity:1}
</style>
</head>
<body>
<div class="container">
  <h1>📷 Camera2 RTSP Control</h1>
  <p class="sub">Samsung Galaxy Note10+ &middot; SM-N975F</p>

  <!-- Status bar -->
  <div class="statusbar">
    <div class="badge"><span class="dot" id="dot-stream"></span><span id="lbl-stream">...</span></div>
    <div class="badge">📷 <span id="lbl-cam">—</span></div>
    <div class="badge">🎞️ <span id="lbl-res">—</span></div>
    <div class="badge">📶 <span id="lbl-br">—</span> kbps</div>
    <div class="badge">⏱️ <span id="lbl-lat">—</span></div>
  </div>

  <!-- Info da câmera atual (estilo status.json) -->
  <div class="card">
    <h3>📊 Status da câmera</h3>
    <div class="info-row">
      <div class="info-pill">Foco: <span id="info-focusmode">—</span></div>
      <div class="info-pill">Dist: <span id="info-focusdist">—</span></div>
      <div class="info-pill">ISO: <span id="info-iso">—</span></div>
      <div class="info-pill">Exp: <span id="info-exp">—</span></div>
      <div class="info-pill">Focal: <span id="info-focal">—</span> mm</div>
      <div class="info-pill">Apertura: f/<span id="info-ap">—</span></div>
      <div class="info-pill">WB: <span id="info-wb">—</span></div>
      <div class="info-pill">OIS: <span id="info-ois">—</span></div>
    </div>
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
    </div>
    <div class="card">
      <h3>📶 Bitrate <span class="val" id="br-value">4000</span> kbps</h3>
      <input type="range" id="bitrate" min="500" max="25000" value="4000" step="500"
             oninput="updateBitrate(this.value)">
      <div class="rlabels"><span>500k</span><span>25M</span></div>
    </div>
  </div>

  <!-- Zoom -->
  <div class="card">
    <h3>🔍 Zoom <span class="val" id="zoom-val">100%</span></h3>
    <input type="range" id="zoom" min="0" max="1" value="0" step="0.01"
           oninput="updateZoom(this.value)">
    <div class="rlabels"><span>1× (sem zoom)</span><span>Máximo</span></div>
  </div>

  <!-- Foco -->
  <div class="card">
    <h3>🎯 Foco <span class="val" id="focus-val">Auto</span></h3>
    <!-- range 0.0 (auto/infinito) a 10.0 diopters (macro) — igual ao status.json IPWebcam -->
    <input type="range" id="focus" min="0" max="10" value="0" step="0.1"
           oninput="updateFocus(this.value)">
    <div class="rlabels"><span>Auto / ∞</span><span>Macro (10D)</span></div>
    <div class="btngroup" style="margin-top:10px">
      <button onclick="setFocusAuto()">♻️ Auto Focus</button>
      <button onclick="setFocusMode('continuous-video')">🎬 Contínuo</button>
      <button onclick="setFocusMode('off')">🔒 Travar</button>
    </div>
  </div>

  <!-- ISO + Exposição manual -->
  <div class="grid2">
    <div class="card">
      <h3>🌡️ ISO <span class="val" id="iso-val">50</span></h3>
      <!-- valores reais do IPWebcam: 50..3200 em passos de 31 -->
      <input type="range" id="iso" min="0" max="100" value="0" step="1"
             oninput="updateISO(this.value)">
      <div class="rlabels"><span>50 (mín ruído)</span><span>3200 (máx sens)</span></div>
      <div class="toggle-row" style="margin-top:8px">
        <span style="font-size:13px">Sensor Manual</span>
        <label class="switch">
          <input type="checkbox" id="toggle-manual" onchange="toggleManual(this)">
          <span class="sw"></span>
        </label>
      </div>
    </div>
    <div class="card">
      <h3>🕐 Exposure (EV) <span class="val" id="ev-val">0</span></h3>
      <!-- EV de -8 a +8 -->
      <input type="range" id="ev" min="-8" max="8" value="0" step="1"
             oninput="updateEV(this.value)">
      <div class="rlabels"><span>-8 (escuro)</span><span>+8 (claro)</span></div>
    </div>
  </div>

  <!-- Balanço de branco -->
  <div class="card">
    <h3>☀️ Balanço de Branco</h3>
    <div class="btngroup">
      <button data-wb="auto"       onclick="setWB('auto',this)">Auto</button>
      <button data-wb="daylight"   onclick="setWB('daylight',this)">☀️ Dia</button>
      <button data-wb="cloudy"     onclick="setWB('cloudy',this)">☁️ Nublado</button>
      <button data-wb="tungsten"   onclick="setWB('tungsten',this)">💡 Tungstênio</button>
      <button data-wb="fluorescent" onclick="setWB('fluorescent',this)">🔦 Fluorescente</button>
    </div>
  </div>

  <!-- Controles extras -->
  <div class="card">
    <h3>🛠️ Controles Extras</h3>
    <div class="toggle-row">
      <span class="toggle-label">🔦 Lanterna (Torch)</span>
      <label class="switch">
        <input type="checkbox" id="toggle-lantern" onchange="toggleLantern(this)">
        <span class="sw"></span>
      </label>
    </div>
    <div class="toggle-row">
      <span class="toggle-label">🎬 Estabilização OIS</span>
      <label class="switch">
        <input type="checkbox" id="toggle-ois" onchange="toggleOIS(this)">
        <span class="sw"></span>
      </label>
    </div>
  </div>

  <p style="text-align:center;margin-top:18px;color:var(--muted);font-size:11px">
    Camera2 API &middot; RootEncoder &middot; NanoHTTPD
  </p>
</div>
<div id="toast">✓ Enviado</div>

<script>
// ─── Listas ISO reais do IPWebcam ────────────────────────────────────────────
const ISO_LIST=[50,81,112,143,174,205,236,267,298,329,360,391,422,453,484,
  515,546,577,608,639,670,701,732,763,794,825,856,887,918,949,980,1011,1042,
  1073,1104,1135,1166,1197,1228,1259,1290,1321,1352,1383,1414,1445,1476,1507,
  1538,1569,1600,1631,1662,1693,1724,1755,1786,1817,1848,1879,1910,1941,1972,
  2003,2034,2065,2096,2127,2158,2189,2220,2251,2282,2313,2344,2375,2406,2437,
  2468,2499,2530,2561,2592,2623,2654,2685,2716,2747,2778,2809,2840,2871,2902,
  2933,2964,2995,3026,3057,3088,3119,3150,3200];

const CAM_NAMES={'0':'Wide','1':'Frontal','2':'Ultra Wide','3':'Telephoto'};

// ─── Toast ───────────────────────────────────────────────────────────────────
function showToast(msg){
  const t=document.getElementById('toast');
  t.textContent=msg||'✓ Enviado';
  t.classList.add('show');
  clearTimeout(t._t);
  t._t=setTimeout(()=>t.classList.remove('show'),1600);
}
function feedback(btn){
  if(!btn)return;
  btn.classList.remove('feedback');
  void btn.offsetWidth;
  btn.classList.add('feedback');
}
function markActive(attr,val){
  document.querySelectorAll('['+attr+']').forEach(b=>{
    b.classList.toggle('active',b.getAttribute(attr)===val);
  });
}

// ─── send ────────────────────────────────────────────────────────────────────
function sendControl(data,btn,msg){
  fetch('/api/control',{
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body:JSON.stringify(data)
  }).then(r=>r.json()).then(()=>{
    feedback(btn);
    showToast(msg||'✓ OK');
  }).catch(err=>showToast('❌ '+err.message));
}

// ─── Câmera ──────────────────────────────────────────────────────────────────
function switchCamera(id,btn){
  markActive('data-cam',id);
  sendControl({camera:id},btn,'📷 '+CAM_NAMES[id]);
}

// ─── Resolução ───────────────────────────────────────────────────────────────
function setResolution(res,btn){
  markActive('data-res',res);
  sendControl({resolution:res},btn,'📐 '+res);
}

// ─── Bitrate ─────────────────────────────────────────────────────────────────
let _brT;
function updateBitrate(v){
  document.getElementById('br-value').textContent=v;
  clearTimeout(_brT);
  _brT=setTimeout(()=>sendControl({bitrate:+v},null,'📶 '+v+'kbps'),300);
}

// ─── Zoom ────────────────────────────────────────────────────────────────────
let _zT;
function updateZoom(v){
  document.getElementById('zoom-val').textContent=Math.round(parseFloat(v)*100)+'%';
  clearTimeout(_zT);
  _zT=setTimeout(()=>sendControl({zoom:parseFloat(v)},null,'🔍 '+Math.round(v*100)+'%'),150);
}

// ─── Foco manual (0..10 diopters igual IPWebcam) ─────────────────────────────
// O backend Camera2Controller já espera 0.0 = auto, >0 = distância em diopters
let _fT;
function updateFocus(v){
  const f=parseFloat(v);
  document.getElementById('focus-val').textContent=f===0?'Auto':f.toFixed(1)+'D';
  clearTimeout(_fT);
  // Normaliza 0..10 para 0..1 para o backend
  _fT=setTimeout(()=>sendControl({focus:f/10},null,'🎯 '+(f===0?'Auto':f.toFixed(1)+'D')),200);
}
function setFocusAuto(){
  document.getElementById('focus').value=0;
  updateFocus(0);
}
function setFocusMode(mode){
  sendControl({focusmode:mode},null,'🎯 '+mode);
}

// ─── ISO (índice 0..100 → valor real da lista) ───────────────────────────────
let _iT;
function updateISO(v){
  const iso=ISO_LIST[Math.min(+v,ISO_LIST.length-1)];
  document.getElementById('iso-val').textContent=iso;
  clearTimeout(_iT);
  _iT=setTimeout(()=>sendControl({iso:iso},null,'🌡️ ISO '+iso),300);
}

// ─── EV (exposição) ──────────────────────────────────────────────────────────
let _eT;
function updateEV(v){
  document.getElementById('ev-val').textContent=(+v>0?'+':'')+v;
  clearTimeout(_eT);
  _eT=setTimeout(()=>sendControl({exposure:+v},null,'🕐 EV '+(+v>0?'+':'')+v),300);
}

// ─── WB ──────────────────────────────────────────────────────────────────────
function setWB(mode,btn){
  markActive('data-wb',mode);
  sendControl({whiteBalance:mode},btn,'☀️ '+mode);
}

// ─── Manual sensor toggle ────────────────────────────────────────────────────
function toggleManual(chk){
  sendControl({manualSensor:chk.checked},null,chk.checked?'Manual ON':'Manual OFF');
}

// ─── Lanterna ────────────────────────────────────────────────────────────────
function toggleLantern(chk){
  sendControl({lantern:chk.checked},null,chk.checked?'🔦 ON':'🔦 OFF');
}

// ─── OIS ─────────────────────────────────────────────────────────────────────
function toggleOIS(chk){
  sendControl({ois:chk.checked},null,chk.checked?'🎬 OIS ON':'🎬 OFF');
}

// ─── Polling de status ───────────────────────────────────────────────────────
async function pollStatus(){
  const t0=Date.now();
  try{
    const d=await(await fetch('/api/status')).json();
    const lat=Date.now()-t0;
    const c=d.curvals||{};

    document.getElementById('dot-stream').className='dot'+(d.streaming?'':' off');
    document.getElementById('lbl-stream').textContent=d.streaming?'Streaming ▶':'Parado ⏹';
    document.getElementById('lbl-cam').textContent=CAM_NAMES[c.camera_id]||c.camera_id||'—';
    document.getElementById('lbl-res').textContent=c.video_size||'—';
    document.getElementById('lbl-br').textContent=c.bitrate_kbps||'—';
    document.getElementById('lbl-lat').textContent=lat+'ms';

    // Painel de info
    document.getElementById('info-focusmode').textContent=c.focusmode||'—';
    document.getElementById('info-focusdist').textContent=(c.focus_distance||'0.00')+'D';
    document.getElementById('info-iso').textContent=c.iso||'—';
    document.getElementById('info-exp').textContent=c.exposure_ns?nsToShutter(+c.exposure_ns):'—';
    document.getElementById('info-focal').textContent=c.focal_length||'—';
    document.getElementById('info-ap').textContent=c.aperture||'—';
    document.getElementById('info-wb').textContent=c.whitebalance||'—';
    document.getElementById('info-ois').textContent=c.ois||'—';

    // Sync toggles
    const lanternEl=document.getElementById('toggle-lantern');
    if(lanternEl) lanternEl.checked=(c.torch==='on');
    const oisEl=document.getElementById('toggle-ois');
    if(oisEl) oisEl.checked=(c.ois==='on');
    const manEl=document.getElementById('toggle-manual');
    if(manEl) manEl.checked=(c.manual_sensor==='on');

    // Marca câmera ativa
    if(c.camera_id) markActive('data-cam',c.camera_id);
    if(c.whitebalance) markActive('data-wb',c.whitebalance);

  }catch(e){
    document.getElementById('dot-stream').className='dot off';
    document.getElementById('lbl-stream').textContent='Sem conexão';
  }
}

function nsToShutter(ns){
  if(ns<=0) return '—';
  const s=ns/1e9;
  if(s>=1) return s.toFixed(1)+'s';
  return '1/'+Math.round(1/s)+'s';
}

pollStatus();
setInterval(pollStatus,2000);
</script>
</body>
</html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
