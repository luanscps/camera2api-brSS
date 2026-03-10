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
            uri == "/" -> serveControlPanel()
            uri == "/api/status" -> serveStatus()
            uri == "/api/control" && session.method == Method.POST -> 
                handleControl(session)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, 
                "text/plain", 
                "Not Found"
            )
        }
    }
    
    private fun serveControlPanel(): Response {
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Camera2 RTSP Control Panel</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { 
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                    background: #0f172a; 
                    color: #f1f5f9; 
                    padding: 20px; 
                }
                .container { max-width: 900px; margin: 0 auto; }
                h1 { 
                    color: #38bdf8; 
                    text-align: center; 
                    margin-bottom: 12px;
                    font-size: 28px;
                }
                .subtitle {
                    text-align: center;
                    color: #cbd5e1;
                    margin-bottom: 24px;
                    font-size: 14px;
                }
                .status-badge {
                    background: #10b981;
                    color: white;
                    padding: 8px 20px;
                    border-radius: 20px;
                    display: inline-block;
                    font-weight: 600;
                    font-size: 14px;
                    margin-bottom: 32px;
                }
                .status-container {
                    text-align: center;
                    margin-bottom: 32px;
                }
                .control { 
                    background: #1e293b; 
                    padding: 24px; 
                    margin: 20px 0; 
                    border-radius: 12px;
                    border: 1px solid #334155;
                }
                .control h3 { 
                    color: #38bdf8; 
                    margin-bottom: 16px; 
                    font-size: 18px;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .value-display {
                    display: inline-block;
                    background: #334155;
                    padding: 4px 12px;
                    border-radius: 6px;
                    font-weight: 600;
                    color: #38bdf8;
                    margin-left: 8px;
                    min-width: 80px;
                    text-align: center;
                }
                input[type="range"] { 
                    width: 100%; 
                    height: 8px; 
                    background: #334155;
                    border-radius: 4px;
                    outline: none;
                    -webkit-appearance: none;
                    margin: 12px 0;
                }
                input[type="range"]::-webkit-slider-thumb {
                    -webkit-appearance: none;
                    appearance: none;
                    width: 20px;
                    height: 20px;
                    background: #38bdf8;
                    cursor: pointer;
                    border-radius: 50%;
                }
                input[type="range"]::-moz-range-thumb {
                    width: 20px;
                    height: 20px;
                    background: #38bdf8;
                    cursor: pointer;
                    border-radius: 50%;
                    border: none;
                }
                button { 
                    background: #38bdf8; 
                    color: #0f172a; 
                    border: none; 
                    padding: 12px 24px; 
                    border-radius: 8px; 
                    cursor: pointer; 
                    font-weight: 600; 
                    margin: 8px 6px 8px 0;
                    font-size: 14px;
                    transition: all 0.2s;
                }
                button:hover { 
                    background: #0ea5e9;
                    transform: translateY(-1px);
                }
                button:active {
                    transform: translateY(0);
                }
                .button-group {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 8px;
                }
                .footer {
                    text-align: center;
                    margin-top: 40px;
                    padding-top: 20px;
                    border-top: 1px solid #334155;
                    color: #64748b;
                    font-size: 13px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>📷 Camera2 RTSP Control Panel</h1>
                <p class="subtitle">Samsung Galaxy Note10+ - Controle Profissional</p>
                
                <div class="status-container">
                    <span class="status-badge">● Sistema Ativo</span>
                </div>
                
                <div class="control">
                    <h3>🎥 Seleção de Câmera</h3>
                    <div class="button-group">
                        <button onclick="switchCamera('0')">📷 Wide (Principal)</button>
                        <button onclick="switchCamera('2')">🌊 Ultra Wide</button>
                        <button onclick="switchCamera('3')">🔭 Telephoto</button>
                        <button onclick="switchCamera('1')">🤳 Frontal</button>
                    </div>
                </div>
                
                <div class="control">
                    <h3>⚡ ISO <span class="value-display" id="iso-value">400</span></h3>
                    <input type="range" id="iso" min="50" max="3200" 
                           value="400" step="50" 
                           oninput="updateISO(this.value)">
                    <div style="display: flex; justify-content: space-between; font-size: 12px; color: #64748b;">
                        <span>50</span>
                        <span>3200</span>
                    </div>
                </div>
                
                <div class="control">
                    <h3>🕐 Exposição <span class="value-display" id="exp-value">1/60s</span></h3>
                    <input type="range" id="exposure" min="0" max="100" 
                           value="50" oninput="updateExposure(this.value)">
                    <div style="display: flex; justify-content: space-between; font-size: 12px; color: #64748b;">
                        <span>1/8000s</span>
                        <span>30s</span>
                    </div>
                </div>
                
                <div class="control">
                    <h3>🎯 Foco <span class="value-display" id="focus-value">Auto</span></h3>
                    <input type="range" id="focus" min="0" max="10" 
                           value="0" step="0.5" 
                           oninput="updateFocus(this.value)">
                    <div style="display: flex; justify-content: space-between; font-size: 12px; color: #64748b;">
                        <span>Auto</span>
                        <span>Manual</span>
                    </div>
                </div>
                
                <div class="control">
                    <h3>☀️ Balanço de Branco</h3>
                    <div class="button-group">
                        <button onclick="setWB('auto')">🤖 Auto</button>
                        <button onclick="setWB('daylight')">☀️ Luz do Dia</button>
                        <button onclick="setWB('cloudy')">☁️ Nublado</button>
                        <button onclick="setWB('tungsten')">💡 Tungstênio</button>
                    </div>
                </div>
                
                <div class="footer">
                    <p>💡 <strong>Como usar:</strong> Ajuste os controles aqui e veja mudanças instantâneas no stream RTSP (VLC/OBS)</p>
                    <p style="margin-top: 8px;">Desenvolvido com Camera2 API + RootEncoder + NanoHTTPD</p>
                </div>
            </div>
            
            <script>
                function sendControl(data) {
                    fetch('/api/control', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify(data)
                    }).catch(err => console.error('Error:', err));
                }
                
                function switchCamera(id) {
                    sendControl({camera: id});
                }
                
                function updateISO(value) {
                    document.getElementById('iso-value').textContent = value;
                    sendControl({iso: parseInt(value)});
                }
                
                function updateExposure(value) {
                    const times = [
                        1/8000, 1/4000, 1/2000, 1/1000, 1/500, 
                        1/250, 1/125, 1/60, 1/30, 1/15, 1/8, 
                        1/4, 1/2, 1, 2, 4, 8, 15, 30
                    ];
                    const idx = Math.floor((value/100) * (times.length-1));
                    const time = times[idx];
                    const ns = Math.round(time * 1e9);
                    
                    const display = time < 1 ? 
                        '1/' + Math.round(1/time) + 's' : time + 's';
                    document.getElementById('exp-value').textContent = display;
                    sendControl({exposure: ns});
                }
                
                function updateFocus(value) {
                    const display = value == 0 ? 'Auto' : value.toFixed(1);
                    document.getElementById('focus-value').textContent = display;
                    sendControl({focus: parseFloat(value)});
                }
                
                function setWB(mode) {
                    sendControl({whiteBalance: mode});
                }
            </script>
        </body>
        </html>
        """.trimIndent()
        
        return newFixedLengthResponse(
            Response.Status.OK, 
            "text/html", 
            html
        )
    }
    
    private fun handleControl(session: IHTTPSession): Response {
        val map = mutableMapOf<String, String>()
        try {
            session.parseBody(map)
            
            val json = map["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, 
                "text/plain", 
                "No data"
            )
            
            val params = gson.fromJson<Map<String, Any>>(
                json, 
                object : TypeToken<Map<String, Any>>() {}.type
            )
            
            cameraController.updateSettings(params)
            
            return newFixedLengthResponse(
                Response.Status.OK, 
                "application/json", 
                """{"status":"ok"}"""
            )
        } catch (e: Exception) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"status":"error","message":"${e.message}"}"""
            )
        }
    }
    
    private fun serveStatus(): Response {
        val status = mapOf(
            "camera"       to cameraController.currentCameraId,
            "exposureLevel" to cameraController.exposureLevel,
            "whiteBalance" to cameraController.whiteBalanceMode,
            "autoFocus"    to cameraController.autoFocus,
            "status"       to "active"
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(status)
        )
    }
}
