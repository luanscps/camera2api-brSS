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
            <title>Camera2 Control Panel</title>
            <style>
                body { 
                    font-family: Arial; 
                    background: #0f172a; 
                    color: #f1f5f9; 
                    padding: 20px; 
                }
                .container { max-width: 800px; margin: 0 auto; }
                h1 { color: #38bdf8; text-align: center; }
                .control { 
                    background: #1e293b; 
                    padding: 20px; 
                    margin: 16px 0; 
                    border-radius: 8px; 
                }
                .control h3 { color: #38bdf8; margin-bottom: 12px; }
                input[type="range"] { 
                    width: 100%; 
                    height: 6px; 
                    background: #334155; 
                }
                button { 
                    background: #38bdf8; 
                    color: #0f172a; 
                    border: none; 
                    padding: 12px 24px; 
                    border-radius: 8px; 
                    cursor: pointer; 
                    font-weight: 600; 
                    margin: 8px 4px; 
                }
                button:hover { background: #0ea5e9; }
                .status { 
                    background: #10b981; 
                    color: white; 
                    padding: 8px 16px; 
                    border-radius: 4px; 
                    display: inline-block; 
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>📷 Camera2 Control Panel</h1>
                <p style="text-align: center;">
                    <span class="status">● Conectado</span>
                </p>
                
                <div class="control">
                    <h3>🎥 Seleção de Câmera</h3>
                    <button onclick="switchCamera('0')">Wide (Principal)</button>
                    <button onclick="switchCamera('2')">Ultra Wide</button>
                    <button onclick="switchCamera('3')">Telephoto</button>
                    <button onclick="switchCamera('1')">Frontal</button>
                </div>
                
                <div class="control">
                    <h3>ISO: <span id="iso-value">400</span></h3>
                    <input type="range" id="iso" min="50" max="3200" 
                           value="400" step="50" 
                           oninput="updateISO(this.value)">
                </div>
                
                <div class="control">
                    <h3>Exposição: <span id="exp-value">1/60s</span></h3>
                    <input type="range" id="exposure" min="0" max="100" 
                           value="50" oninput="updateExposure(this.value)">
                </div>
                
                <div class="control">
                    <h3>Foco: <span id="focus-value">Auto</span></h3>
                    <input type="range" id="focus" min="0" max="10" 
                           value="0" step="0.5" 
                           oninput="updateFocus(this.value)">
                </div>
                
                <div class="control">
                    <h3>Balanço de Branco</h3>
                    <button onclick="setWB('auto')">Auto</button>
                    <button onclick="setWB('daylight')">Luz do Dia</button>
                    <button onclick="setWB('cloudy')">Nublado</button>
                    <button onclick="setWB('tungsten')">Tungstênio</button>
                </div>
            </div>
            
            <script>
                function sendControl(data) {
                    fetch('/api/control', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify(data)
                    });
                }
                
                function switchCamera(id) {
                    sendControl({camera: id});
                }
                
                function updateISO(value) {
                    document.getElementById('iso-value').textContent = value;
                    sendControl({iso: parseInt(value)});
                }
                
                function updateExposure(value) {
                    const times = [1/8000, 1/4000, 1/2000, 1/1000, 1/500, 
                                   1/250, 1/125, 1/60, 1/30, 1/15, 1/8, 
                                   1/4, 1/2, 1, 2, 4, 8, 15, 30];
                    const idx = Math.floor((value/100) * (times.length-1));
                    const time = times[idx];
                    const ns = Math.round(time * 1e9);
                    
                    const display = time < 1 ? 
                        '1/' + Math.round(1/time) + 's' : time + 's';
                    document.getElementById('exp-value').textContent = display;
                    sendControl({exposure: ns});
                }
                
                function updateFocus(value) {
                    const display = value == 0 ? 'Auto' : value;
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
        } catch (e: Exception) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Error parsing body"
            )
        }
        
        val json = map["postData"] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, 
            "text/plain", 
            "No data"
        )
        
        val params = gson.fromJson(json, 
            object : TypeToken<Map<String, Any>>() {}.type
        ) as Map<String, Any>
        
        cameraController.updateSettings(params)
        
        return newFixedLengthResponse(
            Response.Status.OK, 
            "application/json", 
            """{ "status":"ok" }"""
        )
    }
    
    private fun serveStatus(): Response {
        val status = mapOf(
            "camera" to cameraController.currentCameraId,
            "iso" to cameraController.iso,
            "exposure" to cameraController.exposureTime
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(status)
        )
    }
}
