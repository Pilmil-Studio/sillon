package fr.pilmil.sillon

import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.ui.garden.CellNeighborhood
import fr.pilmil.sillon.ui.garden.neighborLinks
import fr.pilmil.sillon.ui.garden.neighborhoodOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GardenNeighborhoodTest {

    private val catalog = VegetableCatalog.ALL_VEGETABLES

    @Test
    fun neighborhoodOfDetectsOrthogonalGoodAndBadAndIgnoresDiagonal() {
        // Grille 2x2 :
        // (0,0)=carotte  (0,1)=poireau (synergie orthogonale + répulsif)
        // (1,0)=vide     (1,1)=haricot_nain (en diagonale de carotte, orthogonal à poireau)
        // Vérifions tomate + pomme_de_terre en diagonale : aucun antagonisme orthogonal
        val diagonalOnly = Parcel(
            id = "p_diag",
            name = "Diagonale",
            type = ParcelType.RAISED_BED_SQUARES,
            rows = 2,
            cols = 2,
            slots = listOf(
                ParcelSlot(0, 0, "tomate"),
                ParcelSlot(1, 1, "pomme_de_terre"),
            ),
        )
        val diagResult = neighborhoodOf(diagonalOnly, catalog)
        assertEquals(CellNeighborhood(hasGood = false, hasBad = false), diagResult[0 to 0])
        assertEquals(CellNeighborhood(hasGood = false, hasBad = false), diagResult[1 to 1])
        assertEquals(CellNeighborhood(hasGood = false, hasBad = false), diagResult[0 to 1])

        // Orthogonal : tomate (0,0) + basilic (0,1) + pomme_de_terre (1,0)
        val orthoParcel = Parcel(
            id = "p_ortho",
            name = "Orthogonal",
            type = ParcelType.RAISED_BED_SQUARES,
            rows = 2,
            cols = 2,
            slots = listOf(
                ParcelSlot(0, 0, "tomate"),
                ParcelSlot(0, 1, "basilic"),
                ParcelSlot(1, 0, "pomme_de_terre"),
            ),
        )
        val orthoResult = neighborhoodOf(orthoParcel, catalog)
        assertEquals(CellNeighborhood(hasGood = true, hasBad = true), orthoResult[0 to 0])
        assertEquals(CellNeighborhood(hasGood = true, hasBad = false), orthoResult[0 to 1])
        assertEquals(CellNeighborhood(hasGood = false, hasBad = true), orthoResult[1 to 0])
        assertEquals(CellNeighborhood(hasGood = false, hasBad = false), orthoResult[1 to 1])
    }

    @Test
    fun neighborLinksDeduplicatesSymmetricPairsAndMarksProtects() {
        val parcel = Parcel(
            id = "p_links",
            name = "Liens",
            type = ParcelType.RAISED_BED_SQUARES,
            rows = 2,
            cols = 2,
            slots = listOf(
                ParcelSlot(0, 0, "carotte"),
                ParcelSlot(0, 1, "poireau"),
                ParcelSlot(1, 0, "carotte"), // même légume adjacent à (0,0) : ignoré
                ParcelSlot(1, 1, "tomate"),
            ),
        )
        val links = neighborLinks(parcel, catalog)
        val carrotLeek = links.filter {
            setOf(it.first.id, it.second.id) == setOf("carotte", "poireau")
        }
        assertEquals(1, carrotLeek.size)
        assertTrue(carrotLeek.single().positive)
        assertTrue(carrotLeek.single().protects)

        // Aucun lien auto-référent carotte-carotte
        assertFalse(links.any { it.first.id == it.second.id })
    }
}
