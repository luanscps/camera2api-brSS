# 🚀 Setup Completo — Sistema de Licenciamento

## 1. Banco de Dados (Supabase)

1. Acesse [supabase.com](https://supabase.com) → seu projeto
2. Vá em **SQL Editor** → **New Query**
3. Cole o conteúdo de `supabase/setup.sql` e clique em **Run**

---

## 2. Edge Functions (Supabase)

Instale a CLI do Supabase:
```bash
npm install -g supabase
supabase login
supabase link --project-ref rqjxvzzfgoagcsxihdwe
```

Deploy das funções:
```bash
supabase functions deploy activate-license
supabase functions deploy payment-webhook
```

Configura os secrets (variáveis de ambiente das funções):
```bash
supabase secrets set RESEND_API_KEY=re_buFBYdek_PsDFfKre1gMMCucab1pmBvso
supabase secrets set MP_ACCESS_TOKEN=SEU_TOKEN_MERCADO_PAGO_AQUI
```

---

## 3. Mercado Pago — Webhook

1. Acesse [mercadopago.com.br/developers](https://www.mercadopago.com.br/developers)
2. Vá em **Suas integrações** → **Webhooks**
3. Adicione a URL:
   ```
   https://rqjxvzzfgoagcsxihdwe.supabase.co/functions/v1/payment-webhook
   ```
4. Selecione evento: **Pagamentos**
5. Copie o **Access Token** de produção e cole no secrets acima

---

## 4. Resend — Domínio remetente

1. Acesse [resend.com](https://resend.com) → **Domains**
2. Adicione e verifique seu domínio (ou use `onboarding@resend.dev` para testes)
3. No `payment-webhook/index.ts`, altere o campo `from:` para seu email verificado

---

## 5. Build do APK

```bash
# No Android Studio:
# Build → Generate Signed Bundle/APK → APK → Release
# ou via terminal:
./gradlew assembleRelease
```

O APK gerado estará em `app/build/outputs/apk/release/app-release.apk`

---

## 6. Gerar seriais manualmente (venda manual via WhatsApp)

No SQL Editor do Supabase:
```sql
INSERT INTO licenses (serial, email, is_active)
VALUES ('ABCD-EFGH-IJKL', 'cliente@email.com', true);
```

Ou use o script Python:
```python
import secrets, string

def gerar_serial():
    chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    p = lambda: ''.join(secrets.choice(chars) for _ in range(4))
    return f'{p()}-{p()}-{p()}'

for _ in range(10):
    print(gerar_serial())
```

---

## 7. Fluxo completo de venda automática

```
Cliente paga via link do Mercado Pago
        ↓
Mercado Pago dispara webhook automaticamente
        ↓
Edge Function gera serial único
        ↓
Salva no banco de dados
        ↓
Resend envia email com serial + instruções
        ↓
Cliente instala APK → digita serial → ativa
        ↓
Serial vinculado ao dispositivo para sempre
```

---

## 8. Gerenciar licenças

No painel do Supabase → **Table Editor** → `licenses`:

- **Ver todas**: listagem visual com filtros
- **Revogar**: edite `is_active = false`
- **Resetar device**: zere o campo `device_id` para reativar em novo celular
