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
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.AutoFitTextureView
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────────────
    private lateinit var statusText: TextView
    private lateinit var rtspIndicator: TextView
    private lateinit var cameraPreview: AutoFitTextureView
    private lateinit var topOverlay: LinearLayout

    // HUD
    private lateinit var hudContainer: LinearLayout
    private lateinit var hudIso: TextView
    private lateinit var hudExposure: TextView
    private lateinit var hudFocus: TextView
    private lateinit var hudFps: TextView
    private lateinit var hudTemp: TextView
    private lateinit var hudBattery: TextView
    private lateinit var hudClients: TextView
    private lateinit var hudNetwork: TextView

    // FABs
    private lateinit var fabSwitchCamera: ImageButton
    private lateinit var fabFlash: ImageButton
    private lateinit var fabTakePhoto: ImageButton
    private lateinit var fabToggleHud: ImageButton
    private lateinit var fabControls: FloatingActionButton
    private lateinit var fabContainer: LinearLayout

    // Bottom Sheet
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var btnCameraWide: Button
    private lateinit var btnCameraUltra: Button
    private lateinit var btnCameraTele: Button
    private lateinit var btnCameraFront: Button
    private lateinit var seekIso: SeekBar
    private lateinit var txtIsoValue: TextView
    private lateinit var seekExposure: SeekBar
    private lateinit var txtExposureValue: TextView
    private lateinit var seekFocus: SeekBar
    private lateinit var txtFocusValue: TextView
    private lateinit var btnLockAE: Button
    private lateinit var btnLockAF: Button
    private lateinit var btnFlash: Button

    // ── Estado ─────────────────────────────────────────────────────────────
    private var isHudVisible  = true
    private var isFlashOn     = false
    private var isAELocked    = false
    private var isAFLocked    = false
    private var cameraFacing  = CameraHelper.Facing.BACK

    // ── SurfaceTextureListener ───────────────────────────────────────────
    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
            StreamingService.instance?.startPreview(cameraFacing)
        }
        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            StreamingService.instance?.stopPreview()
            return true
        }
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    // ── Gesture + HUD ────────────────────────────────────────────────────
    private lateinit var gestureDetector: GestureDetector
    private val hudHandler  = Handler(Looper.getMainLooper())
    private val hudRunnable = object : Runnable {
        override fun run() { updateHudInfo(); hudHandler.postDelayed(this, 1000) }
    }
    private val permissionCode = 100

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeViews()
        setupBottomSheet()
        setupFABs()
        setupBottomSheetControls()
        setupGestureDetector()
        if (checkPermissions()) startStreamingService()
        else requestPermissions()
        hudHandler.post(hudRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (cameraPreview.isAvailable) StreamingService.instance?.startPreview(cameraFacing)
        updateStatusUI()
    }

    override fun onPause() {
        super.onPause()
        StreamingService.instance?.stopPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        hudHandler.removeCallbacks(hudRunnable)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "onConfigurationChanged")
    }

    // ── Views ───────────────────────────────────────────────────────────────

    private fun initializeViews() {
        statusText    = findViewById(R.id.statusText)
        rtspIndicator = findViewById(R.id.rtspIndicator)
        cameraPreview = findViewById(R.id.cameraPreview)
        topOverlay    = findViewById(R.id.topOverlay)
        cameraPreview.surfaceTextureListener = surfaceListener

        val hud      = findViewById<View>(R.id.hudLayout)
        hudContainer = hud.findViewById(R.id.hudContainer)
        hudIso       = hud.findViewById(R.id.hudIso)
        hudExposure  = hud.findViewById(R.id.hudExposure)
        hudFocus     = hud.findViewById(R.id.hudFocus)
        hudFps       = hud.findViewById(R.id.hudFps)
        hudTemp      = hud.findViewById(R.id.hudTemp)
        hudBattery   = hud.findViewById(R.id.hudBattery)
        hudClients   = hud.findViewById(R.id.hudClients)
        hudNetwork   = hud.findViewById(R.id.hudNetwork)

        fabSwitchCamera = findViewById(R.id.fabSwitchCamera)
        fabFlash        = findViewById(R.id.fabFlash)
        fabTakePhoto    = findViewById(R.id.fabTakePhoto)
        fabToggleHud    = findViewById(R.id.fabToggleHud)
        fabControls     = findViewById(R.id.fabControls)
        fabContainer    = findViewById(R.id.fabContainer)
        bottomSheet     = findViewById(R.id.bottomSheet)
    }

    // ── Bottom Sheet ──────────────────────────────────────────────────────

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        btnCameraWide    = bottomSheet.findViewById(R.id.btnCameraWide)
        btnCameraUltra   = bottomSheet.findViewById(R.id.btnCameraUltra)
        btnCameraTele    = bottomSheet.findViewById(R.id.btnCameraTele)
        btnCameraFront   = bottomSheet.findViewById(R.id.btnCameraFront)
        seekIso          = bottomSheet.findViewById(R.id.seekIso)
        txtIsoValue      = bottomSheet.findViewById(R.id.txtIsoValue)
        seekExposure     = bottomSheet.findViewById(R.id.seekExposure)
        txtExposureValue = bottomSheet.findViewById(R.id.txtExposureValue)
        seekFocus        = bottomSheet.findViewById(R.id.seekFocus)
        txtFocusValue    = bottomSheet.findViewById(R.id.txtFocusValue)
        btnLockAE        = bottomSheet.findViewById(R.id.btnLockAE)
        btnLockAF        = bottomSheet.findViewById(R.id.btnLockAF)
        btnFlash         = bottomSheet.findViewById(R.id.btnFlash)
    }

    // ── FABs ────────────────────────────────────────────────────────────────

    private fun setupFABs() {
        fabSwitchCamera.setOnClickListener { cycleCamera(); animateFAB(fabSwitchCamera) }

        fabFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            applyCamera("lantern" to isFlashOn)
            fabFlash.setColorFilter(if (isFlashOn) 0xFFfbbf24.toInt() else 0xFFf1f5f9.toInt())
            showToast(if (isFlashOn) "Flash LIGADO" else "Flash DESLIGADO")
            animateFAB(fabFlash)
        }

        fabTakePhoto.setOnClickListener { takeSnapshot(); animateFAB(fabTakePhoto) }
        fabToggleHud.setOnClickListener { toggleHud() }

        fabControls.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheet.isVisible = true
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }

    // ── Bottom Sheet Controls ───────────────────────────────────────────────

    private fun setupBottomSheetControls() {
        btnCameraWide.setOnClickListener  { selectCamera("0", CameraHelper.Facing.BACK,  "Wide",  btnCameraWide) }
        btnCameraUltra.setOnClickListener { selectCamera("2", CameraHelper.Facing.BACK,  "Ultra", btnCameraUltra) }
        btnCameraTele.setOnClickListener  { selectCamera("3", CameraHelper.Facing.BACK,  "Tele",  btnCameraTele) }
        btnCameraFront.setOnClickListener { selectCamera("1", CameraHelper.Facing.FRONT, "Frontal", btnCameraFront) }

        seekIso.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val iso = mapProgressToIso(p)
                txtIsoValue.text = iso.toString()
                if (fromUser) applyCamera("iso" to iso.toDouble())
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        seekExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val exp = mapProgressToExposure(p)
                txtExposureValue.text = exp
                if (fromUser) applyCamera("shutterSpeed" to exp)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        seekFocus.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                txtFocusValue.text = if (p == 0) "AUTO" else "$p%"
                if (fromUser) applyCamera("focus" to p / 100.0)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnLockAE.setOnClickListener {
            isAELocked = !isAELocked
            applyCamera("aeLock" to isAELocked)
            showToast(if (isAELocked) "Exposição bloqueada" else "Exposição desbloqueada")
        }

        btnLockAF.setOnClickListener {
            isAFLocked = !isAFLocked
            if (isAFLocked) applyCamera("focusmode" to "off")
            else {
                applyCamera("focusmode" to "continuous-video")
                seekFocus.progress = 0
                txtFocusValue.text = "AUTO"
            }
            showToast(if (isAFLocked) "Foco bloqueado" else "Foco automático")
        }

        btnFlash.setOnClickListener {
            fabFlash.performClick()
            btnFlash.text = if (isFlashOn) "Flash ON" else "Flash OFF"
        }
    }

    // ── Gesture ────────────────────────────────────────────────────────────────

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { toggleUICompactMode(); return true }
        })
        cameraPreview.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) v.performClick()
            true
        }
    }

    // ── Câmara ─────────────────────────────────────────────────────────────────

    private fun applyCamera(vararg pairs: Pair<String, Any>) {
        StreamingService.instance?.cameraController?.updateSettings(mapOf(*pairs))
            ?: Log.w("MainActivity", "cameraController indisponível")
    }

    private fun cycleCamera() {
        val (id, facing, label) = when (cameraFacing) {
            CameraHelper.Facing.BACK  -> Triple("1", CameraHelper.Facing.FRONT, "Frontal")
            CameraHelper.Facing.FRONT -> Triple("0", CameraHelper.Facing.BACK,  "Wide")
        }
        cameraFacing = facing
        applyCamera("camera" to id)
        // Reinicia preview com o novo facing
        StreamingService.instance?.rtspServer?.stopPreview()
        StreamingService.instance?.rtspServer?.startPreview(facing)
        showToast(label)
    }

    private fun selectCamera(id: String, facing: CameraHelper.Facing, label: String, button: Button) {
        cameraFacing = facing
        applyCamera("camera" to id)
        StreamingService.instance?.rtspServer?.stopPreview()
        StreamingService.instance?.rtspServer?.startPreview(facing)
        listOf(btnCameraWide, btnCameraUltra, btnCameraTele, btnCameraFront).forEach {
            it.setBackgroundResource(R.drawable.bg_button_secondary)
            it.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
        button.setBackgroundResource(R.drawable.bg_button_primary)
        button.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        showToast("$label selecionada")
    }

    private fun takeSnapshot() {
        val bmp = cameraPreview.bitmap ?: run { showToast("Preview indisponível"); return }
        try {
            val dir  = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val file = java.io.File(dir, "snapshot_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            showToast("Foto salva: ${file.name}")
        } catch (e: Exception) {
            showToast("Erro ao salvar foto")
            Log.e("MainActivity", "Erro snapshot", e)
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun toggleHud() {
        isHudVisible = !isHudVisible
        hudContainer.isVisible = isHudVisible
        animateFAB(fabToggleHud)
    }

    private fun toggleUICompactMode() {
        val compact = topOverlay.isVisible
        topOverlay.isVisible    = !compact
        fabContainer.isVisible  = !compact
        rtspIndicator.isVisible = !compact
        hudContainer.isVisible  = !compact && isHudVisible
        showToast(if (compact) "Modo Compacto" else "Modo Normal")
    }

    private fun updateHudInfo() {
        val ctrl = StreamingService.instance?.cameraController
        hudIso.text      = "ISO: ${ctrl?.isoValue ?: "--"}"
        hudExposure.text = "EXP: ${txtExposureValue.text}"
        hudFocus.text    = "FOCUS: ${if (ctrl?.autoFocus != false) "AUTO" else txtFocusValue.text}"
        hudFps.text      = "FPS: ${ctrl?.currentFps ?: 30}"
        hudTemp.text     = "TEMP: 38\u00b0C"
        val bat = getBatteryLevel()
        hudBattery.text = "BAT: $bat%"
        hudBattery.setTextColor(when {
            bat > 50 -> 0xFF10b981.toInt()
            bat > 20 -> 0xFFfbbf24.toInt()
            else     -> 0xFFef4444.toInt()
        })
        val clients = StreamingService.instance?.rtspServer?.connectedClients ?: 0
        hudClients.text = "CLIENTS: $clients"
        rtspIndicator.setTextColor(if (clients > 0) 0xFF10b981.toInt() else 0xFFef4444.toInt())
        hudNetwork.text = "NET: 0.0MB/s"
    }

    private fun getBatteryLevel(): Int =
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun mapProgressToIso(p: Int): Int = 50 + (p * 31.5).toInt()

    private fun mapProgressToExposure(p: Int): String = when (p) {
        in 0..10  -> "1/8000s"
        in 11..20 -> "1/4000s"
        in 21..30 -> "1/2000s"
        in 31..40 -> "1/1000s"
        in 41..50 -> "1/500s"
        in 51..60 -> "1/250s"
        in 61..70 -> "1/125s"
        in 71..80 -> "1/60s"
        in 81..90 -> "1/30s"
        else      -> "1/15s"
    }

    private fun animateFAB(fab: View) {
        ObjectAnimator.ofFloat(fab, "scaleX", 1f, 1.2f, 1f).apply { duration = 200; start() }
        ObjectAnimator.ofFloat(fab, "scaleY", 1f, 1.2f, 1f).apply { duration = 200; start() }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ── Status + Permissões ───────────────────────────────────────────────────

    private fun updateStatusUI() {
        val ip = getLocalIpAddress()
        statusText.text    = "rtsp://$ip:8554/live  |  http://$ip:8080"
        rtspIndicator.text = "\u25cf RTSP :8554"
    }

    private fun getLocalIpAddress(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address)
                        return addr.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (e: Exception) { Log.e("MainActivity", "Erro IP", e) }
        return "127.0.0.1"
    }

    private fun startStreamingService() {
        startForegroundService(Intent(this, StreamingService::class.java))
    }

    private fun checkPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), permissionCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode &&
            grantResults.size >= 2 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            grantResults[1] == PackageManager.PERMISSION_GRANTED
        ) startStreamingService()
        else statusText.text = "Permissões de câmara/áudio negadas"
    }
}
