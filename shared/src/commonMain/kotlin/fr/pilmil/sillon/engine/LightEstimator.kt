package fr.pilmil.sillon.engine

import kotlin.math.roundToInt

/**
 * Repères indicatifs d'éclairement lumineux instantané (en lux, pondération photopique CIE).
 *
 * Note méthodologique : un capteur de téléphone mesure un éclairement ponctuel en lux et non
 * le flux de photons photosynthétiques intégré sur la journée (PPFD / DLI). Ces seuils servent
 * uniquement d'aide au repérage d'un rebord de fenêtre ou d'un balcon à différents moments du jour :
 * - < 1 000 lx : éclairage ambiant de pièce, insuffisant sans lampe horticole.
 * - 1 000 à 4 999 lx : rebord lumineux sans soleil direct ou ciel couvert.
 * - 5 000 à 14 999 lx : fenêtre très claire / voilée ou lampe horticole à distance moyenne.
 * - >= 15 000 lx : rayonnement solaire direct ou lampe horticole proche.
 */
enum class AmbientLightCategory(
    val frenchLabel: String,
    val guidance: String,
) {
    LOW(
        frenchLabel = "Lumière faible (< 1 000 lx)",
        guidance = "Insuffisant pour des cultures potagères sur la durée : rapprochez le pot d'une fenêtre exposée ou utilisez une lampe horticole.",
    ),
    MEDIUM(
        frenchLabel = "Lumière tamisée (1 000 – 5 000 lx)",
        guidance = "Convient aux aromatiques tolérantes à la mi-ombre (menthe, persil, ciboulette) et aux jeunes pousses.",
    ),
    BRIGHT_INDIRECT(
        frenchLabel = "Lumière vive (5 000 – 15 000 lx)",
        guidance = "Convient aux salades à couper, radis, roquette, épinard et à la plupart des herbes aromatiques.",
    ),
    DIRECT_SUN(
        frenchLabel = "Plein soleil / lampe proche (≥ 15 000 lx)",
        guidance = "Éclairement élevé adapté aux cultures exigeantes en lumière (basilic, piment, tomate cerise, fraise).",
    ),
}

object LightEstimator {
    fun classifyLux(lux: Float): AmbientLightCategory {
        val safeLux = lux.coerceAtLeast(0f)
        return when {
            safeLux < 1_000f -> AmbientLightCategory.LOW
            safeLux < 5_000f -> AmbientLightCategory.MEDIUM
            safeLux < 15_000f -> AmbientLightCategory.BRIGHT_INDIRECT
            else -> AmbientLightCategory.DIRECT_SUN
        }
    }

    fun formatSummary(lux: Float): String {
        val rounded = lux.coerceAtLeast(0f).roundToInt()
        val category = classifyLux(lux)
        return "$rounded lx · ${category.frenchLabel}"
    }
}
