package fr.pilmil.sillon

import fr.pilmil.sillon.data.GardenStore
import fr.pilmil.sillon.data.PlatformGardenStorage
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.GrowingEnvironment
import fr.pilmil.sillon.model.LightSource
import fr.pilmil.sillon.model.LocalWeather
import fr.pilmil.sillon.model.Orientation
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.model.WindExposure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeGardenStorage(
    private var content: String? = null,
) : PlatformGardenStorage {
    var quarantineCalls = 0
        private set
    var writeCalls = 0
        private set

    override fun exists(): Boolean = content != null

    override fun readText(): String? = content

    override fun writeTextAtomic(content: String): Boolean {
        writeCalls++
        this.content = content
        return true
    }

    override fun quarantineCorrupt() {
        quarantineCalls++
        content = null
    }
}

class GardenStoreTest {

    @Test
    fun loadGardenCreatesAndSavesDefaultWhenMissing() {
        val storage = FakeGardenStorage(null)
        val store = GardenStore(storage)

        val garden = store.loadGarden()

        assertEquals("Mon Potager", garden.name)
        assertEquals(1, garden.parcels.size)
        assertEquals(1, storage.writeCalls)
        assertFalse(store.consumeRecoveryNotice())
    }

    @Test
    fun loadGardenQuarantinesCorruptJsonAndSetsOneShotRecoveryNotice() {
        val storage = FakeGardenStorage("{ invalid json")
        val store = GardenStore(storage)

        val garden = store.loadGarden()

        assertEquals("Mon Potager", garden.name)
        assertEquals(1, storage.quarantineCalls)
        assertEquals(1, storage.writeCalls)
        assertTrue(store.consumeRecoveryNotice())
        assertFalse(store.consumeRecoveryNotice())
    }

    @Test
    fun updateSlotRecordsHistoryOnlyWhenReplacingOrClearingDifferentVegetable() {
        val store = GardenStore(FakeGardenStorage())
        store.loadGarden()

        // Plantation initiale sur une case vide (0,0 est "radis" dans le jardin par défaut,
        // on ajoute une parcelle vierge pour tester à partir de zéro).
        val added = store.addParcel("Planche", ParcelType.ROW_BED, 2, 2)
        val parcelId = added.garden.parcels.last().id

        val planted = store.updateSlot(parcelId, 0, 0, "tomate")
        assertTrue(planted.saved)
        assertTrue(planted.garden.history.isEmpty())
        val slotAfterPlant = planted.garden.parcels.last().getSlot(0, 0)
        assertEquals("tomate", slotAfterPlant?.vegetableId)
        assertNotNull(slotAfterPlant?.plantedDate)

        // Remplacement par la même plante : aucun historique ajouté
        val sameVeg = store.updateSlot(parcelId, 0, 0, "tomate")
        assertTrue(sameVeg.garden.history.isEmpty())

        // Remplacement par une autre plante : l'ancienne est archivée dans l'historique
        val replaced = store.updateSlot(parcelId, 0, 0, "carotte")
        assertEquals(1, replaced.garden.history.size)
        val entry = replaced.garden.history.single()
        assertEquals(parcelId, entry.parcelId)
        assertEquals(0, entry.row)
        assertEquals(0, entry.col)
        assertEquals("tomate", entry.vegetableId)
        assertEquals(BotanicalFamily.SOLANACEAE, entry.family)

        // Vidage de la case : la carotte est archivée et le slot est supprimé
        val cleared = store.updateSlot(parcelId, 0, 0, null)
        assertEquals(2, cleared.garden.history.size)
        assertNull(cleared.garden.parcels.last().getSlot(0, 0))
    }

