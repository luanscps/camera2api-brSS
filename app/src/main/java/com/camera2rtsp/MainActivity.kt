package com.camera2rtsp

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    // Views principais
    private lateinit var statusText: TextView
    private lateinit var rtspIndicator: TextView
    private lateinit var cameraPreview: TextureView
    private lateinit var topOverlay: LinearLayout

    // HUD (Heads-Up Display)
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

    // Bottom Sheet Controls
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

    // Estado
    private var isHudVisible = true
    private var isFlashOn = false
    private var isAELocked = false
    private var isAFLocked = false
    private var currentCamera = "wide"

    // Gesture detector para duplo toque (sem depreciação)
    private lateinit var gestureDetector: GestureDetector

    // Handler para atualizar HUD
    private val hudUpdateHandler = Handler(Looper.getMainLooper())
    private val hudUpdateRunnable = object : Runnable {
        override fun run() {
            updateHudInfo()
            hudUpdateHandler.postDelayed(this, 1000)
        }
    }

    private val permissionCode = 100

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

        hudUpdateHandler.post(hudUpdateRunnable)
    }

    private fun initializeViews() {
        statusText    = findViewById(R.id.statusText)
        rtspIndicator = findViewById(R.id.rtspIndicator)
        cameraPreview = findViewById(R.id.cameraPreview)
        topOverlay    = findViewById(R.id.topOverlay)

        // HUD está dentro do <include id="hudLayout">
        val hudLayout = findViewById<View>(R.id.hudLayout)
        hudContainer  = hudLayout.findViewById(R.id.hudContainer)
        hudIso        = hudLayout.findViewById(R.id.hudIso)
        hudExposure   = hudLayout.findViewById(R.id.hudExposure)
        hudFocus      = hudLayout.findViewById(R.id.hudFocus)
        hudFps        = hudLayout.findViewById(R.id.hudFps)
        hudTemp       = hudLayout.findViewById(R.id.hudTemp)
        hudBattery    = hudLayout.findViewById(R.id.hudBattery)
        hudClients    = hudLayout.findViewById(R.id.hudClients)
        hudNetwork    = hudLayout.findViewById(R.id.hudNetwork)

        // FABs
        fabSwitchCamera = findViewById(R.id.fabSwitchCamera)
        fabFlash        = findViewById(R.id.fabFlash)
        fabTakePhoto    = findViewById(R.id.fabTakePhoto)
        fabToggleHud    = findViewById(R.id.fabToggleHud)
        fabControls     = findViewById(R.id.fabControls)
        fabContainer    = findViewById(R.id.fabContainer)

        // Bottom Sheet
        bottomSheet = findViewById(R.id.bottomSheet)
    }

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

    private fun setupFABs() {
        fabSwitchCamera.setOnClickListener {
            cycleCamera()
            showToast("Câmara: ${currentCamera.uppercase()}")
        }

        fabFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            showToast(if (isFlashOn) "Flash LIGADO" else "Flash DESLIGADO")
        }

        fabTakePhoto.setOnClickListener {
            showToast("Foto captada!")
            animateFAB(fabTakePhoto)
        }

        fabToggleHud.setOnClickListener {
            toggleHud()
        }

        fabControls.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheet.isVisible = true
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }

    private fun setupBottomSheetControls() {
        btnCameraWide.setOnClickListener  { selectCamera("wide",  btnCameraWide) }
        btnCameraUltra.setOnClickListener { selectCamera("ultra", btnCameraUltra) }
        btnCameraTele.setOnClickListener  { selectCamera("tele",  btnCameraTele) }
        btnCameraFront.setOnClickListener { selectCamera("front", btnCameraFront) }

        seekIso.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                txtIsoValue.text = mapProgressToIso(progress).toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                txtExposureValue.text = mapProgressToExposure(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekFocus.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                txtFocusValue.text = if (progress == 0) "AUTO" else "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnLockAE.setOnClickListener {
            isAELocked = !isAELocked
            btnLockAE.text = if (isAELocked) "Exp" else "Exp"
            showToast(if (isAELocked) "Exposição bloqueada" else "Exposição desbloqueada")
        }

        btnLockAF.setOnClickListener {
            isAFLocked = !isAFLocked
            btnLockAF.text = if (isAFLocked) "Foco" else "Foco"
            showToast(if (isAFLocked) "Foco bloqueado" else "Foco desbloqueado")
        }

        btnFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            btnFlash.text = if (isFlashOn) "Flash ON" else "Flash OFF"
        }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleUICompactMode()
                return true
            }
        })

        cameraPreview.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                v.performClick()
            }
            true
        }
    }

    private fun cycleCamera() {
        currentCamera = when (currentCamera) {
            "wide"  -> "ultra"
            "ultra" -> "tele"
            "tele"  -> "front"
            else    -> "wide"
        }
    }

    private fun selectCamera(camera: String, button: Button) {
        currentCamera = camera
        listOf(btnCameraWide, btnCameraUltra, btnCameraTele, btnCameraFront).forEach {
            it.setBackgroundResource(R.drawable.bg_button_secondary)
            it.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
        button.setBackgroundResource(R.drawable.bg_button_primary)
        button.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        showToast("${camera.uppercase()} selecionada")
    }

    private fun toggleHud() {
        isHudVisible = !isHudVisible
        hudContainer.isVisible = isHudVisible
        animateFAB(fabToggleHud)
    }

    private fun toggleUICompactMode() {
        val isCompact = topOverlay.isVisible
        topOverlay.isVisible   = !isCompact
        fabContainer.isVisible = !isCompact
        rtspIndicator.isVisible = !isCompact
        hudContainer.isVisible  = !isCompact && isHudVisible
        showToast(if (isCompact) "Modo Compacto" else "Modo Normal")
    }

    private fun updateHudInfo() {
        hudIso.text      = "ISO: ${txtIsoValue.text}"
        hudExposure.text = "EXP: ${txtExposureValue.text}"
        hudFocus.text    = "FOCUS: ${txtFocusValue.text}"
        hudFps.text      = "FPS: 30"
        hudTemp.text     = "TEMP: 38\u00b0C"

        val bat = getBatteryLevel()
        hudBattery.text = "BAT: $bat%"
        hudBattery.setTextColor(
            when {
                bat > 50 -> 0xFF10b981.toInt()
                bat > 20 -> 0xFFfbbf24.toInt()
                else     -> 0xFFef4444.toInt()
            }
        )
        hudClients.text = "CLIENTS: 0"
        hudNetwork.text = "NET: 0.0MB/s"
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun mapProgressToIso(progress: Int): Int = 50 + (progress * 31.5).toInt()

    private fun mapProgressToExposure(progress: Int): String = when (progress) {
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

    private fun showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        StreamingService.instance?.rtspServer?.attachTextureView(cameraPreview)
        updateStatusUI()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "onConfigurationChanged - stream mantido")
    }

    private fun startStreamingService() {
        val intent = android.content.Intent(this, StreamingService::class.java)
        startForegroundService(intent)
    }

    private fun updateStatusUI() {
        val ip = getLocalIpAddress()
        statusText.text    = "rtsp://$ip:8554/live  |  http://$ip:8080"
        rtspIndicator.text = "\u25cf RTSP :8554"
        rtspIndicator.setTextColor(0xFF10b981.toInt())
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address)
                        return addr.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao obter IP", e)
        }
        return "127.0.0.1"
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), permissionCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode &&
            grantResults.size >= 2 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            grantResults[1] == PackageManager.PERMISSION_GRANTED
        ) {
            startStreamingService()
        } else {
            statusText.text = "Permissões de câmara/áudio negadas"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hudUpdateHandler.removeCallbacks(hudUpdateRunnable)
        Log.d("MainActivity", "Activity destruída - serviço continua rodando")
    }
}
