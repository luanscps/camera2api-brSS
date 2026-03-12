package com.camera2rtsp

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.pedro.rtspserver.RtspServerCamera2
import fi.iki.elonen.NanoHTTPD

class WebControlServer(
    port: Int,
    private val cameraController: Camera2Controller,
    private val context: Context
) : NanoHTTPD(port) {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/"                  -> serveControlPanel()
            uri == "/status"            -> serveStatus()
            uri == "/api/status"        -> serveStatus()
            uri == "/api/capabilities"  -> serveCapabilities()
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

    private fun edgeModeStr(v: Int) = when (v) {
        android.hardware.camera2.CameraMetadata.EDGE_MODE_OFF          -> "off"
        android.hardware.camera2.CameraMetadata.EDGE_MODE_FAST         -> "fast"
        android.hardware.camera2.CameraMetadata.EDGE_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }
    private fun nrModeStr(v: Int) = when (v) {
        android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_OFF          -> "off"
        android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL      -> "minimal"
        android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_FAST         -> "fast"
        android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }
    private fun hotPixelModeStr(v: Int) = when (v) {
        android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_OFF          -> "off"
        android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_FAST         -> "fast"
        android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }

    private fun serveStatus(): Response {
        val c = cameraController
        val srv: RtspServerCamera2? = c.server
        val focusMode = if (c.autoFocus) "continuous-video" else "off"
        val focusDist = String.format("%.2f", c.focusDistance)
        val numClients = try { if (srv?.isStreaming == true) 1 else 0 } catch (_: Exception) { 0 }

        val curvals = mapOf(
            "video_size"           to "${c.currentWidth}x${c.currentHeight}",
            "ffc"                  to if (c.currentCameraId == "1") "on" else "off",
            "camera_id"            to c.currentCameraId,
            "zoom"                 to "${(c.zoomLevel * 100).toInt() + 100}",
            "focusmode"            to focusMode,
            "focus_distance"       to focusDist,
            "focal_length"         to "4.30",
            "aperture"             to "1.5 (fixo)",
            "whitebalance"         to c.whiteBalanceMode,
            "torch"                to if (c.lanternEnabled) "on" else "off",
            "iso"                  to c.isoValue.toString(),
            "exposure_ns"          to c.exposureNs.toString(),
            "frame_duration"       to c.frameDurationNs.toString(),
            "manual_sensor"        to if (c.manualSensor) "on" else "off",
            "bitrate_kbps"         to c.currentBitrate.toString(),
            "fps"                  to c.currentFps.toString(),
            "ois"                  to if (c.oisEnabled) "on" else "off",
            "eis"                  to if (c.eisEnabled) "on" else "off",
            "ae_lock"              to if (c.aeLocked) "on" else "off",
            "awb_lock"             to if (c.awbLocked) "on" else "off",
            "flash_mode"           to c.flashMode,
            "edge_mode"            to edgeModeStr(c.edgeMode),
            "noise_reduction_mode" to nrModeStr(c.noiseReductionMode),
            "tonemap_mode"         to "high_quality",
            "hot_pixel_mode"       to hotPixelModeStr(c.hotPixelMode)
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
            "camera_id"      to listOf("0", "1", "2", "3"),
            "flash_mode"     to listOf("off", "torch", "single"),
            "edge_mode"      to listOf("off", "fast", "high_quality"),
            "noise_reduction_mode" to listOf("off", "minimal", "fast", "high_quality"),
            "tonemap_mode"   to listOf("high_quality"),
            "hot_pixel_mode" to listOf("off", "fast", "high_quality")
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
            val params = com.google.gson.Gson().fromJson<Map<String, Any>>(
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
        val html = buildHtml()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun buildHtml(): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>")
        sb.append("<html lang=\"pt-BR\">")
        sb.append("<head>")
        sb.append("<meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">")
        sb.append("<title>Camera2 RTSP Control</title>")
        sb.append("<style>")
        sb.append(":root{--bg:#0f172a;--surface:#1e293b;--surface2:#263348;--border:#334155;")
        sb.append("--accent:#38bdf8;--text:#f1f5f9;--muted:#94a3b8;")
        sb.append("--green:#10b981;--red:#ef4444;--yellow:#f59e0b;--purple:#a855f7;}")
        sb.append("*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}")
        sb.append("html{font-size:14px}")
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
        sb.append("background:var(--bg);color:var(--text);padding:10px;min-height:100vh;overscroll-behavior:none}")
        sb.append(".container{max-width:900px;margin:0 auto}")
        sb.append(".header{text-align:center;margin-bottom:14px}")
        sb.append(".header h1{color:var(--accent);font-size:clamp(16px,5vw,24px);font-weight:800}")
        sb.append(".header p{color:var(--muted);font-size:11px;margin-top:3px}")
        sb.append(".statusbar{display:flex;flex-wrap:wrap;gap:6px;justify-content:center;")
        sb.append("padding:10px;background:var(--surface);border-radius:14px;")
        sb.append("border:1px solid var(--border);margin-bottom:14px}")
        sb.append(".badge{display:flex;align-items:center;gap:5px;background:var(--bg);")
        sb.append("padding:5px 11px;border-radius:20px;font-size:11px;font-weight:600;white-space:nowrap}")
        sb.append(".dot{width:8px;height:8px;border-radius:50%;background:var(--green);")
        sb.append("box-shadow:0 0 6px var(--green);transition:background .3s}")
        sb.append(".dot.off{background:var(--red);box-shadow:0 0 6px var(--red)}")
        sb.append(".lat-ok{color:var(--green)}.lat-warn{color:var(--yellow)}.lat-bad{color:var(--red)}")
        sb.append(".badge-manual{background:var(--yellow)!important;color:#0f172a!important;font-weight:800}")
        sb.append(".card{background:var(--surface);padding:14px;margin-bottom:12px;")
        sb.append("border-radius:14px;border:1px solid var(--border)}")
        sb.append(".card.hidden{display:none!important}")
        sb.append(".card.manual-active{border-color:var(--yellow)!important;")
        sb.append("box-shadow:0 0 0 2px rgba(245,158,11,.25)}")
        sb.append(".card.postproc-active{border-color:var(--purple)!important;")
        sb.append("box-shadow:0 0 0 2px rgba(168,85,247,.18)}")
        sb.append(".card h3{color:var(--accent);font-size:13px;font-weight:700;margin-bottom:11px;")
        sb.append("display:flex;align-items:center;gap:6px}")
        sb.append(".val{display:inline-block;background:var(--bg);padding:2px 10px;")
        sb.append("border-radius:6px;font-weight:700;color:var(--accent);min-width:72px;text-align:center;font-size:12px}")
        sb.append(".val.manual{color:var(--yellow)}")
        sb.append(".info-row{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:6px}")
        sb.append(".info-pill{background:var(--bg);padding:4px 10px;border-radius:8px;font-size:11px;color:var(--muted)}")
        sb.append(".info-pill span{color:var(--text);font-weight:600}")
        sb.append(".btngroup{display:flex;flex-wrap:wrap;gap:7px}")
        sb.append("button{background:var(--surface2);color:var(--text);border:1px solid var(--border);")
        sb.append("padding:9px 13px;border-radius:10px;cursor:pointer;font-weight:600;font-size:12px;")
        sb.append("line-height:1.2;transition:background .15s,transform .1s;")
        sb.append("touch-action:manipulation;user-select:none;min-height:40px}")
        sb.append("button:hover{background:var(--accent);color:#0f172a;border-color:var(--accent)}")
        sb.append("button:active{transform:scale(.93)}")
        sb.append("button.active{background:var(--green);color:#fff;border-color:var(--green)}")
        sb.append("button.preset-quality{background:rgba(168,85,247,.18);border-color:var(--purple);color:var(--purple)}")
        sb.append("button.preset-quality:hover{background:var(--purple);color:#fff;border-color:var(--purple)}")
        sb.append("button.preset-fast{background:rgba(245,158,11,.12);border-color:var(--yellow);color:var(--yellow)}")
        sb.append("button.preset-fast:hover{background:var(--yellow);color:#0f172a;border-color:var(--yellow)}")
        sb.append("@keyframes pulse-ok{0%{box-shadow:0 0 0 0 rgba(16,185,129,.8)}")
        sb.append("70%{box-shadow:0 0 0 12px rgba(16,185,129,0)}100%{box-shadow:0 0 0 0 rgba(16,185,129,0)}}")
        sb.append("@keyframes pulse-err{0%{box-shadow:0 0 0 0 rgba(239,68,68,.8)}")
        sb.append("70%{box-shadow:0 0 0 12px rgba(239,68,68,0)}100%{box-shadow:0 0 0 0 rgba(239,68,68,0)}}")
        sb.append("button.fb-ok{animation:pulse-ok .45s ease}")
        sb.append("button.fb-err{animation:pulse-err .45s ease}")
        sb.append(".slider-wrap{padding:4px 0}")
        sb.append("input[type=range]{width:100%;height:5px;background:var(--border);")
        sb.append("border-radius:4px;outline:none;-webkit-appearance:none;margin:10px 0;cursor:pointer}")
        sb.append("input[type=range]::-webkit-slider-thumb{-webkit-appearance:none;")
        sb.append("width:24px;height:24px;background:var(--accent);cursor:grab;border-radius:50%;")
        sb.append("border:3px solid var(--bg);box-shadow:0 2px 6px rgba(0,0,0,.5)}")
        sb.append("input[type=range].manual-slider::-webkit-slider-thumb{background:var(--yellow)}")
        sb.append("input[type=range]:disabled{opacity:.3;cursor:not-allowed}")
        sb.append(".rlabels{display:flex;justify-content:space-between;font-size:10px;color:var(--muted);margin-top:2px}")
        sb.append(".toggle-row{display:flex;align-items:center;justify-content:space-between;")
        sb.append("padding:9px 0;border-bottom:1px solid var(--border)}")
        sb.append(".toggle-row:last-child{border-bottom:none}")
        sb.append(".toggle-label{font-size:13px;display:flex;align-items:center;gap:7px}")
        sb.append(".switch{position:relative;display:inline-block;width:46px;height:26px;flex-shrink:0}")
        sb.append(".switch input{opacity:0;width:0;height:0}")
        sb.append(".sw{position:absolute;cursor:pointer;inset:0;background:var(--border);border-radius:26px;transition:.3s}")
        sb.append(".sw:before{content:'';position:absolute;height:20px;width:20px;left:3px;bottom:3px;")
        sb.append("background:#fff;border-radius:50%;transition:.3s;box-shadow:0 1px 3px rgba(0,0,0,.4)}")
        sb.append("input:checked+.sw{background:var(--green)}")
        sb.append("input:checked+.sw:before{transform:translateX(20px)}")
        sb.append("input:disabled+.sw{opacity:.3;cursor:not-allowed}")
        sb.append(".ois-no-support{font-size:10px;color:var(--muted);font-style:italic;margin-left:8px}")
        sb.append(".ev-disabled-hint{font-size:10px;color:var(--muted);margin-top:4px;font-style:italic;display:none}")
        sb.append(".ev-disabled-hint.show{display:block}")
        sb.append(".postproc-section{margin-bottom:12px}")
        sb.append(".postproc-section h4{color:var(--muted);font-size:11px;font-weight:700;")
        sb.append("text-transform:uppercase;letter-spacing:.06em;margin-bottom:7px}")
        sb.append(".postproc-presets{display:flex;gap:8px;margin-bottom:14px}")
        sb.append(".postproc-presets button{flex:1;font-size:11px;padding:8px 6px}")
        sb.append("@media(min-width:480px){.grid2{display:grid;grid-template-columns:1fr 1fr;gap:12px}}")
        sb.append("#toast{position:fixed;bottom:22px;left:50%;transform:translateX(-50%) translateY(80px);")
        sb.append("padding:10px 22px;border-radius:24px;font-size:13px;font-weight:700;")
        sb.append("pointer-events:none;transition:transform .25s,opacity .25s;opacity:0;z-index:999;white-space:nowrap}")
        sb.append("#toast.show{transform:translateX(-50%) translateY(0);opacity:1}")
        sb.append("#toast.ok{background:var(--green);color:#fff}")
        sb.append("#toast.err{background:var(--red);color:#fff}")
        sb.append(".pq-btn{flex:1;padding:8px 4px;font-size:11px;text-align:center}")
        sb.append(".fps-row{display:flex;gap:6px;margin-top:8px}")
        sb.append(".fps-row button{flex:1;font-size:11px;padding:7px 4px}")
        sb.append(".shutter-presets{display:flex;flex-wrap:wrap;gap:5px;margin-top:8px}")
        sb.append(".shutter-presets button{flex:1;min-width:52px;font-size:10px;padding:6px 4px}")
        sb.append("</style></head><body>")
        sb.append("<div class=\"container\">")
        sb.append("<div class=\"header\"><h1>\uD83C\uDFA5 Camera2 RTSP Control</h1>")
        sb.append("<p>Samsung Galaxy Note10+ · SM-N975F · Android 12</p></div>")

        // ── Status bar ───────────────────────────────────────────────────────
        sb.append("<div class=\"statusbar\">")
        sb.append("<div class=\"badge\"><span class=\"dot off\" id=\"dot-stream\"></span><span id=\"lbl-stream\">Conectando...</span></div>")
        sb.append("<div class=\"badge\">Cam: <span id=\"lbl-cam\">-</span></div>")
        sb.append("<div class=\"badge\">Res: <span id=\"lbl-res\">-</span></div>")
        sb.append("<div class=\"badge\">BR: <span id=\"lbl-br\">-</span> kbps</div>")
        sb.append("<div class=\"badge\">Clientes: <span id=\"lbl-clients\">0</span></div>")
        sb.append("<div class=\"badge\">Lat: <span id=\"lbl-lat\" class=\"lat-ok\">-</span></div>")
        sb.append("<div class=\"badge\" id=\"badge-manual\" style=\"display:none\">\uD83C\uDFAC Manual</div>")
        sb.append("</div>")

        // ── Estado da Camera ─────────────────────────────────────────────────
        sb.append("<div class=\"card\"><h3>Estado da Camera</h3><div class=\"info-row\">")
        sb.append("<div class=\"info-pill\">Foco: <span id=\"info-focusmode\">-</span></div>")
        sb.append("<div class=\"info-pill\">Dist: <span id=\"info-focusdist\">-</span></div>")
        sb.append("<div class=\"info-pill\">ISO: <span id=\"info-iso\">-</span></div>")
        sb.append("<div class=\"info-pill\">Shutter: <span id=\"info-exp\">-</span></div>")
        sb.append("<div class=\"info-pill\">Frame: <span id=\"info-frame\">-</span></div>")
        sb.append("<div class=\"info-pill\">Focal: <span id=\"info-focal\">-</span> mm</div>")
        sb.append("<div class=\"info-pill\">f/<span id=\"info-ap\">-</span></div>")
        sb.append("<div class=\"info-pill\">WB: <span id=\"info-wb\">-</span></div>")
        sb.append("<div class=\"info-pill\">OIS: <span id=\"info-ois\">-</span></div>")
        sb.append("<div class=\"info-pill\">EIS: <span id=\"info-eis\">-</span></div>")
        sb.append("<div class=\"info-pill\">FPS: <span id=\"info-fps\">-</span></div>")
        sb.append("<div class=\"info-pill\">Edge: <span id=\"info-edge\">-</span></div>")
        sb.append("<div class=\"info-pill\">NR: <span id=\"info-nr\">-</span></div>")
        sb.append("<div class=\"info-pill\">HotPx: <span id=\"info-hotpx\">-</span></div>")
        sb.append("</div></div>")

        // ── Camera selector ──────────────────────────────────────────────────
        sb.append("<div class=\"card\" id=\"card-camera\"><h3>\uD83D\uDCF7 Camera</h3>")
        sb.append("<div class=\"btngroup\" id=\"btngroup-camera\">Carregando cameras...</div></div>")

        // ── Resolução + Bitrate ──────────────────────────────────────────────
        sb.append("<div class=\"grid2\">")
        sb.append("<div class=\"card\"><h3>Resolucao</h3>")
        sb.append("<div class=\"btngroup\" id=\"btngroup-resolution\" style=\"margin-bottom:10px\"></div>")
        sb.append("<div class=\"fps-row\" id=\"btngroup-fps\"></div></div>")
        sb.append("<div class=\"card\"><h3>Bitrate <span class=\"val\" id=\"br-value\">4000</span> kbps</h3>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"bitrate\" min=\"500\" max=\"25000\" value=\"4000\" step=\"500\" oninput=\"updateBitrate(this.value)\">")
        sb.append("<div class=\"rlabels\"><span>500k</span><span>12M</span><span>25M</span></div></div>")
        sb.append("<div class=\"btngroup\" style=\"margin-top:8px\">")
        sb.append("<button onclick=\"setBitratePreset(2000)\">2M</button>")
        sb.append("<button onclick=\"setBitratePreset(4000)\">4M</button>")
        sb.append("<button onclick=\"setBitratePreset(8000)\">8M</button>")
        sb.append("<button onclick=\"setBitratePreset(20000)\">20M</button>")
        sb.append("</div></div></div>")

        // ── Zoom ─────────────────────────────────────────────────────────────
        sb.append("<div class=\"card\" id=\"card-zoom\"><h3>\uD83D\uDD0D Zoom <span class=\"val\" id=\"zoom-val\">1x</span></h3>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"zoom\" min=\"0\" max=\"1\" value=\"0\" step=\"0.01\" oninput=\"updateZoom(this.value)\">")
        sb.append("<div class=\"rlabels\"><span>1x</span><span>Max</span></div></div>")
        sb.append("<div class=\"btngroup\" style=\"margin-top:8px\">")
        sb.append("<button onclick=\"setZoomPreset(0)\">1x</button>")
        sb.append("<button onclick=\"setZoomPreset(0.25)\">2x</button>")
        sb.append("<button onclick=\"setZoomPreset(0.5)\">4x</button>")
        sb.append("<button onclick=\"setZoomPreset(1.0)\">Max</button>")
        sb.append("</div></div>")

        // ── Foco ─────────────────────────────────────────────────────────────
        sb.append("<div class=\"card\" id=\"card-focus\"><h3>\uD83C\uDFAF Foco <span class=\"val\" id=\"focus-val\">Auto</span></h3>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"focus\" min=\"0\" max=\"10\" value=\"0\" step=\"0.1\" oninput=\"updateFocus(this.value)\">")
        sb.append("<div class=\"rlabels\"><span>Auto/inf</span><span>Macro(10D)</span></div></div>")
        sb.append("<div class=\"btngroup\" style=\"margin-top:8px\" id=\"btngroup-focusmode\"></div>")
        sb.append("<button style=\"width:100%;margin-top:8px\" onclick=\"triggerAF(this)\">\uD83C\uDFAF Tocar para Focar</button>")
        sb.append("</div>")

        // ── ISO + Sensor Manual ──────────────────────────────────────────────
        sb.append("<div class=\"grid2\">")
        sb.append("<div class=\"card\" id=\"card-iso\"><h3>ISO <span class=\"val\" id=\"iso-val\">50</span></h3>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"iso\" min=\"0\" max=\"100\" value=\"0\" step=\"1\" oninput=\"updateISO(this.value)\">")
        sb.append("<div class=\"rlabels\"><span>50</span><span>1600</span><span>3200</span></div></div>")
        sb.append("<div class=\"toggle-row\" style=\"margin-top:6px\">")
        sb.append("<span class=\"toggle-label\">\uD83C\uDFAC Sensor Manual</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-manual\" onchange=\"toggleManual(this)\">")
        sb.append("<span class=\"sw\"></span></label></div></div>")

        // ── EV ───────────────────────────────────────────────────────────────
        sb.append("<div class=\"card\" id=\"card-ev\"><h3>EV <span class=\"val\" id=\"ev-val\">0</span></h3>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"ev\" min=\"-8\" max=\"8\" value=\"0\" step=\"1\" oninput=\"updateEV(this.value)\">")
        sb.append("<div class=\"rlabels\" id=\"ev-labels\"><span>-8</span><span>0</span><span>+8</span></div></div>")
        sb.append("<p class=\"ev-disabled-hint\" id=\"ev-hint\">EV inativo — sensor manual ativo</p>")
        sb.append("<div class=\"btngroup\" style=\"margin-top:8px\" id=\"ev-presets\">")
        sb.append("<button onclick=\"setEVPreset(-4)\">-4</button>")
        sb.append("<button onclick=\"setEVPreset(0)\">0</button>")
        sb.append("<button onclick=\"setEVPreset(4)\">+4</button>")
        sb.append("</div>")
        sb.append("<div class=\"toggle-row\" style=\"margin-top:8px\">")
        sb.append("<span class=\"toggle-label\">\uD83D\uDD12 Travar AE</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-ae-lock\" onchange=\"toggleAELock(this)\">")
        sb.append("<span class=\"sw\"></span></label></div></div></div>")

        // ── Shutter Speed ────────────────────────────────────────────────────
        sb.append("<div class=\"card hidden\" id=\"card-shutter\"><h3>\u23F1 Shutter <span class=\"val manual\" id=\"shutter-val\">1/50s</span></h3>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"shutter\" class=\"manual-slider\" min=\"0\" max=\"10\" value=\"2\" step=\"1\" oninput=\"updateShutter(this.value)\">")
        sb.append("<div class=\"rlabels\"><span>1/24s</span><span>1/10000s</span></div></div>")
        sb.append("<div class=\"shutter-presets\">")
        sb.append("<button onclick=\"setShutterPreset('1/24')\">1/24</button>")
        sb.append("<button onclick=\"setShutterPreset('1/30')\">1/30</button>")
        sb.append("<button onclick=\"setShutterPreset('1/50')\">1/50</button>")
        sb.append("<button onclick=\"setShutterPreset('1/60')\">1/60</button>")
        sb.append("<button onclick=\"setShutterPreset('1/100')\">1/100</button>")
        sb.append("<button onclick=\"setShutterPreset('1/250')\">1/250</button>")
        sb.append("<button onclick=\"setShutterPreset('1/500')\">1/500</button>")
        sb.append("<button onclick=\"setShutterPreset('1/1000')\">1/1000</button>")
        sb.append("</div></div>")

        // ── Frame Time ───────────────────────────────────────────────────────
        sb.append("<div class=\"card hidden\" id=\"card-frame\"><h3>\uD83C\uDFAC Frame Time <span class=\"val manual\" id=\"frame-val\">1/30s</span></h3>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"frame\" class=\"manual-slider\" min=\"0\" max=\"3\" value=\"1\" step=\"1\" oninput=\"updateFrameTime(this.value)\">")
        sb.append("<div class=\"rlabels\"><span>1/15</span><span>1/24</span><span>1/30</span><span>1/60</span></div></div>")
        sb.append("<div class=\"btngroup\" style=\"margin-top:8px\">")
        sb.append("<button onclick=\"setFramePreset(0)\">1/15 ~60fps</button>")
        sb.append("<button onclick=\"setFramePreset(1)\">1/24 ~41fps</button>")
        sb.append("<button onclick=\"setFramePreset(2)\">1/30 ~30fps</button>")
        sb.append("<button onclick=\"setFramePreset(3)\">1/60 ~16fps</button>")
        sb.append("</div>")
        sb.append("<p style=\"font-size:10px;color:var(--muted);margin-top:8px\">Frame Duration define o FPS máximo do sensor.</p>")
        sb.append("</div>")

        // ── White Balance ─────────────────────────────────────────────────────
        sb.append("<div class=\"card\" id=\"card-wb\"><h3>Balanco de Branco</h3>")
        sb.append("<div class=\"btngroup\" id=\"btngroup-wb\"></div>")
        sb.append("<div class=\"toggle-row\" style=\"margin-top:8px\">")
        sb.append("<span class=\"toggle-label\">\uD83D\uDD12 Travar AWB</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-awb-lock\" onchange=\"toggleAWBLock(this)\">")
        sb.append("<span class=\"sw\"></span></label></div></div>")

        // ── Controles Extras ─────────────────────────────────────────────────
        sb.append("<div class=\"card\" id=\"card-extras\"><h3>Controles Extras</h3>")
        sb.append("<div id=\"extras-flash\"></div>")
        sb.append("<div class=\"toggle-row\" id=\"row-ois\">")
        sb.append("<span class=\"toggle-label\">OIS (\u00d3tica)<span class=\"ois-no-support\" id=\"ois-hint\"></span></span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-ois\" onchange=\"toggleOIS(this)\">")
        sb.append("<span class=\"sw\"></span></label></div>")
        sb.append("<div class=\"toggle-row\" id=\"row-eis\">")
        sb.append("<span class=\"toggle-label\">EIS (Digital)</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-eis\" onchange=\"toggleEIS(this)\">")
        sb.append("<span class=\"sw\"></span></label></div>")
        sb.append("</div>")

        // ── Onda 3: Processamento de Imagem ───────────────────────────────────
        sb.append("<div class=\"card hidden postproc-active\" id=\"card-postproc\">")
        sb.append("<h3>\uD83C\uDF9E Processamento de Imagem</h3>")
        sb.append("<div class=\"postproc-presets\">")
        sb.append("<button class=\"preset-quality\" onclick=\"applyQualityMax(this)\">\u2B50 Qualidade M\u00e1xima</button>")
        sb.append("<button class=\"preset-fast\" onclick=\"applyLatencyMin(this)\">\u26A1 Lat\u00eancia M\u00ednima</button>")
        sb.append("</div>")
        // EDGE
        sb.append("<div class=\"postproc-section\"><h4>Nitidez (Edge)</h4><div class=\"btngroup\">")
        sb.append("<button data-edge=\"off\" onclick=\"setEdge('off',this)\">Desligado</button>")
        sb.append("<button data-edge=\"fast\" onclick=\"setEdge('fast',this)\">R\u00e1pido</button>")
        sb.append("<button data-edge=\"high_quality\" onclick=\"setEdge('high_quality',this)\" class=\"active\">Qualidade</button>")
        sb.append("</div></div>")
        // NR
        sb.append("<div class=\"postproc-section\"><h4>Redu\u00e7\u00e3o de Ru\u00eddo (NR)</h4><div class=\"btngroup\">")
        sb.append("<button data-nr=\"off\" onclick=\"setNR('off',this)\">Desligado</button>")
        sb.append("<button data-nr=\"minimal\" onclick=\"setNR('minimal',this)\">M\u00ednimo</button>")
        sb.append("<button data-nr=\"fast\" onclick=\"setNR('fast',this)\">R\u00e1pido</button>")
        sb.append("<button data-nr=\"high_quality\" onclick=\"setNR('high_quality',this)\" class=\"active\">Qualidade</button>")
        sb.append("</div></div>")
        // HOT PIXEL
        sb.append("<div class=\"postproc-section\" style=\"margin-bottom:0\"><h4>Pixel Quente</h4><div class=\"btngroup\">")
        sb.append("<button data-hotpx=\"off\" onclick=\"setHotPx('off',this)\">Desligado</button>")
        sb.append("<button data-hotpx=\"fast\" onclick=\"setHotPx('fast',this)\">R\u00e1pido</button>")
        sb.append("<button data-hotpx=\"high_quality\" onclick=\"setHotPx('high_quality',this)\" class=\"active\">Qualidade</button>")
        sb.append("</div></div>")
        sb.append("</div>") // fim card-postproc

        sb.append("<p style=\"text-align:center;margin-top:16px;color:var(--muted);font-size:10px;padding-bottom:20px\">")
        sb.append("Camera2 API \u00b7 RootEncoder \u00b7 NanoHTTPD \u00b7 v2.7</p>")
        sb.append("</div>")
        sb.append("<div id=\"toast\" class=\"ok\">OK</div>")

        // ── JavaScript ────────────────────────────────────────────────────────
        sb.append("<script>")

        sb.append("var ISO_LIST=[50,81,112,143,174,205,236,267,298,329,360,391,422,453,484,")
        sb.append("515,546,577,608,639,670,701,732,763,794,825,856,887,918,949,980,1011,1042,")
        sb.append("1073,1104,1135,1166,1197,1228,1259,1290,1321,1352,1383,1414,1445,1476,1507,")
        sb.append("1538,1569,1600,1631,1662,1693,1724,1755,1786,1817,1848,1879,1910,1941,1972,")
        sb.append("2003,2034,2065,2096,2127,2158,2189,2220,2251,2282,2313,2344,2375,2406,2437,")
        sb.append("2468,2499,2530,2561,2592,2623,2654,2685,2716,2747,2778,2809,2840,2871,2902,")
        sb.append("2933,2964,2995,3026,3057,3088,3119,3150,3200];")
        sb.append("var SHUTTER_STOPS=['1/24','1/30','1/50','1/60','1/100','1/250','1/500','1/1000','1/2000','1/4000','1/10000'];")
        sb.append("var FRAME_STOPS=['1/15','1/24','1/30','1/60'];")
        sb.append("var _caps=null;var _currentCamId='0';var _isManual=false;")
        sb.append("var _toastTimer;var _pollFail=0;")
        sb.append("var _brT,_zT,_fT,_iT,_eT,_shT,_frT;")

        sb.append("function showToast(msg,isErr){")
        sb.append("var t=document.getElementById('toast');")
        sb.append("t.textContent=msg;t.className=isErr?'err':'ok';t.classList.add('show');")
        sb.append("clearTimeout(_toastTimer);_toastTimer=setTimeout(function(){t.classList.remove('show');},1800);}")
        sb.append("function feedback(btn,ok){if(!btn)return;")
        sb.append("var cls=ok?'fb-ok':'fb-err';btn.classList.remove('fb-ok','fb-err');void btn.offsetWidth;")
        sb.append("btn.classList.add(cls);setTimeout(function(){btn.classList.remove(cls);},500);}")
        sb.append("function markActive(attr,val){")
        sb.append("var els=document.querySelectorAll('['+attr+']');")
        sb.append("for(var i=0;i<els.length;i++){els[i].classList.toggle('active',els[i].getAttribute(attr)===String(val));}")
        sb.append("}")
        sb.append("function sendControl(data,btn,msg){")
        sb.append("fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)})")
        sb.append(".then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.json();})")
        sb.append(".then(function(){feedback(btn,true);showToast(msg||'OK',false);})")
        sb.append(".catch(function(e){feedback(btn,false);showToast('ERR:'+e.message,true);});}")
        sb.append("function getCap(camId){if(!_caps)return null;")
        sb.append("for(var i=0;i<_caps.length;i++){if(_caps[i].camera_id===String(camId))return _caps[i];}return null;}")
        sb.append("function showCard(id,show){var el=document.getElementById(id);if(!el)return;")
        sb.append("if(show){el.classList.remove('hidden');}else{el.classList.add('hidden');}}")

        sb.append("function updateManualUI(isManual){")
        sb.append("_isManual=isManual;")
        sb.append("var badge=document.getElementById('badge-manual');")
        sb.append("if(badge)badge.style.display=isManual?'flex':'none';")
        sb.append("['card-iso','card-shutter','card-frame'].forEach(function(id){")
        sb.append("var el=document.getElementById(id);if(!el)return;")
        sb.append("if(isManual)el.classList.add('manual-active');else el.classList.remove('manual-active');});")
        sb.append("var evSlider=document.getElementById('ev');")
        sb.append("var evHint=document.getElementById('ev-hint');")
        sb.append("var evPresets=document.getElementById('ev-presets');")
        sb.append("if(evSlider)evSlider.disabled=isManual;")
        sb.append("if(evHint){isManual?evHint.classList.add('show'):evHint.classList.remove('show');}")
        sb.append("if(evPresets){var btns=evPresets.querySelectorAll('button');")
        sb.append("for(var i=0;i<btns.length;i++)btns[i].disabled=isManual;}")
        sb.append("}")

        sb.append("function updateOISCapability(hasOIS){")
        sb.append("var chk=document.getElementById('toggle-ois');")
        sb.append("var hint=document.getElementById('ois-hint');")
        sb.append("if(chk)chk.disabled=!hasOIS;")
        sb.append("if(hint)hint.textContent=hasOIS?'':'(sem suporte nesta câmera)';}")

        sb.append("function setEdge(val,btn){markActive('data-edge',val);sendControl({edgeMode:val},btn,'Edge '+val);}")
        sb.append("function setNR(val,btn){markActive('data-nr',val);sendControl({noiseReduction:val},btn,'NR '+val);}")
        sb.append("function setHotPx(val,btn){markActive('data-hotpx',val);sendControl({hotPixel:val},btn,'HotPx '+val);}")
        sb.append("function applyQualityMax(btn){")
        sb.append("markActive('data-edge','high_quality');markActive('data-nr','high_quality');markActive('data-hotpx','high_quality');")
        sb.append("sendControl({edgeMode:'high_quality',noiseReduction:'high_quality',hotPixel:'high_quality'},btn,'Qualidade M\u00e1xima');}")
        sb.append("function applyLatencyMin(btn){")
        sb.append("markActive('data-edge','off');markActive('data-nr','off');markActive('data-hotpx','off');")
        sb.append("sendControl({edgeMode:'off',noiseReduction:'off',hotPixel:'off'},btn,'Lat\u00eancia M\u00ednima');}")

        sb.append("function updateUIForCamera(camId){")
        sb.append("_currentCamId=String(camId);")
        sb.append("var cap=getCap(camId);")
        sb.append("if(!cap){showCard('card-zoom',true);showCard('card-focus',true);")
        sb.append("showCard('card-iso',true);showCard('card-ev',true);")
        sb.append("showCard('card-shutter',false);showCard('card-frame',false);")
        sb.append("showCard('card-wb',true);showCard('card-extras',true);")
        sb.append("showCard('card-postproc',false);")
        sb.append("updateOISCapability(false);return;}")
        sb.append("var hasZoom=cap.zoom_range&&cap.zoom_range[1]>1.0;showCard('card-zoom',hasZoom!==false);")
        sb.append("var zs=document.getElementById('zoom');if(zs)zs.disabled=!hasZoom;")
        sb.append("var hasFocus=cap.focus_distance_range&&cap.focus_distance_range[1]>0;showCard('card-focus',hasFocus);")
        sb.append("var fs=document.getElementById('focus');if(fs)fs.disabled=!hasFocus;")
        sb.append("if(hasFocus&&cap.supported_af_modes){")
        sb.append("var fg=document.getElementById('btngroup-focusmode');fg.innerHTML='';")
        sb.append("var afModes=cap.supported_af_modes;")
        sb.append("for(var i=0;i<afModes.length;i++){(function(mode){")
        sb.append("if(mode==='off'||mode==='auto'||mode==='continuous-video'){")
        sb.append("var b=document.createElement('button');")
        sb.append("b.textContent=mode==='off'?'Travar':mode==='auto'?'Auto':'Continuo';")
        sb.append("b.setAttribute('data-fm',mode);")
        sb.append("b.onclick=function(){markActive('data-fm',mode);setFocusMode(mode);};")
        sb.append("fg.appendChild(b);}})(afModes[i]);}}")
        sb.append("var hasISO=cap.supports_manual_sensor&&cap.iso_range;showCard('card-iso',hasISO);")
        sb.append("var is2=document.getElementById('iso'),tm=document.getElementById('toggle-manual');")
        sb.append("if(is2)is2.disabled=!hasISO;if(tm)tm.disabled=!hasISO;")
        sb.append("var hasManual=!!cap.supports_manual_sensor;")
        sb.append("showCard('card-shutter',hasManual);var sh=document.getElementById('shutter');if(sh)sh.disabled=!hasManual;")
        sb.append("showCard('card-frame',hasManual);var fr=document.getElementById('frame');if(fr)fr.disabled=!hasManual;")
        sb.append("var hasEV=Array.isArray(cap.ev_range)&&cap.ev_range.length===2;showCard('card-ev',hasEV);")
        sb.append("if(hasEV){var evMin=cap.ev_range[0],evMax=cap.ev_range[1];")
        sb.append("var evEl=document.getElementById('ev');evEl.min=evMin;evEl.max=evMax;evEl.value=0;")
        sb.append("document.getElementById('ev-labels').innerHTML='<span>'+evMin+'</span><span>0</span><span>+'+(evMax>0?evMax:8)+'</span>';}")
        sb.append("if(cap.supported_awb_modes&&cap.supported_awb_modes.length>0){showCard('card-wb',true);")
        sb.append("var wg=document.getElementById('btngroup-wb');wg.innerHTML='';")
        sb.append("var wbNames={'auto':'Auto','daylight':'Dia','cloudy':'Nublado','tungsten':'Tungst','incandescent':'Incand','fluorescent':'Fluor'};")
        sb.append("var awb=cap.supported_awb_modes;")
        sb.append("for(var j=0;j<awb.length;j++){(function(mode){var b=document.createElement('button');")
        sb.append("b.setAttribute('data-wb',mode);b.textContent=wbNames[mode]||mode;")
        sb.append("b.onclick=function(){markActive('data-wb',mode);sendControl({whiteBalance:mode},b,'WB '+mode);};")
        sb.append("wg.appendChild(b);})(awb[j]);}}")
        sb.append("else{showCard('card-wb',false);}")
        sb.append("var ef=document.getElementById('extras-flash');ef.innerHTML='';")
        sb.append("if(cap.has_flash){var flashDiv=document.createElement('div');flashDiv.style.marginBottom='12px';")
        sb.append("flashDiv.innerHTML='<h4 style=\"color:var(--text);font-size:12px;margin-bottom:6px\">Flash</h4>';")
        sb.append("var flashGroup=document.createElement('div');flashGroup.className='btngroup';")
        sb.append("var fmodes=['off','torch','single'];var flabels={'off':'Desligado','torch':'Lanterna','single':'Flash'};")
        sb.append("for(var m=0;m<fmodes.length;m++){(function(mode){var b=document.createElement('button');")
        sb.append("b.textContent=flabels[mode];b.setAttribute('data-flash',mode);")
        sb.append("b.onclick=function(){markActive('data-flash',mode);sendControl({flashMode:mode},b,'Flash '+mode);};")
        sb.append("flashGroup.appendChild(b);})(fmodes[m]);}")
        sb.append("flashDiv.appendChild(flashGroup);ef.appendChild(flashDiv);}")
        sb.append("updateOISCapability(!!cap.has_ois);showCard('card-extras',true);")
        sb.append("var hasPost=!!cap.supports_manual_post_processing;showCard('card-postproc',hasPost);}")

        sb.append("function switchCamera(id){markActive('data-cam',id);updateUIForCamera(id);sendControl({camera:id},null,'Cam '+id);}")
        sb.append("function setResolution(res,btn){markActive('data-res',res);sendControl({resolution:res},btn,res);}")
        sb.append("function setFPS(fps,btn){markActive('data-fps',fps);sendControl({fps:fps},btn,fps+' fps');}")
        sb.append("function updateBitrate(v){document.getElementById('br-value').textContent=v;")
        sb.append("clearTimeout(_brT);_brT=setTimeout(function(){sendControl({bitrate:+v},null,v+'kbps');},400);}")
        sb.append("function setBitratePreset(v){document.getElementById('bitrate').value=v;")
        sb.append("document.getElementById('br-value').textContent=v;sendControl({bitrate:v},null,v+'kbps');}")
        sb.append("function updateZoom(v){var pct=parseFloat(v);var mult=(1+pct*7).toFixed(1);")
        sb.append("document.getElementById('zoom-val').textContent=mult+'x';")
        sb.append("clearTimeout(_zT);_zT=setTimeout(function(){sendControl({zoom:pct},null,'Zoom '+mult+'x');},150);}")
        sb.append("function setZoomPreset(v){document.getElementById('zoom').value=v;updateZoom(v);}")
        sb.append("function updateFocus(v){var f=parseFloat(v);")
        sb.append("document.getElementById('focus-val').textContent=f===0?'Auto':f.toFixed(1)+'D';")
        sb.append("clearTimeout(_fT);_fT=setTimeout(function(){sendControl({focus:f/10},null,(f===0?'Auto':f.toFixed(1)+'D'));},200);}")
        sb.append("function setFocusMode(mode){sendControl({focusmode:mode},null,'Foco '+mode);}")
        sb.append("function triggerAF(btn){sendControl({afTrigger:true},btn,'AF Trigger');}")
        sb.append("function updateISO(v){var iso=ISO_LIST[Math.min(+v,ISO_LIST.length-1)];")
        sb.append("document.getElementById('iso-val').textContent=iso;")
        sb.append("clearTimeout(_iT);_iT=setTimeout(function(){sendControl({iso:iso},null,'ISO '+iso);},350);}")
        sb.append("function updateEV(v){if(_isManual)return;var n=+v;")
        sb.append("document.getElementById('ev-val').textContent=(n>0?'+':'')+n;")
        sb.append("clearTimeout(_eT);_eT=setTimeout(function(){sendControl({exposure:n},null,'EV '+n);},300);}")
        sb.append("function setEVPreset(v){if(_isManual)return;document.getElementById('ev').value=v;updateEV(v);}")
        sb.append("function toggleManual(chk){updateManualUI(chk.checked);sendControl({manualSensor:chk.checked},null,chk.checked?'\uD83C\uDFAC Manual ON':'Auto');}")
        sb.append("function updateShutter(idx){var i=Math.max(0,Math.min(SHUTTER_STOPS.length-1,parseInt(idx,10)||0));")
        sb.append("var v=SHUTTER_STOPS[i];var lbl=document.getElementById('shutter-val');if(lbl)lbl.textContent=v+'s';")
        sb.append("clearTimeout(_shT);_shT=setTimeout(function(){sendControl({shutterSpeed:v},null,'Shutter '+v);},200);}")
        sb.append("function setShutterPreset(v){var idx=SHUTTER_STOPS.indexOf(v);if(idx<0)return;")
        sb.append("var sh=document.getElementById('shutter');if(sh)sh.value=idx;")
        sb.append("var lbl=document.getElementById('shutter-val');if(lbl)lbl.textContent=v+'s';")
        sb.append("sendControl({shutterSpeed:v},null,'Shutter '+v);}")
        sb.append("function updateFrameTime(idx){var i=Math.max(0,Math.min(FRAME_STOPS.length-1,parseInt(idx,10)||0));")
        sb.append("var v=FRAME_STOPS[i];var lbl=document.getElementById('frame-val');if(lbl)lbl.textContent=v+'s';")
        sb.append("clearTimeout(_frT);_frT=setTimeout(function(){sendControl({frameDuration:v},null,'Frame '+v);},200);}")
        sb.append("function setFramePreset(idx){var fr=document.getElementById('frame');if(fr)fr.value=idx;updateFrameTime(idx);}")
        sb.append("function toggleOIS(chk){sendControl({ois:chk.checked},null,chk.checked?'OIS ON':'OIS OFF');}")
        sb.append("function toggleEIS(chk){sendControl({eis:chk.checked},null,chk.checked?'EIS ON':'EIS OFF');}")
        sb.append("function toggleAELock(chk){sendControl({aeLock:chk.checked},null,chk.checked?'AE Travado':'AE Livre');}")
        sb.append("function toggleAWBLock(chk){sendControl({awbLock:chk.checked},null,chk.checked?'AWB Travado':'AWB Livre');}")
        sb.append("function nsToShutter(ns){if(ns<=0)return '-';var s=ns/1e9;return s>=1?s.toFixed(1)+'s':'1/'+Math.round(1/s)+'s';}")
        sb.append("function latClass(ms){return ms<100?'lat-ok':ms<300?'lat-warn':'lat-bad';}")
        sb.append("function syncChk(id,val){var el=document.getElementById(id);if(el&&el.checked!==val)el.checked=val;}")
        sb.append("function syncSlider(id,val,mn,mx){if(!val)return;var el=document.getElementById(id);if(!el)return;")
        sb.append("var v=Math.max(mn,Math.min(mx,+val));if(document.activeElement!==el)el.value=v;}")

        sb.append("function pollStatus(){")
        sb.append("var t0=Date.now();")
        sb.append("fetch('/api/status')")
        sb.append(".then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.json();})")
        sb.append(".then(function(d){")
        sb.append("var lat=Date.now()-t0;var c=d.curvals||{};_pollFail=0;")
        sb.append("var streaming=d.streaming||false;")
        sb.append("document.getElementById('dot-stream').className='dot'+(streaming?'':' off');")
        sb.append("document.getElementById('lbl-stream').textContent=streaming?'Streaming':'Parado';")
        sb.append("document.getElementById('lbl-cam').textContent=c.camera_id||'-';")
        sb.append("document.getElementById('lbl-res').textContent=c.video_size||'-';")
        sb.append("document.getElementById('lbl-br').textContent=c.bitrate_kbps||'-';")
        sb.append("document.getElementById('lbl-clients').textContent=d.video_connections||0;")
        sb.append("var le=document.getElementById('lbl-lat');le.textContent=lat+'ms';le.className=latClass(lat);")
        sb.append("document.getElementById('info-focusmode').textContent=c.focusmode||'-';")
        sb.append("document.getElementById('info-focusdist').textContent=(c.focus_distance||'0.00')+'D';")
        sb.append("document.getElementById('info-iso').textContent=c.iso||'-';")
        sb.append("if(c.exposure_ns){var shutStr=nsToShutter(+c.exposure_ns);")
        sb.append("document.getElementById('info-exp').textContent=shutStr;")
        sb.append("var shutLbl=document.getElementById('shutter-val');if(shutLbl)shutLbl.textContent=shutStr;")
        sb.append("var shutPlain=shutStr.replace('s','');var shutIdx=SHUTTER_STOPS.indexOf(shutPlain);")
        sb.append("if(shutIdx>=0){var sEl=document.getElementById('shutter');if(sEl&&document.activeElement!==sEl)sEl.value=shutIdx;}}")
        sb.append("if(c.frame_duration){var frameStr=nsToShutter(+c.frame_duration);")
        sb.append("document.getElementById('info-frame').textContent=frameStr;")
        sb.append("var frameLbl=document.getElementById('frame-val');if(frameLbl)frameLbl.textContent=frameStr;")
        sb.append("var framePlain=frameStr.replace('s','');var frameIdx=FRAME_STOPS.indexOf(framePlain);")
        sb.append("if(frameIdx>=0){var fEl=document.getElementById('frame');if(fEl&&document.activeElement!==fEl)fEl.value=frameIdx;}}")
        sb.append("document.getElementById('info-focal').textContent=c.focal_length||'-';")
        sb.append("document.getElementById('info-ap').textContent=c.aperture||'-';")
        sb.append("document.getElementById('info-wb').textContent=c.whitebalance||'-';")
        sb.append("document.getElementById('info-ois').textContent=c.ois||'-';")
        sb.append("document.getElementById('info-eis').textContent=c.eis||'-';")
        sb.append("document.getElementById('info-fps').textContent=(c.fps||'-')+'fps';")
        sb.append("var edgeLbl=document.getElementById('info-edge');")
        sb.append("var nrLbl=document.getElementById('info-nr');")
        sb.append("var hotLbl=document.getElementById('info-hotpx');")
        sb.append("if(edgeLbl&&c.edge_mode)edgeLbl.textContent=c.edge_mode;")
        sb.append("if(nrLbl&&c.noise_reduction_mode)nrLbl.textContent=c.noise_reduction_mode;")
        sb.append("if(hotLbl&&c.hot_pixel_mode)hotLbl.textContent=c.hot_pixel_mode;")
        sb.append("if(c.edge_mode)markActive('data-edge',c.edge_mode);")
        sb.append("if(c.noise_reduction_mode)markActive('data-nr',c.noise_reduction_mode);")
        sb.append("if(c.hot_pixel_mode)markActive('data-hotpx',c.hot_pixel_mode);")
        sb.append("var isManual=c.manual_sensor==='on';syncChk('toggle-manual',isManual);updateManualUI(isManual);")
        sb.append("syncChk('toggle-ois',c.ois==='on');syncChk('toggle-eis',c.eis==='on');")
        sb.append("syncChk('toggle-ae-lock',c.ae_lock==='on');syncChk('toggle-awb-lock',c.awb_lock==='on');")
        sb.append("if(c.whitebalance)markActive('data-wb',c.whitebalance);")
        sb.append("if(c.flash_mode)markActive('data-flash',c.flash_mode);")
        sb.append("if(c.fps)markActive('data-fps',+c.fps);")
        sb.append("syncSlider('bitrate',c.bitrate_kbps,500,25000);")
        sb.append("if(c.bitrate_kbps)document.getElementById('br-value').textContent=c.bitrate_kbps;")
        sb.append("})")
        sb.append(".catch(function(){_pollFail++;")
        sb.append("if(_pollFail>=3){document.getElementById('dot-stream').className='dot off';")
        sb.append("document.getElementById('lbl-stream').textContent='Sem conexao';}});}")

        sb.append("function initCapabilities(){")
        sb.append("fetch('/api/capabilities')")
        sb.append(".then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.json();})")
        sb.append(".then(function(caps){")
        sb.append("_caps=caps;")
        sb.append("var cg=document.getElementById('btngroup-camera');cg.innerHTML='';")
        sb.append("for(var i=0;i<caps.length;i++){(function(cap){")
        sb.append("if(cap.is_depth)return;")
        sb.append("var b=document.createElement('button');b.setAttribute('data-cam',cap.camera_id);")
        sb.append("b.textContent=cap.name||('Cam '+cap.camera_id);")
        sb.append("b.onclick=function(){switchCamera(cap.camera_id);};cg.appendChild(b);})(caps[i]);}")
        sb.append("var rg=document.getElementById('btngroup-resolution');rg.innerHTML='';")
        sb.append("var fc=null;for(var i=0;i<caps.length;i++){if(!caps[i].is_depth){fc=caps[i];break;}}")
        sb.append("if(fc&&fc.available_resolutions){")
        sb.append("var rl=fc.available_resolutions;")
        sb.append("var ps=[{res:'720p',key:'1280x720',lbl:'HD 720p'},{res:'1080p',key:'1920x1080',lbl:'FHD 1080p'},{res:'4k',key:'3840x2160',lbl:'4K 2160p'}];")
        sb.append("for(var j=0;j<ps.length;j++){(function(p){")
        sb.append("var found=false;for(var k=0;k<rl.length;k++){if(rl[k]===p.key){found=true;break;}}")
        sb.append("if(found){var b=document.createElement('button');b.className='pq-btn';b.setAttribute('data-res',p.res);")
        sb.append("b.textContent=p.lbl;b.onclick=function(){setResolution(p.res,b);};rg.appendChild(b);}})(ps[j]);}}")
        sb.append("var fg=document.getElementById('btngroup-fps');fg.innerHTML='';")
        sb.append("var fpsOpts=[15,24,30];")
        sb.append("for(var k=0;k<fpsOpts.length;k++){(function(fps){")
        sb.append("var b=document.createElement('button');b.setAttribute('data-fps',fps);b.textContent=fps+' fps';")
        sb.append("if(fps===30)b.classList.add('active');b.onclick=function(){setFPS(fps,b);};fg.appendChild(b);})(fpsOpts[k]);}")
        sb.append("markActive('data-cam','0');updateUIForCamera('0');")
        sb.append("})")
        sb.append(".catch(function(e){")
        sb.append("console.error('caps err:',e);")
        sb.append("var cg=document.getElementById('btngroup-camera');cg.innerHTML='';")
        sb.append("var defs=[{id:'0',n:'Wide'},{id:'1',n:'Frontal'},{id:'2',n:'Ultra Wide'},{id:'3',n:'Telephoto'}];")
        sb.append("for(var i=0;i<defs.length;i++){(function(d){var b=document.createElement('button');")
        sb.append("b.setAttribute('data-cam',d.id);b.textContent=d.n;")
        sb.append("b.onclick=function(){switchCamera(d.id);};cg.appendChild(b);})(defs[i]);}")
        sb.append("showCard('card-zoom',true);showCard('card-focus',true);showCard('card-iso',true);")
        sb.append("showCard('card-ev',true);showCard('card-shutter',true);showCard('card-frame',true);")
        sb.append("showCard('card-wb',true);showCard('card-postproc',true);")
        sb.append("updateOISCapability(false);")
        sb.append("var wg=document.getElementById('btngroup-wb');wg.innerHTML='';")
        sb.append("var wbDef=['auto','daylight','cloudy','tungsten','fluorescent'];")
        sb.append("var wbN={'auto':'Auto','daylight':'Dia','cloudy':'Nublado','tungsten':'Tungst','fluorescent':'Fluor'};")
        sb.append("for(var j=0;j<wbDef.length;j++){(function(mode){var b=document.createElement('button');")
        sb.append("b.setAttribute('data-wb',mode);b.textContent=wbN[mode]||mode;")
        sb.append("b.onclick=function(){markActive('data-wb',mode);sendControl({whiteBalance:mode},b,'WB '+mode);};")
        sb.append("wg.appendChild(b);})(wbDef[j]);}")
        sb.append("var fg2=document.getElementById('btngroup-focusmode');")
        sb.append("fg2.innerHTML='<button onclick=\"setFocusMode(\\'auto\\');\">Auto</button>'")
        sb.append("+'<button onclick=\"setFocusMode(\\'continuous-video\\');\">Continuo</button>'")
        sb.append("+'<button onclick=\"setFocusMode(\\'off\\');\">Travar</button>';")
        sb.append("markActive('data-cam','0');")
        sb.append("});}")

        sb.append("initCapabilities();")
        sb.append("pollStatus();")
        sb.append("setInterval(pollStatus,2000);")
        sb.append("</script></body></html>")
        return sb.toString()
    }
}
