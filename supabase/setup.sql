-- ============================================================
-- Camera2 RTSP — Setup do banco de licenças no Supabase
-- Cole este SQL no SQL Editor do seu projeto Supabase
-- ============================================================

-- Tabela principal de licenças
CREATE TABLE IF NOT EXISTS licenses (
  id           UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
  serial       TEXT        UNIQUE NOT NULL,
  email        TEXT,
  payment_id   TEXT,
  device_id    TEXT,
  device_model TEXT,
  activated_at TIMESTAMPTZ,
  expires_at   TIMESTAMPTZ,           -- NULL = vitalício
  is_active    BOOLEAN     DEFAULT true,
  created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Índices para performance
CREATE INDEX IF NOT EXISTS idx_licenses_serial    ON licenses (serial);
CREATE INDEX IF NOT EXISTS idx_licenses_email     ON licenses (email);
CREATE INDEX IF NOT EXISTS idx_licenses_device_id ON licenses (device_id);

-- Row Level Security: bloqueia acesso direto pela anon key
ALTER TABLE licenses ENABLE ROW LEVEL SECURITY;

-- Apenas as Edge Functions (service_role) podem ler/escrever
CREATE POLICY "service_only" ON licenses
  USING (auth.role() = 'service_role')
  WITH CHECK (auth.role() = 'service_role');

-- ============================================================
-- Inserir seriais manualmente (opcional, para venda manual)
-- ============================================================
-- INSERT INTO licenses (serial, email, is_active)
-- VALUES ('ABCD-EFGH-IJKL', 'cliente@email.com', true);

-- ============================================================
-- Ver todas as licenças
-- ============================================================
-- SELECT serial, email, device_model, activated_at, is_active FROM licenses ORDER BY created_at DESC;

-- ============================================================
-- Revogar uma licença
-- ============================================================
-- UPDATE licenses SET is_active = false WHERE serial = 'XXXX-XXXX-XXXX';

-- ============================================================
-- Resetar device (cliente trocou de celular)
-- ============================================================
-- UPDATE licenses SET device_id = NULL, device_model = NULL, activated_at = NULL
-- WHERE serial = 'XXXX-XXXX-XXXX';
