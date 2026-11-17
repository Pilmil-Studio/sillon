package fr.pilmil.sillon.ui.garden

import fr.pilmil.sillon.model.CompanionBenefitType
import fr.pilmil.sillon.model.EvidenceLevel
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.Vegetable
import fr.pilmil.sillon.model.badRelationsWith
import fr.pilmil.sillon.model.goodRelationsWith
import fr.pilmil.sillon.model.strongest
import fr.pilmil.sillon.shared.resources.*
import org.jetbrains.compose.resources.StringResource

/** Affinités et alertes d'une case, d'après ses quatre voisins orthogonaux. */
internal data class CellNeighborhood(val hasGood: Boolean, val hasBad: Boolean)

/** Une relation détectée entre deux plantes voisines, pour la carte « Voisinage ». */
internal data class NeighborLink(
    val first: Vegetable,
    val second: Vegetable,
    val positive: Boolean,
    val protects: Boolean,
    val evidence: EvidenceLevel,
) {
    /** Phrase courte du conseil (deux arguments : les noms) : une tradition ou une idée plausible n'est pas présentée comme un fait. */
    fun sentenceRes(): StringResource = when {
        positive && evidence == EvidenceLevel.TRADITION -> Res.string.garden_link_tradition_good
        positive && evidence == EvidenceLevel.PLAUSIBLE && protects -> Res.string.garden_link_plausible_protect
        positive && evidence == EvidenceLevel.PLAUSIBLE -> Res.string.garden_link_plausible_help
        positive && protects -> Res.string.garden_link_protect
        positive -> Res.string.garden_link_help
        evidence == EvidenceLevel.TRADITION -> Res.string.garden_link_tradition_clash
        evidence == EvidenceLevel.PLAUSIBLE -> Res.string.garden_link_plausible_clash
        else -> Res.string.garden_link_clash
    }
}

/**
 * Texte pour lecteur d'écran d'une case : alerte ou affinité avec ses voisins orthogonaux, niveau de preuve compris.
 * Null quand la case n'a ni l'une ni l'autre.
 */
internal fun neighborSpokenNoteRes(veg: Vegetable, neighbors: List<Vegetable>): StringResource? {
    val bad = neighbors.flatMap { veg.badRelationsWith(it) }.strongest()
    if (bad != null) {
        return when (bad.evidence) {
            EvidenceLevel.ETAYE -> Res.string.garden_slot_bad_etaye
            EvidenceLevel.PLAUSIBLE -> Res.string.garden_slot_bad_plausible
            EvidenceLevel.TRADITION -> Res.string.garden_slot_bad_tradition
        }
    }
    val good = neighbors.flatMap { veg.goodRelationsWith(it) }.strongest() ?: return null
    return when (good.evidence) {
        EvidenceLevel.ETAYE -> Res.string.garden_slot_good_etaye
        EvidenceLevel.PLAUSIBLE -> Res.string.garden_slot_good_plausible
        EvidenceLevel.TRADITION -> Res.string.garden_slot_good_tradition
    }
}

/** Voisins orthogonaux d'une case, pour les vues qui n'ont pas la carte complète. */
internal fun orthogonalNeighbors(parcel: Parcel, vegetables: List<Vegetable>, r: Int, c: Int): List<Vegetable> =
    neighborsOf(parcel, vegetables, r, c)

private fun neighborsOf(parcel: Parcel, vegetables: List<Vegetable>, r: Int, c: Int): List<Vegetable> = listOfNotNull(
    parcel.getSlot(r - 1, c)?.vegetableId?.let { id -> vegetables.find { it.id == id } },
    parcel.getSlot(r + 1, c)?.vegetableId?.let { id -> vegetables.find { it.id == id } },
    parcel.getSlot(r, c - 1)?.vegetableId?.let { id -> vegetables.find { it.id == id } },
    parcel.getSlot(r, c + 1)?.vegetableId?.let { id -> vegetables.find { it.id == id } },
)

/** Même logique que la vue à plat : une case a une affinité (ou une alerte) si une relation la lie à l'un de ses voisins, dans un sens ou dans l'autre. */
internal fun neighborhoodOf(parcel: Parcel, vegetables: List<Vegetable>): Map<Pair<Int, Int>, CellNeighborhood> {
    val result = HashMap<Pair<Int, Int>, CellNeighborhood>()
    for (r in 0 until parcel.rows) {
        for (c in 0 until parcel.cols) {
            val veg = vegetables.find { it.id == parcel.getSlot(r, c)?.vegetableId }
            val neighborVegs = neighborsOf(parcel, vegetables, r, c)
            // Lecture dans les deux sens : A vers B est aussi vu depuis la case de B.
            val hasGood = veg != null && neighborVegs.any { neighbor -> veg.goodRelationsWith(neighbor).isNotEmpty() }
            val hasBad = veg != null && neighborVegs.any { neighbor -> veg.badRelationsWith(neighbor).isNotEmpty() }
            result[r to c] = CellNeighborhood(hasGood, hasBad)
        }
    }
    return result
}

/** Paires de plantes voisines concernées, sans doublon (A-B et B-A comptent pour une seule). */
internal fun neighborLinks(parcel: Parcel, vegetables: List<Vegetable>): List<NeighborLink> {
    val seen = HashSet<String>()
    val links = ArrayList<NeighborLink>()
    for (r in 0 until parcel.rows) {
        for (c in 0 until parcel.cols) {
            val veg = vegetables.find { it.id == parcel.getSlot(r, c)?.vegetableId } ?: continue
            for (neighbor in neighborsOf(parcel, vegetables, r, c)) {
                if (neighbor.id == veg.id) continue
                val good = veg.goodRelationsWith(neighbor).strongest()
                val bad = veg.badRelationsWith(neighbor).strongest()
                for ((relation, positive) in listOf(good to true, bad to false)) {
                    if (relation == null) continue
                    val key = listOf(veg.id, neighbor.id).sorted().joinToString("|") + positive
                    if (seen.add(key)) {
                        links += NeighborLink(
                            first = veg,
                            second = neighbor,
                            positive = positive,
                            protects = relation.benefitType == CompanionBenefitType.PEST_REPELLENT,
                            evidence = relation.evidence,
                        )
                    }
                }
            }
        }
    }
    return links
}
