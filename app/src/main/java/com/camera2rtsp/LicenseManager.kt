package com.camera2rtsp

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

object LicenseManager {

    private const val TAG       = "LicenseManager"
    private const val PREFS     = "cam_license_v1"
    private const val SUPABASE_URL = "https://rqjxvzzfgoagcsxihdwe.supabase.co"
    private const val SUPABASE_ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJxanh2enpmZ29hZ2NzeGloZHdlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzMzMjQ0MDAsImV4cCI6MjA4ODkwMDQwMH0.khbppeoBGg1DPaGveTpT7hAetp-uAhAXigqE4LpQrGg"
    private const val ACTIVATE_FN  = "/functions/v1/activate-license"

    // ── Device fingerprint ─────────────────────────────────────────────────────
    fun getDeviceId(ctx: Context): String {
        val androidId = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val raw = androidId + Build.MODEL + Build.MANUFACTURER + Build.BOARD
        return sha256(raw).take(32)
    }

    fun getDeviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Verifica licença salva localmente ──────────────────────────────────────
    fun isLicensed(ctx: Context): Boolean {
        return try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val token      = prefs.getString("token", null)      ?: return false
            val savedDev   = prefs.getString("device_id", null)  ?: return false
            val expiresAt  = prefs.getLong("expires_at", -1L)

            if (token.isBlank()) return false
            // Valida device id para evitar cópia de prefs para outro aparelho
            if (savedDev != getDeviceId(ctx)) {
                Log.w(TAG, "Device ID diverge — licença inválida")
                return false
            }
            // 0L = vitalício; caso contrário verifica expiração
            if (expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
                Log.w(TAG, "Licença expirada")
                return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar licença", e)
            false
        }
    }

    // ── Ativação online ────────────────────────────────────────────────────────
    fun activate(ctx: Context, serial: String): ActivationResult {
        return try {
            val deviceId    = getDeviceId(ctx)
            val deviceModel = getDeviceModel()
            val body = JSONObject().apply {
                put("serial",       serial.trim().uppercase().replace(" ", ""))
                put("device_id",    deviceId)
                put("device_model", deviceModel)
            }

            val url  = URL(SUPABASE_URL + ACTIVATE_FN)
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $SUPABASE_ANON")
                setRequestProperty("apikey", SUPABASE_ANON)
                connectTimeout = 10_000
                readTimeout    = 10_000
                doOutput       = true
                outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).readText()
            Log.d(TAG, "activate response [$responseCode]: $response")

            val json = JSONObject(response)
            if (json.optBoolean("ok", false)) {
                val token      = json.getString("token")
                val expiresAt  = json.optLong("expires_at", 0L)
                saveLocally(ctx, token, deviceId, expiresAt)
                ActivationResult.Success
            } else {
                val err = json.optString("error", "erro_desconhecido")
                ActivationResult.Failure(err)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro de rede na ativação", e)
            ActivationResult.Failure("sem_conexao")
        }
    }

    // ── Salva token localmente ─────────────────────────────────────────────────
    private fun saveLocally(ctx: Context, token: String, deviceId: String, expiresAt: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token",     token)
            .putString("device_id", deviceId)
            .putLong("expires_at",  expiresAt)
            .apply()
    }

    // ── Revoga licença local (ex: para testes) ─────────────────────────────────
    fun revoke(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ── Serial formatado enquanto digita: XXXX-XXXX-XXXX ──────────────────────
    fun formatSerial(raw: String): String {
        val clean = raw.uppercase().replace("-", "").replace(" ", "")
            .filter { it.isLetterOrDigit() }
            .take(12)
        return clean.chunked(4).joinToString("-")
    }
}

sealed class ActivationResult {
    object Success : ActivationResult()
    data class Failure(val reason: String) : ActivationResult()
}
