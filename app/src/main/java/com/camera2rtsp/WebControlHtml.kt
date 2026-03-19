package com.camera2rtsp

object WebControlHtml {
    fun build(): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html lang=\"pt-BR\">")
        sb.append("<head><meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">")
        sb.append("<title>Camera2 Control</title>")
        sb.append("<style>")

        // ── CSS Variables ──
        sb.append(":root{")
        sb.append("--bg:#070d1a;")
        sb.append("--glass:rgba(255,255,255,0.05);")
        sb.append("--glass2:rgba(255,255,255,0.09);")
        sb.append("--border:rgba(255,255,255,0.10);")
        sb.append("--accent:#38bdf8;")
        sb.append("--accent2:#818cf8;")
        sb.append("--green:#10b981;")
        sb.append("--red:#f43f5e;")
        sb.append("--yellow:#fbbf24;")
        sb.append("--text:#f1f5f9;")
        sb.append("--muted:#64748b;")
        sb.append("--nav-h:62px;")
        sb.append("}")

        // ── Reset & Base ──
        sb.append("*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}")
        sb.append("html{font-size:14px;height:100%}")
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
        sb.append("background:var(--bg);color:var(--text);min-height:100vh;")
        sb.append("background-image:radial-gradient(ellipse 80% 50% at 50% -20%,rgba(56,189,248,0.12),transparent),")
        sb.append("radial-gradient(ellipse 60% 40% at 80% 80%,rgba(129,140,248,0.08),transparent);}")

        // ── Top Header ──
        sb.append(".top-header{position:sticky;top:0;z-index:100;backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);")
        sb.append("background:rgba(7,13,26,0.85);border-bottom:1px solid var(--border);padding:10px 16px;")
        sb.append("display:flex;align-items:center;justify-content:space-between;}")
        sb.append(".top-header h1{font-size:15px;font-weight:800;background:linear-gradient(90deg,var(--accent),var(--accent2));")
        sb.append("-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;}")
        sb.append(".top-header .sub{font-size:10px;color:var(--muted);margin-top:1px}")

        // ── Live Status Pills ──
        sb.append(".live-bar{display:flex;gap:6px;align-items:center;flex-wrap:wrap}")
        sb.append(".pill{display:flex;align-items:center;gap:4px;background:var(--glass);border:1px solid var(--border);")
        sb.append("padding:3px 9px;border-radius:20px;font-size:10px;font-weight:600;white-space:nowrap}")
        sb.append(".dot{width:7px;height:7px;border-radius:50%;background:var(--red);box-shadow:0 0 6px var(--red);transition:all .4s}")
        sb.append(".dot.live{background:var(--green);box-shadow:0 0 8px var(--green);animation:pulse 2s infinite}")
        sb.append("@keyframes pulse{0%,100%{opacity:1}50%{opacity:.5}}")

        // ── Main Content ──
        sb.append(".main{padding:14px 12px calc(var(--nav-h) + 14px)}")
        sb.append(".page{display:none}.page.active{display:block}")

        // ── Cards / Panels ──
        sb.append(".card{background:var(--glass);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);")
        sb.append("border:1px solid var(--border);border-radius:18px;padding:16px;margin-bottom:12px;}")
        sb.append(".card-title{display:flex;align-items:center;gap:8px;font-size:13px;font-weight:700;margin-bottom:14px;color:var(--text)}")
        sb.append(".card-title .icon{font-size:16px;line-height:1}")
        sb.append(".card-title .badge-val{margin-left:auto;background:rgba(56,189,248,.15);color:var(--accent);")
        sb.append("font-size:12px;font-weight:700;padding:2px 10px;border-radius:8px;border:1px solid rgba(56,189,248,.25)}")

        // ── Buttons ──
        sb.append(".btn{display:inline-flex;align-items:center;justify-content:center;gap:5px;")
        sb.append("background:var(--glass2);color:var(--text);border:1px solid var(--border);")
        sb.append("padding:9px 14px;border-radius:12px;cursor:pointer;font-weight:600;font-size:12px;")
        sb.append("transition:all .15s;touch-action:manipulation;user-select:none;min-height:40px}")
        sb.append(".btn:active{transform:scale(.91)}")
        sb.append(".btn:hover{background:rgba(56,189,248,.18);border-color:var(--accent);color:var(--accent)}")
        sb.append(".btn.active{background:rgba(16,185,129,.2);border-color:var(--green);color:var(--green)}")
        sb.append(".btn.danger{background:rgba(244,63,94,.12);border-color:var(--red);color:var(--red)}")
        sb.append(".btn.primary{background:linear-gradient(135deg,rgba(56,189,248,.3),rgba(129,140,248,.2));")
        sb.append("border-color:var(--accent);color:var(--accent)}")
        sb.append(".btn-row{display:flex;flex-wrap:wrap;gap:7px}")
        sb.append(".btn-full{width:100%}")

        // ── Slider ──
        sb.append(".slider-wrap{margin:6px 0}")
        sb.append(".slider-labels{display:flex;justify-content:space-between;font-size:10px;color:var(--muted);margin-top:3px}")
        sb.append("input[type=range]{width:100%;height:4px;background:rgba(255,255,255,.15);border-radius:4px;outline:none;")
        sb.append("-webkit-appearance:none;margin:12px 0 4px;cursor:pointer}")
        sb.append("input[type=range]::-webkit-slider-thumb{-webkit-appearance:none;width:26px;height:26px;")
        sb.append("background:linear-gradient(135deg,var(--accent),var(--accent2));cursor:grab;border-radius:50%;")
        sb.append("border:3px solid rgba(7,13,26,.8);box-shadow:0 2px 8px rgba(56,189,248,.4)}")

        // ── Toggle Switch ──
        sb.append(".toggle-row{display:flex;align-items:center;justify-content:space-between;")
        sb.append("padding:10px 0;border-bottom:1px solid var(--border)}")
        sb.append(".toggle-row:last-child{border-bottom:none}")
        sb.append(".toggle-label{font-size:13px;display:flex;align-items:center;gap:8px;color:var(--text)}")
        sb.append(".toggle-label .ticon{font-size:15px}")
        sb.append(".switch{position:relative;display:inline-block;width:48px;height:27px;flex-shrink:0}")
        sb.append(".switch input{opacity:0;width:0;height:0}")
        sb.append(".sw{position:absolute;cursor:pointer;inset:0;background:rgba(255,255,255,.12);border-radius:27px;transition:.3s;border:1px solid var(--border)}")
        sb.append(".sw:before{content:'';position:absolute;height:21px;width:21px;left:3px;bottom:2px;")
        sb.append("background:#fff;border-radius:50%;transition:.3s;box-shadow:0 1px 4px rgba(0,0,0,.5)}")
        sb.append("input:checked+.sw{background:rgba(16,185,129,.4);border-color:var(--green)}")
        sb.append("input:checked+.sw:before{transform:translateX(21px)}")

        // ── RTMP Input ──
        sb.append(".rtmp-input{width:100%;background:rgba(0,0,0,.3);color:var(--text);")
        sb.append("border:1px solid var(--border);border-radius:10px;padding:10px 13px;")
        sb.append("font-size:12px;font-family:monospace;outline:none;margin-top:10px}")
        sb.append(".rtmp-input:focus{border-color:var(--accent);box-shadow:0 0 0 2px rgba(56,189,248,.15)}")

        // ── Stream Status Card ──
        sb.append(".stream-status-card{background:linear-gradient(135deg,rgba(56,189,248,.08),rgba(129,140,248,.06));")
        sb.append("border:1px solid rgba(56,189,248,.2);border-radius:18px;padding:14px;margin-bottom:12px;")
        sb.append("display:grid;grid-template-columns:1fr 1fr;gap:8px}")
        sb.append(".stat-item{background:rgba(0,0,0,.25);border-radius:10px;padding:8px 10px}")
        sb.append(".stat-item .stat-lbl{font-size:10px;color:var(--muted);margin-bottom:2px}")
        sb.append(".stat-item .stat-val{font-size:13px;font-weight:700;color:var(--text)}")

        // ── Grid ──
        sb.append("@media(min-width:480px){.grid2{display:grid;grid-template-columns:1fr 1fr;gap:12px}}")

        // ── Bottom Navigation ──
        sb.append(".bottom-nav{position:fixed;bottom:0;left:0;right:0;height:var(--nav-h);z-index:200;")
        sb.append("backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px);")
        sb.append("background:rgba(7,13,26,0.92);border-top:1px solid var(--border);")
        sb.append("display:flex;align-items:stretch}")
        sb.append(".nav-btn{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;")
        sb.append("gap:3px;cursor:pointer;border:none;background:transparent;color:var(--muted);")
        sb.append("font-size:10px;font-weight:600;transition:color .2s;padding:0;touch-action:manipulation}")
        sb.append(".nav-btn .nav-icon{font-size:20px;line-height:1;transition:transform .2s}")
        sb.append(".nav-btn.active{color:var(--accent)}")
        sb.append(".nav-btn.active .nav-icon{transform:scale(1.15)}")
        sb.append(".nav-indicator{position:absolute;bottom:0;height:2px;background:var(--accent);")
        sb.append("border-radius:2px 2px 0 0;transition:left .25s cubic-bezier(.4,0,.2,1),width .25s;pointer-events:none}")

        // ── Toast ──
        sb.append("#toast{position:fixed;bottom:calc(var(--nav-h) + 12px);left:50%;")
        sb.append("transform:translateX(-50%) translateY(20px);padding:9px 20px;")
        sb.append("border-radius:22px;font-size:12px;font-weight:700;pointer-events:none;")
        sb.append("transition:transform .2s,opacity .2s;opacity:0;z-index:999;white-space:nowrap;")
        sb.append("backdrop-filter:blur(10px)}")
        sb.append("#toast.show{transform:translateX(-50%) translateY(0);opacity:1}")
        sb.append("#toast.ok{background:rgba(16,185,129,.9);color:#fff}")
        sb.append("#toast.err{background:rgba(244,63,94,.9);color:#fff}")

        // ── Section Divider ──
        sb.append(".section-label{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;")
        sb.append("color:var(--muted);margin:14px 0 8px;padding-left:2px}")

        sb.append("</style></head><body>")

        // ═══════════════════════════════════════════
        //  TOP HEADER
        // ═══════════════════════════════════════════
        sb.append("<div class=\"top-header\">")
        sb.append("<div><div class=\"top-header h1\">📷 Camera2 Control</div>")
        sb.append("<div class=\"sub\">Samsung Galaxy Note10+ · Wi-Fi</div></div>")
        sb.append("<div class=\"live-bar\">")
        sb.append("<div class=\"pill\"><span class=\"dot\" id=\"dot-stream\"></span><span id=\"lbl-stream\">Off</span></div>")
        sb.append("<div class=\"pill\">⚡ <span id=\"lbl-lat\">-</span></div>")
        sb.append("</div></div>")

        // ═══════════════════════════════════════════
        //  MAIN CONTENT
        // ═══════════════════════════════════════════
        sb.append("<div class=\"main\">")

        // ─── PAGE: STREAM ───────────────────────────
        sb.append("<div class=\"page active\" id=\"page-stream\">")

        // Stream status grid
        sb.append("<div class=\"stream-status-card\">")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">CÂMERA</div><div class=\"stat-val\" id=\"lbl-cam\">-</div></div>")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">RESOLUÇÃO</div><div class=\"stat-val\" id=\"lbl-res\">-</div></div>")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">BITRATE</div><div class=\"stat-val\"><span id=\"lbl-br\">-</span> kbps</div></div>")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">STATUS</div><div class=\"stat-val\" id=\"lbl-status-full\">-</div></div>")
        sb.append("</div>")

        // Stream controls
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">📡</span> Destino RTMP</div>")
        sb.append("<input class=\"rtmp-input\" id=\"rtmp-url\" type=\"text\" value=\"rtmp://192.168.1.100:1935/live/stream\" placeholder=\"rtmp://IP:1935/live/stream\">")
        sb.append("<div class=\"btn-row\" style=\"margin-top:12px\">")
        sb.append("<button class=\"btn primary\" onclick=\"applyRtmpUrl()\">💾 Salvar URL</button>")
        sb.append("</div>")
        sb.append("<div class=\"btn-row\" style=\"margin-top:8px\">")
        sb.append("<button class=\"btn active btn-full\" onclick=\"sendControl({streamAction:'start'},this,'Stream iniciado')\">▶ Iniciar Stream</button>")
        sb.append("</div>")
        sb.append("<div class=\"btn-row\" style=\"margin-top:6px\">")
        sb.append("<button class=\"btn danger btn-full\" onclick=\"sendControl({streamAction:'stop'},this,'Stream parado')\">⏹ Parar Stream</button>")
        sb.append("</div>")
        sb.append("</div>")

        // Resolução
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">🖥️</span> Resolução</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-resolution\"></div>")
        sb.append("<div class=\"section-label\">Taxa de Quadros</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-fps\"></div>")
        sb.append("</div>")

        // Bitrate
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">📶</span> Bitrate <span class=\"badge-val\" id=\"br-value\">4000 kbps</span></div>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"bitrate\" min=\"500\" max=\"25000\" value=\"4000\" step=\"500\" oninput=\"updateBitrate(this.value)\">")
        sb.append("<div class=\"slider-labels\"><span>500k</span><span>12M</span><span>25M</span></div></div>")
        sb.append("<div class=\"btn-row\" style=\"margin-top:8px\">")
        sb.append("<button class=\"btn\" onclick=\"setBitratePreset(2000)\">2M</button>")
        sb.append("<button class=\"btn\" onclick=\"setBitratePreset(4000)\">4M</button>")
        sb.append("<button class=\"btn\" onclick=\"setBitratePreset(8000)\">8M</button>")
        sb.append("<button class=\"btn\" onclick=\"setBitratePreset(20000)\">20M</button>")
        sb.append("</div></div>")

        sb.append("</div>") // end page-stream

        // ─── PAGE: CÂMERA ────────────────────────────
        sb.append("<div class=\"page\" id=\"page-camera\">")

        // Seleção de câmera
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">📷</span> Seleção de Câmera</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-camera\">Carregando...</div>")
        sb.append("</div>")

        // Zoom
        sb.append("<div class=\"card\" id=\"card-zoom\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">🔍</span> Zoom <span class=\"badge-val\" id=\"zoom-val\">1x</span></div>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"zoom\" min=\"0\" max=\"1\" value=\"0\" step=\"0.01\" oninput=\"updateZoom(this.value)\">")
        sb.append("<div class=\"slider-labels\"><span>1x</span><span>Max</span></div></div>")
        sb.append("<div class=\"btn-row\" style=\"margin-top:8px\">")
        sb.append("<button class=\"btn\" onclick=\"setZoomPreset(0)\">1x</button>")
        sb.append("<button class=\"btn\" onclick=\"setZoomPreset(0.25)\">2x</button>")
        sb.append("<button class=\"btn\" onclick=\"setZoomPreset(0.5)\">4x</button>")
        sb.append("<button class=\"btn\" onclick=\"setZoomPreset(1.0)\">Max</button>")
        sb.append("</div></div>")

        // Foco
        sb.append("<div class=\"card\" id=\"card-focus\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">🎯</span> Foco <span class=\"badge-val\" id=\"focus-val\">Auto</span></div>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"focus\" min=\"0\" max=\"10\" value=\"0\" step=\"0.1\" oninput=\"updateFocus(this.value)\">")
        sb.append("<div class=\"slider-labels\"><span>Auto / ∞</span><span>Macro (10D)</span></div></div>")
        sb.append("<div class=\"section-label\">Modo AF</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-focusmode\"></div>")
        sb.append("<button class=\"btn btn-full\" style=\"margin-top:10px\" onclick=\"triggerAF(this)\">🎯 Disparar Auto Foco</button>")
        sb.append("</div>")

        sb.append("</div>") // end page-camera

        // ─── PAGE: EXPOSIÇÃO ─────────────────────────
        sb.append("<div class=\"page\" id=\"page-exposure\">")

        // Manual toggle
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">🎛️</span> Modo de Controle</div>")
        sb.append("<div class=\"toggle-row\">")
        sb.append("<span class=\"toggle-label\"><span class=\"ticon\">🤖</span> Sensor Manual</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-manual\" onchange=\"toggleManual(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div>")
        sb.append("<div class=\"toggle-row\">")
        sb.append("<span class=\"toggle-label\"><span class=\"ticon\">🔒</span> Travar AE</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-ae-lock\" onchange=\"toggleAELock(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div>")
        sb.append("</div>")

        // ISO
        sb.append("<div class=\"card\" id=\"card-iso\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">📸</span> ISO <span class=\"badge-val\" id=\"iso-val\">50</span></div>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"iso\" min=\"0\" max=\"6\" value=\"0\" step=\"1\" oninput=\"updateISO(this.value)\">")
        sb.append("<div class=\"slider-labels\"><span>50</span><span>800</span><span>3200</span></div></div>")
        sb.append("<div class=\"btn-row\" style=\"margin-top:8px\">")
        sb.append("<button class=\"btn\" onclick=\"setISOPreset(0)\">50</button>")
        sb.append("<button class=\"btn\" onclick=\"setISOPreset(1)\">100</button>")
        sb.append("<button class=\"btn\" onclick=\"setISOPreset(2)\">200</button>")
        sb.append("<button class=\"btn\" onclick=\"setISOPreset(3)\">400</button>")
        sb.append("<button class=\"btn\" onclick=\"setISOPreset(4)\">800</button>")
        sb.append("<button class=\"btn\" onclick=\"setISOPreset(5)\">1600</button>")
        sb.append("<button class=\"btn\" onclick=\"setISOPreset(6)\">3200</button>")
        sb.append("</div></div>")

        // EV
        sb.append("<div class=\"card\" id=\"card-ev\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">☀️</span> Exposição (EV) <span class=\"badge-val\" id=\"ev-val\">0</span></div>")
        sb.append("<div class=\"slider-wrap\">")
        sb.append("<input type=\"range\" id=\"ev\" min=\"-8\" max=\"8\" value=\"0\" step=\"1\" oninput=\"updateEV(this.value)\">")
        sb.append("<div class=\"slider-labels\"><span>-8 (Escuro)</span><span>0</span><span>+8 (Claro)</span></div></div>")
        sb.append("<div class=\"btn-row\" style=\"margin-top:8px\">")
        sb.append("<button class=\"btn\" onclick=\"setEVPreset(-4)\">-4</button>")
        sb.append("<button class=\"btn\" onclick=\"setEVPreset(-2)\">-2</button>")
        sb.append("<button class=\"btn\" onclick=\"setEVPreset(0)\">±0</button>")
        sb.append("<button class=\"btn\" onclick=\"setEVPreset(2)\">+2</button>")
        sb.append("<button class=\"btn\" onclick=\"setEVPreset(4)\">+4</button>")
        sb.append("</div></div>")

        sb.append("</div>") // end page-exposure

        // ─── PAGE: IMAGEM ─────────────────────────────
        sb.append("<div class=\"page\" id=\"page-image\">")

        // Balanço de Branco
        sb.append("<div class=\"card\" id=\"card-wb\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">🎨</span> Balanço de Branco</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-wb\"></div>")
        sb.append("<div class=\"toggle-row\" style=\"margin-top:10px\">")
        sb.append("<span class=\"toggle-label\"><span class=\"ticon\">🔒</span> Travar AWB</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-awb-lock\" onchange=\"toggleAWBLock(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div></div>")

        // Flash
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">💡</span> Flash / Tocha</div>")
        sb.append("<div id=\"extras-flash\"><div class=\"btn-row\">")
        sb.append("<button class=\"btn\" onclick=\"sendControl({flash:'off'},this,'Flash Off')\">⚫ Off</button>")
        sb.append("<button class=\"btn\" onclick=\"sendControl({flash:'auto'},this,'Flash Auto')\">🔆 Auto</button>")
        sb.append("<button class=\"btn\" onclick=\"sendControl({flash:'on'},this,'Flash On')\">⚡ On</button>")
        sb.append("<button class=\"btn\" onclick=\"sendControl({flash:'torch'},this,'Tocha On')\">🔦 Tocha</button>")
        sb.append("</div></div></div>")

        sb.append("</div>") // end page-image

        // ─── PAGE: SISTEMA ────────────────────────────
        sb.append("<div class=\"page\" id=\"page-system\">")

        // Estabilização
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">🔧</span> Estabilização</div>")
        sb.append("<div class=\"toggle-row\">")
        sb.append("<span class=\"toggle-label\"><span class=\"ticon\">🌀</span> OIS — Estabilização Óptica</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-ois\" onchange=\"toggleOIS(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div>")
        sb.append("<div class=\"toggle-row\">")
        sb.append("<span class=\"toggle-label\"><span class=\"ticon\">📐</span> EIS — Estabilização Digital</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-eis\" onchange=\"toggleEIS(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div></div>")

        // Info do sistema
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"icon\">ℹ️</span> Informações do Sistema</div>")
        sb.append("<div class=\"stream-status-card\" style=\"background:transparent;border:none;padding:0;margin:0\">")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">CÂMERA ATIVA</div><div class=\"stat-val\" id=\"info-cam\">-</div></div>")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">RESOLUÇÃO</div><div class=\"stat-val\" id=\"info-res\">-</div></div>")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">BITRATE</div><div class=\"stat-val\" id=\"info-br\">-</div></div>")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">LATÊNCIA</div><div class=\"stat-val\" id=\"info-lat\">-</div></div>")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">ISO ATUAL</div><div class=\"stat-val\" id=\"info-iso\">-</div></div>")
        sb.append("<div class=\"stat-item\"><div class=\"stat-lbl\">STREAM</div><div class=\"stat-val\" id=\"info-stream\">-</div></div>")
        sb.append("</div></div>")

        // Versão
        sb.append("<div style=\"text-align:center;padding:16px 0 4px;color:var(--muted);font-size:10px;\">")
        sb.append("Camera2 API · RootEncoder 2.6.1 · RTMP · MediaMTX · v5-ui</div>")

        sb.append("</div>") // end page-system

        sb.append("</div>") // end .main

        // ═══════════════════════════════════════════
        //  BOTTOM NAVIGATION
        // ═══════════════════════════════════════════
        sb.append("<nav class=\"bottom-nav\" id=\"bottom-nav\">")
        sb.append("<button class=\"nav-btn active\" data-page=\"page-stream\" onclick=\"navTo('page-stream',this)\">")
        sb.append("<span class=\"nav-icon\">📡</span><span>Stream</span></button>")
        sb.append("<button class=\"nav-btn\" data-page=\"page-camera\" onclick=\"navTo('page-camera',this)\">")
        sb.append("<span class=\"nav-icon\">📷</span><span>Câmera</span></button>")
        sb.append("<button class=\"nav-btn\" data-page=\"page-exposure\" onclick=\"navTo('page-exposure',this)\">")
        sb.append("<span class=\"nav-icon\">☀️</span><span>Exposição</span></button>")
        sb.append("<button class=\"nav-btn\" data-page=\"page-image\" onclick=\"navTo('page-image',this)\">")
        sb.append("<span class=\"nav-icon\">🎨</span><span>Imagem</span></button>")
        sb.append("<button class=\"nav-btn\" data-page=\"page-system\" onclick=\"navTo('page-system',this)\">")
        sb.append("<span class=\"nav-icon\">⚙️</span><span>Sistema</span></button>")
        sb.append("</nav>")

        sb.append("<div id=\"toast\" class=\"ok\">OK</div>")

        // ═══════════════════════════════════════════
        //  JAVASCRIPT
        // ═══════════════════════════════════════════
        sb.append("<script>")

        // Navigation
        sb.append("function navTo(pageId,btn){")
        sb.append("document.querySelectorAll('.page').forEach(function(p){p.classList.remove('active');});")
        sb.append("document.getElementById(pageId).classList.add('active');")
        sb.append("document.querySelectorAll('.nav-btn').forEach(function(b){b.classList.remove('active');});")
        sb.append("if(btn)btn.classList.add('active');}")

        // Toast
        sb.append("var _toastTimer;")
        sb.append("function showToast(m,e){var t=document.getElementById('toast');t.textContent=m;")
        sb.append("t.className=e?'err show':'ok show';clearTimeout(_toastTimer);")
        sb.append("_toastTimer=setTimeout(function(){t.classList.remove('show');},1800);}")

        // Send control
        sb.append("function sendControl(d,btn,msg){")
        sb.append("fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(d)})")
        sb.append(".then(function(r){if(!r.ok)throw new Error(r.status);return r.json();})")
        sb.append(".then(function(){showToast(msg||'OK',false);})")
        sb.append(".catch(function(e){showToast('ERR:'+e.message,true);});}")

        // Mark active
        sb.append("function markActive(attr,val){")
        sb.append("document.querySelectorAll('['+attr+']').forEach(function(el){")
        sb.append("el.classList.toggle('active',el.getAttribute(attr)===String(val));});}")

        // RTMP
        sb.append("function applyRtmpUrl(){var v=document.getElementById('rtmp-url').value.trim();")
        sb.append("if(v)sendControl({rtmpUrl:v},null,'URL salva');}")

        // Bitrate
        sb.append("var _brT;")
        sb.append("function updateBitrate(v){document.getElementById('br-value').textContent=v+' kbps';")
        sb.append("clearTimeout(_brT);_brT=setTimeout(function(){sendControl({bitrate:+v},null,v+'kbps');},400);}")
        sb.append("function setBitratePreset(v){document.getElementById('bitrate').value=v;")
        sb.append("document.getElementById('br-value').textContent=v+' kbps';sendControl({bitrate:v},null,v+'kbps');}")

        // Zoom
        sb.append("var _zT;")
        sb.append("function updateZoom(v){var m=(1+parseFloat(v)*7).toFixed(1);")
        sb.append("document.getElementById('zoom-val').textContent=m+'x';")
        sb.append("clearTimeout(_zT);_zT=setTimeout(function(){sendControl({zoom:parseFloat(v)},null,'Zoom '+m+'x');},150);}")
        sb.append("function setZoomPreset(v){document.getElementById('zoom').value=v;updateZoom(v);}")

        // Focus
        sb.append("var _fT;")
        sb.append("function updateFocus(v){var f=parseFloat(v);")
        sb.append("document.getElementById('focus-val').textContent=f===0?'Auto':f.toFixed(1)+'D';")
        sb.append("clearTimeout(_fT);_fT=setTimeout(function(){sendControl({focus:f/10},null,f===0?'Auto':f.toFixed(1)+'D');},200);}")
        sb.append("function triggerAF(btn){sendControl({afTrigger:true},btn,'AF disparado');}")

        // ISO
        sb.append("var ISO_LIST=[50,100,200,400,800,1600,3200];")
        sb.append("var _iT;")
        sb.append("function updateISO(v){var iso=ISO_LIST[Math.min(+v,ISO_LIST.length-1)];")
        sb.append("document.getElementById('iso-val').textContent=iso;")
        sb.append("clearTimeout(_iT);_iT=setTimeout(function(){sendControl({iso:iso},null,'ISO '+iso);},350);}")
        sb.append("function setISOPreset(idx){document.getElementById('iso').value=idx;updateISO(idx);}")

        // EV
        sb.append("var _eT;")
        sb.append("function updateEV(v){var n=+v;document.getElementById('ev-val').textContent=(n>0?'+':'')+n;")
        sb.append("clearTimeout(_eT);_eT=setTimeout(function(){sendControl({exposure:n},null,'EV '+(n>0?'+':'')+n);},300);}")
        sb.append("function setEVPreset(v){document.getElementById('ev').value=v;updateEV(v);}")

        // Toggles
        sb.append("function toggleManual(c){sendControl({manualSensor:c.checked},null,c.checked?'Manual ON':'Auto');}")
        sb.append("function toggleOIS(c){sendControl({ois:c.checked},null,c.checked?'OIS ON':'OIS OFF');}")
        sb.append("function toggleEIS(c){sendControl({eis:c.checked},null,c.checked?'EIS ON':'EIS OFF');}")
        sb.append("function toggleAELock(c){sendControl({aeLock:c.checked},null,c.checked?'AE Travado':'AE Livre');}")
        sb.append("function toggleAWBLock(c){sendControl({awbLock:c.checked},null,c.checked?'AWB Travado':'AWB Livre');}")

        // Camera / Resolution / FPS / WB / AF modes
        sb.append("function switchCamera(id){markActive('data-cam',id);sendControl({camera:id},null,'Cam '+id);}")
        sb.append("function setResolution(res,btn){markActive('data-res',res);sendControl({resolution:res},btn,res);}")
        sb.append("function setFPS(fps,btn){markActive('data-fps',fps);sendControl({fps:fps},btn,fps+' fps');}")
        sb.append("function setFocusMode(m){markActive('data-fm',m);sendControl({focusmode:m},null,'Foco '+m);}")

        // Poll status
        sb.append("var _pollFail=0;")
        sb.append("function pollStatus(){var t0=Date.now();")
        sb.append("fetch('/api/status').then(function(r){return r.json();}).then(function(d){")
        sb.append("var lat=Date.now()-t0;var c=d.curvals||{};_pollFail=0;")
        sb.append("var streaming=d.streaming;")
        sb.append("document.getElementById('dot-stream').className='dot'+(streaming?' live':'');")
        sb.append("document.getElementById('lbl-stream').textContent=streaming?'AO VIVO':'Off';")
        sb.append("document.getElementById('lbl-cam').textContent=c.camera_id||'-';")
        sb.append("document.getElementById('lbl-res').textContent=c.video_size||'-';")
        sb.append("document.getElementById('lbl-br').textContent=c.bitrate_kbps||'-';")
        sb.append("document.getElementById('lbl-lat').textContent=lat+'ms';")
        sb.append("document.getElementById('lbl-status-full').textContent=streaming?('RTMP: '+d.rtmp_url):'Parado';")
        // info page
        sb.append("document.getElementById('info-cam').textContent=c.camera_id||'-';")
        sb.append("document.getElementById('info-res').textContent=c.video_size||'-';")
        sb.append("document.getElementById('info-br').textContent=(c.bitrate_kbps||'-')+' kbps';")
        sb.append("document.getElementById('info-lat').textContent=lat+'ms';")
        sb.append("document.getElementById('info-iso').textContent=c.iso||'-';")
        sb.append("document.getElementById('info-stream').textContent=streaming?'Ativo':'Inativo';")
        // sync inputs
        sb.append("var u=document.getElementById('rtmp-url');if(u&&d.rtmp_url&&document.activeElement!==u)u.value=d.rtmp_url;")
        sb.append("var chkM=document.getElementById('toggle-manual');if(chkM)chkM.checked=c.manual_sensor==='on';")
        sb.append("var chkO=document.getElementById('toggle-ois');if(chkO)chkO.checked=c.ois==='on';")
        sb.append("var chkE=document.getElementById('toggle-eis');if(chkE)chkE.checked=c.eis==='on';")
        sb.append("var chkAE=document.getElementById('toggle-ae-lock');if(chkAE)chkAE.checked=c.ae_lock==='on';")
        sb.append("var chkAW=document.getElementById('toggle-awb-lock');if(chkAW)chkAW.checked=c.awb_lock==='on';")
        sb.append("if(c.bitrate_kbps){document.getElementById('br-value').textContent=c.bitrate_kbps+' kbps';")
        sb.append("var bs=document.getElementById('bitrate');if(bs&&document.activeElement!==bs)bs.value=c.bitrate_kbps;}")
        sb.append("}).catch(function(){_pollFail++;if(_pollFail>=3){")
        sb.append("document.getElementById('dot-stream').className='dot';")
        sb.append("document.getElementById('lbl-stream').textContent='Sem conexão';}});}")

        // Init capabilities
        sb.append("function initCapabilities(){fetch('/api/capabilities').then(function(r){return r.json();}).then(function(caps){")
        // cameras
        sb.append("var cg=document.getElementById('btngroup-camera');cg.innerHTML='';")
        sb.append("for(var i=0;i<caps.length;i++){(function(cap){if(cap.is_depth)return;")
        sb.append("var b=document.createElement('button');b.className='btn';b.setAttribute('data-cam',cap.camera_id);")
        sb.append("b.textContent=cap.name||('Cam '+cap.camera_id);b.onclick=function(){switchCamera(cap.camera_id);};cg.appendChild(b);})(caps[i]);}")
        // resolutions
        sb.append("var rg=document.getElementById('btngroup-resolution');rg.innerHTML='';")
        sb.append("var fc=null;for(var i=0;i<caps.length;i++){if(!caps[i].is_depth){fc=caps[i];break;}}")
        sb.append("if(fc&&fc.available_resolutions){")
        sb.append("var ps=[{res:'720p',key:'1280x720',lbl:'HD 720p'},{res:'1080p',key:'1920x1080',lbl:'FHD 1080p'},{res:'4k',key:'3840x2160',lbl:'4K UHD'}];")
        sb.append("for(var j=0;j<ps.length;j++){(function(p){")
        sb.append("var found=fc.available_resolutions.indexOf(p.key)>=0;if(!found)return;")
        sb.append("var b=document.createElement('button');b.className='btn';b.setAttribute('data-res',p.res);b.textContent=p.lbl;")
        sb.append("b.onclick=function(){setResolution(p.res,b);};rg.appendChild(b);})(ps[j]);}}")
        // fps
        sb.append("var fg=document.getElementById('btngroup-fps');fg.innerHTML='';")
        sb.append("[15,24,30,60].forEach(function(fps){var b=document.createElement('button');b.className='btn';")
        sb.append("b.setAttribute('data-fps',fps);b.textContent=fps+' fps';if(fps===30)b.classList.add('active');")
        sb.append("b.onclick=function(){setFPS(fps,b);};fg.appendChild(b);});")
        // white balance
        sb.append("var wg=document.getElementById('btngroup-wb');wg.innerHTML='';")
        sb.append("var fc2=caps[0];if(fc2&&fc2.supported_awb_modes){")
        sb.append("var wbN={'auto':'☁ Auto','daylight':'☀ Dia','cloudy':'🌥 Nublado','tungsten':'💡 Tungst','incandescent':'🕯 Incand','fluorescent':'🔬 Fluor'};")
        sb.append("fc2.supported_awb_modes.forEach(function(mode){var b=document.createElement('button');b.className='btn';")
        sb.append("b.setAttribute('data-wb',mode);b.textContent=wbN[mode]||mode;")
        sb.append("b.onclick=function(){markActive('data-wb',mode);sendControl({whiteBalance:mode},b,'WB '+mode);};")
        sb.append("wg.appendChild(b);});}")
        // AF modes
        sb.append("var fc3=caps[0];if(fc3&&fc3.supported_af_modes){")
        sb.append("var fg2=document.getElementById('btngroup-focusmode');fg2.innerHTML='';")
        sb.append("var afN={'off':'🔒 Travar','auto':'🎯 Auto','continuous-video':'🎬 Contínuo','continuous-picture':'📸 Cont.Foto'};")
        sb.append("fc3.supported_af_modes.forEach(function(mode){var b=document.createElement('button');b.className='btn';")
        sb.append("b.setAttribute('data-fm',mode);b.textContent=afN[mode]||mode;")
        sb.append("b.onclick=function(){setFocusMode(mode);};fg2.appendChild(b);});}")
        sb.append("}).catch(function(){console.warn('capabilities indisponível');});}")

        sb.append("initCapabilities();pollStatus();setInterval(pollStatus,2000);")
        sb.append("</script></body></html>")
        return sb.toString()
    }
}
