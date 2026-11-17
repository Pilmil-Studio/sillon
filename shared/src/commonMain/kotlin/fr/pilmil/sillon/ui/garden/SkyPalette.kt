package fr.pilmil.sillon.ui.garden

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/*
 * Couleurs du ciel selon la phase, interpolées d'une phase à l'autre pour qu'aucun palier ne soit visible.
 * Purement décoratif : aucune information de l'appli ne dépend de ces teintes.
 */

/** Dégradé du ciel, du zénith à l'horizon. */
internal class SkyColors(val top: Color, val mid: Color, val horizon: Color) {
    /** Couleur du dégradé à la fraction [t] de la hauteur du ciel (0 = haut, 1 = horizon), pour fondre un objet dedans. */
    fun at(t: Float): Color =
        if (t < 0.5f) lerp(top, mid, (t * 2f).coerceIn(0f, 1f)) else lerp(mid, horizon, ((t - 0.5f) * 2f).coerceIn(0f, 1f))
}

private fun lerpColors(a: SkyColors, b: SkyColors, t: Float) =
    SkyColors(lerp(a.top, b.top, t), lerp(a.mid, b.mid, t), lerp(a.horizon, b.horizon, t))

private val NightSky = SkyColors(Color(0xFF0E1B33), Color(0xFF173149), Color(0xFF29493F))
private val DawnSky = SkyColors(Color(0xFF7F9CCF), Color(0xFFF2B8B0), Color(0xFFFFD9A0))
private val DaySky = SkyColors(Color(0xFF9FD0E0), Color(0xFFCBE6E0), Color(0xFFF4E8CB))
private val DuskSky = SkyColors(Color(0xFF55508F), Color(0xFFE58A78), Color(0xFFFFB064))

/** Dégradé du ciel pour [sky] : nuit, aube rose, jour, crépuscule orangé, avec fondu continu entre les quatre. */
internal fun skyColorsFor(sky: SkyState): SkyColors {
    val p = sky.progress
    return when (sky.phase) {
        SkyPhase.NIGHT -> NightSky
        SkyPhase.DAY -> DaySky
        SkyPhase.DAWN -> if (p < 0.5f) lerpColors(NightSky, DawnSky, p * 2f) else lerpColors(DawnSky, DaySky, (p - 0.5f) * 2f)
        SkyPhase.DUSK -> if (p < 0.5f) lerpColors(DaySky, DuskSky, p * 2f) else lerpColors(DuskSky, NightSky, (p - 0.5f) * 2f)
    }
}

/** Couleur du Soleil : jaune pâle haut dans le ciel, orangé près de l'horizon ([height] de 0 à 1). */
internal fun sunColor(height: Float): Color = lerp(Color(0xFFFF9A4A), Color(0xFFFFF3B0), height.coerceIn(0f, 1f))

// Panneau portant le nom de la parcelle : seul texte dessiné dans la scène. L'encre reste sombre et le fond du panneau
// ne s'assombrit presque pas la nuit (comme un panneau éclairé) : le contraste reste au-dessus de 4,5 à toute heure.
internal val SignInk = Color(0xFF3A2312)
private val SignFaceDay = Color(0xFFD2A46C)
private val SignFaceNight = Color(0xFF14223A)
private const val SIGN_NIGHT_DIM = 0.15f

/** Fond clair du panneau ; [night] de 0 (jour) à 1 (nuit). */
internal fun signFace(night: Float): Color = lerp(SignFaceDay, SignFaceNight, SIGN_NIGHT_DIM * night.coerceIn(0f, 1f))
