package fr.pilmil.sillon.ui.garden

import fr.pilmil.sillon.model.CompanionBenefitType
import fr.pilmil.sillon.model.EvidenceLevel
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.Vegetable
import fr.pilmil.sillon.model.badRelationsWith
import fr.pilmil.sillon.model.goodRelationsWith
import fr.pilmil.sillon.model.strongest

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
    /** Phrase courte du conseil : une tradition ou une idée plausible n'est pas présentée comme un fait. */
    fun sentence(): String {
        val secondName = second.name.replaceFirstChar { it.lowercase() }
        return when {
            positive && evidence == EvidenceLevel.TRADITION -> "Selon la tradition, ${first.name} et $secondName font bon ménage"
            positive && evidence == EvidenceLevel.PLAUSIBLE && protects -> "${first.name} et $secondName se protégeraient mutuellement"
            positive && evidence == EvidenceLevel.PLAUSIBLE -> "${first.name} et $secondName s'entraideraient sans doute"
            positive && protects -> "${first.name} et $secondName se protègent mutuellement"
            positive -> "${first.name} et $secondName s'entraident"
            evidence == EvidenceLevel.TRADITION -> "La tradition déconseille ${first.name} à côté de $secondName : à vous de voir"
            evidence == EvidenceLevel.PLAUSIBLE -> "${first.name} et $secondName risquent de se gêner : mieux vaut les éloigner"
            else -> "${first.name} et $secondName se gênent : à éloigner"
        }
    }
}

/**
 * Texte pour lecteur d'écran d'une case : alerte ou affinité avec ses voisins orthogonaux, niveau de preuve compris.
 * Null quand la case n'a ni l'une ni l'autre.
 */
internal fun neighborSpokenNote(veg: Vegetable, neighbors: List<Vegetable>): String? {
    val bad = neighbors.flatMap { veg.badRelationsWith(it) }.strongest()
    if (bad != null) {
        return when (bad.evidence) {
            EvidenceLevel.ETAYE -> "Alerte voisinage, étayée par des essais"
            EvidenceLevel.PLAUSIBLE -> "Alerte voisinage, plausible mais peu étudiée"
            EvidenceLevel.TRADITION -> "Alerte voisinage selon la tradition de jardinage"
        }
    }
    val good = neighbors.flatMap { veg.goodRelationsWith(it) }.strongest() ?: return null
    return when (good.evidence) {
        EvidenceLevel.ETAYE -> "Bon voisinage, étayé par des essais"
        EvidenceLevel.PLAUSIBLE -> "Bon voisinage, plausible mais peu étudié"
        EvidenceLevel.TRADITION -> "Bon voisinage selon la tradition de jardinage"
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
