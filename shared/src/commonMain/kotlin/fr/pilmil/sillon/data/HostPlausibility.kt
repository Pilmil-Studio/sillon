package fr.pilmil.sillon.data

import fr.pilmil.sillon.model.BotanicalFamily

/**
 * Plausibilité d'un hôte pour une maladie ou un ravageur : sert de connaissance a priori au diagnostic photo
 * quand la plante est connue (diagnostic lancé depuis une plantation).
 *
 * La table se déduit de [DiseaseCatalog] : une cause qui vise des familles précises (doryphore, piéride, altises...)
 * est plausible sur ces familles et peu plausible ailleurs ; une cause à large spectre l'est partout. Rien n'est
 * jamais mis à zéro : [FLOOR] garde une chance à une cause même sur un hôte inattendu, au cas où la plante serait
 * mal renseignée dans le potager.
 */
object HostPlausibility {

    /** Plancher du coefficient, jamais zéro. */
    const val FLOOR = 0.05f

    /**
     * Causes traitées comme larges même quand la fiche liste des familles : la liste de la fiche donne des
     * exemples courants, pas une limite (oïdium, rouille, botrytis, pucerons, limaces).
     */
    private val BROAD_SPECTRUM = setOf("oidium", "rouille", "botrytis", "pucerons", "limaces")

    /** Coefficient de 0,05 à 1 pour la cause [catalogId] sur un plant de la famille [family]. */
    fun plausibility(catalogId: String, family: BotanicalFamily): Float {
        if (catalogId in BROAD_SPECTRUM) return 1f
        val targets = DiseaseCatalog.getById(catalogId)?.targetFamilies ?: return 1f
        return if (targets.isEmpty() || family in targets) 1f else FLOOR
    }
}
