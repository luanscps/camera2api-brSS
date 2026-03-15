package com.camera2rtsp

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.AutoFitTextureView
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    // ── Views principais ──────────────────────────────────────────────────────
    private lateinit var cameraPreview: AutoFitTextureView
    private lateinit var gridOverlay: GridOverlayView
    private lateinit var topBar: View
    private lateinit var statusText: TextView
    private lateinit var rtspBadge: TextView
    private lateinit var clientsBadge: TextView
    private lateinit var batteryText: TextView
    private lateinit var hudLeft: View
    private lateinit var hudFps: TextView
    private lateinit var hudBitrate: TextView
    private lateinit var hudResolution: TextView
    private lateinit var bottomActions: View
    private lateinit var paramsBar: View
    private lateinit var settingsPanel: View

    // ── Barra de parâmetros (dials) ───────────────────────────────────────────
    private lateinit var paramIso: View
    private lateinit var paramShutter: View
    private lateinit var paramWb: View
    private lateinit var paramFocus: View
    private lateinit var paramZoom: View

    // ── Roda de ajuste ────────────────────────────────────────────────────────
    private var wheelContainer: View? = null

    // ── Bottom actions ────────────────────────────────────────────────────────
    private lateinit var btnShutter: View
    private lateinit var btnFlashToggle: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnLens1x: TextView
    private lateinit var btnLens05x: TextView
    private lateinit var btnLens3x: TextView

    // ── Painel de configurações ───────────────────────────────────────────────
    private lateinit var btnClosePanel: ImageButton
    private lateinit var spinResolution: Spinner
    private lateinit var spinFrameRate: Spinner
    private lateinit var spinBitrate: Spinner
    private lateinit var switchOis: Switch
    private lateinit var switchGrid: Switch
    private lateinit var switchHud: Switch
    private lateinit var switchAudio: Switch
    private lateinit var seekMicGain: SeekBar

    // ── Estado ────────────────────────────────────────────────────────────────
    private var isPanelOpen    = false
    private var isFlashOn      = false
    private var isStreaming    = false
    private var cameraFacing   = CameraHelper.Facing.BACK
    private var activeParamTag = ""

    // ── Valores dos parâmetros ────────────────────────────────────────────────
    private val isoSteps     = arrayOf("AUTO","50","100","200","400","800","1600","3200")
    private val shutterSteps = arrayOf("AUTO","1/8000","1/4000","1/2000","1/1000","1/500","1/250","1/125","1/60","1/30","1/15")
    private val wbSteps      = arrayOf("AUTO","2700K","3200K","4200K","5600K","6500K","7500K")
    private val focusSteps   = arrayOf("AUTO","0.1","0.2","0.3","0.4","0.5","0.6","0.7","0.8","0.9","1.0")
    private val zoomSteps    = arrayOf("1×","1.5×","2×","3×","4×","5×")
    private var isoIdx = 0; private var shutterIdx = 0; private var wbIdx = 0
    private var focusIdx = 0; private var zoomIdx = 0

    // ── HUD ticker ────────────────────────────────────────────────────────────
    private val hudHandler  = Handler(Looper.getMainLooper())
    private val hudRunnable = object : Runnable {
        override fun run() { tickHud(); hudHandler.postDelayed(this, 1000) }
    }
    private val permissionCode = 100

    // ── SurfaceTexture listener ────────────────────────────────────────────────
    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
            // Surface disponível — tenta iniciar preview imediatamente;
            // se o serviço ainda não iniciou, agenda um retry.
            tryStartPreview()
        }
        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            StreamingService.instance?.stopPreview()
            return true
        }
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupDials()
        setupBottomActions()
        setupSettingsPanel()
        setupGestures()
        if (checkPermissions()) startStreamingService() else requestPermissions()
        hudHandler.post(hudRunnable)
    }

    override fun onResume() {
        super.onResume()
        // Surface já pode estar disponível (volta de onPause); tenta iniciar preview.
        // tryStartPreview usa retry interno caso o serviço ainda esteja inicializando.
        if (cameraPreview.isAvailable) tryStartPreview()
        refreshStatusBar()
    }

    override fun onPause() {
        super.onPause()
        StreamingService.instance?.stopPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        hudHandler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig) }

    // ── Inicia preview com retry caso o serviço ainda esteja inicializando ─────

    private fun tryStartPreview(retries: Int = 10) {
        val svc = StreamingService.instance
        if (svc != null && cameraPreview.isAvailable) {
            svc.startPreview(cameraPreview, cameraFacing)
        } else if (retries > 0) {
            // Serviço ainda não está pronto — aguarda 200ms e tenta novamente
            hudHandler.postDelayed({ tryStartPreview(retries - 1) }, 200)
        }
    }

    // ── Bind views ─────────────────────────────────────────────────────────────

    private fun bindViews() {
        cameraPreview  = findViewById(R.id.cameraPreview)
        gridOverlay    = findViewById(R.id.gridOverlay)
        topBar         = findViewById(R.id.topBar)
        statusText     = findViewById(R.id.statusText)
        rtspBadge      = findViewById(R.id.rtspBadge)
        clientsBadge   = findViewById(R.id.clientsBadge)
        batteryText    = findViewById(R.id.batteryText)
        hudLeft        = findViewById(R.id.hudLeft)
        hudFps         = findViewById(R.id.hudFps)
        hudBitrate     = findViewById(R.id.hudBitrate)
        hudResolution  = findViewById(R.id.hudResolution)
        bottomActions  = findViewById(R.id.bottomActions)
        paramsBar      = findViewById(R.id.paramsBar)
        settingsPanel  = findViewById(R.id.settingsPanel)
        paramIso       = findViewById(R.id.paramIso)
        paramShutter   = findViewById(R.id.paramShutter)
        paramWb        = findViewById(R.id.paramWb)
        paramFocus     = findViewById(R.id.paramFocus)
        paramZoom      = findViewById(R.id.paramZoom)
        btnShutter     = findViewById(R.id.btnShutter)
        btnFlashToggle = findViewById(R.id.btnFlashToggle)
        btnSwitchCamera= findViewById(R.id.btnSwitchCamera)
        btnSettings    = findViewById(R.id.btnSettings)
        btnLens1x      = findViewById(R.id.btnLens1x)
        btnLens05x     = findViewById(R.id.btnLens05x)
        btnLens3x      = findViewById(R.id.btnLens3x)
        btnClosePanel  = settingsPanel.findViewById(R.id.btnClosePanel)
        spinResolution = settingsPanel.findViewById(R.id.spinResolution)
        spinFrameRate  = settingsPanel.findViewById(R.id.spinFrameRate)
        spinBitrate    = settingsPanel.findViewById(R.id.spinBitrate)
        switchOis      = settingsPanel.findViewById(R.id.switchOis)
        switchGrid     = settingsPanel.findViewById(R.id.switchGrid)
        switchHud      = settingsPanel.findViewById(R.id.switchHud)
        switchAudio    = settingsPanel.findViewById(R.id.switchAudio)
        seekMicGain    = settingsPanel.findViewById(R.id.seekMicGain)

        cameraPreview.surfaceTextureListener = surfaceListener

        setDialLabel(paramIso,     "ISO",   isoSteps[isoIdx])
        setDialLabel(paramShutter, "SS",    shutterSteps[shutterIdx])
        setDialLabel(paramWb,      "WB",    wbSteps[wbIdx])
        setDialLabel(paramFocus,   "FOCO",  focusSteps[focusIdx])
        setDialLabel(paramZoom,    "ZOOM",  zoomSteps[zoomIdx])
    }

    // ── Dials da barra de parâmetros ───────────────────────────────────────────

    private fun setupDials() {
        fun attachDial(view: View, tag: String, steps: Array<String>, getIdx: () -> Int, setIdx: (Int) -> Unit, onApply: (String) -> Unit) {
            view.setOnClickListener {
                if (activeParamTag == tag) { closeWheel(); return@setOnClickListener }
                openWheel(view, tag, steps, getIdx, setIdx, onApply)
            }
            view.setOnLongClickListener {
                if (steps[0] == "AUTO") {
                    val newIdx = if (getIdx() == 0) 4 else 0
                    setIdx(newIdx)
                    setDialLabel(view, view.tag.toString(), steps[newIdx])
                    onApply(steps[newIdx])
                }
                true
            }
            view.tag = tag
        }

        attachDial(paramIso,     "ISO",    isoSteps,     { isoIdx },     { isoIdx = it },     { v -> applyCamera("iso" to (v.toDoubleOrNull() ?: 0.0)) })
        attachDial(paramShutter, "SS",     shutterSteps, { shutterIdx }, { shutterIdx = it }, { v -> applyCamera("shutterSpeed" to v) })
        attachDial(paramWb,      "WB",     wbSteps,      { wbIdx },      { wbIdx = it },      { v -> applyCamera("whiteBalance" to v) })
        attachDial(paramFocus,   "FOCO",   focusSteps,   { focusIdx },   { focusIdx = it },   { v -> applyCamera("focus" to (v.replace("×","").toDoubleOrNull() ?: 0.0)) })
        attachDial(paramZoom,    "ZOOM",   zoomSteps,    { zoomIdx },    { zoomIdx = it },    { v -> applyCamera("zoom" to v.replace("×","").toFloat()) })
    }

    // ── Roda de ajuste ─────────────────────────────────────────────────────────

    private fun openWheel(
        anchor: View, tag: String, steps: Array<String>,
        getIdx: () -> Int, setIdx: (Int) -> Unit, onApply: (String) -> Unit
    ) {
        closeWheel()
        activeParamTag = tag
        val inflated = layoutInflater.inflate(R.layout.layout_param_wheel, null)
        inflated.findViewById<TextView>(R.id.wheelLabel).text = tag
        val wheelValue = inflated.findViewById<TextView>(R.id.wheelValue)
        val wheelAuto  = inflated.findViewById<TextView>(R.id.wheelAuto)
        val wheelSeek  = inflated.findViewById<SeekBar>(R.id.wheelSeek)
        wheelSeek.max = steps.size - 1
        wheelSeek.progress = getIdx()
        wheelValue.text = steps[getIdx()]
        wheelAuto.isVisible = getIdx() == 0

        wheelSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                setIdx(p)
                val v = steps[p]
                wheelValue.text = v
                wheelAuto.isVisible = p == 0
                setDialLabel(anchor, tag, v)
                if (fromUser) onApply(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = android.view.Gravity.BOTTOM; bottomMargin = resources.getDimensionPixelSize(R.dimen.params_bar_height) }
        root.addView(inflated, params)
        wheelContainer = inflated
    }

    private fun closeWheel() {
        wheelContainer?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
        }
        wheelContainer = null
        activeParamTag = ""
    }

    private fun setDialLabel(view: View, label: String, value: String) {
        view.findViewById<TextView>(R.id.paramLabel)?.text = label
        view.findViewById<TextView>(R.id.paramValue)?.text = value
        view.findViewById<TextView>(R.id.paramLock)?.text  = if (value == "AUTO") "A" else "M"
    }

    // ── Bottom actions ─────────────────────────────────────────────────────────

    private fun setupBottomActions() {
        btnShutter.setOnClickListener {
            isStreaming = !isStreaming
            btnShutter.setBackgroundResource(if (isStreaming) R.drawable.bg_shutter_active else R.drawable.bg_shutter_inner)
            showToast(if (isStreaming) "A transmitir…" else "Transmissão parada")
        }

        btnFlashToggle.setOnClickListener {
            isFlashOn = !isFlashOn
            btnFlashToggle.setImageResource(R.drawable.ic_flash_off)
            btnFlashToggle.setColorFilter(if (isFlashOn) 0xFFfbbf24.toInt() else 0xFF94a3b8.toInt())
            applyCamera("lantern" to isFlashOn)
            showToast(if (isFlashOn) "Flash LIGADO" else "Flash DESLIGADO")
            animatePop(btnFlashToggle)
        }

        btnSwitchCamera.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraHelper.Facing.BACK) CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK
            StreamingService.instance?.apply {
                stopPreview()
                startPreview(cameraPreview, cameraFacing)
            }
            animatePop(btnSwitchCamera)
        }

        btnSettings.setOnClickListener { togglePanel() }
        btnClosePanel.setOnClickListener { closePanel() }

        fun selectLens(btn: TextView, id: String) {
            listOf(btnLens1x, btnLens05x, btnLens3x).forEach {
                it.setBackgroundResource(R.drawable.bg_lens_idle)
                it.setTextColor(0xFF94a3b8.toInt())
            }
            btn.setBackgroundResource(R.drawable.bg_lens_selected)
            btn.setTextColor(0xFF000000.toInt())
            applyCamera("camera" to id)
        }
        btnLens1x.setOnClickListener  { selectLens(btnLens1x,  "0") }
        btnLens05x.setOnClickListener { selectLens(btnLens05x, "2") }
        btnLens3x.setOnClickListener  { selectLens(btnLens3x,  "3") }
    }

    // ── Painel lateral ─────────────────────────────────────────────────────────

    private fun setupSettingsPanel() {
        ArrayAdapter.createFromResource(this, R.array.resolutions, android.R.layout.simple_spinner_item)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinResolution.adapter = it }
        ArrayAdapter.createFromResource(this, R.array.frame_rates, android.R.layout.simple_spinner_item)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinFrameRate.adapter = it }
        ArrayAdapter.createFromResource(this, R.array.bitrates, android.R.layout.simple_spinner_item)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinBitrate.adapter = it }

        spinResolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val (w, h) = when (pos) { 0 -> 1920 to 1080; 1 -> 1280 to 720; else -> 640 to 480 }
                applyCamera("width" to w, "height" to h)
                hudResolution.text = "${w}×${h}"
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinFrameRate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val fps = when (pos) { 0 -> 30; 1 -> 60; 2 -> 24; else -> 30 }
                applyCamera("fps" to fps)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinBitrate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val kbps = when (pos) { 0 -> 4000; 1 -> 8000; 2 -> 2000; else -> 4000 }
                applyCamera("bitrate" to kbps)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        switchGrid.setOnCheckedChangeListener  { _, on -> gridOverlay.isVisible = on }
        switchHud.setOnCheckedChangeListener   { _, on -> hudLeft.isVisible = on }
        switchOis.setOnCheckedChangeListener   { _, on -> applyCamera("ois" to on) }
        switchAudio.setOnCheckedChangeListener { _, on -> applyCamera("audio" to on) }
        seekMicGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) applyCamera("micGain" to p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun togglePanel() { if (isPanelOpen) closePanel() else openPanel() }

    private fun openPanel() {
        isPanelOpen = true
        ObjectAnimator.ofFloat(settingsPanel, "translationX", 0f).apply {
            duration = 280; interpolator = DecelerateInterpolator(); start()
        }
    }

    private fun closePanel() {
        isPanelOpen = false
        val w = settingsPanel.width.toFloat().takeIf { it > 0f } ?: 280f.dpToPx()
        ObjectAnimator.ofFloat(settingsPanel, "translationX", w).apply {
            duration = 280; interpolator = DecelerateInterpolator(); start()
        }
    }

    // ── Gestos ─────────────────────────────────────────────────────────────────

    private fun setupGestures() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (activeParamTag.isNotEmpty()) { closeWheel(); return true }
                if (isPanelOpen) { closePanel(); return true }
                toggleCleanUI()
                return true
            }
        })
        cameraPreview.setOnTouchListener { v, e ->
            if (gd.onTouchEvent(e)) v.performClick()
            true
        }
    }

    private var cleanUiMode = false
    private fun toggleCleanUI() {
        cleanUiMode = !cleanUiMode
        val a = if (cleanUiMode) 0f else 1f
        listOf(topBar, paramsBar, bottomActions, hudLeft).forEach {
            it.animate().alpha(a).setDuration(300).start()
            it.isVisible = true
        }
    }

    // ── HUD ticker ─────────────────────────────────────────────────────────────

    private fun tickHud() {
        val ctrl    = StreamingService.instance?.cameraController
        val clients = StreamingService.instance?.rtspServer?.connectedClients ?: 0
        hudFps.text       = "${ctrl?.currentFps ?: 30} fps"
        hudBitrate.text   = "${ctrl?.currentBitrate ?: 4000} kbps"
        clientsBadge.text = "$clients CLI"
        clientsBadge.setTextColor(if (clients > 0) 0xFF22c55e.toInt() else 0xFF64748b.toInt())
        rtspBadge.setBackgroundResource(if (clients > 0) R.drawable.bg_badge_red else R.drawable.bg_badge_green)
        batteryText.text = "${getBattery()}%"
        batteryText.setTextColor(when {
            getBattery() > 50 -> 0xFF22c55e.toInt()
            getBattery() > 20 -> 0xFFfbbf24.toInt()
            else              -> 0xFFef4444.toInt()
        })
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun applyCamera(vararg pairs: Pair<String, Any>) {
        StreamingService.instance?.cameraController?.updateSettings(mapOf(*pairs))
    }

    private fun refreshStatusBar() {
        val ip = getLocalIpAddress()
        statusText.text = "rtsp://$ip:8554/live"
    }

    private fun getBattery(): Int =
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun animatePop(v: View) {
        ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.25f, 1f).apply { duration = 180; start() }
        ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.25f, 1f).apply { duration = 180; start() }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun Float.dpToPx() = this * resources.displayMetrics.density

    private fun getLocalIpAddress(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                val addrs  = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    // ── Permissões ─────────────────────────────────────────────────────────────

    private fun startStreamingService() = startForegroundService(Intent(this, StreamingService::class.java))

    private fun checkPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val p = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) p.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, p.toTypedArray(), permissionCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode && grantResults.size >= 2 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            grantResults[1] == PackageManager.PERMISSION_GRANTED) startStreamingService()
    }
}
