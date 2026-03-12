import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL     = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const RESEND_API_KEY   = Deno.env.get('RESEND_API_KEY')!
const MP_ACCESS_TOKEN  = Deno.env.get('MP_ACCESS_TOKEN')!  // Mercado Pago

// ── Gera serial no formato XXXX-XXXX-XXXX ─────────────────────────────────
function generateSerial(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789' // sem 0,O,1,I (confusos)
  const part  = () => Array.from({ length: 4 },
    () => chars[Math.floor(Math.random() * chars.length)]).join('')
  return `${part()}-${part()}-${part()}`
}

// ── Envia email com o serial via Resend ────────────────────────────────────
async function sendLicenseEmail(email: string, nome: string, serial: string) {
  const res = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${RESEND_API_KEY}`,
      'Content-Type':  'application/json',
    },
    body: JSON.stringify({
      from:    'Camera2 RTSP <licenca@camera2rtsp.app>',
      to:      email,
      subject: '✅ Sua licença Camera2 RTSP está pronta!',
      html: `
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"></head>
<body style="font-family:sans-serif;background:#0f172a;color:#f1f5f9;padding:32px;">
  <div style="max-width:480px;margin:0 auto;">
    <h1 style="color:#38bdf8;font-size:22px;">🎥 Camera2 RTSP</h1>
    <p style="font-size:16px;">Olá, <strong>${nome}</strong>!</p>
    <p>Seu pagamento foi confirmado. Aqui está sua licença:</p>

    <div style="background:#1e293b;border-radius:12px;padding:24px;text-align:center;margin:24px 0;">
      <p style="color:#94a3b8;font-size:12px;margin:0 0 8px;">SERIAL DE ATIVAÇÃO</p>
      <p style="font-family:monospace;font-size:28px;font-weight:bold;color:#38bdf8;
                letter-spacing:4px;margin:0;">${serial}</p>
    </div>

    <p style="font-size:14px;">Como ativar:</p>
    <ol style="font-size:14px;line-height:1.8;">
      <li>Instale o APK no seu celular</li>
      <li>Abra o app — a tela de ativação abrirá automaticamente</li>
      <li>Digite o serial acima e toque em <strong>ATIVAR AGORA</strong></li>
      <li>Pronto! O app estará liberado</li>
    </ol>

    <p style="font-size:12px;color:#475569;margin-top:32px;">
      ⚠️ Este serial é pessoal e está vinculado ao seu dispositivo.<br>
      Guarde este email em local seguro.
    </p>

    <hr style="border-color:#1e293b;margin:24px 0;">
    <p style="font-size:11px;color:#334155;">Dúvidas? Responda este email.</p>
  </div>
</body>
</html>`,
    }),
  })
  if (!res.ok) {
    const err = await res.text()
    console.error('Resend error:', err)
    throw new Error('Falha ao enviar email: ' + err)
  }
}

serve(async (req) => {
  try {
    const body = await req.json()
    console.log('Webhook recebido:', JSON.stringify(body))

    // Só processa notificações de pagamento do Mercado Pago
    if (body.type !== 'payment' && body.action !== 'payment.updated') {
      return new Response('ok')
    }

    // ── Consulta o pagamento na API do MP ───────────────────────────────────
    const paymentId = body.data?.id
    if (!paymentId) return new Response('ok')

    const mpRes = await fetch(
      `https://api.mercadopago.com/v1/payments/${paymentId}`,
      { headers: { 'Authorization': `Bearer ${MP_ACCESS_TOKEN}` } }
    )
    const payment = await mpRes.json()
    console.log('Payment status:', payment.status)

    // Só prossegue se pagamento aprovado
    if (payment.status !== 'approved') return new Response('ok')

    const email = payment.payer?.email ?? ''
    const nome  = payment.payer?.first_name ?? 'Cliente'

    if (!email) {
      console.error('Email do pagador não encontrado')
      return new Response('ok')
    }

    // ── Evita processar o mesmo pagamento duas vezes ────────────────────────
    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)
    const { data: existing } = await supabase
      .from('licenses')
      .select('id')
      .eq('payment_id', String(paymentId))
      .single()

    if (existing) {
      console.log('Pagamento já processado:', paymentId)
      return new Response('ok')
    }

    // ── Gera serial único ───────────────────────────────────────────────────
    let serial = ''
    let attempts = 0
    while (attempts < 10) {
      const candidate = generateSerial()
      const { data: dup } = await supabase
        .from('licenses').select('id').eq('serial', candidate).single()
      if (!dup) { serial = candidate; break }
      attempts++
    }
    if (!serial) throw new Error('Não foi possível gerar serial único')

    // ── Salva no banco ──────────────────────────────────────────────────────
    await supabase.from('licenses').insert({
      serial,
      email,
      payment_id: String(paymentId),
      is_active:  true,
      expires_at: null,    // null = vitalício; ou: new Date(+new Date() + 365*86400000)
      created_at: new Date().toISOString(),
    })

    // ── Envia email com o serial ────────────────────────────────────────────
    await sendLicenseEmail(email, nome, serial)
    console.log(`Licença criada: ${serial} → ${email}`)

    return new Response('ok')

  } catch (e) {
    console.error('payment-webhook error:', e)
    return new Response('Internal Error', { status: 500 })
  }
})
