package fr.pilmil.sillon

import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.ui.garden.FrontSlot
import fr.pilmil.sillon.ui.garden.lerpSlots
import fr.pilmil.sillon.ui.garden.slotsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GardenSceneMotionTest {

    private fun parcels(count: Int) = (0 until count).map {
        Parcel(id = "p$it", name = "Carré $it", type = ParcelType.RAISED_BED_SQUARES, rows = 2, cols = 2)
    }

    @Test
    fun selectedParcelTakesTheFrontAndOthersGoToTheBack() {
        val slots = slotsFor(parcels(3), selectedIndex = 1)
        assertEquals(FrontSlot, slots["p1"])
        assertTrue(slots["p0"]!!.z0 > 1f && slots["p2"]!!.z0 > 1f)
        assertTrue(slots["p0"]!!.cx < 0f && slots["p2"]!!.cx > 0f)
    }

    @Test
    fun onlyThreeParcelsFitAtTheBack() {
        val slots = slotsFor(parcels(6), selectedIndex = 0)
        assertEquals(4, slots.size)
        assertEquals(null, slots["p5"])
    }

    @Test
    fun emptyGardenHasNoSlots() {
        assertTrue(slotsFor(emptyList(), 0).isEmpty())
    }

    @Test
    fun movingBetweenParcelsInterpolatesFromOldToNewSlots() {
        val from = slotsFor(parcels(3), selectedIndex = 0)
        val to = slotsFor(parcels(3), selectedIndex = 1)
        assertEquals(from, lerpSlots(from, to, 0f))
        assertEquals(to, lerpSlots(from, to, 1f))
        val mid = lerpSlots(from, to, 0.5f)
        val oldFront = assertNotNull(mid["p0"])
        val newFront = assertNotNull(mid["p1"])
        assertTrue(oldFront.s > 0.46f && oldFront.s < 1f, "l'ancien carré recule : ${oldFront.s}")
        assertTrue(newFront.s > 0.46f && newFront.s < 1f, "le nouveau carré avance : ${newFront.s}")
        assertTrue(newFront.z0 > 0f && newFront.z0 < from["p1"]!!.z0)
    }

    @Test
    fun reboundOvershootsTheTargetSlightlyForParcelsPresentInBothLayouts() {
        val from = slotsFor(parcels(3), selectedIndex = 0)
        val to = slotsFor(parcels(3), selectedIndex = 1)
        val over = lerpSlots(from, to, 1.04f)
        assertTrue(over["p1"]!!.s > 1f)
        assertEquals(to, lerpSlots(from, to, 1f))
    }

    @Test
    fun parcelsEnteringOrLeavingStartAndEndFarAway() {
        val from = slotsFor(parcels(5), selectedIndex = 0) // p4 absent
        val to = slotsFor(parcels(5), selectedIndex = 4) // p4 devant, p3 absent
        val start = lerpSlots(from, to, 0f)
        assertTrue(start["p4"]!!.s < 0.25f && start["p4"]!!.z0 > 2f)
        assertNotNull(start["p3"])
        val end = lerpSlots(from, to, 1f)
        assertEquals(FrontSlot, end["p4"])
        assertEquals(null, end["p3"])
        val overshoot = lerpSlots(from, to, 1.05f)
        assertTrue(overshoot["p4"]!!.s <= 1.1f)
        assertTrue(overshoot["p3"]!!.s >= 0.2f - 0.0001f)
    }
}
