# ⚡ Quick Start - v4-ui

> **Guia rápido para testar a nova interface em 5 minutos**

---

## 🚀 Instalação Rápida

### **1️⃣ Clone e Checkout**

```bash
# Se ainda não clonou o repositório
git clone https://github.com/luanscps/camera2api-brSS.git
cd camera2api-brSS

# Checkout da branch v4-ui
git checkout v4-ui
```

### **2️⃣ Abra no Android Studio**

```
1. File → Open
2. Selecione a pasta camera2api-brSS
3. Aguarde o Gradle sync automático
```

### **3️⃣ Conecte seu Samsung Note10+**

```
1. Ative "Depuração USB" no celular
2. Conecte via cabo USB
3. Aceite a autorização no celular
```

### **4️⃣ Build e Instale**

**Opção A - Via Android Studio:**
```
Clique no botão "Run" (Shift+F10)
```

**Opção B - Via Terminal:**
```bash
./gradlew installDebug
```

### **5️⃣ Conceda Permissões**

No primeiro uso, o app vai pedir:
- 📷 Câmera
- 🎤 Microfone
- 🔔 Notificações (Android 13+)
- 🌐 Rede

**Clique em "Permitir" para todas.**

---

## 🎮 Testando as 5 Melhorias

### ✅ **1. HUD (Informações em Tempo Real)**

**Localização:** Canto superior direito

**O que ver:**
- ISO, Exposição, Foco
- FPS, Temperatura
- Nível de bateria
- Clientes conectados
- Tráfego de rede

**Teste:**
```
1. Observe os valores atualizando a cada 1 segundo
2. Veja a cor da bateria mudar conforme o nível
```

---

### ✅ **2. FABs (Botões Flutuantes)**

**Localização:** Lado esquerdo da tela

**Botões (de cima para baixo):**
1. 📷 **Trocar Câmera**
2. 💡 **Flash**
3. 📸 **Tirar Foto**
4. 👁️ **Toggle HUD**

**Teste cada botão:**
```
✅ Trocar câmera: Ver toast "Câmera: WIDE/ULTRA/TELE/FRONT"
✅ Flash: Toast "Flash LIGADO/DESLIGADO"
✅ Tirar foto: Toast "Foto capturada!" + animação
✅ Toggle HUD: HUD aparece/desaparece
```

---

### ✅ **3. Bottom Sheet (Painel de Controles)**

**Como abrir:**
- Toque no **FAB principal** (canto inferior esquerdo)
- Ou **deslize de baixo para cima**

**O que testar:**

**A) Seleção de Câmera**
```
1. Toque em cada botão: Wide | Ultra | Tele | Front
2. Veja o botão ficar azul quando selecionado
3. Toast confirma a seleção
```

**B) Controle de ISO**
```
1. Arraste o slider ISO
2. Veja o valor mudar em tempo real (50-3200)
3. Valor também atualiza no HUD
```

**C) Controle de Exposição**
```
1. Arraste o slider Exposição
2. Veja o tempo mudar (1/8000s até 1/15s)
3. Valor também atualiza no HUD
```

**D) Controle de Foco**
```
1. Arraste o slider Foco
2. 0 = AUTO, >0 = MANUAL
3. Veja "AUTO" ou percentual
```

**E) Botões de Lock**
```
✅ Lock Exposição: Toque e veja mudar 🔒/🔓
✅ Lock Foco: Toque e veja mudar 🔒/🔓
✅ Flash: Toque e veja ON/OFF
```

**Fechar Bottom Sheet:**
```
- Deslize para baixo
- Ou toque fora do painel
```

---

### ✅ **4. Notificações Visuais**

**Toasts:**
Cada ação mostra um toast com emoji:
```
📷 "Câmera: WIDE"
💡 "Flash LIGADO"
📸 "Foto capturada!"
etc.
```

**Indicador RTSP:**
**Localização:** Canto inferior direito
```
🟭 Verde = RTSP Online
🔴 Vermelho = RTSP Offline
```

**Cores no HUD:**
```
Bateria:
  🟢 Verde: >50%
  🟡 Amarelo: 20-50%
  🔴 Vermelho: <20%
```

