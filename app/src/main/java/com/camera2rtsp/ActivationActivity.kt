package com.camera2rtsp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
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

        // Exibe o device ID do aparelho (útil para suporte)
        val devId = LicenseManager.getDeviceId(this)
        txtDeviceId.text = "ID: ${devId.take(8)}-${devId.substring(8, 16)}…"

        // Auto-formata o serial enquanto o usuário digita (XXXX-XXXX-XXXX)
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

        // Botão de compra — abre link externo (configure seu link aqui)
        btnBuy.setOnClickListener {
            val intent = Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://wa.me/55XXXXXXXXXXX?text=Quero+comprar+Camera2+RTSP")
            }
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

        CoroutineScope(Dispatchers.IO).launch {
            val result = LicenseManager.activate(this@ActivationActivity, serial)
            withContext(Dispatchers.Main) {
                setLoading(false)
                when (result) {
                    is ActivationResult.Success -> {
                        showStatus("✅ Ativado com sucesso! Iniciando...", isError = false)
                        btnActivate.isEnabled = false
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1400)
                            startActivity(Intent(this@ActivationActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                    is ActivationResult.Failure -> {
                        val msg = when (result.reason) {
                            "serial_invalido"           -> "❌ Serial não encontrado."
                            "serial_revogado"           -> "❌ Serial revogado. Contate o suporte."
                            "serial_outro_dispositivo"  -> "❌ Serial já ativado em outro celular."
                            "serial_expirado"           -> "❌ Licença expirada."
                            "sem_conexao"               -> "⚠️ Sem internet. Verifique a conexão."
                            else                        -> "❌ Erro: ${result.reason}"
                        }
                        showStatus(msg, isError = true)
                    }
                }
            }
        }
    }

    private fun showStatus(msg: String, isError: Boolean) {
        txtStatus.text = msg
        txtStatus.setTextColor(
            if (isError) 0xFFEF4444.toInt() else 0xFF10B981.toInt()
        )
        txtStatus.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnActivate.isEnabled  = !loading
        inputSerial.isEnabled  = !loading
    }
}