    @Test
    fun addParcelAndDeleteParcelEnforceBoundsAndKeepAtLeastOneParcel() {
        val store = GardenStore(FakeGardenStorage())
        val initial = store.loadGarden()
        val initialId = initial.parcels.single().id

        // Impossible de supprimer la dernière parcelle
        val afterDeleteAttempt = store.deleteParcel(initialId)
        assertEquals(1, afterDeleteAttempt.garden.parcels.size)

        // Ajout avec nom vide et bornes dépassées
        val afterAdd = store.addParcel(
            name = "   ",
            type = ParcelType.CONTAINER,
            rows = 99,
            cols = 0,
            containerVolumeLiters = 5000,
        )
        assertEquals(2, afterAdd.garden.parcels.size)
        val newParcel = afterAdd.garden.parcels.last()
        assertEquals("Nouvelle parcelle", newParcel.name)
        assertEquals(8, newParcel.rows)
        assertEquals(1, newParcel.cols)
        assertEquals(2000, newParcel.containerVolumeLiters)

        // Suppression possible maintenant qu'il y en a deux
        val afterDelete = store.deleteParcel(newParcel.id)
        assertEquals(1, afterDelete.garden.parcels.size)
        assertEquals(initialId, afterDelete.garden.parcels.single().id)
    }

    @Test
    fun settingsUpdatesCoerceValues() {
        val store = GardenStore(FakeGardenStorage())
        store.loadGarden()

        val env = store.updateEnvironment(
            environment = GrowingEnvironment.INDOOR,
            orientation = Orientation.SE,
            windExposure = WindExposure.LOW,
            covered = true,
            lightSource = LightSource.GROW_LIGHT,
            growLightHoursPerDay = 30,
        ).garden
        assertEquals(GrowingEnvironment.INDOOR, env.environment)
        assertEquals(Orientation.SE, env.orientation)
        assertEquals(WindExposure.LOW, env.windExposure)
        assertTrue(env.covered)
        assertEquals(LightSource.GROW_LIGHT, env.lightSource)
        assertEquals(24, env.growLightHoursPerDay)

        val frost = store.updateFrostDate(month = 15, day = 0).garden
        assertEquals(FrostDate(12, 1), frost.frostDate)

        val alt = store.updateAltitude(-50).garden
        assertEquals(0, alt.altitudeMeters)
        val altMax = store.updateAltitude(4500).garden
        assertEquals(3000, altMax.altitudeMeters)

        val water = store.updateWateringConditions(mulched = true, localWeather = LocalWeather.DRY_HOT).garden
        assertTrue(water.mulched)
        assertEquals(LocalWeather.DRY_HOT, water.localWeather)
    }

    @Test
    fun exportCsvStringFormatsBomHeaderAndSlots() {
        val store = GardenStore(FakeGardenStorage())
        store.loadGarden()
        val added = store.addParcel("Pot Basilic", ParcelType.CONTAINER, 1, 2)
        val parcelId = added.garden.parcels.last().id
        store.updateSlot(parcelId, 0, 0, "basilic")

        val csv = store.exportCsvString()
        assertTrue(csv.startsWith("\uFEFF"))
        val lines = csv.removePrefix("\uFEFF").trimEnd().lines()
        assertEquals("Parcelle;Type;Ligne;Colonne;Plante ID;Date Plantation", lines.first())
        assertTrue(lines.any { it.startsWith("Pot Basilic;Pot / jardinière;1;1;basilic;") })
        assertTrue(lines.any { it == "Pot Basilic;Pot / jardinière;1;2;Vide;" })
    }

    @Test
    fun unreadableStorageReturnsDefaultWithoutQuarantineAndWriteFailurePropagates() {
        val unreadable = object : PlatformGardenStorage {
            var quarantineCalled = false
            var writeCalled = false
            override fun exists(): Boolean = true
            override fun readText(): String? = null
            override fun writeTextAtomic(content: String): Boolean {
                writeCalled = true
                return false
            }
            override fun quarantineCorrupt() {
                quarantineCalled = true
            }
        }
        val store = GardenStore(unreadable)
        val garden = store.loadGarden()
        assertEquals("Mon Potager", garden.name)
        assertFalse(unreadable.quarantineCalled)
        assertFalse(unreadable.writeCalled)
        assertFalse(store.consumeRecoveryNotice())

        val failedUpdate = store.updateAltitude(500)
        assertFalse(failedUpdate.saved)
        assertEquals(500, failedUpdate.garden.altitudeMeters)
    }

    @Test
    fun updateSlotWithUnknownParcelLeavesGardenUnchanged() {
        val store = GardenStore(FakeGardenStorage())
        val initial = store.loadGarden()
        val updated = store.updateSlot("inexistant", 0, 0, "tomate")
        assertTrue(updated.saved)
        assertEquals(initial.parcels, updated.garden.parcels)
    }
}