---

### ✅ **5. Modo Compacto + Gestos**

**Gesto:** Duplo toque na preview da câmera

**O que acontece:**
```
Modo Normal → Modo Compacto:
  - Top overlay desaparece
  - FABs desaparecem
  - HUD desaparece
  - Indicador RTSP desaparece
  - Preview limpo, sem UI

Modo Compacto → Modo Normal:
  - Tudo volta a aparecer
```

**Teste:**
```
1. Dê duplo toque rápido na preview
2. Veja toast "Modo Compacto"
3. UI desaparece
4. Dê duplo toque novamente
5. Veja toast "Modo Normal"
6. UI reaparece
```

---

## 🔍 Checklist de Testes

**Copie e cole para acompanhar:**

```markdown
### HUD
- [ ] HUD aparece no canto superior direito
- [ ] Valores atualizam a cada 1 segundo
- [ ] Cor da bateria muda conforme nível

### FABs
- [ ] 4 FABs visíveis no lado esquerdo
- [ ] Botão trocar câmera funciona
- [ ] Botão flash funciona
- [ ] Botão foto funciona com animação
- [ ] Botão toggle HUD funciona

### Bottom Sheet
- [ ] FAB principal abre/fecha bottom sheet
- [ ] Botões de câmera funcionam
- [ ] Slider ISO funciona
- [ ] Slider Exposição funciona
- [ ] Slider Foco funciona
- [ ] Botões de lock funcionam
- [ ] Botão flash funciona

### Notificações
- [ ] Toasts aparecem para cada ação
- [ ] Indicador RTSP visível
- [ ] Cores no HUD corretas

### Modo Compacto
- [ ] Duplo toque alterna modo
- [ ] UI desaparece no modo compacto
- [ ] UI reaparece no modo normal
- [ ] Toasts confirmam transição
```

---

## ⚠️ Troubleshooting

### **Problema: App não compila**

**Solução:**
```bash
# Limpe o build
./gradlew clean

# Rebuild
./gradlew build
```

### **Problema: Bottom Sheet não abre**

**Verificações:**
```
1. Certifique-se que Material Design está nas dependências
2. Verifique se CoordinatorLayout foi adicionado
3. Faça Gradle Sync
```

### **Problema: HUD não atualiza**

**Causa:** Handler pode não estar iniciando

**Solução:**
```
Verifique MainActivity.onCreate():
hudUpdateHandler.post(hudUpdateRunnable) deve estar presente
```

### **Problema: Duplo toque não funciona**

**Verificação:**
```kotlin
// Em MainActivity, deve ter:
cameraPreview.setOnTouchListener { _, event ->
    gestureDetector.onTouchEvent(event)
    true
}
```

---

## 📝 Próximos Passos

Depois de testar tudo:

1. **Integre com Camera2Controller**
   - Conecte os SeekBars aos parâmetros reais da câmera
   - Implemente captura de foto funcional
   - Conecte flash ao hardware

2. **Adicione Persistência**
   ```kotlin
   // Salvar preferências
   SharedPreferences prefs = getSharedPreferences("camera2rtsp", MODE_PRIVATE)
   prefs.edit().putInt("iso", isoValue).apply()
   ```

3. **Melhore Feedback**
   - Adicione animações mais elaboradas
   - Implemente vibração no toque
   - Crie efeitos visuais no preview

---

## 📦 Exportar APK

Se quiser instalar em outros dispositivos:

```bash
# Gerar APK de debug
./gradlew assembleDebug

# APK estará em:
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📚 Documentação Completa

Para mais detalhes:
- 📘 [README_v4-UI.md](README_v4-UI.md) - Documentação completa
- 📝 [CHANGELOG_v4-ui.md](CHANGELOG_v4-ui.md) - Todas as mudanças

---

## ❓ Precisa de Ajuda?

**GitHub Issues:** [Abrir issue](https://github.com/luanscps/camera2api-brSS/issues)  
**Email:** luanscps@gmail.com

---

<p align="center">
  <strong>Bons testes! 🚀</strong>
</p>
