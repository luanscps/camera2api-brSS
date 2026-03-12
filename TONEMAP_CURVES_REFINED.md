# Tonemap Curves Refinados - Fase 1 Cinema

## Substituir no Camera2Controller.kt (linhas ~120-167)

```kotlin
// ── Cinema: Tonemap Curve Builders (REFINED) ──────────────────────────────
// Cada preset retorna FloatArray de pares (in, out) normalizados [0..1]
// Camera2 requer mínimo 2 pontos, máximo 64. Recomendado: 9-17 pontos.

private fun buildLinear(): FloatArray = floatArrayOf(
    0.0f, 0.0f,
    1.0f, 1.0f
)

private fun buildSCurve(): FloatArray {
    // S-curve cinematográfica agressiva: crush blacks + roll highlights
    // Baseada em curvas de DaVinci Resolve e ACES
    return floatArrayOf(
        0.00f, 0.00f,
        0.05f, 0.01f,  // crush deep shadows
        0.10f, 0.05f,  // lift shadow detail
        0.20f, 0.15f,
        0.30f, 0.28f,
        0.40f, 0.42f,
        0.50f, 0.50f,  // midtone anchor (unchanged)
        0.60f, 0.58f,
        0.70f, 0.70f,
        0.80f, 0.82f,
        0.90f, 0.92f,  // soft roll highlights
        0.95f, 0.96f,
        1.00f, 1.00f
    )
}

private fun buildLogCurve(): FloatArray {
    // Log-C estilo ARRI: máxima preservação de dynamic range
    // Comprime highlights agressivamente, levanta sombras
    val points = mutableListOf<Float>()
    for (i in 0..16) {
        val x = i / 16f
        // Log-C formula: y = c * log10(a * x + b) + d
        val a = 5.555556f
        val b = 0.047996f
        val c = 0.244161f
        val d = 0.386036f
        val y = (c * kotlin.math.log10(a * x + b) + d).coerceIn(0f, 1f)
        points.add(x)
        points.add(y)
    }
    return points.toFloatArray()
}

private fun buildCinematicCurve(): FloatArray {
    // Perfil "Filmic" estilo Blender: sombras levantadas, highlights protegidos
    // Mid-tones com contraste aumentado
    return floatArrayOf(
        0.00f, 0.03f,  // lift blacks (evita pure black)
        0.05f, 0.08f,
        0.10f, 0.14f,
        0.20f, 0.26f,
        0.30f, 0.37f,
        0.40f, 0.48f,
        0.50f, 0.55f,  // mid-tone boost
        0.60f, 0.64f,
        0.70f, 0.74f,
        0.80f, 0.83f,
        0.90f, 0.91f,  // roll highlights
        0.95f, 0.95f,
        1.00f, 0.97f   // cap highlights (protege de clipping)
    )
}

private fun buildPower22(): FloatArray {
    // Gamma 2.2 standard (sRGB): curva power law
    // y = x^(1/2.2) ≈ x^0.4545
    val points = mutableListOf<Float>()
    val gamma = 1.0f / 2.2f
    for (i in 0..16) {
        val x = i / 16f
        val y = x.pow(gamma)
        points.add(x)
        points.add(y)
    }
    return points.toFloatArray()
}
```

## Atualizar handler de tonemapCurve (linha ~700)

```kotlin
// ── Cinema: Tonemap Curve Selection ───────────────────────────────────
params["tonemapCurve"]?.let {
    customTonemapCurve = it as String
    tonemapCurvePoints = when (customTonemapCurve) {
        "linear"     -> buildLinear()
        "s-curve"    -> buildSCurve()
        "log"        -> buildLogCurve()
        "cinematic"  -> buildCinematicCurve()
        "power22"    -> buildPower22()  // NOVO
        else         -> buildSCurve()   // fallback
    }
    // Forçar modo CONTRAST_CURVE e reaplicar
    tonemapMode = CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
    applyPostProcessing()
    Log.d(tag, "tonemapCurve -> $customTonemapCurve (${tonemapCurvePoints.size / 2} pontos)\")\n}
```

## Características das curvas:

| Preset | Uso cinematográfico | Matemática |
|--------|---------------------|------------|
| **Linear** | RAW-like, sem processamento | `y = x` |
| **S-Curve** | Blockbuster Hollywood (contraste alto, blacks crushed) | Interpolação cúbica com pontos críticos |
| **Log** | ARRI Alexa / RED (max DR para grading) | `y = c·log10(a·x + b) + d` |
| **Cinematic** | Blender Filmic (proteção highlights, lift shadows) | Piecewise com lift/roll |
| **Power 2.2** | Standard sRGB para monitores | `y = x^0.4545` |

