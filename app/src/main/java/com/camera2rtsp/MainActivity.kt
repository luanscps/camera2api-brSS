package com.camera2rtsp

import android.Manifest
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.view.GestureDetectorCompat
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
    
    // FABs (Floating Action Buttons)
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
    
    // Gesture detector para duplo toque
    private lateinit var gestureDetector: GestureDetectorCompat
    
    // Handler para atualizar HUD
    private val hudUpdateHandler = Handler(Looper.getMainLooper())
    private val hudUpdateRunnable = object : Runnable {
        override fun run() {
            updateHudInfo()
            hudUpdateHandler.postDelayed(this, 1000) // Atualiza a cada 1 segundo
        }
    }

    private val PERMISSION_CODE = 100

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
        
        // Inicia atualização do HUD
        hudUpdateHandler.post(hudUpdateRunnable)
    }
    
    private fun initializeViews() {
        // Views principais
        statusText = findViewById(R.id.statusText)
        rtspIndicator = findViewById(R.id.rtspIndicator)
        cameraPreview = findViewById(R.id.cameraPreview)
        topOverlay = findViewById(R.id.topOverlay)
        
        // HUD
        hudContainer = findViewById(R.id.hudContainer)
        hudIso = findViewById(R.id.hudIso)
        hudExposure = findViewById(R.id.hudExposure)
        hudFocus = findViewById(R.id.hudFocus)
        hudFps = findViewById(R.id.hudFps)
        hudTemp = findViewById(R.id.hudTemp)
        hudBattery = findViewById(R.id.hudBattery)
        hudClients = findViewById(R.id.hudClients)
        hudNetwork = findViewById(R.id.hudNetwork)
        
        // FABs
        fabSwitchCamera = findViewById(R.id.fabSwitchCamera)
        fabFlash = findViewById(R.id.fabFlash)
        fabTakePhoto = findViewById(R.id.fabTakePhoto)
        fabToggleHud = findViewById(R.id.fabToggleHud)
        fabControls = findViewById(R.id.fabControls)
        fabContainer = findViewById(R.id.fabContainer)
        
        // Bottom Sheet
        bottomSheet = findViewById(R.id.bottomSheet)
    }
    
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        
        // Bottom Sheet Controls
        btnCameraWide = bottomSheet.findViewById(R.id.btnCameraWide)
        btnCameraUltra = bottomSheet.findViewById(R.id.btnCameraUltra)
        btnCameraTele = bottomSheet.findViewById(R.id.btnCameraTele)
        btnCameraFront = bottomSheet.findViewById(R.id.btnCameraFront)
        seekIso = bottomSheet.findViewById(R.id.seekIso)
        txtIsoValue = bottomSheet.findViewById(R.id.txtIsoValue)
        seekExposure = bottomSheet.findViewById(R.id.seekExposure)
        txtExposureValue = bottomSheet.findViewById(R.id.txtExposureValue)
        seekFocus = bottomSheet.findViewById(R.id.seekFocus)
        txtFocusValue = bottomSheet.findViewById(R.id.txtFocusValue)
        btnLockAE = bottomSheet.findViewById(R.id.btnLockAE)
        btnLockAF = bottomSheet.findViewById(R.id.btnLockAF)
        btnFlash = bottomSheet.findViewById(R.id.btnFlash)
    }
    
    private fun setupFABs() {
        // FAB: Trocar câmera
        fabSwitchCamera.setOnClickListener {
            cycleCamera()
            showToast("📷 Câmera: ${currentCamera.uppercase()}")
        }
        
        // FAB: Flash
        fabFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            // Aqui você integraria com Camera2Controller para ativar flash
            showToast(if (isFlashOn) "💡 Flash LIGADO" else "💡 Flash DESLIGADO")
        }
        
        // FAB: Tirar foto
        fabTakePhoto.setOnClickListener {
            // Aqui você integraria com Camera2Controller para capturar foto
            showToast("📸 Foto capturada!")
            animateFAB(fabTakePhoto)
        }
        
        // FAB: Alternar HUD
        fabToggleHud.setOnClickListener {
            toggleHud()
        }
        
        // FAB: Abrir controles (Bottom Sheet)
        fabControls.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                bottomSheet.visibility = View.VISIBLE
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }
    
    private fun setupBottomSheetControls() {
        // Botões de seleção de câmera
        btnCameraWide.setOnClickListener { selectCamera("wide", btnCameraWide) }
        btnCameraUltra.setOnClickListener { selectCamera("ultra", btnCameraUltra) }
        btnCameraTele.setOnClickListener { selectCamera("tele", btnCameraTele) }
        btnCameraFront.setOnClickListener { selectCamera("front", btnCameraFront) }
        
        // SeekBar ISO
        seekIso.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val iso = mapProgressToIso(progress)
                txtIsoValue.text = iso.toString()
                // Aqui você integraria com Camera2Controller para ajustar ISO
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // SeekBar Exposição
        seekExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val exposure = mapProgressToExposure(progress)
                txtExposureValue.text = exposure
                // Aqui você integraria com Camera2Controller para ajustar exposição
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // SeekBar Foco
        seekFocus.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val focus = if (progress == 0) "AUTO" else "${progress}%"
                txtFocusValue.text = focus
                // Aqui você integraria com Camera2Controller para ajustar foco
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Botões de lock
        btnLockAE.setOnClickListener {
            isAELocked = !isAELocked
            btnLockAE.text = if (isAELocked) "🔒 Exp" else "🔓 Exp"
            showToast(if (isAELocked) "Exposição bloqueada" else "Exposição desbloqueada")
        }
        
        btnLockAF.setOnClickListener {
            isAFLocked = !isAFLocked
            btnLockAF.text = if (isAFLocked) "🔒 Foco" else "🔓 Foco"
            showToast(if (isAFLocked) "Foco bloqueado" else "Foco desbloqueado")
        }
        
        btnFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            btnFlash.text = if (isFlashOn) "💡 Flash ON" else "💡 Flash OFF"
        }
    }
    
    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleUICompactMode()
                return true
            }
        })
        
        cameraPreview.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }
    
    private fun cycleCamera() {
        currentCamera = when (currentCamera) {
            "wide" -> "ultra"
            "ultra" -> "tele"
            "tele" -> "front"
            else -> "wide"
        }
        // Aqui você integraria com Camera2Controller para trocar câmera
    }
    
    private fun selectCamera(camera: String, button: Button) {
        currentCamera = camera
        
        // Reseta todos os botões
        listOf(btnCameraWide, btnCameraUltra, btnCameraTele, btnCameraFront).forEach {
            it.setBackgroundResource(R.drawable.bg_button_secondary)
            it.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
        
        // Destaca botão selecionado
        button.setBackgroundResource(R.drawable.bg_button_primary)
        button.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        
        showToast("📷 ${camera.uppercase()} selecionada")
        // Aqui você integraria com Camera2Controller para trocar câmera
    }
    
    private fun toggleHud() {
        isHudVisible = !isHudVisible
        hudContainer.visibility = if (isHudVisible) View.VISIBLE else View.GONE
        animateFAB(fabToggleHud)
    }
    
    private fun toggleUICompactMode() {
        val isCompact = topOverlay.visibility == View.VISIBLE
        
        topOverlay.visibility = if (isCompact) View.GONE else View.VISIBLE
        fabContainer.visibility = if (isCompact) View.GONE else View.VISIBLE
        rtspIndicator.visibility = if (isCompact) View.GONE else View.VISIBLE
        hudContainer.visibility = if (isCompact || !isHudVisible) View.GONE else View.VISIBLE
        
        showToast(if (isCompact) "👁️ Modo Compacto" else "📱 Modo Normal")
    }
    
    private fun updateHudInfo() {
        // ISO e Exposição (você pode integrar com Camera2Controller)
        hudIso.text = "ISO: ${txtIsoValue.text}"
        hudExposure.text = "EXP: ${txtExposureValue.text}"
        hudFocus.text = "FOCUS: ${txtFocusValue.text}"
        
        // FPS (placeholder - integrar com RtspServer)
        hudFps.text = "FPS: 30"
        
        // Temperatura (placeholder)
        hudTemp.text = "TEMP: 38°C"
        
        // Bateria
        val batteryLevel = getBatteryLevel()
        hudBattery.text = "BAT: ${batteryLevel}%"
        hudBattery.setTextColor(
            when {
                batteryLevel > 50 -> 0xFF10b981.toInt()
                batteryLevel > 20 -> 0xFFfbbf24.toInt()
                else -> 0xFFef4444.toInt()
            }
        )
        
        // Clientes conectados (placeholder - integrar com RtspServer)
        hudClients.text = "CLIENTS: 0"
        
        // Tráfego de rede (placeholder)
        hudNetwork.text = "NET: 0.0MB/s"
    }
    
    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    private fun mapProgressToIso(progress: Int): Int {
        // Mapeia 0-100 para 50-3200
        return 50 + (progress * 31.5).toInt()
    }
    
    private fun mapProgressToExposure(progress: Int): String {
        // Mapeia 0-100 para diferentes tempos de exposição
        return when (progress) {
            in 0..10 -> "1/8000s"
            in 11..20 -> "1/4000s"
            in 21..30 -> "1/2000s"
            in 31..40 -> "1/1000s"
            in 41..50 -> "1/500s"
            in 51..60 -> "1/250s"
            in 61..70 -> "1/125s"
            in 71..80 -> "1/60s"
            in 81..90 -> "1/30s"
            else -> "1/15s"
        }
    }
    
    private fun animateFAB(fab: View) {
        ObjectAnimator.ofFloat(fab, "scaleX", 1f, 1.2f, 1f).apply {
            duration = 200
            start()
        }
        ObjectAnimator.ofFloat(fab, "scaleY", 1f, 1.2f, 1f).apply {
            duration = 200
            start()
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        StreamingService.instance?.rtspServer?.attachTextureView(cameraPreview)
        updateStatusUI()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "onConfigurationChanged — stream mantido")
    }

    private fun startStreamingService() {
        val intent = Intent(this, StreamingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateStatusUI() {
        val ip = getLocalIpAddress()
        val rtspUrl = "rtsp://$ip:8554/live"
        val webUrl  = "http://$ip:8080"
        statusText.text    = "📡 $rtspUrl\n🌐 $webUrl"
        rtspIndicator.text = "● RTSP :8554"
        rtspIndicator.setTextColor(0xFF10b981.toInt())
    }

    private fun getLocalIpAddress(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.let { interfaces ->
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address)
                            return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) { Log.e("MainActivity", "Erro ao obter IP", e) }
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
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            grantResults[1] == PackageManager.PERMISSION_GRANTED
        ) {
            startStreamingService()
        } else {
            statusText.text = "❌ Permissões de câmera/áudio negadas"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hudUpdateHandler.removeCallbacks(hudUpdateRunnable)
        Log.d("MainActivity", "Activity destruída — serviço continua rodando")
    }
}
