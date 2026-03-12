import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL      = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY  = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const RESEND_API_KEY    = Deno.env.get('RESEND_API_KEY')!

// ── Gerador de token de sessão simples ─────────────────────────────────────
function generateToken(serial: string, deviceId: string): string {
  const payload = btoa(JSON.stringify({
    s: serial,
    d: deviceId.substring(0, 8),
    t: Date.now()
  }))
  return `cam2_${payload}`
}

serve(async (req) => {
  const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  }

  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { serial, device_id, device_model } = await req.json()

    if (!serial || !device_id) {
      return Response.json(
        { ok: false, error: 'serial_invalido' },
        { headers: corsHeaders, status: 400 }
      )
    }

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

    // ── Busca o serial no banco ─────────────────────────────────────────────
    const { data: license, error: dbErr } = await supabase
      .from('licenses')
      .select('*')
      .eq('serial', serial.toUpperCase().trim())
      .single()

    if (dbErr || !license) {
      return Response.json(
        { ok: false, error: 'serial_invalido' },
        { headers: corsHeaders, status: 404 }
      )
    }

    if (!license.is_active) {
      return Response.json(
        { ok: false, error: 'serial_revogado' },
        { headers: corsHeaders, status: 403 }
      )
    }

    // ── Verifica expiração ──────────────────────────────────────────────────
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return Response.json(
        { ok: false, error: 'serial_expirado' },
        { headers: corsHeaders, status: 403 }
      )
    }

    // ── Verifica se já está vinculado a outro device ────────────────────────
    if (license.device_id && license.device_id !== device_id) {
      return Response.json(
        { ok: false, error: 'serial_outro_dispositivo' },
        { headers: corsHeaders, status: 403 }
      )
    }

    // ── Primeira ativação: vincula ao device ────────────────────────────────
    if (!license.device_id) {
      await supabase.from('licenses').update({
        device_id,
        device_model: device_model ?? 'desconhecido',
        activated_at: new Date().toISOString(),
      }).eq('serial', serial.toUpperCase().trim())
    }

    const token     = generateToken(serial, device_id)
    const expiresAt = license.expires_at
      ? new Date(license.expires_at).getTime()
      : 0

    return Response.json(
      { ok: true, token, expires_at: expiresAt },
      { headers: corsHeaders }
    )

  } catch (e) {
    console.error('activate-license error:', e)
    return Response.json(
      { ok: false, error: 'erro_interno' },
      { headers: corsHeaders, status: 500 }
    )
  }
})
