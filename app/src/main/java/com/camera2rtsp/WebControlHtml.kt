package com.camera2rtsp

object WebControlHtml {
    fun build(): String {
        val sb = StringBuilder()

        // ════════════════════════════════════════
        //  HEAD
        // ════════════════════════════════════════
        sb.append("<!DOCTYPE html><html lang=\"pt-BR\"><head>")
        sb.append("<meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">")
        sb.append("<title>Camera2 Control v5</title>")
        sb.append("<style>")

        // ── Variáveis ──────────────────────────────────────────────────
        sb.append(":root{")
        sb.append("--bg:#070d1a;--surface:rgba(255,255,255,.055);--surface2:rgba(255,255,255,.09);")
        sb.append("--border:rgba(255,255,255,.10);--accent:#38bdf8;--accent2:#818cf8;")
        sb.append("--green:#10b981;--red:#f43f5e;--yellow:#fbbf24;")
        sb.append("--text:#f1f5f9;--muted:#64748b;")
        sb.append("--sidebar-w:260px;--header-h:56px;}")

        // ── Reset ──────────────────────────────────────────────────────
        sb.append("*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}")
        sb.append("html,body{height:100%;font-size:14px;")
        sb.append("font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;")
        sb.append("background:var(--bg);color:var(--text);}")
        sb.append("body{background-image:")
        sb.append("radial-gradient(ellipse 90% 50% at 50% -10%,rgba(56,189,248,.13),transparent),")
        sb.append("radial-gradient(ellipse 60% 50% at 90% 90%,rgba(129,140,248,.09),transparent);}")

        // ── Overlay (fundo escuro ao abrir sidebar) ────────────────────
        sb.append(".overlay{position:fixed;inset:0;background:rgba(0,0,0,.55);z-index:300;")
        sb.append("opacity:0;pointer-events:none;transition:opacity .28s;}")
        sb.append(".overlay.open{opacity:1;pointer-events:all;}")

        // ── Sidebar ────────────────────────────────────────────────────
        sb.append(".sidebar{position:fixed;top:0;left:0;height:100%;width:var(--sidebar-w);z-index:400;")
        sb.append("background:rgba(10,17,34,.97);border-right:1px solid var(--border);")
        sb.append("backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);")
        sb.append("transform:translateX(calc(-1 * var(--sidebar-w)));transition:transform .28s cubic-bezier(.4,0,.2,1);")
        sb.append("display:flex;flex-direction:column;overflow:hidden;}")
        sb.append(".sidebar.open{transform:translateX(0);}")

        // cabeçalho da sidebar
        sb.append(".sb-head{padding:20px 18px 14px;border-bottom:1px solid var(--border);}")
        sb.append(".sb-head h2{font-size:15px;font-weight:800;")
        sb.append("background:linear-gradient(90deg,var(--accent),var(--accent2));")
        sb.append("-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;}")
        sb.append(".sb-head p{font-size:10px;color:var(--muted);margin-top:2px;}")

        // itens de menu
        sb.append(".sb-menu{flex:1;overflow-y:auto;padding:10px 0;}")
        sb.append(".sb-item{display:flex;align-items:center;gap:12px;padding:13px 20px;cursor:pointer;")
        sb.append("border:none;background:transparent;color:var(--muted);width:100%;text-align:left;")
        sb.append("font-size:13px;font-weight:600;transition:background .15s,color .15s;border-left:3px solid transparent;}")
        sb.append(".sb-item .si{font-size:18px;line-height:1;flex-shrink:0;}")
        sb.append(".sb-item:hover{background:var(--surface);color:var(--text);}")
        sb.append(".sb-item.active{background:rgba(56,189,248,.1);color:var(--accent);")
        sb.append("border-left-color:var(--accent);}")
        sb.append(".sb-sep{height:1px;background:var(--border);margin:6px 16px;}")
        sb.append(".sb-label{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;")
        sb.append("color:var(--muted);padding:10px 20px 4px;}")

        // live badge na sidebar
        sb.append(".sb-live{display:flex;align-items:center;gap:8px;margin:10px 16px;")
        sb.append("background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:8px 12px;}")
        sb.append(".dot{width:8px;height:8px;border-radius:50%;background:var(--red);")
        sb.append("box-shadow:0 0 6px var(--red);flex-shrink:0;transition:all .4s;}")
        sb.append(".dot.live{background:var(--green);box-shadow:0 0 8px var(--green);animation:pulse 2s infinite;}")
        sb.append("@keyframes pulse{0%,100%{opacity:1}50%{opacity:.45}}")
        sb.append(".sb-live-txt{font-size:11px;font-weight:700;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}")

        // ── Header fixo ────────────────────────────────────────────────
        sb.append(".header{position:fixed;top:0;left:0;right:0;height:var(--header-h);z-index:200;")
        sb.append("backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);")
        sb.append("background:rgba(7,13,26,.88);border-bottom:1px solid var(--border);")
        sb.append("display:flex;align-items:center;gap:12px;padding:0 14px;}")
        sb.append(".hamburger{background:var(--surface);border:1px solid var(--border);")
        sb.append("border-radius:10px;width:38px;height:38px;cursor:pointer;")
        sb.append("display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;")
        sb.append("flex-shrink:0;transition:background .15s;}")
        sb.append(".hamburger:active{background:var(--surface2);}")
        sb.append(".hamburger span{display:block;width:18px;height:2px;background:var(--text);")
        sb.append("border-radius:2px;transition:all .25s;}")
        sb.append(".header-title{flex:1;min-width:0;}")
        sb.append(".header-title h1{font-size:14px;font-weight:800;")
        sb.append("background:linear-gradient(90deg,var(--accent),var(--accent2));")
        sb.append("-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;")
        sb.append("white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}")
        sb.append(".header-title .sub{font-size:10px;color:var(--muted);}")
        sb.append(".header-pills{display:flex;gap:5px;flex-shrink:0;}")
        sb.append(".pill{display:flex;align-items:center;gap:4px;background:var(--surface);")
        sb.append("border:1px solid var(--border);padding:4px 9px;border-radius:20px;")
        sb.append("font-size:10px;font-weight:600;white-space:nowrap;}")

        // ── Conteúdo principal ─────────────────────────────────────────
        sb.append(".main{padding:calc(var(--header-h) + 14px) 12px 28px;}")

        // seção
        sb.append(".section{display:none;}")
        sb.append(".section.active{display:block;}")
        sb.append(".section-header{display:flex;align-items:center;gap:10px;margin-bottom:14px;}")
        sb.append(".section-icon{font-size:22px;line-height:1;}")
        sb.append(".section-header h2{font-size:16px;font-weight:800;color:var(--text);}")
        sb.append(".section-header p{font-size:11px;color:var(--muted);margin-top:1px;}")

        // ── Cards ──────────────────────────────────────────────────────
        sb.append(".card{background:var(--surface);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);")
        sb.append("border:1px solid var(--border);border-radius:16px;padding:15px;margin-bottom:12px;}")
        sb.append(".card-title{display:flex;align-items:center;gap:8px;font-size:12px;")
        sb.append("font-weight:700;margin-bottom:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.06em;}")
        sb.append(".card-title .ci{font-size:14px;}")
        sb.append(".card-val{margin-left:auto;background:rgba(56,189,248,.12);color:var(--accent);")
        sb.append("font-size:12px;font-weight:700;padding:2px 10px;border-radius:7px;")
        sb.append("border:1px solid rgba(56,189,248,.22);text-transform:none;}")

        // ── Status grid ────────────────────────────────────────────────
        sb.append(".stat-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:12px;}")
        sb.append(".stat-cell{background:rgba(56,189,248,.06);border:1px solid rgba(56,189,248,.14);")
        sb.append("border-radius:12px;padding:10px 12px;}")
        sb.append(".stat-lbl{font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.07em;color:var(--muted);margin-bottom:3px;}")
        sb.append(".stat-val{font-size:14px;font-weight:700;color:var(--text);}")

        // ── Botões ─────────────────────────────────────────────────────
        sb.append(".btn{display:inline-flex;align-items:center;justify-content:center;gap:5px;")
        sb.append("background:var(--surface2);color:var(--text);border:1px solid var(--border);")
        sb.append("padding:9px 14px;border-radius:11px;cursor:pointer;font-weight:600;font-size:12px;")
        sb.append("transition:all .15s;touch-action:manipulation;user-select:none;min-height:40px;}")
        sb.append(".btn:active{transform:scale(.9);}")
        sb.append(".btn:hover{background:rgba(56,189,248,.18);border-color:var(--accent);color:var(--accent);}")
        sb.append(".btn.sel{background:rgba(56,189,248,.2);border-color:var(--accent);color:var(--accent);}")
        sb.append(".btn.on{background:rgba(16,185,129,.2);border-color:var(--green);color:var(--green);}")
        sb.append(".btn.off{background:rgba(244,63,94,.12);border-color:var(--red);color:var(--red);}")
        sb.append(".btn-full{width:100%;}")
        sb.append(".btn-row{display:flex;flex-wrap:wrap;gap:7px;}")

        // ── Slider ─────────────────────────────────────────────────────
        sb.append("input[type=range]{width:100%;height:4px;background:rgba(255,255,255,.14);")
        sb.append("border-radius:4px;outline:none;-webkit-appearance:none;margin:12px 0 2px;cursor:pointer;}")
        sb.append("input[type=range]::-webkit-slider-thumb{-webkit-appearance:none;width:26px;height:26px;")
        sb.append("background:linear-gradient(135deg,var(--accent),var(--accent2));cursor:grab;")
        sb.append("border-radius:50%;border:3px solid rgba(7,13,26,.9);box-shadow:0 2px 8px rgba(56,189,248,.45);}")
        sb.append(".s-labels{display:flex;justify-content:space-between;font-size:10px;color:var(--muted);margin-top:2px;}")

        // ── Toggle ─────────────────────────────────────────────────────
        sb.append(".trow{display:flex;align-items:center;justify-content:space-between;")
        sb.append("padding:11px 0;border-bottom:1px solid var(--border);}")
        sb.append(".trow:last-child{border-bottom:none;}")
        sb.append(".tlabel{font-size:13px;display:flex;align-items:center;gap:8px;}")
        sb.append(".ti{font-size:16px;}")
        sb.append(".switch{position:relative;display:inline-block;width:48px;height:27px;flex-shrink:0;}")
        sb.append(".switch input{opacity:0;width:0;height:0;}")
        sb.append(".sw{position:absolute;cursor:pointer;inset:0;background:rgba(255,255,255,.12);")
        sb.append("border-radius:27px;transition:.3s;border:1px solid var(--border);}")
        sb.append(".sw:before{content:'';position:absolute;height:21px;width:21px;left:3px;bottom:2px;")
        sb.append("background:#fff;border-radius:50%;transition:.3s;box-shadow:0 1px 4px rgba(0,0,0,.5);}")
        sb.append("input:checked+.sw{background:rgba(16,185,129,.4);border-color:var(--green);}")
        sb.append("input:checked+.sw:before{transform:translateX(21px);}")

        // ── Input texto ────────────────────────────────────────────────
        sb.append(".txt-input{width:100%;background:rgba(0,0,0,.35);color:var(--text);")
        sb.append("border:1px solid var(--border);border-radius:10px;padding:10px 13px;")
        sb.append("font-size:12px;font-family:monospace;outline:none;margin-top:8px;}")
        sb.append(".txt-input:focus{border-color:var(--accent);}")

        // ── Sub-label ─────────────────────────────────────────────────
        sb.append(".sub-label{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.07em;")
        sb.append("color:var(--muted);margin:12px 0 6px;}")

        // ── Toast ─────────────────────────────────────────────────────
        sb.append("#toast{position:fixed;bottom:22px;left:50%;")
        sb.append("transform:translateX(-50%) translateY(16px);")
        sb.append("padding:9px 20px;border-radius:22px;font-size:12px;font-weight:700;")
        sb.append("pointer-events:none;transition:transform .2s,opacity .2s;opacity:0;z-index:999;")
        sb.append("white-space:nowrap;backdrop-filter:blur(10px);}")
        sb.append("#toast.show{transform:translateX(-50%) translateY(0);opacity:1;}")
        sb.append("#toast.ok{background:rgba(16,185,129,.92);color:#fff;}")
        sb.append("#toast.err{background:rgba(244,63,94,.92);color:#fff;}")

        sb.append("</style></head><body>")

        // ════════════════════════════════════════
        //  OVERLAY
        // ════════════════════════════════════════
        sb.append("<div class=\"overlay\" id=\"overlay\" onclick=\"closeSidebar()\"></div>")

        // ════════════════════════════════════════
        //  SIDEBAR
        // ════════════════════════════════════════
        sb.append("<div class=\"sidebar\" id=\"sidebar\">")

        // cabeçalho sidebar
        sb.append("<div class=\"sb-head\">")
        sb.append("<h2>&#128247; Camera2 Control</h2>")
        sb.append("<p>Samsung Galaxy Note10+ &middot; v5-ui</p>")
        sb.append("</div>")

        // live status na sidebar
        sb.append("<div class=\"sb-live\">")
        sb.append("<span class=\"dot\" id=\"sb-dot\"></span>")
        sb.append("<span class=\"sb-live-txt\" id=\"sb-status\">Desconectado</span>")
        sb.append("</div>")

        // menu items
        sb.append("<div class=\"sb-menu\">")
        sb.append("<div class=\"sb-label\">Transmiss&#227;o</div>")
        sb.append("<button class=\"sb-item active\" id=\"menu-stream\" onclick=\"openSection('stream',this)\">")
        sb.append("<span class=\"si\">&#128225;</span> Stream RTMP</button>")

        sb.append("<div class=\"sb-sep\"></div>")
        sb.append("<div class=\"sb-label\">C&#226;mera</div>")
        sb.append("<button class=\"sb-item\" id=\"menu-camera\" onclick=\"openSection('camera',this)\">")
        sb.append("<span class=\"si\">&#128247;</span> C&#226;mera &amp; Lentes</button>")
        sb.append("<button class=\"sb-item\" id=\"menu-focus\" onclick=\"openSection('focus',this)\">")
        sb.append("<span class=\"si\">&#127919;</span> Zoom &amp; Foco</button>")

        sb.append("<div class=\"sb-sep\"></div>")
        sb.append("<div class=\"sb-label\">Exposi&#231;&#227;o</div>")
        sb.append("<button class=\"sb-item\" id=\"menu-exposure\" onclick=\"openSection('exposure',this)\">")
        sb.append("<span class=\"si\">&#9728;</span> ISO &amp; EV</button>")

        sb.append("<div class=\"sb-sep\"></div>")
        sb.append("<div class=\"sb-label\">Imagem</div>")
        sb.append("<button class=\"sb-item\" id=\"menu-image\" onclick=\"openSection('image',this)\">")
        sb.append("<span class=\"si\">&#127912;</span> Cor &amp; Ilumina&#231;&#227;o</button>")

        sb.append("<div class=\"sb-sep\"></div>")
        sb.append("<div class=\"sb-label\">Sistema</div>")
        sb.append("<button class=\"sb-item\" id=\"menu-system\" onclick=\"openSection('system',this)\">")
        sb.append("<span class=\"si\">&#9881;</span> Sistema &amp; Status</button>")
        sb.append("</div>") // sb-menu

        sb.append("</div>") // sidebar

        // ════════════════════════════════════════
        //  HEADER
        // ════════════════════════════════════════
        sb.append("<header class=\"header\">")
        sb.append("<button class=\"hamburger\" onclick=\"openSidebar()\" aria-label=\"Menu\">")
        sb.append("<span></span><span></span><span></span></button>")
        sb.append("<div class=\"header-title\">")
        sb.append("<h1 id=\"hdr-title\">&#128225; Stream RTMP</h1>")
        sb.append("<div class=\"sub\">Camera2 API &middot; Wi-Fi</div>")
        sb.append("</div>")
        sb.append("<div class=\"header-pills\">")
        sb.append("<div class=\"pill\"><span class=\"dot\" id=\"dot-stream\"></span><span id=\"lbl-stream\">Off</span></div>")
        sb.append("<div class=\"pill\">&#9889; <span id=\"lbl-lat\">--</span></div>")
        sb.append("</div>")
        sb.append("</header>")

        // ════════════════════════════════════════
        //  CONTEÚDO PRINCIPAL
        // ════════════════════════════════════════
        sb.append("<main class=\"main\">")

        // ─────────────────────────────────────────
        //  SEÇÃO: STREAM RTMP
        // ─────────────────────────────────────────
        sb.append("<div class=\"section active\" id=\"sec-stream\">")
        sb.append("<div class=\"section-header\">")
        sb.append("<span class=\"section-icon\">&#128225;</span>")
        sb.append("<div><h2>Stream RTMP</h2><p>Transmiss&#227;o, resolu&#231;&#227;o e bitrate</p></div>")
        sb.append("</div>")

        // status ao vivo
        sb.append("<div class=\"stat-grid\">")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">C&#226;mera Ativa</div><div class=\"stat-val\" id=\"lbl-cam\">-</div></div>")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">Resolu&#231;&#227;o</div><div class=\"stat-val\" id=\"lbl-res\">-</div></div>")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">Bitrate</div><div class=\"stat-val\"><span id=\"lbl-br\">-</span> kbps</div></div>")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">Endere&#231;o RTMP</div><div class=\"stat-val\" id=\"lbl-rtmp-short\" style=\"font-size:10px;word-break:break-all\">-</div></div>")
        sb.append("</div>")

        // URL + controles
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#128279;</span> Destino RTMP</div>")
        sb.append("<input class=\"txt-input\" id=\"rtmp-url\" type=\"text\" value=\"rtmp://192.168.1.100:1935/live/stream\" placeholder=\"rtmp://IP:1935/live/stream\">")
        sb.append("<div class=\"btn-row\" style=\"margin-top:10px\">")
        sb.append("<button class=\"btn\" onclick=\"applyRtmpUrl()\">&#128190; Salvar URL</button>")
        sb.append("</div></div>")

        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#9654;</span> Controle do Stream</div>")
        sb.append("<div class=\"btn-row\">")
        sb.append("<button class=\"btn on btn-full\" onclick=\"sendControl({streamAction:'start'},this,'Stream iniciado')\">&#9654; Iniciar Stream</button>")
        sb.append("</div>")
        sb.append("<div class=\"btn-row\" style=\"margin-top:8px\">")
        sb.append("<button class=\"btn off btn-full\" onclick=\"sendControl({streamAction:'stop'},this,'Stream parado')\">&#9209; Parar Stream</button>")
        sb.append("</div></div>")

        // Resolução
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#128251;</span> Resolu&#231;&#227;o de V&#237;deo</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-resolution\"></div>")
        sb.append("<div class=\"sub-label\">Taxa de Quadros</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-fps\"></div>")
        sb.append("</div>")

        // Bitrate
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#128246;</span> Bitrate <span class=\"card-val\" id=\"br-value\">4000 kbps</span></div>")
        sb.append("<input type=\"range\" id=\"bitrate\" min=\"500\" max=\"25000\" value=\"4000\" step=\"500\" oninput=\"updateBitrate(this.value)\">")
        sb.append("<div class=\"s-labels\"><span>500k</span><span>12M</span><span>25M</span></div>")
        sb.append("<div class=\"sub-label\">Presets R&#225;pidos</div>")
        sb.append("<div class=\"btn-row\">")
        sb.append("<button class=\"btn\" onclick=\"setBitrate(2000)\">2M</button>")
        sb.append("<button class=\"btn\" onclick=\"setBitrate(4000)\">4M</button>")
        sb.append("<button class=\"btn\" onclick=\"setBitrate(8000)\">8M</button>")
        sb.append("<button class=\"btn\" onclick=\"setBitrate(15000)\">15M</button>")
        sb.append("<button class=\"btn\" onclick=\"setBitrate(20000)\">20M</button>")
        sb.append("</div></div>")

        sb.append("</div>") // sec-stream

        // ─────────────────────────────────────────
        //  SEÇÃO: CÂMERA & LENTES
        // ─────────────────────────────────────────
        sb.append("<div class=\"section\" id=\"sec-camera\">")
        sb.append("<div class=\"section-header\">")
        sb.append("<span class=\"section-icon\">&#128247;</span>")
        sb.append("<div><h2>C&#226;mera &amp; Lentes</h2><p>Sele&#231;&#227;o de sensor e c&#226;mera</p></div>")
        sb.append("</div>")

        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#128247;</span> Sensores Dispon&#237;veis</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-camera\"><span style=\"color:var(--muted);font-size:12px\">Carregando...</span></div>")
        sb.append("</div>")

        sb.append("</div>") // sec-camera

        // ─────────────────────────────────────────
        //  SEÇÃO: ZOOM & FOCO
        // ─────────────────────────────────────────
        sb.append("<div class=\"section\" id=\"sec-focus\">")
        sb.append("<div class=\"section-header\">")
        sb.append("<span class=\"section-icon\">&#127919;</span>")
        sb.append("<div><h2>Zoom &amp; Foco</h2><p>&#211;ptica, AF e controle manual</p></div>")
        sb.append("</div>")

        // Zoom
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#128269;</span> Zoom <span class=\"card-val\" id=\"zoom-val\">1x</span></div>")
        sb.append("<input type=\"range\" id=\"zoom\" min=\"0\" max=\"1\" value=\"0\" step=\"0.01\" oninput=\"updateZoom(this.value)\">")
        sb.append("<div class=\"s-labels\"><span>1x (M&#237;nimo)</span><span>M&#225;ximo</span></div>")
        sb.append("<div class=\"sub-label\">Presets R&#225;pidos</div>")
        sb.append("<div class=\"btn-row\">")
        sb.append("<button class=\"btn\" onclick=\"setZoom(0)\">1x</button>")
        sb.append("<button class=\"btn\" onclick=\"setZoom(0.15)\">2x</button>")
        sb.append("<button class=\"btn\" onclick=\"setZoom(0.35)\">3x</button>")
        sb.append("<button class=\"btn\" onclick=\"setZoom(0.5)\">4x</button>")
        sb.append("<button class=\"btn\" onclick=\"setZoom(1.0)\">Max</button>")
        sb.append("</div></div>")

        // Foco
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#127919;</span> Dist&#226;ncia de Foco <span class=\"card-val\" id=\"focus-val\">Auto</span></div>")
        sb.append("<input type=\"range\" id=\"focus\" min=\"0\" max=\"10\" value=\"0\" step=\"0.1\" oninput=\"updateFocus(this.value)\">")
        sb.append("<div class=\"s-labels\"><span>Auto / &#8734;</span><span>Macro (10D)</span></div>")
        sb.append("<div class=\"sub-label\">Modo AF</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-focusmode\"></div>")
        sb.append("<button class=\"btn btn-full\" style=\"margin-top:10px\" onclick=\"triggerAF()\">&#127919; Disparar Auto Foco</button>")
        sb.append("</div>")

        sb.append("</div>") // sec-focus

        // ─────────────────────────────────────────
        //  SEÇÃO: ISO & EV (EXPOSIÇÃO)
        // ─────────────────────────────────────────
        sb.append("<div class=\"section\" id=\"sec-exposure\">")
        sb.append("<div class=\"section-header\">")
        sb.append("<span class=\"section-icon\">&#9728;</span>")
        sb.append("<div><h2>ISO &amp; Exposi&#231;&#227;o</h2><p>Sensibilidade, EV e controle manual</p></div>")
        sb.append("</div>")

        // Modo de controle
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#127895;</span> Modo de Controle</div>")
        sb.append("<div class=\"trow\">")
        sb.append("<span class=\"tlabel\"><span class=\"ti\">&#129504;</span> Sensor Manual (ISO + SS)</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-manual\" onchange=\"toggleManual(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div>")
        sb.append("<div class=\"trow\">")
        sb.append("<span class=\"tlabel\"><span class=\"ti\">&#128274;</span> Travar AE (Auto Exposure)</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-ae-lock\" onchange=\"toggleAELock(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div></div>")

        // ISO
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#128248;</span> ISO <span class=\"card-val\" id=\"iso-val\">50</span></div>")
        sb.append("<input type=\"range\" id=\"iso\" min=\"0\" max=\"6\" value=\"0\" step=\"1\" oninput=\"updateISO(this.value)\">")
        sb.append("<div class=\"s-labels\"><span>50</span><span>800</span><span>3200</span></div>")
        sb.append("<div class=\"sub-label\">Valores Fixos</div>")
        sb.append("<div class=\"btn-row\">")
        sb.append("<button class=\"btn\" onclick=\"setISO(0)\">50</button>")
        sb.append("<button class=\"btn\" onclick=\"setISO(1)\">100</button>")
        sb.append("<button class=\"btn\" onclick=\"setISO(2)\">200</button>")
        sb.append("<button class=\"btn\" onclick=\"setISO(3)\">400</button>")
        sb.append("<button class=\"btn\" onclick=\"setISO(4)\">800</button>")
        sb.append("<button class=\"btn\" onclick=\"setISO(5)\">1600</button>")
        sb.append("<button class=\"btn\" onclick=\"setISO(6)\">3200</button>")
        sb.append("</div></div>")

        // EV
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#9728;&#65039;</span> Compensa&#231;&#227;o de Exposi&#231;&#227;o (EV) <span class=\"card-val\" id=\"ev-val\">&#177;0</span></div>")
        sb.append("<input type=\"range\" id=\"ev\" min=\"-8\" max=\"8\" value=\"0\" step=\"1\" oninput=\"updateEV(this.value)\">")
        sb.append("<div class=\"s-labels\"><span>-8 Escuro</span><span>0</span><span>+8 Claro</span></div>")
        sb.append("<div class=\"sub-label\">Presets R&#225;pidos</div>")
        sb.append("<div class=\"btn-row\">")
        sb.append("<button class=\"btn\" onclick=\"setEV(-6)\">-6</button>")
        sb.append("<button class=\"btn\" onclick=\"setEV(-4)\">-4</button>")
        sb.append("<button class=\"btn\" onclick=\"setEV(-2)\">-2</button>")
        sb.append("<button class=\"btn\" onclick=\"setEV(0)\">&#177;0</button>")
        sb.append("<button class=\"btn\" onclick=\"setEV(2)\">+2</button>")
        sb.append("<button class=\"btn\" onclick=\"setEV(4)\">+4</button>")
        sb.append("<button class=\"btn\" onclick=\"setEV(6)\">+6</button>")
        sb.append("</div></div>")

        sb.append("</div>") // sec-exposure

        // ─────────────────────────────────────────
        //  SEÇÃO: COR & ILUMINAÇÃO
        // ─────────────────────────────────────────
        sb.append("<div class=\"section\" id=\"sec-image\">")
        sb.append("<div class=\"section-header\">")
        sb.append("<span class=\"section-icon\">&#127912;</span>")
        sb.append("<div><h2>Cor &amp; Ilumina&#231;&#227;o</h2><p>Balan&#231;o de branco, flash e tocha</p></div>")
        sb.append("</div>")

        // White Balance
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#127755;</span> Balan&#231;o de Branco (WB)</div>")
        sb.append("<div class=\"btn-row\" id=\"btngroup-wb\"></div>")
        sb.append("<div class=\"trow\" style=\"margin-top:10px\">")
        sb.append("<span class=\"tlabel\"><span class=\"ti\">&#128274;</span> Travar AWB</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-awb-lock\" onchange=\"toggleAWBLock(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div></div>")

        // Flash
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#128161;</span> Flash &amp; Tocha</div>")
        sb.append("<div class=\"btn-row\">")
        sb.append("<button class=\"btn\" onclick=\"sendControl({flash:'off'},this,'Flash Off')\">&#9899; Off</button>")
        sb.append("<button class=\"btn\" onclick=\"sendControl({flash:'auto'},this,'Flash Auto')\">&#128262; Auto</button>")
        sb.append("<button class=\"btn\" onclick=\"sendControl({flash:'on'},this,'Flash Ligado')\">&#9889; On</button>")
        sb.append("<button class=\"btn\" onclick=\"sendControl({flash:'torch'},this,'Tocha Ligada')\">&#128294; Tocha</button>")
        sb.append("</div></div>")

        sb.append("</div>") // sec-image

        // ─────────────────────────────────────────
        //  SEÇÃO: SISTEMA & STATUS
        // ─────────────────────────────────────────
        sb.append("<div class=\"section\" id=\"sec-system\">")
        sb.append("<div class=\"section-header\">")
        sb.append("<span class=\"section-icon\">&#9881;</span>")
        sb.append("<div><h2>Sistema &amp; Status</h2><p>Estabiliza&#231;&#227;o e diagn&#243;stico</p></div>")
        sb.append("</div>")

        // Estabilização
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#128307;</span> Estabiliza&#231;&#227;o de Imagem</div>")
        sb.append("<div class=\"trow\">")
        sb.append("<span class=\"tlabel\"><span class=\"ti\">&#127744;</span> OIS &#8212; Estabiliza&#231;&#227;o &#211;ptica</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-ois\" onchange=\"toggleOIS(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div>")
        sb.append("<div class=\"trow\">")
        sb.append("<span class=\"tlabel\"><span class=\"ti\">&#128208;</span> EIS &#8212; Estabiliza&#231;&#227;o Digital</span>")
        sb.append("<label class=\"switch\"><input type=\"checkbox\" id=\"toggle-eis\" onchange=\"toggleEIS(this)\"><span class=\"sw\"></span></label>")
        sb.append("</div></div>")

        // Diagnóstico
        sb.append("<div class=\"card\">")
        sb.append("<div class=\"card-title\"><span class=\"ci\">&#8505;</span> Diagn&#243;stico em Tempo Real</div>")
        sb.append("<div class=\"stat-grid\">")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">C&#226;mera</div><div class=\"stat-val\" id=\"diag-cam\">-</div></div>")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">Resolu&#231;&#227;o</div><div class=\"stat-val\" id=\"diag-res\">-</div></div>")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">Bitrate</div><div class=\"stat-val\" id=\"diag-br\">-</div></div>")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">Lat&#234;ncia</div><div class=\"stat-val\" id=\"diag-lat\">-</div></div>")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">ISO</div><div class=\"stat-val\" id=\"diag-iso\">-</div></div>")
        sb.append("<div class=\"stat-cell\"><div class=\"stat-lbl\">Stream</div><div class=\"stat-val\" id=\"diag-stream\">-</div></div>")
        sb.append("</div></div>")

        sb.append("<p style=\"text-align:center;color:var(--muted);font-size:10px;padding:8px 0 4px\">")
        sb.append("Camera2 API &middot; RootEncoder 2.6.1 &middot; RTMP &middot; MediaMTX &middot; v5-ui</p>")

        sb.append("</div>") // sec-system

        sb.append("</main>")
        sb.append("<div id=\"toast\" class=\"ok\">OK</div>")

        // ════════════════════════════════════════
        //  JAVASCRIPT
        // ════════════════════════════════════════
        sb.append("<script>")

        // Sidebar
        sb.append("function openSidebar(){")
        sb.append("document.getElementById('sidebar').classList.add('open');")
        sb.append("document.getElementById('overlay').classList.add('open');}")
        sb.append("function closeSidebar(){")
        sb.append("document.getElementById('sidebar').classList.remove('open');")
        sb.append("document.getElementById('overlay').classList.remove('open');}")

        // Navegar para seção
        val titles = mapOf(
            "stream" to "&#128225; Stream RTMP",
            "camera" to "&#128247; C&#226;mera &amp; Lentes",
            "focus"  to "&#127919; Zoom &amp; Foco",
            "exposure" to "&#9728; ISO &amp; Exposi&#231;&#227;o",
            "image"  to "&#127912; Cor &amp; Ilumina&#231;&#227;o",
            "system" to "&#9881; Sistema &amp; Status"
        )
        sb.append("var SEC_TITLES={")
        sb.append(titles.entries.joinToString(",") { (k, v) -> "'$k':'$v'" })
        sb.append("};")

        sb.append("function openSection(id, btn){")
        sb.append("document.querySelectorAll('.section').forEach(function(s){s.classList.remove('active');});")
        sb.append("document.getElementById('sec-'+id).classList.add('active');")
        sb.append("document.querySelectorAll('.sb-item').forEach(function(b){b.classList.remove('active');});")
        sb.append("if(btn)btn.classList.add('active');")
        sb.append("var t=SEC_TITLES[id];if(t)document.getElementById('hdr-title').innerHTML=t;")
        sb.append("closeSidebar();}")

        // Toast
        sb.append("var _tt;function showToast(m,e){")
        sb.append("var t=document.getElementById('toast');t.textContent=m;")
        sb.append("t.className=e?'err show':'ok show';clearTimeout(_tt);")
        sb.append("_tt=setTimeout(function(){t.classList.remove('show');},1800);}")

        // Send
        sb.append("function sendControl(d,btn,msg){")
        sb.append("fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(d)})")
        sb.append(".then(function(r){if(!r.ok)throw new Error(r.status);return r.json();})")
        sb.append(".then(function(){showToast(msg||'OK',false);})")
        sb.append(".catch(function(e){showToast('ERR:'+e.message,true);});}")

        sb.append("function markSel(attr,val){document.querySelectorAll('['+attr+']').forEach(function(el){el.classList.toggle('sel',el.getAttribute(attr)===String(val));});}")

        // RTMP
        sb.append("function applyRtmpUrl(){var v=document.getElementById('rtmp-url').value.trim();if(v)sendControl({rtmpUrl:v},null,'URL salva');}")

        // Bitrate
        sb.append("var _brT;")
        sb.append("function updateBitrate(v){document.getElementById('br-value').textContent=v+' kbps';clearTimeout(_brT);_brT=setTimeout(function(){sendControl({bitrate:+v},null,v+'kbps');},400);}")
        sb.append("function setBitrate(v){document.getElementById('bitrate').value=v;document.getElementById('br-value').textContent=v+' kbps';sendControl({bitrate:v},null,v+'kbps');}")

        // Zoom
        sb.append("var _zT;")
        sb.append("function updateZoom(v){var m=(1+parseFloat(v)*7).toFixed(1);document.getElementById('zoom-val').textContent=m+'x';clearTimeout(_zT);_zT=setTimeout(function(){sendControl({zoom:parseFloat(v)},null,'Zoom '+m+'x');},150);}")
        sb.append("function setZoom(v){document.getElementById('zoom').value=v;updateZoom(v);}")

        // Foco
        sb.append("var _fT;")
        sb.append("function updateFocus(v){var f=parseFloat(v);document.getElementById('focus-val').textContent=f===0?'Auto':f.toFixed(1)+'D';clearTimeout(_fT);_fT=setTimeout(function(){sendControl({focus:f/10},null,f===0?'Auto':f.toFixed(1)+'D');},200);}")
        sb.append("function triggerAF(){sendControl({afTrigger:true},null,'AF disparado');}")

        // ISO
        sb.append("var ISO_LIST=[50,100,200,400,800,1600,3200];var _iT;")
        sb.append("function updateISO(v){var iso=ISO_LIST[Math.min(+v,6)];document.getElementById('iso-val').textContent=iso;clearTimeout(_iT);_iT=setTimeout(function(){sendControl({iso:iso},null,'ISO '+iso);},350);}")
        sb.append("function setISO(idx){document.getElementById('iso').value=idx;updateISO(idx);}")

        // EV
        sb.append("var _eT;")
        sb.append("function updateEV(v){var n=+v;document.getElementById('ev-val').textContent=(n>0?'+':n===0?'\u00b1':'')+n;clearTimeout(_eT);_eT=setTimeout(function(){sendControl({exposure:n},null,'EV '+(n>0?'+':'')+n);},300);}")
        sb.append("function setEV(v){document.getElementById('ev').value=v;updateEV(v);}")

        // Toggles
        sb.append("function toggleManual(c){sendControl({manualSensor:c.checked},null,c.checked?'Manual ON':'Auto');}")
        sb.append("function toggleOIS(c){sendControl({ois:c.checked},null,c.checked?'OIS ON':'OIS OFF');}")
        sb.append("function toggleEIS(c){sendControl({eis:c.checked},null,c.checked?'EIS ON':'EIS OFF');}")
        sb.append("function toggleAELock(c){sendControl({aeLock:c.checked},null,c.checked?'AE Travado':'AE Livre');}")
        sb.append("function toggleAWBLock(c){sendControl({awbLock:c.checked},null,c.checked?'AWB Travado':'AWB Livre');}")

        // Camera / Resolution / FPS / WB / AF
        sb.append("function switchCamera(id){markSel('data-cam',id);sendControl({camera:id},null,'Cam '+id);}")
        sb.append("function setResolution(res){markSel('data-res',res);sendControl({resolution:res},null,res);}")
        sb.append("function setFPS(fps){markSel('data-fps',fps);sendControl({fps:fps},null,fps+' fps');}")
        sb.append("function setFocusMode(m){markSel('data-fm',m);sendControl({focusmode:m},null,'Foco: '+m);}")

        // Poll status
        sb.append("var _pf=0;")
        sb.append("function pollStatus(){var t0=Date.now();")
        sb.append("fetch('/api/status').then(function(r){return r.json();}).then(function(d){")
        sb.append("var lat=Date.now()-t0;var c=d.curvals||{};_pf=0;var on=d.streaming;")
        sb.append("var dotCls='dot'+(on?' live':'');")
        sb.append("document.getElementById('dot-stream').className=dotCls;")
        sb.append("document.getElementById('sb-dot').className=dotCls;")
        sb.append("document.getElementById('lbl-stream').textContent=on?'AO VIVO':'Off';")
        sb.append("document.getElementById('sb-status').textContent=on?('RTMP '+d.rtmp_url):'Desconectado';")
        sb.append("document.getElementById('lbl-cam').textContent=c.camera_id||'-';")
        sb.append("document.getElementById('lbl-res').textContent=c.video_size||'-';")
        sb.append("document.getElementById('lbl-br').textContent=c.bitrate_kbps||'-';")
        sb.append("document.getElementById('lbl-lat').textContent=lat+'ms';")
        sb.append("var rs=d.rtmp_url||'-';document.getElementById('lbl-rtmp-short').textContent=rs;")
        // diag page
        sb.append("document.getElementById('diag-cam').textContent=c.camera_id||'-';")
        sb.append("document.getElementById('diag-res').textContent=c.video_size||'-';")
        sb.append("document.getElementById('diag-br').textContent=(c.bitrate_kbps||'-')+' kbps';")
        sb.append("document.getElementById('diag-lat').textContent=lat+'ms';")
        sb.append("document.getElementById('diag-iso').textContent=c.iso||'-';")
        sb.append("document.getElementById('diag-stream').textContent=on?'Ativo':'Inativo';")
        // sync inputs
        sb.append("var u=document.getElementById('rtmp-url');if(u&&d.rtmp_url&&document.activeElement!==u)u.value=d.rtmp_url;")
        sb.append("var el;")
        sb.append("el=document.getElementById('toggle-manual');if(el)el.checked=c.manual_sensor==='on';")
        sb.append("el=document.getElementById('toggle-ois');if(el)el.checked=c.ois==='on';")
        sb.append("el=document.getElementById('toggle-eis');if(el)el.checked=c.eis==='on';")
        sb.append("el=document.getElementById('toggle-ae-lock');if(el)el.checked=c.ae_lock==='on';")
        sb.append("el=document.getElementById('toggle-awb-lock');if(el)el.checked=c.awb_lock==='on';")
        sb.append("el=document.getElementById('bitrate');if(c.bitrate_kbps&&el&&document.activeElement!==el){el.value=c.bitrate_kbps;document.getElementById('br-value').textContent=c.bitrate_kbps+' kbps';}")
        sb.append("}).catch(function(){_pf++;if(_pf>=3){document.getElementById('dot-stream').className='dot';document.getElementById('sb-dot').className='dot';document.getElementById('lbl-stream').textContent='Sem conex\u00e3o';}});}")

        // Init capabilities
        sb.append("function initCapabilities(){fetch('/api/capabilities').then(function(r){return r.json();}).then(function(caps){")
        // câmeras
        sb.append("var cg=document.getElementById('btngroup-camera');cg.innerHTML='';")
        sb.append("caps.forEach(function(cap){if(cap.is_depth)return;")
        sb.append("var b=document.createElement('button');b.className='btn';b.setAttribute('data-cam',cap.camera_id);")
        sb.append("b.textContent=cap.name||('Cam '+cap.camera_id);")
        sb.append("b.onclick=function(){switchCamera(cap.camera_id);};cg.appendChild(b);});")
        // resoluções
        sb.append("var rg=document.getElementById('btngroup-resolution');rg.innerHTML='';")
        sb.append("var fc=caps.find(function(c){return !c.is_depth;})||null;")
        sb.append("if(fc&&fc.available_resolutions){")
        sb.append("[{res:'720p',key:'1280x720',lbl:'HD 720p'},{res:'1080p',key:'1920x1080',lbl:'FHD 1080p'},{res:'4k',key:'3840x2160',lbl:'4K UHD'}]")
        sb.append(".forEach(function(p){")
        sb.append("if(fc.available_resolutions.indexOf(p.key)<0)return;")
        sb.append("var b=document.createElement('button');b.className='btn';b.setAttribute('data-res',p.res);b.textContent=p.lbl;")
        sb.append("b.onclick=function(){setResolution(p.res);};rg.appendChild(b);});}")
        // FPS
        sb.append("var fg=document.getElementById('btngroup-fps');fg.innerHTML='';")
        sb.append("[15,24,30,60].forEach(function(fps){")
        sb.append("var b=document.createElement('button');b.className='btn';b.setAttribute('data-fps',fps);")
        sb.append("b.textContent=fps+' fps';if(fps===30)b.classList.add('sel');")
        sb.append("b.onclick=function(){setFPS(fps);};fg.appendChild(b);});")
        // WB
        sb.append("var wg=document.getElementById('btngroup-wb');wg.innerHTML='';")
        sb.append("var wbN={'auto':'Auto','daylight':'Dia','cloudy':'Nublado','tungsten':'Tungst.','incandescent':'Incand.','fluorescent':'Fluores.'};")
        sb.append("if(caps[0]&&caps[0].supported_awb_modes)caps[0].supported_awb_modes.forEach(function(mode){")
        sb.append("var b=document.createElement('button');b.className='btn';b.setAttribute('data-wb',mode);")
        sb.append("b.textContent=wbN[mode]||mode;")
        sb.append("b.onclick=function(){markSel('data-wb',mode);sendControl({whiteBalance:mode},null,'WB '+mode);};")
        sb.append("wg.appendChild(b);});")
        // AF modes
        sb.append("var fg2=document.getElementById('btngroup-focusmode');fg2.innerHTML='';")
        sb.append("var afN={'off':'Travar','auto':'Auto','continuous-video':'Cont. V\u00eddeo','continuous-picture':'Cont. Foto'};")
        sb.append("if(caps[0]&&caps[0].supported_af_modes)caps[0].supported_af_modes.forEach(function(mode){")
        sb.append("var b=document.createElement('button');b.className='btn';b.setAttribute('data-fm',mode);")
        sb.append("b.textContent=afN[mode]||mode;")
        sb.append("b.onclick=function(){setFocusMode(mode);};fg2.appendChild(b);});")
        sb.append("}).catch(function(){console.warn('capabilities indispon\u00edvel');});}")

        sb.append("initCapabilities();pollStatus();setInterval(pollStatus,2000);")
        sb.append("</script></body></html>")
        return sb.toString()
    }
}
