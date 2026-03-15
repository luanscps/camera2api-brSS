package com.camera2rtsp

import android.Manifest
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    // -- Views principais ----------------------------------------------------
    private lateinit var cameraPreview: OpenGlView
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
    private lateinit var previewToggle: ImageButton

    // -- Dials ----------------------------------------------------------------
    private lateinit var paramIso: View
    private lateinit var paramShutter: View
    private lateinit var paramWb: View
    private lateinit var paramFocus: View
    private lateinit var paramZoom: View
    private var wheelContainer: View? = null

    // -- Bottom actions -------------------------------------------------------
    private lateinit var btnShutter: View
    private lateinit var btnFlashToggle: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var lensContainer: LinearLayout

    // -- Painel de configuracoes ----------------------------------------------
    private lateinit var btnClosePanel: ImageButton
    private lateinit var editRtmpUrl: EditText
    private lateinit var btnApplyRtmpUrl: Button
    private lateinit var spinResolution: Spinner
    private lateinit var spinFrameRate: Spinner
    private lateinit var spinBitrate: Spinner
    private lateinit var switchOis: Switch
    private lateinit var switchGrid: Switch
    private lateinit var switchHud: Switch
    private lateinit var switchAudio: Switch
    private lateinit var seekMicGain: SeekBar

    // -- SharedPreferences ----------------------------------------------------
    private lateinit var prefs: SharedPreferences
    private val PREF_FILE    = "camera2rtmp_prefs"
    private val KEY_RTMP_URL = "rtmp_url"
    private val DEFAULT_URL  = "rtmp://192.168.1.100:1935/live/stream"

    // -- Estado ---------------------------------------------------------------
    private var isPanelOpen   = false
    private var isFlashOn     = false
    private var isStreaming   = false
    private var isPreviewOn   = true
    private var activeParamTag = ""

    // -- Cameras descobertas em runtime --------------------------------------
    private data class CameraEntry(val id: String, val name: String, val facing: CameraHelper.Facing)
    private var cameras: List<CameraEntry> = emptyList()
    private var activeCameraIdx = 0

    // -- Valores dos parametros -----------------------------------------------
    private val isoSteps     = arrayOf("AUTO","50","100","200","400","800","1600","3200")
    private val shutterSteps = arrayOf("AUTO","1/8000","1/4000","1/2000","1/1000","1/500","1/250","1/125","1/60","1/30","1/15")
    private val wbSteps      = arrayOf("AUTO","2700K","3200K","4200K","5600K","6500K","7500K")
    private val focusSteps   = arrayOf("AUTO","0.1","0.2","0.3","0.4","0.5","0.6","0.7","0.8","0.9","1.0")
    private val zoomSteps    = arrayOf("1x","1.5x","2x","3x","4x","5x")
    private var isoIdx = 0; private var shutterIdx = 0; private var wbIdx = 0
    private var focusIdx = 0; private var zoomIdx = 0

    // -- HUD ticker -----------------------------------------------------------
    private val hudHandler  = Handler(Looper.getMainLooper())
    private val hudRunnable = object : Runnable {
        override fun run() { tickHud(); hudHandler.postDelayed(this, 1000) }
    }
    private val permissionCode = 100

    // -- Service binding ------------------------------------------------------
    private var service: StreamingService? = null
    private var viewAttached = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? StreamingService.LocalBinder ?: return
            service = b.getService()
            attachViewIfReady()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            viewAttached = false
        }
    }

    // -- Lifecycle ------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        bindViews()           // 1. faz todos os findViewById (incluindo dials)
        discoverCameras()     // 2. descobre cameras
        setupDials()          // 3. configura listeners dos dials
        setupBottomActions()  // 4. configura botoes (buildLensButtons esta aqui)
        setupSettingsPanel()  // 5. configura painel
        setupGestures()       // 6. gestos
        if (checkPermissions()) startAndBindService() else requestPermissions()
        hudHandler.post(hudRunnable)
    }

    override fun onResume() {
        super.onResume()
        refreshStatusBar()
        if (service == null) bindToService()
    }

    override fun onStop() {
        super.onStop()
        service?.detachView()
        viewAttached = false
        try { unbindService(serviceConnection) } catch (_: Exception) {}
        service = null
    }

    override fun onDestroy() {
        super.onDestroy()
        hudHandler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig) }

    // -- Service binding helpers ----------------------------------------------

    private fun startAndBindService() {
        val url = prefs.getString(KEY_RTMP_URL, DEFAULT_URL) ?: DEFAULT_URL
        val intent = Intent(this, StreamingService::class.java)
            .putExtra(StreamingService.EXTRA_RTMP_URL, url)
        startForegroundService(intent)
        bindToService()
    }

    private fun bindToService() {
        val intent = Intent(this, StreamingService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun attachViewIfReady() {
        if (viewAttached) return
        val svc = service ?: return
        if (!cameraPreview.holder.surface.isValid) {
            cameraPreview.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    cameraPreview.holder.removeCallback(this)
                    if (!viewAttached) {
                        svc.attachView(cameraPreview)
                        viewAttached = true
                    }
                }
                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                override fun surfaceDestroyed(h: SurfaceHolder) { viewAttached = false }
            })
        } else {
            svc.attachView(cameraPreview)
            viewAttached = true
        }
    }

    // -- Descobre cameras -----------------------------------------------------

    private fun discoverCameras() {
        val mgr = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val entries = mutableListOf<CameraEntry>()

        data class BackInfo(val id: String, val focalLen: Float)
        val backCameras = mutableListOf<BackInfo>()

        for (id in mgr.cameraIdList) {
            try {
                val chars = mgr.getCameraCharacteristics(id)
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: continue
                // Ignora sensores depth-only
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) &&
                    !caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) continue

                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val focalLen = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull() ?: 4.0f

                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    entries.add(CameraEntry(id, "Frontal", CameraHelper.Facing.FRONT))
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameras.add(BackInfo(id, focalLen))
                }
            } catch (_: Exception) {}
        }

        if (backCameras.isNotEmpty()) {
            val sorted = backCameras.sortedBy { it.focalLen }
            val minF = sorted.first().focalLen
            val maxF = sorted.last().focalLen
            for (bc in backCameras) {
                val name = when {
                    backCameras.size == 1 -> "Traseira"
                    bc.focalLen == minF   -> "Ultra Wide"
                    bc.focalLen == maxF   -> "Tele"
                    else                  -> "Wide"
                }
                entries.add(CameraEntry(bc.id, name, CameraHelper.Facing.BACK))
            }
        }

        cameras = entries.sortedWith(compareBy {
            when (it.name) { "Wide" -> 0; "Ultra Wide" -> 1; "Tele" -> 2; else -> 3 }
        })
        android.util.Log.i("MainActivity", "Cameras: ${cameras.map { "${it.name}(${it.id})" }}")
    }

    // -- Bind views -----------------------------------------------------------

    private fun bindViews() {
        cameraPreview   = findViewById(R.id.cameraPreview)
        gridOverlay     = findViewById(R.id.gridOverlay)
        topBar          = findViewById(R.id.topBar)
        statusText      = findViewById(R.id.statusText)
        rtspBadge       = findViewById(R.id.rtspBadge)
        clientsBadge    = findViewById(R.id.clientsBadge)
        batteryText     = findViewById(R.id.batteryText)
        hudLeft         = findViewById(R.id.hudLeft)
        hudFps          = findViewById(R.id.hudFps)
        hudBitrate      = findViewById(R.id.hudBitrate)
        hudResolution   = findViewById(R.id.hudResolution)
        bottomActions   = findViewById(R.id.bottomActions)
        paramsBar       = findViewById(R.id.paramsBar)
        settingsPanel   = findViewById(R.id.settingsPanel)
        lensContainer   = findViewById(R.id.lensContainer)
        previewToggle   = findViewById(R.id.btnPreviewToggle)
        btnShutter      = findViewById(R.id.btnShutter)
        btnFlashToggle  = findViewById(R.id.btnFlashToggle)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnSettings     = findViewById(R.id.btnSettings)

        // *** FIX: dials precisam de findViewById ANTES de setDialLabel ***
        paramIso     = findViewById(R.id.paramIso)
        paramShutter = findViewById(R.id.paramShutter)
        paramWb      = findViewById(R.id.paramWb)
        paramFocus   = findViewById(R.id.paramFocus)
        paramZoom    = findViewById(R.id.paramZoom)

        btnClosePanel   = settingsPanel.findViewById(R.id.btnClosePanel)
        editRtmpUrl     = settingsPanel.findViewById(R.id.editRtmpUrl)
        btnApplyRtmpUrl = settingsPanel.findViewById(R.id.btnApplyRtmpUrl)
        spinResolution  = settingsPanel.findViewById(R.id.spinResolution)
        spinFrameRate   = settingsPanel.findViewById(R.id.spinFrameRate)
        spinBitrate     = settingsPanel.findViewById(R.id.spinBitrate)
        switchOis       = settingsPanel.findViewById(R.id.switchOis)
        switchGrid      = settingsPanel.findViewById(R.id.switchGrid)
        switchHud       = settingsPanel.findViewById(R.id.switchHud)
        switchAudio     = settingsPanel.findViewById(R.id.switchAudio)
        seekMicGain     = settingsPanel.findViewById(R.id.seekMicGain)

        val savedUrl = prefs.getString(KEY_RTMP_URL, DEFAULT_URL) ?: DEFAULT_URL
        editRtmpUrl.setText(savedUrl)

        // Agora e seguro chamar setDialLabel — todas as views ja foram inicializadas
        setDialLabel(paramIso,     "ISO",  isoSteps[isoIdx])
        setDialLabel(paramShutter, "SS",   shutterSteps[shutterIdx])
        setDialLabel(paramWb,      "WB",   wbSteps[wbIdx])
        setDialLabel(paramFocus,   "FOCO", focusSteps[focusIdx])
        setDialLabel(paramZoom,    "ZOOM", zoomSteps[zoomIdx])
    }

    // -- Botoes de lente dinamicos --------------------------------------------

    private fun buildLensButtons() {
        lensContainer.removeAllViews()
        val dp6  = (6  * resources.displayMetrics.density).toInt()
        val size = (36 * resources.displayMetrics.density).toInt()

        cameras.forEachIndexed { idx, cam ->
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp6 }
                gravity = android.view.Gravity.CENTER
                text = cam.name
                textSize = if (cam.name.length <= 4) 13f else 9f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(if (idx == activeCameraIdx) R.drawable.bg_lens_selected else R.drawable.bg_lens_idle)
                setTextColor(if (idx == activeCameraIdx) 0xFF000000.toInt() else 0xFF94a3b8.toInt())
                setOnClickListener { selectCamera(idx) }
            }
            lensContainer.addView(tv)
        }
    }

    private fun selectCamera(idx: Int) {
        if (idx == activeCameraIdx && service != null) return
        activeCameraIdx = idx
        val cam = cameras[idx]
        for (i in 0 until lensContainer.childCount) {
            val tv = lensContainer.getChildAt(i) as? TextView ?: continue
            tv.setBackgroundResource(if (i == idx) R.drawable.bg_lens_selected else R.drawable.bg_lens_idle)
            tv.setTextColor(if (i == idx) 0xFF000000.toInt() else 0xFF94a3b8.toInt())
        }
        service?.switchCamera(cam.id, cam.facing)
    }

    // -- Dials ----------------------------------------------------------------

    private fun setupDials() {
        fun attachDial(view: View, tag: String, steps: Array<String>,
                       getIdx: () -> Int, setIdx: (Int) -> Unit, onApply: (String) -> Unit) {
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
        attachDial(paramIso,     "ISO",  isoSteps,     { isoIdx },     { isoIdx = it },     { v -> applyCamera("iso" to (v.toDoubleOrNull() ?: 0.0)) })
        attachDial(paramShutter, "SS",   shutterSteps, { shutterIdx }, { shutterIdx = it }, { v -> applyCamera("shutterSpeed" to v) })
        attachDial(paramWb,      "WB",   wbSteps,      { wbIdx },      { wbIdx = it },      { v -> applyCamera("whiteBalance" to v) })
        attachDial(paramFocus,   "FOCO", focusSteps,   { focusIdx },   { focusIdx = it },   { v -> applyCamera("focus" to (v.replace("x","").toDoubleOrNull() ?: 0.0)) })
        attachDial(paramZoom,    "ZOOM", zoomSteps,    { zoomIdx },    { zoomIdx = it },    { v -> applyCamera("zoom" to v.replace("x","").toFloat()) })
    }

    // -- Roda de ajuste -------------------------------------------------------

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
                setIdx(p); val v = steps[p]
                wheelValue.text = v; wheelAuto.isVisible = p == 0
                setDialLabel(anchor, tag, v); if (fromUser) onApply(v)
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
        wheelContainer?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        wheelContainer = null; activeParamTag = ""
    }

    private fun setDialLabel(view: View, label: String, value: String) {
        view.findViewById<TextView>(R.id.paramLabel)?.text = label
        view.findViewById<TextView>(R.id.paramValue)?.text = value
        view.findViewById<TextView>(R.id.paramLock)?.text  = if (value == "AUTO") "A" else "M"
    }

    // -- Bottom actions -------------------------------------------------------

    private fun setupBottomActions() {
        btnShutter.setOnClickListener {
            isStreaming = !isStreaming
            if (isStreaming) service?.startStream()
            else             service?.stopStream()
            btnShutter.setBackgroundResource(if (isStreaming) R.drawable.bg_shutter_active else R.drawable.bg_shutter_inner)
            showToast(if (isStreaming) "Transmitindo..." else "Stream parado")
        }

        btnFlashToggle.setOnClickListener {
            isFlashOn = !isFlashOn
            btnFlashToggle.setImageResource(R.drawable.ic_flash_off)
            btnFlashToggle.setColorFilter(if (isFlashOn) 0xFFfbbf24.toInt() else 0xFF94a3b8.toInt())
            applyCamera("lantern" to isFlashOn)
            animatePop(btnFlashToggle)
        }

        btnSwitchCamera.setOnClickListener {
            if (cameras.isEmpty()) return@setOnClickListener
            val nextIdx = (activeCameraIdx + 1) % cameras.size
            selectCamera(nextIdx)
            animatePop(btnSwitchCamera)
        }

        previewToggle.setOnClickListener {
            isPreviewOn = !isPreviewOn
            if (isPreviewOn) {
                service?.startPreview()
                previewToggle.setColorFilter(0xFF38bdf8.toInt())
                showToast("Preview ligado")
            } else {
                service?.stopPreview()
                previewToggle.setColorFilter(0xFF94a3b8.toInt())
                showToast("Preview desligado")
            }
        }

        btnSettings.setOnClickListener { togglePanel() }
        btnClosePanel.setOnClickListener { closePanel() }

        buildLensButtons()
    }

    // -- Painel de configuracoes ----------------------------------------------

    private fun setupSettingsPanel() {
        ArrayAdapter.createFromResource(this, R.array.resolutions, android.R.layout.simple_spinner_item)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinResolution.adapter = it }
        ArrayAdapter.createFromResource(this, R.array.frame_rates, android.R.layout.simple_spinner_item)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinFrameRate.adapter = it }
        ArrayAdapter.createFromResource(this, R.array.bitrates, android.R.layout.simple_spinner_item)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinBitrate.adapter = it }

        btnApplyRtmpUrl.setOnClickListener {
            val url = editRtmpUrl.text.toString().trim()
            if (url.isEmpty() || !url.startsWith("rtmp://")) {
                showToast("URL invalida. Use rtmp://IP:1935/live/stream")
                return@setOnClickListener
            }
            prefs.edit().putString(KEY_RTMP_URL, url).apply()
            service?.let { svc ->
                svc.rtmpUrl = url
                svc.stopStream()
                svc.startStream()
                showToast("Reconectando para $url")
            } ?: showToast("URL salva. Sera usada no proximo inicio.")
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editRtmpUrl.windowToken, 0)
            statusText.text = url
        }

        spinResolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val (w, h) = when (pos) { 0 -> 1920 to 1080; 1 -> 1280 to 720; else -> 640 to 480 }
                applyCamera("width" to w, "height" to h)
                hudResolution.text = "${w}x${h}"
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
        val url = prefs.getString(KEY_RTMP_URL, DEFAULT_URL) ?: DEFAULT_URL
        editRtmpUrl.setText(url)
        ObjectAnimator.ofFloat(settingsPanel, "translationX", 0f).apply {
            duration = 280; interpolator = DecelerateInterpolator(); start()
        }
    }

    private fun closePanel() {
        isPanelOpen = false
        val w = settingsPanel.width.toFloat().takeIf { it > 0f } ?: (280f * resources.displayMetrics.density)
        ObjectAnimator.ofFloat(settingsPanel, "translationX", w).apply {
            duration = 280; interpolator = DecelerateInterpolator(); start()
        }
    }

    // -- Gestos ---------------------------------------------------------------

    private fun setupGestures() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (activeParamTag.isNotEmpty()) { closeWheel(); return true }
                if (isPanelOpen) { closePanel(); return true }
                toggleCleanUI(); return true
            }
        })
        cameraPreview.setOnTouchListener { v, e ->
            if (gd.onTouchEvent(e)) v.performClick(); true
        }
    }

    private var cleanUiMode = false
    private fun toggleCleanUI() {
        cleanUiMode = !cleanUiMode
        val a = if (cleanUiMode) 0f else 1f
        listOf(topBar, paramsBar, bottomActions, hudLeft).forEach {
            it.animate().alpha(a).setDuration(300).start(); it.isVisible = true
        }
    }

    // -- HUD ticker -----------------------------------------------------------

    private fun tickHud() {
        val ctrl      = service?.cameraController
        val streaming = service?.rtmpStreamer?.isStreaming ?: false
        hudFps.text       = "${ctrl?.currentFps ?: 30} fps"
        hudBitrate.text   = "${ctrl?.currentBitrate ?: 4000} kbps"
        rtspBadge.text    = if (streaming) "LIVE" else "OFF"
        rtspBadge.setBackgroundResource(if (streaming) R.drawable.bg_badge_red else R.drawable.bg_badge_green)
        batteryText.text  = "${getBattery()}%"
        val bat = getBattery()
        batteryText.setTextColor(when {
            bat > 50 -> 0xFF22c55e.toInt()
            bat > 20 -> 0xFFfbbf24.toInt()
            else     -> 0xFFef4444.toInt()
        })
    }

    // -- Helpers --------------------------------------------------------------

    private fun applyCamera(vararg pairs: Pair<String, Any>) {
        service?.cameraController?.updateSettings(mapOf(*pairs))
    }

    private fun refreshStatusBar() {
        val url = prefs.getString(KEY_RTMP_URL, DEFAULT_URL) ?: DEFAULT_URL
        statusText.text = url
    }

    private fun getBattery(): Int =
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun animatePop(v: View) {
        ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.25f, 1f).apply { duration = 180; start() }
        ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.25f, 1f).apply { duration = 180; start() }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // -- Permissoes -----------------------------------------------------------

    private fun checkPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val p = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) p.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, p.toTypedArray(), permissionCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode && grantResults.size >= 2 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            grantResults[1] == PackageManager.PERMISSION_GRANTED) startAndBindService()
    }
}
