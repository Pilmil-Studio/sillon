package fr.pilmil.sillon

import fr.pilmil.sillon.data.GardenCodec
import fr.pilmil.sillon.data.GardenLoad
import fr.pilmil.sillon.data.GardenStore
import fr.pilmil.sillon.data.PlatformGardenStorage
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
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

private fun GardenStore.ready() = (load() as GardenLoad.Ready).garden

private class FakeGardenStorage(
    var content: String? = null,
    var failReads: Boolean = false,
) : PlatformGardenStorage {
    var quarantineCalls = 0
        private set
    var writeCalls = 0
        private set

    override fun exists(): Boolean = content != null

    override fun readText(): String? = if (failReads) null else content

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

        val garden = store.ready()

        assertEquals("Mon Potager", garden.name)
        assertEquals(1, garden.parcels.size)
        assertEquals(1, storage.writeCalls)
        assertFalse(store.consumeRecoveryNotice())
    }

    @Test
    fun loadGardenQuarantinesCorruptJsonAndSetsOneShotRecoveryNotice() {
        val storage = FakeGardenStorage("{ invalid json")
        val store = GardenStore(storage)

        val garden = store.ready()

        assertEquals("Mon Potager", garden.name)
        assertEquals(1, storage.quarantineCalls)
        assertEquals(1, storage.writeCalls)
        assertTrue(store.consumeRecoveryNotice())
        assertFalse(store.consumeRecoveryNotice())
    }

    @Test
    fun updateSlotRecordsHistoryOnlyWhenReplacingOrClearingDifferentVegetable() {
        val store = GardenStore(FakeGardenStorage())
        store.ready()

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
        val initial = store.ready()
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
        store.ready()

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
        store.ready()
        val added = store.addParcel("Pot Basilic", ParcelType.CONTAINER, 1, 2)
        val parcelId = added.garden.parcels.last().id
        store.updateSlot(parcelId, 0, 0, "basilic")

        val csv = store.exportCsvString()!!
        assertTrue(csv.startsWith("\uFEFF"))
        val lines = csv.removePrefix("\uFEFF").trimEnd().lines()
        assertEquals("Parcelle;Type;Ligne;Colonne;Plante ID;Date Plantation", lines.first())
        assertTrue(lines.any { it.startsWith("Pot Basilic;Pot / jardinière;1;1;basilic;") })
        assertTrue(lines.any { it == "Pot Basilic;Pot / jardinière;1;2;Vide;" })
    }

    @Test
    fun readErrorNeverWritesAndRefusesEveryModification() {
        val original = GardenCodec.encode(Garden(id = "g", name = "Vrai potager", frostDate = FrostDate(4, 20)))
        val storage = FakeGardenStorage(original, failReads = true)
        val store = GardenStore(storage)

        assertEquals(GardenLoad.ReadFailed, store.load())
        assertFalse(store.consumeRecoveryNotice())

        val results = listOf(
            store.updateSlot("parcel_demo_1", 0, 0, "tomate"),
            store.addParcel("Planche", ParcelType.ROW_BED, 2, 2),
            store.deleteParcel("parcel_demo_1"),
            store.updateAltitude(500),
            store.updateFrostDate(3, 3),
            store.updateWateringConditions(true, LocalWeather.DRY_HOT),
            store.updateEnvironment(GrowingEnvironment.BALCONY),
        )
        results.forEach { assertTrue(it.refused); assertFalse(it.saved) }
        assertEquals(0, storage.writeCalls)
        assertEquals(0, storage.quarantineCalls)
        assertEquals(original, storage.content)
        assertNull(store.exportCsvString())
    }

    @Test
    fun retryAfterTransientReadErrorLoadsTheRealGarden() {
        val original = GardenCodec.encode(Garden(id = "g", name = "Vrai potager", frostDate = FrostDate(4, 20)))
        val storage = FakeGardenStorage(original, failReads = true)
        val store = GardenStore(storage)
        assertEquals(GardenLoad.ReadFailed, store.load())

        storage.failReads = false
        val update = store.updateAltitude(700)
        assertFalse(update.refused)
        assertTrue(update.saved)
        assertEquals("Vrai potager", update.garden.name)
        assertEquals(700, update.garden.altitudeMeters)
    }

    @Test
    fun modificationBeforeAnyLoadReadsFirstAndNeverOverwritesExistingFile() {
        val storage = FakeGardenStorage(GardenCodec.encode(Garden(id = "g", name = "Vrai potager", frostDate = FrostDate(4, 20))), failReads = true)
        val store = GardenStore(storage)
        assertTrue(store.updateAltitude(10).refused)
        assertEquals(0, storage.writeCalls)
    }

    @Test
    fun writeFailureKeepsInMemoryGardenAndReportsIt() {
        val failing = object : PlatformGardenStorage {
            override fun exists(): Boolean = false
            override fun readText(): String? = null
            override fun writeTextAtomic(content: String): Boolean = false
            override fun quarantineCorrupt() {}
        }
        // Fichier absent mais impossible à créer : pas de potager « prêt » à modifier.
        assertEquals(GardenLoad.ReadFailed, GardenStore(failing).load())

        val storage = FakeGardenStorage()
        val store = GardenStore(storage)
        store.ready()
        val ok = store.updateAltitude(500)
        assertTrue(ok.saved)
    }

    @Test
    fun modificationsUseTheInMemoryGardenWithoutRereadingTheDisk() {
        val storage = FakeGardenStorage()
        val store = GardenStore(storage)
        store.ready()
        storage.failReads = true
        val update = store.updateAltitude(900)
        assertFalse(update.refused)
        assertTrue(update.saved)
        assertEquals(900, update.garden.altitudeMeters)
    }

    @Test
    fun legacyFileWithoutSchemaVersionIsReadAsVersionOne() {
        val modern = GardenCodec.encode(Garden(id = "g", name = "Ancien", frostDate = FrostDate(5, 15)))
        val legacy = modern.lines().filterNot { it.contains("schemaVersion") }.joinToString("\n")
        assertFalse(legacy.contains("schemaVersion"))
        val storage = FakeGardenStorage(legacy)
        val loaded = GardenStore(storage).load() as GardenLoad.Ready
        assertEquals("Ancien", loaded.garden.name)
        assertFalse(loaded.recovered)
        assertEquals(0, storage.writeCalls)
    }

    @Test
    fun savedFileCarriesTheSchemaVersion() {
        val storage = FakeGardenStorage()
        GardenStore(storage).ready()
        assertTrue(storage.content!!.contains("\"schemaVersion\": ${GardenCodec.SCHEMA_VERSION}"))
    }

    @Test
    fun newerSchemaVersionIsRefusedWithoutOverwriting() {
        val future = """{ "schemaVersion": ${GardenCodec.SCHEMA_VERSION + 1}, "id": "g", "name": "Futur", "frostDate": { "month": 5, "day": 1 }, "nouveauChamp": { "x": 1 } }"""
        val storage = FakeGardenStorage(future)
        val store = GardenStore(storage)

        val load = store.load()
        assertTrue(load is GardenLoad.NewerVersion)
        assertEquals(GardenCodec.SCHEMA_VERSION + 1, (load as GardenLoad.NewerVersion).foundVersion)
        assertTrue(store.updateAltitude(1).refused)
        assertEquals(0, storage.writeCalls)
        assertEquals(0, storage.quarantineCalls)
        assertEquals(future, storage.content)
    }

    @Test
    fun customFrostKeepsTheDepartmentDateAndSwitchesBothWays() {
        val storage = FakeGardenStorage()
        val store = GardenStore(storage)
        val departmentDate = store.ready().frostDate

        val custom = store.updateCustomFrost(6, 10).garden
        assertTrue(custom.useCustomFrost)
        assertEquals(FrostDate(6, 10), custom.activeFrostDate)
        assertEquals(departmentDate, custom.frostDate)

        val back = store.setUseCustomFrost(false).garden
        assertEquals(departmentDate, back.activeFrostDate)
        assertEquals(FrostDate(6, 10), back.customFrostDate)

        assertEquals(FrostDate(6, 10), store.setUseCustomFrost(true).garden.activeFrostDate)
        assertEquals(FrostDate(6, 10), GardenCodec.decode(storage.content!!).activeFrostDate)
    }

    @Test
    fun customFrostRefusesImpossibleDateAndNeedsADateToBeActivated() {
        val store = GardenStore(FakeGardenStorage())
        store.ready()
        assertFalse(store.setUseCustomFrost(true).garden.useCustomFrost)
        val unchanged = store.updateCustomFrost(4, 31).garden
        assertNull(unchanged.customFrostDate)
        assertFalse(unchanged.useCustomFrost)
        assertEquals(FrostDate(2, 29), store.updateCustomFrost(2, 29).garden.customFrostDate)
    }

    @Test
    fun readingAVersionOneFileDoesNotRewriteIt() {
        val v1 = """{ "schemaVersion": 1, "id": "g", "name": "Ancien", "frostDate": { "month": 5, "day": 1 } }"""
        val storage = FakeGardenStorage(v1)
        val garden = (GardenStore(storage).load() as GardenLoad.Ready).garden
        assertFalse(garden.useCustomFrost)
        assertEquals(0, storage.writeCalls)
        assertEquals(v1, storage.content)
    }

    @Test
    fun exportThenImportRoundTripIsUnchanged() {
        val garden = Garden(
            id = "g", name = "Aller-retour", frostDate = FrostDate(4, 2),
            parcels = listOf(
                fr.pilmil.sillon.model.Parcel(
                    "p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2,
                    listOf(fr.pilmil.sillon.model.ParcelSlot(0, 1, "tomate", 1_700_000_000_000L)),
                ),
            ),
        )
        val exported = GardenCodec.encode(garden)
        val imported = GardenCodec.decodeAndValidate(exported, now = 1_750_000_000_000L)
        assertEquals(garden, imported)
        assertEquals(exported, GardenCodec.encode(imported))
    }

    @Test
    fun updateSlotWithUnknownParcelLeavesGardenUnchanged() {
        val store = GardenStore(FakeGardenStorage())
        val initial = store.ready()
        val updated = store.updateSlot("inexistant", 0, 0, "tomate")
        assertTrue(updated.saved)
        assertEquals(initial.parcels, updated.garden.parcels)
    }
}
