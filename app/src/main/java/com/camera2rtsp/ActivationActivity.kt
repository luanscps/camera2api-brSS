package com.camera2rtsp

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivationActivity : AppCompatActivity() {

    private lateinit var inputSerial : EditText
    private lateinit var btnActivate : Button
    private lateinit var txtStatus   : TextView
    private lateinit var txtDeviceId : TextView
    private lateinit var progressBar : ProgressBar
    private lateinit var btnBuy      : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activation)

        inputSerial = findViewById(R.id.inputSerial)
        btnActivate = findViewById(R.id.btnActivate)
        txtStatus   = findViewById(R.id.txtStatus)
        txtDeviceId = findViewById(R.id.txtDeviceId)
        progressBar = findViewById(R.id.progressBar)
        btnBuy      = findViewById(R.id.btnBuy)

        // Exibe o device ID do aparelho (util para suporte)
        val devId = LicenseManager.getDeviceId(this)
        txtDeviceId.text = "ID: ${devId.take(8)}-${devId.substring(8, 16)}..."

        // Auto-formata o serial enquanto o usuario digita (XXXX-XXXX-XXXX)
        inputSerial.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val raw       = s.toString()
                val formatted = LicenseManager.formatSerial(raw)
                if (raw != formatted) {
                    inputSerial.setText(formatted)
                    inputSerial.setSelection(formatted.length)
                }
                isFormatting = false
            }
        })

        btnActivate.setOnClickListener { tryActivate() }

        // Botao de compra — abre WhatsApp (substitua pelo seu numero)
        btnBuy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/55XXXXXXXXXXX?text=Quero+comprar+Camera2+RTSP"))
            startActivity(intent)
        }
    }

    private fun tryActivate() {
        val serial = inputSerial.text.toString().trim()
        if (serial.replace("-", "").length < 12) {
            showStatus("⚠️ Digite o serial completo (XXXX-XXXX-XXXX)", isError = true)
            return
        }

        setLoading(true)
        showStatus("🔄 Verificando serial...", isError = false)

        // lifecycleScope cancela automaticamente se a Activity for destruida
        lifecycleScope.launch(Dispatchers.IO) {
            val result = LicenseManager.activate(this@ActivationActivity, serial)
            withContext(Dispatchers.Main) {
                setLoading(false)
                when (result) {
                    is ActivationResult.Success -> {
                        showStatus("✅ Ativado com sucesso! Iniciando...", isError = false)
                        btnActivate.isEnabled = false
                        lifecycleScope.launch {
                            delay(1400)
                            startActivity(Intent(this@ActivationActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                    is ActivationResult.Failure -> {
                        val msg = when (result.reason) {
                            "serial_invalido"          -> "❌ Serial nao encontrado."
                            "serial_revogado"          -> "❌ Serial revogado. Contate o suporte."
                            "serial_outro_dispositivo" -> "❌ Serial ja ativado em outro celular."
                            "serial_expirado"          -> "❌ Licenca expirada."
                            "sem_conexao"              -> "⚠️ Sem internet. Verifique a conexao."
                            else                       -> "❌ Erro: ${result.reason}"
                        }
                        showStatus(msg, isError = true)
                    }
                }
            }
        }
    }

    private fun showStatus(msg: String, isError: Boolean) {
        txtStatus.text = msg
        // Color.parseColor evita o erro de Long/Int com literais hexadecimais grandes
        txtStatus.setTextColor(
            if (isError) Color.parseColor("#EF4444") else Color.parseColor("#10B981")
        )
        txtStatus.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnActivate.isEnabled  = !loading
        inputSerial.isEnabled  = !loading
    }
}
